package de.danoeh.antennapod.smartplay.radio;

import java.util.Locale;

public final class StreamLinks {

    private StreamLinks() {
    }

    public static boolean looksLikePlaylistFile(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.endsWith(".pls") || path.endsWith(".m3u") || path.endsWith(".asx");
    }

    public static String firstStream(String playlist) {
        for (String raw : playlist.split("\\r?\\n")) {
            String line = raw.trim();
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.matches("file\\d+=.*")) {
                line = line.substring(line.indexOf('=') + 1).trim();
                lower = line.toLowerCase(Locale.ROOT);
            } else if (lower.contains("href=\"")) {
                int start = lower.indexOf("href=\"") + 6;
                int end = line.indexOf('"', start);
                if (end > start) {
                    line = line.substring(start, end).trim();
                    lower = line.toLowerCase(Locale.ROOT);
                }
            }
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                return line;
            }
        }
        return null;
    }

    public static String resolve(String base, String relative) {
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        int query = base.indexOf('?');
        String path = query >= 0 ? base.substring(0, query) : base;
        if (relative.startsWith("/")) {
            int schemeEnd = path.indexOf("://");
            int hostEnd = path.indexOf('/', schemeEnd + 3);
            return (hostEnd >= 0 ? path.substring(0, hostEnd) : path) + relative;
        }
        return path.substring(0, path.lastIndexOf('/') + 1) + relative;
    }

    public static String nameFromUrl(String url) {
        String rest = url.replaceFirst("^[a-zA-Z]+://", "");
        int slash = rest.indexOf('/');
        String host = slash >= 0 ? rest.substring(0, slash) : rest;
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }
}
