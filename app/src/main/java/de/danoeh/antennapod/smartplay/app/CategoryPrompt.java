package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class CategoryPrompt {
    private static final String PREFS = "SmartPlayCategoryPrompt";
    private static final String KEY_FEED_ID = "feedId";

    private CategoryPrompt() {
    }

    public static void request(Context context, long feedId) {
        prefs(context).edit().putLong(KEY_FEED_ID, feedId).apply();
    }

    public static boolean consume(Context context, long feedId) {
        SharedPreferences prefs = prefs(context);
        if (prefs.getLong(KEY_FEED_ID, -1) != feedId) {
            return false;
        }
        prefs.edit().remove(KEY_FEED_ID).apply();
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
