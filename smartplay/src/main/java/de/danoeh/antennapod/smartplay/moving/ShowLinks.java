package de.danoeh.antennapod.smartplay.moving;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShowLinks {
    public static final int FEED = 0;
    public static final int APPLE = 1;
    public static final int SPOTIFY = 2;
    public static final int NAME = 3;

    private static final Pattern URL = Pattern.compile(
            "(?i)\\b((?:https?|podcast|pcast|itpc|feed)://[^\\s<>\"']+|pcast:[^\\s<>\"']+)");
    private static final Pattern APPLE_ID = Pattern.compile("(?i)(?:podcasts|itunes)\\.apple\\.com/.*?id(\\d+)");
    private static final Pattern SPOTIFY_SHOW = Pattern.compile(
            "(?i)open\\.spotify\\.com/(?:[a-z-]+/)?show/([A-Za-z0-9]+)");
    private static final Pattern TRAILING = Pattern.compile("[).,;!?]+$");

    private ShowLinks() {
    }

    public static String extractUrl(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = URL.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return TRAILING.matcher(matcher.group(1)).replaceAll("");
    }

    public static int kind(String line) {
        String url = extractUrl(line);
        if (url == null) {
            return NAME;
        }
        if (appleId(url) != null) {
            return APPLE;
        }
        if (spotifyShowId(url) != null) {
            return SPOTIFY;
        }
        return FEED;
    }

    public static String appleId(String url) {
        Matcher matcher = APPLE_ID.matcher(url == null ? "" : url);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static String spotifyShowId(String url) {
        Matcher matcher = SPOTIFY_SHOW.matcher(url == null ? "" : url);
        return matcher.find() ? matcher.group(1) : null;
    }

    public static boolean isSpotifyEpisode(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).contains("open.spotify.com/")
                && url.toLowerCase(Locale.ROOT).contains("/episode/");
    }

    public static String withoutPodcastScheme(String url) {
        if (url != null && url.toLowerCase(Locale.ROOT).startsWith("podcast://")) {
            return "https://" + url.substring("podcast://".length());
        }
        return url;
    }

    public static List<String> lines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public static String normalisedName(String name) {
        if (name == null) {
            return "";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("&", " and ");
        plain = plain.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\b(the|a|podcast|show)\\b", " ");
        return plain.replaceAll("\\s+", " ").trim();
    }

    public static boolean sameShow(String wanted, String found) {
        String a = normalisedName(wanted);
        String b = normalisedName(found);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        return longer.startsWith(shorter + " ") && shorter.length() >= 0.6 * longer.length();
    }
}
