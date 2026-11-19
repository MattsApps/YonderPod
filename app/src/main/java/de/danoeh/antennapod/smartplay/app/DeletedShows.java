package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * Names of deleted shows (0.9.34). Deleting a show removes it from the database, but Smart Play rules that point
 * at it stay as they were, so the editor can still name it "⚠ name (no longer subscribed)" rather than
 * "Unknown show". Only the name is kept.
 */
public final class DeletedShows {
    private static final String PREFS = "YonderDeletedShows";

    private DeletedShows() {
    }

    public static void remember(Context context, long feedId, String title) {
        prefs(context).edit().putString(String.valueOf(feedId), title != null ? title : "").apply();
    }

    /** Feed ID to name, for every show deleted so far. */
    public static Map<Long, String> titles(Context context) {
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            try {
                result.put(Long.parseLong(entry.getKey()), String.valueOf(entry.getValue()));
            } catch (NumberFormatException e) {
                // Not one of ours.
            }
        }
        return result;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
