package de.danoeh.antennapod.smartplay.app;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.smartplay.radio.AudioSniff;
import de.danoeh.antennapod.smartplay.radio.HlsSegments;
import de.danoeh.antennapod.smartplay.radio.Mp4AudioWriter;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.smartplay.radio.StreamLinks;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Recorder {
    private static final String TAG = "Recorder";
    private static final long LATE_START_TOLERANCE = 5 * 60_000L;
    private static final String PART = ".part";

    public static final class Saved {
        public final File file;
        public final String mimeType;

        Saved(File file, String mimeType) {
            this.file = file;
            this.mimeType = mimeType;
        }
    }

    public static class NotYetException extends IOException {
        NotYetException() {
            super("The show isn't in the stream yet");
        }
    }

    public static class GoneException extends IOException {
        GoneException() {
            super("The show is no longer in the stream");
        }
    }

    public static class UnsupportedException extends IOException {
        UnsupportedException(String message) {
            super(message);
        }
    }

    public interface StopSignal {
        boolean shouldStop();
    }

    private Recorder() {
    }

    /** Where a recording is written while it's being made: encrypted from the first byte. */
    static File partFile(File folder, String name) {
        return new File(folder, name + PART + RecordingCipher.EXTENSION);
    }

    /** Where a finished recording goes. */
    static File finalFile(File folder, String name) {
        return new File(folder, name + RecordingCipher.EXTENSION);
    }

    /** The finished name for a part file, or null if it isn't one. */
    @Nullable
    static File finalFileFor(File part) {
        String suffix = PART + RecordingCipher.EXTENSION;
        String name = part.getName();
        if (!name.endsWith(suffix)) {
            return null;
        }
        return new File(part.getParentFile(), name.substring(0, name.length() - suffix.length())
                + RecordingCipher.EXTENSION);
    }

    static boolean isPartFile(File file) {
        return file.getName().endsWith(PART + RecordingCipher.EXTENSION);
    }

    /** Encrypts everything written to it. Unbuffered, so what has arrived is on disk if Yonder is closed. */
    private static OutputStream encryptedOutput(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            return RecordingCipher.writer(out, RecordingKeys.get());
        } catch (IOException e) {
            out.close();
            throw e;
        }
    }

    public static Saved burst(String sourceUrl, int maxBitrate, ShowTimes.Airing airing, File part)
            throws IOException {
        String mediaUrl = mediaPlaylistUrl(sourceUrl, maxBitrate);
        HlsSegments.Playlist playlist = HlsSegments.parse(text(mediaUrl), System.currentTimeMillis());
        if (playlist.isEncrypted()) {
            throw new UnsupportedException("Encrypted stream");
        }
        if (!playlist.hasClockTimes()) {
            throw new UnsupportedException("No broadcast times in the stream");
        }
        long from = airing.getStart() - RecordingRules.LIVE_PADDING_MILLIS;
        long to = airing.getEnd() + RecordingRules.LIVE_PADDING_MILLIS;
        List<HlsSegments.Segment> segments = playlist.getSegments();
        if (segments.isEmpty() || segments.get(segments.size() - 1).getEnd() < airing.getEnd()) {
            throw new NotYetException();
        }
        if (segments.get(0).getStart() > airing.getStart() + LATE_START_TOLERANCE) {
            throw new GoneException();
        }
        try (OutputStream out = encryptedOutput(part)) {
            if (playlist.getInitUri() != null) {
                copy(StreamLinks.resolve(mediaUrl, playlist.getInitUri()), out);
            }
            for (HlsSegments.Segment segment : playlist.between(from, to)) {
                copy(StreamLinks.resolve(mediaUrl, segment.getUri()), out);
            }
        }
        return finish(part);
    }

    /**
     * Records a live stream into part (encrypted as it's written) until stop says so, then finishes it.
     * If Yonder is closed part-way, the part file stays, encrypted, and is finished next time Yonder starts.
     */
    public static Saved recordLive(String url, String mimeType, File part, StopSignal stop) throws IOException {
        boolean hls = "application/x-mpegURL".equalsIgnoreCase(mimeType)
                || url.toLowerCase(Locale.ROOT).contains(".m3u8");
        if (hls) {
            return recordHls(url, part, stop);
        }
        if ("application/dash+xml".equalsIgnoreCase(mimeType)) {
            throw new UnsupportedException("DASH streams can't be recorded yet");
        }
        return recordProgressive(url, part, stop);
    }

    private static Saved recordHls(String url, File part, StopSignal stop) throws IOException {
        String mediaUrl = mediaPlaylistUrl(url, Integer.MAX_VALUE);
        Set<Long> done = new HashSet<>();
        boolean wroteInit = false;
        try (OutputStream out = encryptedOutput(part)) {
            while (!stop.shouldStop()) {
                HlsSegments.Playlist playlist;
                try {
                    playlist = HlsSegments.parse(text(mediaUrl), System.currentTimeMillis());
                } catch (IOException e) {
                    Log.d(TAG, "Playlist fetch failed, retrying: " + e.getMessage());
                    sleep(5000);
                    continue;
                }
                if (playlist.isEncrypted()) {
                    throw new UnsupportedException("Encrypted stream");
                }
                if (!wroteInit && playlist.getInitUri() != null) {
                    copy(StreamLinks.resolve(mediaUrl, playlist.getInitUri()), out);
                    wroteInit = true;
                }
                for (HlsSegments.Segment segment : playlist.getSegments()) {
                    if (done.isEmpty() && segment != playlist.getSegments().get(playlist.getSegments().size() - 1)) {
                        continue;
                    }
                    if (done.add(segment.getSequence())) {
                        try {
                            copy(StreamLinks.resolve(mediaUrl, segment.getUri()), out);
                        } catch (IOException e) {
                            Log.d(TAG, "Segment failed: " + e.getMessage());
                        }
                    }
                }
                sleep(Math.max(1000, playlist.getTargetDurationMillis() / 2));
            }
        } catch (UnsupportedException e) {
            FileUtils.deleteQuietly(part);
            throw e;
        }
        if (done.isEmpty()) {
            FileUtils.deleteQuietly(part);
            throw new IOException("Nothing was recorded");
        }
        return finish(part);
    }

    private static Saved recordProgressive(String url, File part, StopSignal stop) throws IOException {
        long written = 0;
        try (OutputStream out = encryptedOutput(part)) {
            while (!stop.shouldStop()) {
                Request request = new Request.Builder().url(url).build();
                try (Response response = RecordingHttp.client().newBuilder()
                        .readTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    InputStream in = body.byteStream();
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while (!stop.shouldStop() && (read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        written += read;
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Stream dropped, reconnecting: " + e.getMessage());
                    sleep(3000);
                }
            }
        }
        if (written == 0) {
            FileUtils.deleteQuietly(part);
            throw new IOException("Nothing was recorded");
        }
        return finish(part);
    }

    /**
     * Turns a part file into a finished recording, encrypted throughout. AAC (most HLS streams) becomes a
     * seekable .m4a; anything else (MP3, for example) is kept as it came. Used when a recording ends, and when
     * Yonder starts again after one was cut short.
     */
    static Saved finish(File part) throws IOException {
        File target = finalFileFor(part);
        if (target == null) {
            throw new IOException("Not a recording in progress: " + part.getName());
        }
        if (dataLength(part) <= 0) {
            FileUtils.deleteQuietly(part);
            throw new IOException("Nothing was recorded");
        }
        if (remux(part, target)) {
            FileUtils.deleteQuietly(part);
            return new Saved(target, "audio/mp4");
        }
        FileUtils.deleteQuietly(target);
        String mimeType = sniff(part);
        if (!part.renameTo(target)) {
            throw new IOException("Could not save recording");
        }
        return new Saved(target, mimeType);
    }

    /** How much audio an encrypted recording holds, or -1 if it can't be opened. */
    static long dataLength(File file) {
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, RecordingKeys.get())) {
            return reader.length();
        } catch (IOException e) {
            return -1;
        }
    }

    static String sniff(File file) {
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, RecordingKeys.get())) {
            byte[] start = new byte[AudioSniff.BYTES_NEEDED];
            int length = 0;
            while (length < start.length) {
                int read = reader.read(length, start, length, start.length - length);
                if (read <= 0) {
                    break;
                }
                length += read;
            }
            return AudioSniff.mimeType(start, length);
        } catch (IOException e) {
            return "audio/mpeg";
        }
    }

    /**
     * Rewrites an encrypted recording's AAC audio as an encrypted .m4a, reading and writing only through the
     * cipher: nothing unencrypted is written, not even for a moment. Two passes over the audio: the first
     * measures every frame for the index, the second copies the frames after it.
     */
    static boolean remux(File part, File target) {
        Mp4AudioWriter mp4;
        try {
            mp4 = measure(part);
        } catch (Exception e) {
            Log.d(TAG, "Not remuxing " + part.getName() + ": " + e.getMessage());
            return false;
        }
        if (mp4 == null) {
            return false;
        }
        Track track = null;
        try (OutputStream out = new BufferedOutputStream(encryptedOutput(target), 64 * 1024)) {
            track = Track.open(part);
            if (track == null) {
                return false;
            }
            out.write(mp4.header());
            byte[] frame = new byte[track.buffer.capacity()];
            for (int i = 0; i < mp4.getSampleCount(); i++) {
                track.buffer.clear();
                int size = track.extractor.readSampleData(track.buffer, 0);
                if (size != mp4.getSampleSize(i)) {
                    throw new IOException("The audio read differently the second time");
                }
                track.buffer.position(0);
                track.buffer.limit(size);
                track.buffer.get(frame, 0, size);
                out.write(frame, 0, size);
                track.extractor.advance();
            }
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Remux failed: " + e.getMessage());
            return false;
        } finally {
            if (track != null) {
                track.close();
            }
        }
    }

    @Nullable
    private static Mp4AudioWriter measure(File part) throws IOException {
        Track track = Track.open(part);
        if (track == null) {
            return null;
        }
        try {
            ByteBuffer csd = track.format.getByteBuffer("csd-0");
            if (csd == null || !track.format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    || !track.format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                return null;
            }
            byte[] config = new byte[csd.remaining()];
            csd.duplicate().get(config);
            Mp4AudioWriter mp4 = new Mp4AudioWriter(track.format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    track.format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), config);
            while (true) {
                track.buffer.clear();
                int size = track.extractor.readSampleData(track.buffer, 0);
                if (size < 0) {
                    break;
                }
                mp4.addSample(size, track.extractor.getSampleTime());
                track.extractor.advance();
            }
            return mp4.getSampleCount() > 0 ? mp4 : null;
        } finally {
            track.close();
        }
    }

    /** The AAC track of an encrypted recording, read through the cipher. */
    private static final class Track {
        final RecordingCipher.Reader reader;
        final MediaExtractor extractor;
        final MediaFormat format;
        final ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);

        private Track(RecordingCipher.Reader reader, MediaExtractor extractor, MediaFormat format) {
            this.reader = reader;
            this.extractor = extractor;
            this.format = format;
        }

        @Nullable
        static Track open(File file) throws IOException {
            RecordingCipher.Reader reader = RecordingCipher.open(file, RecordingKeys.get());
            MediaExtractor extractor = new MediaExtractor();
            try {
                extractor.setDataSource(new RecordingSource(reader));
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/mp4a")) {
                        extractor.selectTrack(i);
                        return new Track(reader, extractor, format);
                    }
                }
            } catch (IOException | RuntimeException e) {
                Log.d(TAG, "Can't read the audio: " + e.getMessage());
            }
            extractor.release();
            reader.close();
            return null;
        }

        void close() {
            try {
                extractor.release();
            } catch (Exception ignored) {
                Log.d(TAG, "Could not release extractor");
            }
            try {
                reader.close();
            } catch (IOException ignored) {
                Log.d(TAG, "Could not close recording");
            }
        }
    }

    public static RewindWindow bestWindow(List<String> candidates, String[] chosenUrl) {
        RewindWindow best = null;
        for (String candidate : candidates) {
            try {
                RewindWindow window = StreamCheck.check(candidate).rewind;
                if (best == null || window.getSeconds() > best.getSeconds()
                        || (best.getKind() != RewindWindow.Kind.REWIND
                        && window.getKind() == RewindWindow.Kind.REWIND)) {
                    best = window;
                    chosenUrl[0] = candidate;
                }
            } catch (IOException e) {
                Log.d(TAG, "Could not check " + candidate);
            }
        }
        return best;
    }

    private static String mediaPlaylistUrl(String url, int maxBitrate) throws IOException {
        String body = text(url);
        if (RewindWindow.isHlsMaster(body)) {
            String variant = HlsSegments.chooseVariant(body, maxBitrate);
            if (variant == null) {
                throw new UnsupportedException("No stream in playlist");
            }
            return StreamLinks.resolve(url, variant);
        }
        if (!body.contains("#EXTM3U")) {
            throw new UnsupportedException("Not an HLS stream");
        }
        return url;
    }

    private static String text(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = RecordingHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return new String(response.body().bytes(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(String url, OutputStream out) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = RecordingHttp.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " for " + url);
            }
            try (InputStream in = response.body().byteStream()) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
