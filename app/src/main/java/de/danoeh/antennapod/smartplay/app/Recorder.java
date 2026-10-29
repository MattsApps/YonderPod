package de.danoeh.antennapod.smartplay.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

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

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.smartplay.radio.HlsSegments;
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

    public static Saved burst(String sourceUrl, int maxBitrate, ShowTimes.Airing airing, File folder, String name)
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
        File raw = new File(folder, name + ".part");
        try (OutputStream out = new FileOutputStream(raw)) {
            if (playlist.getInitUri() != null) {
                copy(StreamLinks.resolve(mediaUrl, playlist.getInitUri()), out);
            }
            for (HlsSegments.Segment segment : playlist.between(from, to)) {
                copy(StreamLinks.resolve(mediaUrl, segment.getUri()), out);
            }
        }
        return finish(raw, folder, name, segmentExtension(playlist));
    }

    public static Saved recordLive(String url, String mimeType, long until, File folder, String name,
                                   StopSignal stop) throws IOException {
        boolean hls = "application/x-mpegURL".equalsIgnoreCase(mimeType)
                || url.toLowerCase(Locale.ROOT).contains(".m3u8");
        if (hls) {
            return recordHls(url, until, folder, name, stop);
        }
        if ("application/dash+xml".equalsIgnoreCase(mimeType)) {
            throw new UnsupportedException("DASH streams can't be recorded yet");
        }
        return recordProgressive(url, until, folder, name, stop);
    }

    private static Saved recordHls(String url, long until, File folder, String name, StopSignal stop)
            throws IOException {
        String mediaUrl = mediaPlaylistUrl(url, Integer.MAX_VALUE);
        File raw = new File(folder, name + ".part");
        Set<Long> done = new HashSet<>();
        String extension = ".ts";
        boolean wroteInit = false;
        try (OutputStream out = new FileOutputStream(raw)) {
            while (System.currentTimeMillis() < until && !stop.shouldStop()) {
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
                extension = segmentExtension(playlist);
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
        }
        if (done.isEmpty()) {
            raw.delete();
            throw new IOException("Nothing was recorded");
        }
        return finish(raw, folder, name, extension);
    }

    private static Saved recordProgressive(String url, long until, File folder, String name, StopSignal stop)
            throws IOException {
        File raw = new File(folder, name + ".part");
        String extension = ".mp3";
        long written = 0;
        try (OutputStream out = new FileOutputStream(raw)) {
            while (System.currentTimeMillis() < until && !stop.shouldStop()) {
                Request request = new Request.Builder().url(url).build();
                try (Response response = AntennapodHttpClient.getHttpClient().newBuilder()
                        .readTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute()) {
                    ResponseBody body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        throw new IOException("HTTP " + response.code());
                    }
                    extension = extensionFor(body.contentType() != null ? body.contentType().toString() : "");
                    InputStream in = body.byteStream();
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while (System.currentTimeMillis() < until && !stop.shouldStop()
                            && (read = in.read(buffer)) != -1) {
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
            raw.delete();
            throw new IOException("Nothing was recorded");
        }
        File file = new File(folder, name + extension);
        if (!raw.renameTo(file)) {
            throw new IOException("Could not save recording");
        }
        return new Saved(file, mimeFor(extension));
    }

    private static Saved finish(File raw, File folder, String name, String extension) throws IOException {
        File m4a = new File(folder, name + ".m4a");
        if (remux(raw, m4a)) {
            raw.delete();
            return new Saved(m4a, "audio/mp4");
        }
        m4a.delete();
        File file = new File(folder, name + extension);
        if (!raw.renameTo(file)) {
            throw new IOException("Could not save recording");
        }
        return new Saved(file, mimeFor(extension));
    }

    static boolean remux(File in, File out) {
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            extractor.setDataSource(in.getAbsolutePath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/mp4a")) {
                    track = i;
                    break;
                }
            }
            if (track < 0) {
                return false;
            }
            extractor.selectTrack(track);
            MediaFormat format = extractor.getTrackFormat(track);
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long firstTime = -1;
            long lastTime = -1;
            int samples = 0;
            while (true) {
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) {
                    break;
                }
                long time = extractor.getSampleTime();
                if (firstTime < 0) {
                    firstTime = time;
                }
                time -= firstTime;
                if (time <= lastTime) {
                    time = lastTime + 1;
                }
                lastTime = time;
                info.presentationTimeUs = time;
                info.offset = 0;
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                muxer.writeSampleData(outTrack, buffer, info);
                samples++;
                extractor.advance();
            }
            muxer.stop();
            return samples > 0;
        } catch (Exception e) {
            Log.d(TAG, "Remux failed: " + e.getMessage());
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {
                    Log.d(TAG, "Could not release muxer");
                }
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
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return new String(response.body().bytes(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(String url, OutputStream out) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
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

    private static String segmentExtension(HlsSegments.Playlist playlist) {
        if (playlist.getInitUri() != null) {
            return ".mp4";
        }
        if (!playlist.getSegments().isEmpty()) {
            String uri = playlist.getSegments().get(0).getUri().toLowerCase(Locale.ROOT);
            if (uri.contains(".aac")) {
                return ".aac";
            }
            if (uri.contains(".mp3")) {
                return ".mp3";
            }
        }
        return ".ts";
    }

    private static String extensionFor(String contentType) {
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("aac")) {
            return ".aac";
        }
        if (type.contains("ogg") || type.contains("opus")) {
            return ".ogg";
        }
        if (type.contains("flac")) {
            return ".flac";
        }
        return ".mp3";
    }

    private static String mimeFor(String extension) {
        switch (extension) {
            case ".aac":
                return "audio/aac";
            case ".ogg":
                return "audio/ogg";
            case ".flac":
                return "audio/flac";
            case ".mp4":
                return "audio/mp4";
            case ".ts":
                return "video/mp2t";
            case ".mp3":
            default:
                return "audio/mpeg";
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
