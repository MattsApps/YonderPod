package de.danoeh.antennapod.smartplay.app;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.common.UrlChecker;
import de.danoeh.antennapod.smartplay.moving.ShowLinks;
import io.reactivex.rxjava3.core.Single;
import okhttp3.Request;
import okhttp3.Response;

public final class ShowResolver {
    private static final Pattern OG_TITLE = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_TITLE_REVERSED = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE);

    public static final class Match {
        public final String input;
        public final int kind;
        public String title = "";
        public String author = "";
        public String imageUrl = "";
        @Nullable
        public String feedUrl;
        public boolean spotifyOnly;
        public boolean closest;

        Match(String input, int kind) {
            this.input = input;
            this.kind = kind;
        }

        public boolean found() {
            return feedUrl != null;
        }
    }

    public static class SpotifyOnlyException extends IOException {
        public final String name;

        public SpotifyOnlyException(String name) {
            super(name);
            this.name = name;
        }
    }

    private ShowResolver() {
    }

    public static Single<String> spotifyFeed(String url) {
        return Single.fromCallable(() -> {
            Match match = resolve(url);
            if (match.feedUrl == null) {
                throw new SpotifyOnlyException(match.title);
            }
            return match.feedUrl;
        });
    }

    public static Match resolve(String line) {
        int kind = ShowLinks.kind(line);
        Match match = new Match(line, kind);
        String url = ShowLinks.extractUrl(line);
        try {
            switch (kind) {
                case ShowLinks.FEED:
                    match.feedUrl = UrlChecker.prepareUrl(ShowLinks.withoutPodcastScheme(url));
                    match.title = match.feedUrl;
                    break;
                case ShowLinks.APPLE:
                    appleLookup(ShowLinks.appleId(url), match);
                    break;
                case ShowLinks.SPOTIFY:
                    String name = spotifyName(url);
                    if (name != null) {
                        match.title = name;
                        search(name, match, true);
                        match.spotifyOnly = match.feedUrl == null;
                    }
                    break;
                case ShowLinks.NAME:
                default:
                    match.title = line;
                    search(line, match, false);
                    break;
            }
        } catch (IOException | JSONException e) {
            match.feedUrl = null;
        }
        return match;
    }

    private static String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).header("Accept-Language", "en").build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private static void appleLookup(String id, Match match) throws IOException, JSONException {
        JSONArray results = new JSONObject(get("https://itunes.apple.com/lookup?id=" + id)).optJSONArray("results");
        if (results == null || results.length() == 0) {
            return;
        }
        JSONObject show = results.getJSONObject(0);
        fill(match, show);
    }

    private static void fill(Match match, JSONObject show) {
        match.title = show.optString("collectionName", match.title);
        match.author = show.optString("artistName", "");
        match.imageUrl = show.optString("artworkUrl100", "");
        String feedUrl = show.optString("feedUrl", "");
        match.feedUrl = feedUrl.isEmpty() ? null : feedUrl;
    }

    private static void search(String name, Match match, boolean exactOnly) throws IOException, JSONException {
        String query;
        try {
            query = URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            query = name;
        }
        JSONArray results = new JSONObject(get("https://itunes.apple.com/search?media=podcast&entity=podcast&term="
                + query)).optJSONArray("results");
        if (results == null) {
            return;
        }
        JSONObject first = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject show = results.getJSONObject(i);
            if (!show.has("feedUrl")) {
                continue;
            }
            if (first == null) {
                first = show;
            }
            if (ShowLinks.sameShow(name, show.optString("collectionName"))) {
                fill(match, show);
                return;
            }
        }
        if (!exactOnly && first != null) {
            fill(match, first);
            match.closest = true;
        }
    }

    @Nullable
    private static String spotifyName(String url) throws IOException {
        String id = ShowLinks.spotifyShowId(url);
        if (id == null) {
            return null;
        }
        String page = get("https://open.spotify.com/show/" + id);
        Matcher matcher = OG_TITLE.matcher(page);
        if (!matcher.find()) {
            matcher = OG_TITLE_REVERSED.matcher(page);
            if (!matcher.find()) {
                return null;
            }
        }
        String title = HtmlCompat.fromHtml(matcher.group(1), HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim();
        int bar = title.indexOf(" | ");
        return bar > 0 ? title.substring(0, bar) : title;
    }
}
