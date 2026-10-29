package de.danoeh.antennapod.smartplay.app;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class RadioBrowser {
    private static final String[] SERVERS = {
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
    };
    private static final int LIMIT = 40;
    private static final int FEW = 10;

    public static final class Station {
        public final String id;
        public final String name;
        public final String streamUrl;
        public final String homepage;
        public final String logoUrl;
        public final String country;
        public final String countryCode;
        public final String codec;
        public final int bitrate;
        public final boolean hls;

        Station(String id, String name, String streamUrl, String homepage, String logoUrl, String country,
                String countryCode, String codec, int bitrate, boolean hls) {
            this.id = id;
            this.name = name;
            this.streamUrl = streamUrl;
            this.homepage = homepage;
            this.logoUrl = logoUrl;
            this.country = country;
            this.countryCode = countryCode;
            this.codec = codec;
            this.bitrate = bitrate;
            this.hls = hls;
        }
    }

    private RadioBrowser() {
    }

    public static List<Station> search(String query) throws IOException {
        Map<String, Station> found = new LinkedHashMap<>();
        for (Station station : fetch("name", query.trim())) {
            found.put(station.id, station);
        }
        if (found.size() < FEW) {
            for (Station station : fetch("tag", query.trim())) {
                if (!found.containsKey(station.id)) {
                    found.put(station.id, station);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    public static void countClick(String stationId) {
        if (stationId == null || stationId.isEmpty()) {
            return;
        }
        for (String server : SERVERS) {
            Request request = new Request.Builder().url(server + "/json/url/" + Uri.encode(stationId)).build();
            try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return;
                }
            } catch (IOException e) {
                continue;
            }
        }
    }

    private static List<Station> fetch(String field, String value) throws IOException {
        IOException failure = null;
        for (String server : SERVERS) {
            String url = Uri.parse(server + "/json/stations/search").buildUpon()
                    .appendQueryParameter(field, value)
                    .appendQueryParameter("limit", String.valueOf(LIMIT))
                    .appendQueryParameter("hidebroken", "true")
                    .appendQueryParameter("order", "clickcount")
                    .appendQueryParameter("reverse", "true")
                    .build().toString();
            Request request = new Request.Builder().url(url).build();
            try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    failure = new IOException("Radio Browser answered " + response.code());
                    continue;
                }
                return parse(response.body().string());
            } catch (IOException e) {
                failure = e;
            } catch (JSONException e) {
                failure = new IOException(e);
            }
        }
        throw failure != null ? failure : new IOException("No Radio Browser server");
    }

    private static List<Station> parse(String json) throws JSONException {
        List<Station> result = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject station = array.getJSONObject(i);
            String stream = station.optString("url_resolved", "");
            if (stream.isEmpty()) {
                stream = station.optString("url", "");
            }
            String name = station.optString("name", "").trim();
            if (stream.isEmpty() || name.isEmpty()) {
                continue;
            }
            result.add(new Station(station.optString("stationuuid", stream), name, stream,
                    station.optString("homepage", ""), station.optString("favicon", ""),
                    station.optString("country", ""), station.optString("countrycode", ""),
                    station.optString("codec", ""), station.optInt("bitrate", 0), station.optInt("hls", 0) == 1));
        }
        return result;
    }
}
