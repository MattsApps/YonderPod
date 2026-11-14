package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import de.danoeh.antennapod.smartplay.radio.AudioSniff;
import de.danoeh.antennapod.smartplay.radio.HlsSegments;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.StreamLinks;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Live pause (0.9.26): records a live station behind the listener while they're paused or playing behind
 * live. The stream is written in short chunks, each encrypted from its first byte through
 * {@link RecordingCipher#writer} (unbuffered, so nothing readable ever reaches the disk), in its own folder
 * under no_backup. A chunk's file name is the time its first byte arrived. Chunks that have been played
 * are deleted (see {@link #deleteBefore}). The buffer is never a recording or an episode: nothing here is
 * in the database, lists, Smart Plays, exports or backups.
 */
public final class LiveBuffer {
    private static final String TAG = "LiveBuffer";
    private static final String FOLDER = "live_buffer";
    private static final long CHUNK_MILLIS = 20_000;
    private static final long RECONNECT_MILLIS = 3000;
    private static final long WAIT_MILLIS = 100;

    @Nullable
    private static volatile LiveBuffer current;

    /** Told (on the main thread) when the stream can't be buffered: the station pauses as before. */
    public interface Unsupported {
        void onUnsupported(LiveBuffer buffer, String why);
    }

    static final class Chunk {
        final File file;
        final long start;
        volatile long end;
        volatile long bytes;

        Chunk(File file, long start) {
            this.file = file;
            this.start = start;
            this.end = start;
        }
    }

    private final File folder;
    private final String url;
    private final boolean hls;
    private final Unsupported unsupported;
    private final OkHttpClient client;
    private final List<Chunk> chunks = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Thread thread;
    private volatile boolean stopped;
    @Nullable
    private volatile Call call;
    @Nullable
    private OutputStream out;
    @Nullable
    private Chunk writing;
    private byte[] sniff = new byte[AudioSniff.BYTES_NEEDED];
    private int sniffed;

    private LiveBuffer(Context context, String url, @Nullable String mimeType, Unsupported unsupported) {
        this.folder = folder(context);
        this.url = url;
        this.hls = "application/x-mpegURL".equalsIgnoreCase(mimeType)
                || "application/vnd.apple.mpegurl".equalsIgnoreCase(mimeType)
                || url.toLowerCase(Locale.ROOT).contains(".m3u8");
        this.unsupported = unsupported;
        String userAgent = UserAgentInterceptor.liveRadioUserAgent();
        this.client = AntennapodHttpClient.getHttpClient().newBuilder()
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent).build()))
                .build();
        this.thread = new Thread(this::run, "LiveBuffer");
    }

    static File folder(Context context) {
        return new File(context.getNoBackupFilesDir(), FOLDER);
    }

    /** Starts buffering url (same User-Agent as live listening, no listener tag). */
    static LiveBuffer start(Context context, String url, @Nullable String mimeType, Unsupported unsupported) {
        LiveBuffer old = current;
        if (old != null) {
            old.stop();
        }
        deleteAll(folder(context));
        LiveBuffer buffer = new LiveBuffer(context, url, mimeType, unsupported);
        current = buffer;
        Log.d(TAG, "Buffering " + (buffer.hls ? "HLS" : "stream") + ", User-Agent "
                + UserAgentInterceptor.liveRadioUserAgent());
        buffer.thread.start();
        return buffer;
    }

    @Nullable
    static LiveBuffer current() {
        return current;
    }

    /**
     * Deletes anything left in the buffer folder, as when Android killed Yonder while paused. Called when
     * Yonder starts (RecordingRecovery) and when the player starts. Leaves a running buffer alone.
     */
    public static void deleteLeftovers(Context context) {
        if (current != null) {
            return;
        }
        int count = deleteAll(folder(context));
        if (count > 0) {
            Log.d(TAG, "Deleted " + count + " leftover buffer files");
        }
    }

    private static int deleteAll(File folder) {
        File[] files = folder.listFiles();
        int count = 0;
        if (files != null) {
            for (File file : files) {
                if (file.delete()) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Stops buffering and deletes every file. */
    void stop() {
        stopped = true;
        Call running = call;
        if (running != null) {
            running.cancel();
        }
        thread.interrupt();
        synchronized (this) {
            for (Chunk chunk : chunks) {
                if (!chunk.file.delete()) {
                    Log.d(TAG, "Couldn't delete " + chunk.file.getName() + " yet");
                }
            }
            chunks.clear();
        }
        if (current == this) {
            current = null;
        }
        Log.d(TAG, "Stopped and deleted");
    }

    boolean isStopped() {
        return stopped;
    }

    /** The first moment still in the buffer, or -1 if nothing has arrived yet. */
    synchronized long oldest() {
        return chunks.isEmpty() ? -1 : chunks.get(0).start;
    }

    /** The latest moment in the buffer, or -1 if nothing has arrived yet. */
    synchronized long newest() {
        return chunks.isEmpty() ? -1 : chunks.get(chunks.size() - 1).end;
    }

    /** The chunk and byte offset for the audio that arrived at time, or null if nothing has arrived yet. */
    @Nullable
    synchronized long[] locate(long time) {
        if (chunks.isEmpty()) {
            return null;
        }
        Chunk found = chunks.get(0);
        for (Chunk chunk : chunks) {
            if (chunk.start <= time) {
                found = chunk;
            }
        }
        long span = Math.max(1, found.end - found.start);
        long offset = found.bytes <= 0 ? 0
                : Math.max(0, Math.min(found.bytes, (time - found.start) * found.bytes / span));
        return new long[] {found.start, offset};
    }

    @Nullable
    synchronized File fileOf(long start) {
        for (Chunk chunk : chunks) {
            if (chunk.start == start) {
                return chunk.file;
            }
        }
        return null;
    }

    /** The start of the chunk after the one starting at start, or -1 if the writer hasn't moved on yet. */
    synchronized long nextAfter(long start) {
        for (Chunk chunk : chunks) {
            if (chunk.start > start) {
                return chunk.start;
            }
        }
        return -1;
    }

    /**
     * Deletes chunks that ended before time (the player passes the moment being heard, less a minute kept
     * for rewind). The chunk being written is never deleted.
     */
    synchronized void deleteBefore(long time) {
        while (chunks.size() > 1 && chunks.get(0).end < time) {
            Chunk chunk = chunks.remove(0);
            if (!chunk.file.delete()) {
                Log.d(TAG, "Couldn't delete " + chunk.file.getName());
            }
        }
    }

    private void run() {
        try {
            if (hls) {
                runHls();
            } else {
                runProgressive();
            }
        } catch (UnsupportedStream e) {
            Log.d(TAG, "Can't buffer this station: " + e.getMessage());
            stopped = true;
            main.post(() -> unsupported.onUnsupported(this, e.getMessage()));
        } catch (InterruptedException e) {
            // Stopped.
        } finally {
            closeChunk();
            if (stopped) {
                synchronized (this) {
                    for (Chunk chunk : chunks) {
                        chunk.file.delete();
                    }
                    chunks.clear();
                }
                if (writing != null) {
                    writing.file.delete();
                }
            }
        }
    }

    private void runProgressive() throws UnsupportedStream, InterruptedException {
        byte[] buffer = new byte[16 * 1024];
        while (!stopped) {
            Call next = client.newCall(new Request.Builder().url(url).build());
            call = next;
            try (Response response = next.execute()) {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    throw new IOException("HTTP " + response.code());
                }
                InputStream in = body.byteStream();
                int read;
                while (!stopped && (read = in.read(buffer)) != -1) {
                    write(buffer, read);
                }
            } catch (IOException e) {
                if (stopped) {
                    return;
                }
                Log.d(TAG, "Stream dropped, reconnecting: " + e.getMessage());
                Thread.sleep(RECONNECT_MILLIS);
            }
        }
    }

    private void runHls() throws UnsupportedStream, InterruptedException {
        String mediaUrl;
        while (true) {
            try {
                mediaUrl = mediaPlaylistUrl();
                break;
            } catch (IOException e) {
                if (stopped) {
                    return;
                }
                Log.d(TAG, "Playlist fetch failed, retrying: " + e.getMessage());
                Thread.sleep(RECONNECT_MILLIS);
            }
        }
        Set<Long> done = new HashSet<>();
        while (!stopped) {
            HlsSegments.Playlist playlist;
            try {
                playlist = HlsSegments.parse(text(mediaUrl), System.currentTimeMillis());
            } catch (IOException e) {
                Log.d(TAG, "Playlist fetch failed, retrying: " + e.getMessage());
                Thread.sleep(RECONNECT_MILLIS);
                continue;
            }
            if (playlist.isEncrypted()) {
                throw new UnsupportedStream("encrypted stream");
            }
            if (playlist.getInitUri() != null) {
                throw new UnsupportedStream("fragmented MP4 stream");
            }
            List<HlsSegments.Segment> segments = playlist.getSegments();
            for (HlsSegments.Segment segment : segments) {
                if (stopped) {
                    return;
                }
                // Starts at the newest segment, which is about where the listener paused.
                if (done.isEmpty() && segment != segments.get(segments.size() - 1)) {
                    continue;
                }
                if (done.add(segment.getSequence())) {
                    try {
                        copy(StreamLinks.resolve(mediaUrl, segment.getUri()));
                    } catch (IOException e) {
                        Log.d(TAG, "Segment failed: " + e.getMessage());
                    }
                }
            }
            Thread.sleep(Math.max(1000, playlist.getTargetDurationMillis() / 2));
        }
    }

    private String mediaPlaylistUrl() throws IOException, UnsupportedStream {
        String body = text(url);
        if (RewindWindow.isHlsMaster(body)) {
            String variant = HlsSegments.chooseVariant(body, Integer.MAX_VALUE);
            if (variant == null) {
                throw new UnsupportedStream("no stream in playlist");
            }
            return StreamLinks.resolve(url, variant);
        }
        return url;
    }

    private String text(String address) throws IOException {
        Call next = client.newCall(new Request.Builder().url(address).build());
        call = next;
        try (Response response = next.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return new String(response.body().bytes(), StandardCharsets.UTF_8);
        }
    }

    private void copy(String address) throws IOException, UnsupportedStream {
        Call next = client.newCall(new Request.Builder().url(address).build());
        call = next;
        try (Response response = next.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            InputStream in = response.body().byteStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while (!stopped && (read = in.read(buffer)) != -1) {
                write(buffer, read);
            }
        }
    }

    /** Writes through the cipher into the current chunk, starting a new chunk every 20 seconds. */
    private void write(byte[] data, int length) throws IOException, UnsupportedStream {
        if (sniff != null) {
            int take = Math.min(length, sniff.length - sniffed);
            System.arraycopy(data, 0, sniff, sniffed, take);
            sniffed += take;
            if (sniffed >= sniff.length) {
                String type = AudioSniff.mimeType(sniff, sniffed);
                sniff = null;
                if (!"audio/mpeg".equals(type) && !"audio/aac".equals(type) && !"video/mp2t".equals(type)) {
                    throw new UnsupportedStream(type);
                }
            }
        }
        long now = System.currentTimeMillis();
        if (out == null || writing == null || now - writing.start >= CHUNK_MILLIS) {
            startChunk(now);
        }
        if (out == null || writing == null) {
            return;
        }
        out.write(data, 0, length);
        writing.bytes += length;
        writing.end = now;
    }

    private void startChunk(long now) throws IOException {
        closeChunk();
        if (stopped) {
            return;
        }
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
        File file = new File(folder, String.format(Locale.US, "%013d", now) + RecordingCipher.EXTENSION);
        FileOutputStream stream = new FileOutputStream(file);
        try {
            out = RecordingCipher.writer(stream, RecordingKeys.get());
        } catch (IOException e) {
            stream.close();
            file.delete();
            throw e;
        }
        Chunk chunk = new Chunk(file, now);
        writing = chunk;
        synchronized (this) {
            if (stopped) {
                file.delete();
                return;
            }
            chunks.add(chunk);
        }
    }

    private void closeChunk() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                Log.d(TAG, "Closing chunk: " + e.getMessage());
            }
            out = null;
        }
    }

    /** Opens a chunk for reading, decrypting as it reads. */
    static RecordingCipher.Reader open(File file) throws IOException {
        return RecordingCipher.open(file, RecordingKeys.get());
    }

    static long waitMillis() {
        return WAIT_MILLIS;
    }

    private static final class UnsupportedStream extends Exception {
        UnsupportedStream(String message) {
            super(message);
        }
    }
}
