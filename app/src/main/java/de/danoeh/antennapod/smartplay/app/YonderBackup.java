package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.backup.BackupCrypto;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.storage.database.PodDBAdapter;
import de.danoeh.antennapod.storage.importexport.DatabaseExporter;

public final class YonderBackup {
    private static final String TAG = "YonderBackup";
    public static final int FORMAT = 1;
    public static final String BACKUP_PREFS = "YonderBackup";
    private static final String MANIFEST = "manifest.json";
    private static final String PREFERENCES = "preferences.json";
    private static final String DATABASES = "databases/";
    private static final String SMART_PLAY_DB = "smartplay.db";
    private static final String RECORDINGS_DB = "recordings.db";
    private static final List<String> DATABASE_FILES = Arrays.asList(PodDBAdapter.DATABASE_NAME, SMART_PLAY_DB,
            RECORDINGS_DB);
    private static final Set<String> SKIPPED_PREF_KEYS = new HashSet<>(Arrays.asList(
            "prefDataFolder", "prefAutomaticExportFolder"));
    private static final byte[] ZIP_HEADER = {0x50, 0x4b, 0x03, 0x04};
    private static final byte[] SQLITE_HEADER = "SQLite format 3".getBytes(StandardCharsets.US_ASCII);

    public enum Kind { YONDER, ANTENNAPOD, BEYONDPOD, OPML, UNKNOWN }

    public static class NeedsPasswordException extends IOException {
        public NeedsPasswordException() {
            super("Needs password");
        }
    }

    public static class TooNewException extends IOException {
        public TooNewException() {
            super("Made by a newer Yonder");
        }
    }

    public static final class Summary {
        public Kind kind = Kind.UNKNOWN;
        public long created;
        public String appVersion = "";
        public int format;
        public int shows;
        public int smartPlays;
        public int stations;
        public int recordings;
        public boolean encrypted;
        File folder;
        File original;
    }

    private YonderBackup() {
    }

    public static void write(Context context, OutputStream out, @Nullable String password) throws IOException {
        File work = freshDir(context, "backup-out");
        File zip = new File(context.getCacheDir(), "backup-out.zip");
        try {
            buildZip(context, work, zip);
            try (InputStream in = new FileInputStream(zip);
                    OutputStream target = password == null || password.isEmpty()
                            ? out : BackupCrypto.encrypt(out, password)) {
                IOUtils.copy(in, target);
            }
        } finally {
            FileUtils.deleteQuietly(work);
            FileUtils.deleteQuietly(zip);
        }
    }

    private static void buildZip(Context context, File work, File zip) throws IOException {
        File databases = new File(work, "databases");
        databases.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(databases, PodDBAdapter.DATABASE_NAME))) {
            DatabaseExporter.exportToStream(out, context);
        }
        copyHelperDatabase(context, SmartPlayStore.getInstance(context).getWritableDatabase(), SMART_PLAY_DB,
                databases);
        copyHelperDatabase(context, RecordingStore.getInstance(context).getWritableDatabase(), RECORDINGS_DB,
                databases);
        leaveOutRecordings(databases);
        Summary counts = new Summary();
        count(databases, counts);
        try {
            JSONObject manifest = new JSONObject()
                    .put("format", FORMAT)
                    .put("app", "Yonder")
                    .put("appVersion", BuildConfig.VERSION_NAME)
                    .put("appVersionCode", BuildConfig.VERSION_CODE)
                    .put("created", System.currentTimeMillis())
                    .put("shows", counts.shows)
                    .put("smartPlays", counts.smartPlays)
                    .put("stations", counts.stations)
                    .put("recordings", counts.recordings);
            FileUtils.writeStringToFile(new File(work, MANIFEST), manifest.toString(2), StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(new File(work, PREFERENCES), preferences(context).toString(),
                    StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            addToZip(out, new File(work, MANIFEST), MANIFEST);
            addToZip(out, new File(work, PREFERENCES), PREFERENCES);
            for (String name : DATABASE_FILES) {
                File file = new File(databases, name);
                if (file.exists()) {
                    addToZip(out, file, DATABASES + name);
                }
            }
        }
    }

    private static void leaveOutRecordings(File databases) throws IOException {
        String recordingItems = "SELECT " + PodDBAdapter.KEY_ID + " FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                + " WHERE " + PodDBAdapter.KEY_FEED + " IN (SELECT " + PodDBAdapter.KEY_ID + " FROM "
                + PodDBAdapter.TABLE_NAME_FEEDS + " WHERE " + PodDBAdapter.KEY_DOWNLOAD_URL + " LIKE ?)";
        String[] prefix = {Feed.RECORDINGS_URL_PREFIX + "%"};
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(databases, PodDBAdapter.DATABASE_NAME).getPath(),
                null, SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL("DELETE FROM " + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " WHERE " + PodDBAdapter.KEY_FEEDITEM
                    + " IN (" + recordingItems + ")", prefix);
            db.execSQL("DELETE FROM " + PodDBAdapter.TABLE_NAME_QUEUE + " WHERE " + PodDBAdapter.KEY_FEEDITEM
                    + " IN (" + recordingItems + ")", prefix);
            db.execSQL("DELETE FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " WHERE " + PodDBAdapter.KEY_ID
                    + " IN (" + recordingItems + ")", prefix);
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
        File recordings = new File(databases, RECORDINGS_DB);
        if (!recordings.exists()) {
            return;
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(recordings.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            db.execSQL("DELETE FROM pending");
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
    }

    private static void copyHelperDatabase(Context context, SQLiteDatabase db, String name, File target)
            throws IOException {
        try (Cursor cursor = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
            cursor.moveToFirst();
        } catch (SQLiteException e) {
            Log.d(TAG, "No checkpoint for " + name);
        }
        File source = context.getDatabasePath(name);
        if (source.exists()) {
            FileUtils.copyFile(source, new File(target, name));
        }
    }

    private static void addToZip(ZipOutputStream out, File file, String name) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(file)) {
            IOUtils.copy(in, out);
        }
        out.closeEntry();
    }

    public static Kind sniff(Context context, Uri uri) throws IOException {
        try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri))) {
            byte[] header = new byte[64];
            int read = IOUtils.read(in, header);
            return sniff(Arrays.copyOf(header, read), context, uri);
        }
    }

    private static Kind sniff(byte[] header, Context context, Uri uri) throws IOException {
        if (BackupCrypto.isEncrypted(header)) {
            return Kind.YONDER;
        }
        if (startsWith(header, SQLITE_HEADER)) {
            return Kind.ANTENNAPOD;
        }
        if (startsWith(header, ZIP_HEADER)) {
            try (ZipInputStream zip = new ZipInputStream(context.getContentResolver().openInputStream(uri))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    if (name.equals(MANIFEST)) {
                        return Kind.YONDER;
                    } else if (name.contains("beyondpod.db") || name.contains("backupmanifest")) {
                        return Kind.BEYONDPOD;
                    }
                }
            }
            return Kind.UNKNOWN;
        }
        String text = new String(header, StandardCharsets.UTF_8).trim().toLowerCase();
        if (text.startsWith("<?xml") || text.startsWith("<opml")) {
            return Kind.OPML;
        }
        return Kind.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    public static Summary inspect(Context context, Uri uri, @Nullable String password) throws IOException {
        File folder = freshDir(context, "restore");
        File original = new File(folder, "original.bin");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            FileUtils.copyInputStreamToFile(in, original);
        }
        byte[] header = new byte[64];
        int read;
        try (InputStream in = new FileInputStream(original)) {
            read = IOUtils.read(in, header);
        }
        header = Arrays.copyOf(header, read);
        Summary summary = new Summary();
        summary.folder = folder;
        summary.original = original;
        File databases = new File(folder, "databases");
        databases.mkdirs();
        if (startsWith(header, SQLITE_HEADER)) {
            summary.kind = Kind.ANTENNAPOD;
            FileUtils.copyFile(original, new File(databases, PodDBAdapter.DATABASE_NAME));
            summary.created = original.lastModified();
            count(databases, summary);
            return summary;
        }
        File zip = original;
        if (BackupCrypto.isEncrypted(header)) {
            summary.encrypted = true;
            if (password == null || password.isEmpty()) {
                throw new NeedsPasswordException();
            }
            zip = new File(folder, "decrypted.zip");
            try (InputStream in = BackupCrypto.decrypt(new FileInputStream(original), password)) {
                FileUtils.copyInputStreamToFile(in, zip);
            }
        }
        unzip(zip, folder);
        File manifestFile = new File(folder, MANIFEST);
        if (!manifestFile.exists()) {
            throw new IOException("Not a Yonder backup");
        }
        summary.kind = Kind.YONDER;
        try {
            JSONObject manifest = new JSONObject(FileUtils.readFileToString(manifestFile, StandardCharsets.UTF_8));
            summary.format = manifest.optInt("format", 1);
            summary.created = manifest.optLong("created", 0);
            summary.appVersion = manifest.optString("appVersion", "");
        } catch (JSONException e) {
            throw new IOException(e);
        }
        if (summary.format > FORMAT) {
            throw new TooNewException();
        }
        count(databases, summary);
        return summary;
    }

    private static void unzip(File zip, File folder) throws IOException {
        String root = folder.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                File target = new File(folder, entry.getName());
                if (!target.getCanonicalPath().startsWith(root) || entry.isDirectory()) {
                    continue;
                }
                target.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(target)) {
                    IOUtils.copy(in, out);
                }
            }
        }
    }

    private static void count(File databases, Summary summary) {
        File main = new File(databases, PodDBAdapter.DATABASE_NAME);
        if (main.exists()) {
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(main.getPath(), null,
                    SQLiteDatabase.OPEN_READONLY)) {
                summary.shows = number(db, "SELECT COUNT(*) FROM " + PodDBAdapter.TABLE_NAME_FEEDS
                        + " WHERE " + PodDBAdapter.KEY_STATE + " = " + Feed.STATE_SUBSCRIBED
                        + " AND " + PodDBAdapter.KEY_DOWNLOAD_URL + " != ?"
                        + " AND " + PodDBAdapter.KEY_DOWNLOAD_URL + " NOT LIKE ?",
                        Feed.RADIO_DOWNLOAD_URL, Feed.RECORDINGS_URL_PREFIX + "%");
                summary.stations = number(db, "SELECT COUNT(*) FROM " + PodDBAdapter.TABLE_NAME_FEED_ITEMS
                        + " WHERE " + PodDBAdapter.KEY_FEED + " IN (SELECT " + PodDBAdapter.KEY_ID + " FROM "
                        + PodDBAdapter.TABLE_NAME_FEEDS + " WHERE " + PodDBAdapter.KEY_DOWNLOAD_URL + " = ?)",
                        Feed.RADIO_DOWNLOAD_URL);
            } catch (SQLiteException e) {
                summary.shows = 0;
            }
        }
        summary.smartPlays = countRows(new File(databases, SMART_PLAY_DB), "smart_plays");
        summary.recordings = countRows(new File(databases, RECORDINGS_DB), "shows");
    }

    private static int countRows(File file, String table) {
        if (!file.exists()) {
            return 0;
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            return number(db, "SELECT COUNT(*) FROM " + table);
        } catch (SQLiteException e) {
            return 0;
        }
    }

    private static int number(SQLiteDatabase db, String sql, String... args) {
        try (Cursor cursor = db.rawQuery(sql, args)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public static void restore(Context context, Summary summary) throws IOException {
        File databases = new File(summary.folder, "databases");
        File main = new File(databases, PodDBAdapter.DATABASE_NAME);
        if (!main.exists()) {
            throw new IOException("Backup has no database");
        }
        if (version(main) > PodDBAdapter.VERSION) {
            throw new TooNewException();
        }
        File smartPlays = new File(databases, SMART_PLAY_DB);
        if (smartPlays.exists()
                && version(smartPlays) > SmartPlayStore.getInstance(context).getReadableDatabase().getVersion()) {
            throw new TooNewException();
        }
        File recordings = new File(databases, RECORDINGS_DB);
        if (recordings.exists()
                && version(recordings) > RecordingStore.getInstance(context).getReadableDatabase().getVersion()) {
            throw new TooNewException();
        }
        Set<Long> downloadAgain = forgetMissingDownloads(main);
        if (smartPlays.exists()) {
            downloadAgain.addAll(forgetPastDownloads(main, downloadingShows(smartPlays)));
            forgetDownloadHistory(smartPlays, downloadAgain);
        }
        JSONObject preferences = null;
        File preferencesFile = new File(summary.folder, PREFERENCES);
        if (preferencesFile.exists()) {
            try {
                preferences = new JSONObject(FileUtils.readFileToString(preferencesFile, StandardCharsets.UTF_8));
            } catch (JSONException e) {
                throw new IOException(e);
            }
        }
        SmartPlayStore.getInstance(context).close();
        RecordingStore.getInstance(context).close();
        replace(context, main, PodDBAdapter.DATABASE_NAME);
        if (smartPlays.exists()) {
            replace(context, smartPlays, SMART_PLAY_DB);
        }
        if (recordings.exists()) {
            replace(context, recordings, RECORDINGS_DB);
        }
        if (preferences != null) {
            restorePreferences(context, preferences);
        }
        FileUtils.deleteQuietly(summary.folder);
    }

    public static void discard(@Nullable Summary summary) {
        if (summary != null) {
            FileUtils.deleteQuietly(summary.folder);
        }
    }

    private static int version(File file) throws IOException {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            return db.getVersion();
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
    }

    private static Set<Long> forgetMissingDownloads(File main) throws IOException {
        Set<Long> downloadAgain = new HashSet<>();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(main.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            Map<Long, String> files = new HashMap<>();
            Map<Long, Long> items = new HashMap<>();
            try (Cursor cursor = db.rawQuery("SELECT " + PodDBAdapter.KEY_ID + ", " + PodDBAdapter.KEY_FILE_URL
                    + ", " + PodDBAdapter.KEY_FEEDITEM + " FROM " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                    + " WHERE " + PodDBAdapter.KEY_FILE_URL + " IS NOT NULL", null)) {
                while (cursor.moveToNext()) {
                    files.put(cursor.getLong(0), cursor.getString(1));
                    items.put(cursor.getLong(0), cursor.getLong(2));
                }
            }
            for (Map.Entry<Long, String> entry : files.entrySet()) {
                String path = entry.getValue();
                if (path.isEmpty() || path.startsWith("content:") || new File(path).exists()) {
                    continue;
                }
                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_MEDIA + " SET "
                        + PodDBAdapter.KEY_DOWNLOAD_DATE + " = 0, " + PodDBAdapter.KEY_FILE_URL + " = NULL WHERE "
                        + PodDBAdapter.KEY_ID + " = ?", new Object[] {entry.getKey()});
                Long itemId = items.get(entry.getKey());
                if (itemId == null) {
                    continue;
                }
                try (Cursor played = db.rawQuery("SELECT " + PodDBAdapter.KEY_READ + " FROM "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " WHERE " + PodDBAdapter.KEY_ID + " = ?",
                        new String[] {String.valueOf(itemId)})) {
                    if (played.moveToFirst() && played.getInt(0) != FeedItem.PLAYED) {
                        db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " SET "
                                + PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED + " = 1 WHERE "
                                + PodDBAdapter.KEY_ID + " = ?", new Object[] {itemId});
                        downloadAgain.add(itemId);
                    }
                }
            }
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
        return downloadAgain;
    }

    private static Set<Long> downloadingShows(File smartPlays) {
        Set<Long> feeds = new HashSet<>();
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(smartPlays.getPath(), null,
                SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("SELECT feed_id, mode FROM feed_download_settings", null)) {
            while (cursor.moveToNext()) {
                try {
                    if (DownloadMode.valueOf(cursor.getString(1)).downloads()) {
                        feeds.add(cursor.getLong(0));
                    }
                } catch (IllegalArgumentException e) {
                    Log.d(TAG, "Unknown download mode " + cursor.getString(1));
                }
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Could not read download settings", e);
        }
        return feeds;
    }

    private static Set<Long> forgetPastDownloads(File main, Set<Long> feeds) throws IOException {
        Set<Long> downloadAgain = new HashSet<>();
        if (feeds.isEmpty()) {
            return downloadAgain;
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(main.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            for (long feedId : feeds) {
                try (Cursor cursor = db.rawQuery("SELECT i." + PodDBAdapter.KEY_ID + " FROM "
                        + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " i JOIN " + PodDBAdapter.TABLE_NAME_FEED_MEDIA
                        + " m ON m." + PodDBAdapter.KEY_FEEDITEM + " = i." + PodDBAdapter.KEY_ID
                        + " WHERE i." + PodDBAdapter.KEY_FEED + " = ? AND i." + PodDBAdapter.KEY_READ + " != "
                        + FeedItem.PLAYED + " AND m." + PodDBAdapter.KEY_FILE_URL + " IS NULL",
                        new String[] {String.valueOf(feedId)})) {
                    while (cursor.moveToNext()) {
                        downloadAgain.add(cursor.getLong(0));
                    }
                }
            }
            for (long itemId : downloadAgain) {
                db.execSQL("UPDATE " + PodDBAdapter.TABLE_NAME_FEED_ITEMS + " SET "
                        + PodDBAdapter.KEY_AUTO_DOWNLOAD_ENABLED + " = 1 WHERE " + PodDBAdapter.KEY_ID + " = ?",
                        new Object[] {itemId});
            }
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
        Log.d(TAG, "Restore: " + downloadAgain.size()
                + " unplayed episodes in shows set to download can download again");
        return downloadAgain;
    }

    private static void forgetDownloadHistory(File smartPlays, Set<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(smartPlays.getPath(), null,
                SQLiteDatabase.OPEN_READWRITE)) {
            for (long itemId : itemIds) {
                db.delete("download_history", "item_id = ?", new String[] {String.valueOf(itemId)});
            }
        } catch (SQLiteException e) {
            Log.e(TAG, "Could not clear download history", e);
        }
    }

    private static void replace(Context context, File source, String name) throws IOException {
        File current = context.getDatabasePath(name);
        current.getParentFile().mkdirs();
        if (current.exists() && !current.delete()) {
            throw new IOException("Unable to replace " + name);
        }
        for (String suffix : new String[] {"-wal", "-shm", "-journal"}) {
            FileUtils.deleteQuietly(new File(current.getPath() + suffix));
        }
        FileUtils.moveFile(source, current);
    }

    private static List<String> preferenceFiles(Context context) {
        File folder = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        String[] names = folder.list();
        List<String> result = new ArrayList<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            if (!name.endsWith(".xml")) {
                continue;
            }
            String file = name.substring(0, name.length() - 4);
            if (!skippedFile(file)) {
                result.add(file);
            }
        }
        return result;
    }

    private static boolean skippedFile(String name) {
        return name.startsWith("androidx.") || name.startsWith("WorkManager") || name.startsWith("com.google")
                || name.equals(BACKUP_PREFS);
    }

    private static String defaultPreferencesName(Context context) {
        return context.getPackageName() + "_preferences";
    }

    private static JSONObject preferences(Context context) throws JSONException {
        JSONObject files = new JSONObject();
        for (String file : preferenceFiles(context)) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(file, Context.MODE_PRIVATE)
                    .getAll().entrySet()) {
                if (SKIPPED_PREF_KEYS.contains(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                Object value = entry.getValue();
                JSONObject typed = new JSONObject();
                if (value instanceof String) {
                    typed.put("t", "s").put("v", value);
                } else if (value instanceof Integer) {
                    typed.put("t", "i").put("v", value);
                } else if (value instanceof Long) {
                    typed.put("t", "l").put("v", value);
                } else if (value instanceof Float) {
                    typed.put("t", "f").put("v", String.valueOf(value));
                } else if (value instanceof Boolean) {
                    typed.put("t", "b").put("v", value);
                } else if (value instanceof Set) {
                    JSONArray array = new JSONArray();
                    for (Object item : (Set<?>) value) {
                        array.put(String.valueOf(item));
                    }
                    typed.put("t", "set").put("v", array);
                } else {
                    continue;
                }
                values.put(entry.getKey(), typed);
            }
            files.put(file.equals(defaultPreferencesName(context)) ? "default" : file, values);
        }
        return new JSONObject().put("files", files);
    }

    private static void restorePreferences(Context context, JSONObject preferences) {
        JSONObject files = preferences.optJSONObject("files");
        if (files == null) {
            return;
        }
        Iterator<String> names = files.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (skippedFile(name)) {
                continue;
            }
            JSONObject values = files.optJSONObject(name);
            SharedPreferences prefs = name.equals("default")
                    ? PreferenceManager.getDefaultSharedPreferences(context)
                    : context.getSharedPreferences(name, Context.MODE_PRIVATE);
            Map<String, ?> current = prefs.getAll();
            SharedPreferences.Editor editor = prefs.edit().clear();
            for (String key : SKIPPED_PREF_KEYS) {
                Object kept = current.get(key);
                if (kept instanceof String) {
                    editor.putString(key, (String) kept);
                }
            }
            Iterator<String> keys = values != null ? values.keys() : Collections.<String>emptyIterator();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject typed = values.optJSONObject(key);
                if (typed == null || SKIPPED_PREF_KEYS.contains(key)) {
                    continue;
                }
                switch (typed.optString("t")) {
                    case "s":
                        editor.putString(key, typed.optString("v"));
                        break;
                    case "i":
                        editor.putInt(key, typed.optInt("v"));
                        break;
                    case "l":
                        editor.putLong(key, typed.optLong("v"));
                        break;
                    case "f":
                        editor.putFloat(key, (float) typed.optDouble("v"));
                        break;
                    case "b":
                        editor.putBoolean(key, typed.optBoolean("v"));
                        break;
                    case "set":
                        JSONArray array = typed.optJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; array != null && i < array.length(); i++) {
                            set.add(array.optString(i));
                        }
                        editor.putStringSet(key, set);
                        break;
                    default:
                        break;
                }
            }
            editor.commit();
        }
    }

    public static void restartApp(Context context) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.getApplicationContext().startActivity(intent);
        }
        Runtime.getRuntime().exit(0);
    }

    private static File freshDir(Context context, String name) {
        File dir = new File(context.getCacheDir(), name);
        FileUtils.deleteQuietly(dir);
        dir.mkdirs();
        return dir;
    }
}
