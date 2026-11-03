package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import de.danoeh.antennapod.smartplay.backup.BackupFiles;

public final class BackupSettings {
    public static final String OFF = "off";
    public static final String DAILY = "daily";
    public static final String WEEKLY = "weekly";
    private static final String KEY_FREQUENCY = "frequency";
    private static final String KEY_FOLDER = "folder";
    private static final String KEY_KEEP = "keep";
    private static final String KEY_LAST = "last";
    private static final String KEY_LAST_ERROR = "lastError";
    private static final String KEY_PASSWORD = "password";

    private BackupSettings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(YonderBackup.BACKUP_PREFS, Context.MODE_PRIVATE);
    }

    public static String frequency(Context context) {
        return prefs(context).getString(KEY_FREQUENCY, OFF);
    }

    public static void setFrequency(Context context, String frequency) {
        prefs(context).edit().putString(KEY_FREQUENCY, frequency).apply();
    }

    @Nullable
    public static String folder(Context context) {
        return prefs(context).getString(KEY_FOLDER, null);
    }

    public static void setFolder(Context context, String folder) {
        prefs(context).edit().putString(KEY_FOLDER, folder).apply();
    }

    public static int keep(Context context) {
        return prefs(context).getInt(KEY_KEEP, BackupFiles.DEFAULT_KEEP);
    }

    public static void setKeep(Context context, int keep) {
        prefs(context).edit().putInt(KEY_KEEP, keep).apply();
    }

    public static long last(Context context) {
        return prefs(context).getLong(KEY_LAST, 0);
    }

    public static String lastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    public static void setLast(Context context, long time, String error) {
        SharedPreferences.Editor editor = prefs(context).edit().putString(KEY_LAST_ERROR, error);
        if (error.isEmpty()) {
            editor.putLong(KEY_LAST, time);
        }
        editor.apply();
    }

    @Nullable
    public static String password(Context context) {
        String stored = prefs(context).getString(KEY_PASSWORD, "");
        if (stored.isEmpty()) {
            return null;
        }
        if (!SecretBox.isSealed(stored)) {
            setPassword(context, stored);
            return stored;
        }
        return SecretBox.open(stored);
    }

    public static void setPassword(Context context, @Nullable String password) {
        String stored = password == null || password.isEmpty() ? "" : SecretBox.seal(password);
        prefs(context).edit().putString(KEY_PASSWORD, stored == null ? "" : stored).apply();
    }
}
