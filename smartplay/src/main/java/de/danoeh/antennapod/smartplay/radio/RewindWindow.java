package de.danoeh.antennapod.smartplay.radio;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RewindWindow {
    public static final long LIVE_ONLY_BELOW_SECONDS = 120;

    public enum Kind {
        REWIND,
        LIVE_ONLY,
        NOT_LIVE,
        UNKNOWN
    }

    private static final Pattern EXTINF = Pattern.compile("#EXTINF:\\s*([0-9.]+)");
    private static final Pattern MPD_TYPE = Pattern.compile("<MPD[^>]*\\stype=\"([a-zA-Z]+)\"", Pattern.DOTALL);
    private static final Pattern MPD_DEPTH =
            Pattern.compile("<MPD[^>]*\\stimeShiftBufferDepth=\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern DURATION = Pattern.compile(
            "P(?:([0-9.]+)D)?(?:T(?:([0-9.]+)H)?(?:([0-9.]+)M)?(?:([0-9.]+)S)?)?");

    private final Kind kind;
    private final long seconds;

    private RewindWindow(Kind kind, long seconds) {
        this.kind = kind;
        this.seconds = seconds;
    }

    public Kind getKind() {
        return kind;
    }

    public long getSeconds() {
        return seconds;
    }

    public static RewindWindow liveOnly() {
        return new RewindWindow(Kind.LIVE_ONLY, 0);
    }

    public static RewindWindow unknown() {
        return new RewindWindow(Kind.UNKNOWN, 0);
    }

    private static RewindWindow ofSeconds(long seconds) {
        if (seconds < LIVE_ONLY_BELOW_SECONDS) {
            return new RewindWindow(Kind.LIVE_ONLY, seconds);
        }
        return new RewindWindow(Kind.REWIND, seconds);
    }

    public static boolean isHlsMaster(String playlist) {
        return playlist.contains("#EXT-X-STREAM-INF");
    }

    public static String firstVariant(String masterPlaylist) {
        String[] lines = masterPlaylist.split("\\r?\\n");
        boolean nextIsUri = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                nextIsUri = true;
            } else if (nextIsUri && !line.isEmpty() && !line.startsWith("#")) {
                return line;
            }
        }
        return null;
    }

    public static RewindWindow fromHlsMediaPlaylist(String playlist) {
        if (!playlist.contains("#EXTM3U")) {
            return unknown();
        }
        if (playlist.contains("#EXT-X-ENDLIST")) {
            return new RewindWindow(Kind.NOT_LIVE, 0);
        }
        Matcher matcher = EXTINF.matcher(playlist);
        double total = 0;
        boolean any = false;
        while (matcher.find()) {
            try {
                total += Double.parseDouble(matcher.group(1));
                any = true;
            } catch (NumberFormatException e) {
                return unknown();
            }
        }
        return any ? ofSeconds(Math.round(total)) : unknown();
    }

    public static RewindWindow fromDashManifest(String manifest) {
        Matcher type = MPD_TYPE.matcher(manifest);
        if (!type.find()) {
            return unknown();
        }
        if (!"dynamic".equals(type.group(1).toLowerCase(Locale.ROOT))) {
            return new RewindWindow(Kind.NOT_LIVE, 0);
        }
        Matcher depth = MPD_DEPTH.matcher(manifest);
        if (!depth.find()) {
            return unknown();
        }
        long seconds = parseIsoDuration(depth.group(1));
        return seconds < 0 ? unknown() : ofSeconds(seconds);
    }

    public static long parseIsoDuration(String value) {
        Matcher matcher = DURATION.matcher(value.trim());
        if (!matcher.matches() || value.trim().equals("P") || value.trim().equals("PT")) {
            return -1;
        }
        double seconds = 0;
        double[] factors = {86400, 3600, 60, 1};
        for (int i = 0; i < factors.length; i++) {
            String part = matcher.group(i + 1);
            if (part != null) {
                seconds += Double.parseDouble(part) * factors[i];
            }
        }
        return Math.round(seconds);
    }
}
