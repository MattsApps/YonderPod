package de.danoeh.antennapod.smartplay.app;

import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class LibriVox {
    private static final String API = "https://librivox.org/api/feed/audiobooks";
    private static final int LIMIT = 20;

    public static final class Book {
        public final String id;
        public final String title;
        public final String authors;
        public final List<String> readers;
        public final long seconds;
        public final String language;
        public final String rssUrl;
        public final String coverUrl;

        Book(String id, String title, String authors, List<String> readers, long seconds, String language,
             String rssUrl, String coverUrl) {
            this.id = id;
            this.title = title;
            this.authors = authors;
            this.readers = readers;
            this.seconds = seconds;
            this.language = language;
            this.rssUrl = rssUrl;
            this.coverUrl = coverUrl;
        }
    }

    private LibriVox() {
    }

    public static List<Book> search(String query) throws IOException {
        String trimmed = query.trim();
        Map<String, Book> found = new LinkedHashMap<>();
        IOException failure = null;
        List<String[]> searches = new ArrayList<>();
        searches.add(new String[] {"title", trimmed});
        searches.add(new String[] {"author", trimmed});
        String[] words = trimmed.split("\\s+");
        if (words.length > 1) {
            searches.add(new String[] {"author", words[words.length - 1]});
        }
        searches.add(new String[] {"genre", trimmed});
        int succeeded = 0;
        for (String[] search : searches) {
            try {
                for (Book book : fetch(search[0], search[1])) {
                    if (!found.containsKey(book.id)) {
                        found.put(book.id, book);
                    }
                }
                succeeded++;
            } catch (IOException e) {
                failure = e;
            }
        }
        if (succeeded == 0 && failure != null) {
            throw failure;
        }
        return new ArrayList<>(found.values());
    }

    private static List<Book> fetch(String field, String value) throws IOException {
        String url = Uri.parse(API).buildUpon()
                .appendQueryParameter(field, value)
                .appendQueryParameter("format", "json")
                .appendQueryParameter("extended", "1")
                .appendQueryParameter("coverart", "1")
                .appendQueryParameter("limit", String.valueOf(LIMIT))
                .build().toString();
        Request request = new Request.Builder().url(url).build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            if (response.code() == 404) {
                return new ArrayList<>();
            }
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("LibriVox answered " + response.code());
            }
            return parse(response.body().string());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    static List<Book> parse(String json) throws JSONException {
        List<Book> result = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray books = root.optJSONArray("books");
        if (books == null) {
            return result;
        }
        for (int i = 0; i < books.length(); i++) {
            JSONObject book = books.getJSONObject(i);
            String rss = book.optString("url_rss", "");
            if (rss.isEmpty()) {
                continue;
            }
            List<String> authorNames = new ArrayList<>();
            JSONArray authors = book.optJSONArray("authors");
            if (authors != null) {
                for (int a = 0; a < authors.length(); a++) {
                    JSONObject author = authors.getJSONObject(a);
                    String name = (author.optString("first_name", "") + " "
                            + author.optString("last_name", "")).trim();
                    if (!name.isEmpty()) {
                        authorNames.add(name);
                    }
                }
            }
            Set<String> readers = new LinkedHashSet<>();
            JSONArray sections = book.optJSONArray("sections");
            if (sections != null) {
                for (int s = 0; s < sections.length(); s++) {
                    JSONArray sectionReaders = sections.getJSONObject(s).optJSONArray("readers");
                    if (sectionReaders == null) {
                        continue;
                    }
                    for (int r = 0; r < sectionReaders.length(); r++) {
                        String name = sectionReaders.getJSONObject(r).optString("display_name", "").trim();
                        if (!name.isEmpty()) {
                            readers.add(name);
                        }
                    }
                }
            }
            result.add(new Book(book.optString("id", rss), book.optString("title", "").trim(),
                    TextUtils.join(", ", authorNames), new ArrayList<>(readers), book.optLong("totaltimesecs", 0),
                    book.optString("language", ""), rss, book.optString("coverart_thumbnail", "")));
        }
        return result;
    }
}
