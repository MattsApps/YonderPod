package de.danoeh.antennapod.smartplay.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.StreamLinks;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public final class StreamCheck {
    private static final long MAX_TEXT = 512 * 1024;

    public static final class Result {
        public final String streamUrl;
        public final String mimeType;
        public final RewindWindow rewind;

        Result(String streamUrl, String mimeType, RewindWindow rewind) {
            this.streamUrl = streamUrl;
            this.mimeType = mimeType;
            this.rewind = rewind;
        }
    }

    private static final class Fetched {
        final String url;
        final String contentType;
        final String text;

        Fetched(String url, String contentType, String text) {
            this.url = url;
            this.contentType = contentType;
            this.text = text;
        }
    }

    private StreamCheck() {
    }

    public static Result check(String url) throws IOException {
        Fetched fetched = fetch(url);
        if (fetched.text == null) {
            return new Result(url, fetched.contentType, RewindWindow.liveOnly());
        }
        String text = fetched.text;
        if (text.contains("<MPD")) {
            return new Result(url, MediaItemAdapter.MIME_DASH, RewindWindow.fromDashManifest(text));
        }
        if (text.contains("#EXT-X-")) {
            if (RewindWindow.isHlsMaster(text)) {
                String variant = RewindWindow.firstVariant(text);
                if (variant == null) {
                    return new Result(url, MediaItemAdapter.MIME_HLS, RewindWindow.unknown());
                }
                Fetched media = fetch(StreamLinks.resolve(fetched.url, variant));
                return new Result(url, MediaItemAdapter.MIME_HLS, media.text != null
                        ? RewindWindow.fromHlsMediaPlaylist(media.text) : RewindWindow.unknown());
            }
            return new Result(url, MediaItemAdapter.MIME_HLS, RewindWindow.fromHlsMediaPlaylist(text));
        }
        String inner = StreamLinks.firstStream(text);
        if (inner != null && !inner.equals(url)) {
            return check(inner);
        }
        return new Result(url, fetched.contentType, RewindWindow.unknown());
    }

    private static Fetched fetch(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            String contentType = body != null && body.contentType() != null
                    ? body.contentType().type() + "/" + body.contentType().subtype() : "";
            String finalUrl = response.request().url().toString();
            if (body == null || !isText(contentType, finalUrl)) {
                return new Fetched(finalUrl, contentType, null);
            }
            BufferedSource source = body.source();
            Buffer buffer = new Buffer();
            while (buffer.size() < MAX_TEXT && source.read(buffer, 8192) != -1) {
                continue;
            }
            return new Fetched(finalUrl, contentType, buffer.readString(StandardCharsets.UTF_8));
        }
    }

    private static boolean isText(String contentType, String url) {
        String type = contentType.toLowerCase(Locale.ROOT);
        if (type.contains("mpegurl") || type.contains("scpls") || type.contains("dash") || type.contains("xml")
                || type.startsWith("text/") || type.contains("x-ms-asf")) {
            return true;
        }
        String path = url.toLowerCase(Locale.ROOT);
        return !type.startsWith("audio/") && (path.contains(".m3u") || path.contains(".mpd")
                || StreamLinks.looksLikePlaylistFile(url));
    }
}
