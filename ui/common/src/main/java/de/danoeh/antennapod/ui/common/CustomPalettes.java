package de.danoeh.antennapod.ui.common;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class CustomPalettes {
    public static final String PREFIX = "custom:";
    private static final String TAG = "CustomPalettes";
    private static final String OLD_KEY = "custom";

    public static final class Entry {
        public final String id;
        public final String name;
        public final int background;
        public final int accent;

        Entry(String id, String name, int background, int accent) {
            this.id = id;
            this.name = name;
            this.background = background;
            this.accent = accent;
        }

        public String key() {
            return PREFIX + id;
        }
    }

    private CustomPalettes() {
    }

    public static synchronized List<Entry> all() {
        migrate();
        List<Entry> result = new ArrayList<>();
        String json = UserPreferences.getCustomPalettes();
        if (json.isEmpty()) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                result.add(new Entry(object.getString("id"), object.optString("name", ""),
                        object.getInt("background"), object.getInt("accent")));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not read custom palettes", e);
        }
        return result;
    }

    @Nullable
    public static Entry get(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return null;
        }
        String id = key.substring(PREFIX.length());
        for (Entry entry : all()) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public static boolean isCustom(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    public static synchronized String add(String name, int background, int accent) {
        List<Entry> entries = all();
        Entry entry = new Entry(UUID.randomUUID().toString().substring(0, 8), name, background, accent);
        entries.add(entry);
        save(entries);
        return entry.key();
    }

    public static synchronized void update(String key, String name, int background, int accent) {
        List<Entry> entries = all();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(key)) {
                entries.set(i, new Entry(entries.get(i).id, name, background, accent));
            }
        }
        save(entries);
    }

    public static synchronized void delete(String key) {
        List<Entry> entries = all();
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.key().equals(key)) {
                kept.add(entry);
            }
        }
        save(kept);
    }

    public static String suggestName(String pattern) {
        List<Entry> entries = all();
        for (int number = entries.size() + 1; ; number++) {
            String name = String.format(pattern, number);
            boolean used = false;
            for (Entry entry : entries) {
                used |= entry.name.equalsIgnoreCase(name);
            }
            if (!used) {
                return name;
            }
        }
    }

    private static void save(List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                array.put(new JSONObject().put("id", entry.id).put("name", entry.name)
                        .put("background", entry.background).put("accent", entry.accent));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not save custom palettes", e);
            return;
        }
        UserPreferences.setCustomPalettes(array.toString());
    }

    private static void migrate() {
        if (!UserPreferences.hasOldCustomPalette()) {
            return;
        }
        int background = UserPreferences.getOldCustomBackground();
        int accent = UserPreferences.getOldCustomAccent();
        UserPreferences.clearOldCustomPalette();
        String key = add("custom 1", background, accent);
        if (OLD_KEY.equals(UserPreferences.getPalette())) {
            UserPreferences.setPalette(key);
        }
        for (int world = 0; world < 3; world++) {
            if (OLD_KEY.equals(UserPreferences.getWorldPalette(world))) {
                UserPreferences.setWorldPalette(world, key);
            }
        }
    }
}
