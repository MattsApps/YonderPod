package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;

public final class RadioPreferences {
    public static final String PREFS = "YonderRadio";
    private static final String KEY_STATION = "station_";
    /** A station's own volume boost (1.0.1), as a VolumeAdaptionSetting code. Absent or 0 means usual. */
    private static final String KEY_BOOST = "boost_";

    public enum Quality {
        LOW(80_000),
        STANDARD(160_000),
        HIGH(Integer.MAX_VALUE);

        public final int maxBitrate;

        Quality(int maxBitrate) {
            this.maxBitrate = maxBitrate;
        }
    }

    public static final class Station {
        public final String country;
        public final String countryCode;
        public final String codec;
        public final int bitrate;
        public final String directoryId;
        public final Quality quality;

        public Station(String country, String countryCode, String codec, int bitrate, String directoryId,
                       Quality quality) {
            this.country = country != null ? country : "";
            this.countryCode = countryCode != null ? countryCode : "";
            this.codec = codec != null ? codec : "";
            this.bitrate = bitrate;
            this.directoryId = directoryId != null ? directoryId : "";
            this.quality = quality != null ? quality : Quality.STANDARD;
        }

        public Station withQuality(Quality newQuality) {
            return new Station(country, countryCode, codec, bitrate, directoryId, newQuality);
        }
    }

    private static SharedPreferences prefs;

    private RadioPreferences() {
    }

    public static void init(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public static Station get(long itemId) {
        String json = prefs != null ? prefs.getString(KEY_STATION + itemId, null) : null;
        if (json == null) {
            return new Station("", "", "", 0, "", Quality.STANDARD);
        }
        try {
            JSONObject object = new JSONObject(json);
            Quality quality;
            try {
                quality = Quality.valueOf(object.optString("quality", Quality.STANDARD.name()));
            } catch (IllegalArgumentException e) {
                quality = Quality.STANDARD;
            }
            return new Station(object.optString("country"), object.optString("countryCode"),
                    object.optString("codec"), object.optInt("bitrate"), object.optString("directoryId"), quality);
        } catch (JSONException e) {
            return new Station("", "", "", 0, "", Quality.STANDARD);
        }
    }

    public static void set(long itemId, Station station) {
        try {
            JSONObject object = new JSONObject();
            object.put("country", station.country);
            object.put("countryCode", station.countryCode);
            object.put("codec", station.codec);
            object.put("bitrate", station.bitrate);
            object.put("directoryId", station.directoryId);
            object.put("quality", station.quality.name());
            prefs.edit().putString(KEY_STATION + itemId, object.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void remove(long itemId) {
        prefs.edit().remove(KEY_STATION + itemId).remove(KEY_BOOST + itemId).apply();
    }

    /**
     * The station's own volume boost for live radio and live pause (1.0.1). OFF means usual (Tune's usual
     * boost), as it does for a show.
     */
    @NonNull
    public static VolumeAdaptionSetting boost(long itemId) {
        if (prefs == null) {
            return VolumeAdaptionSetting.OFF;
        }
        try {
            return VolumeAdaptionSetting.fromInteger(prefs.getInt(KEY_BOOST + itemId,
                    VolumeAdaptionSetting.OFF.toInteger()));
        } catch (RuntimeException e) {
            return VolumeAdaptionSetting.OFF;
        }
    }

    public static void setBoost(long itemId, @NonNull VolumeAdaptionSetting setting) {
        if (setting == VolumeAdaptionSetting.OFF) {
            prefs.edit().remove(KEY_BOOST + itemId).apply();
        } else {
            prefs.edit().putInt(KEY_BOOST + itemId, setting.toInteger()).apply();
        }
    }

    public static int maxBitrate(long itemId) {
        return get(itemId).quality.maxBitrate;
    }
}
