package de.danoeh.antennapod.smartplay.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;

public class SmartPlayStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "smartplay.db";
    private static final int DATABASE_VERSION = 5;
    private static final String TABLE_BOOKS = "books";
    private static final String TABLE_BOOKMARKS = "bookmarks";
    private static final String KEY_IS_BOOK = "is_book";
    private static final String KEY_FINISHED = "finished";
    private static final String KEY_NOTE = "note";
    private static final String KEY_CREATED = "created";
    private static final String TABLE_PLAY_ORDER = "show_play_order";
    private static final String TABLE_TRACKS = "local_tracks";
    private static final String KEY_PLAY_ORDER = "play_order";
    private static final String KEY_SIZE = "size";
    private static final String KEY_TRACK = "track";
    private static final String TABLE_SCHEDULES = "update_schedules";
    private static final String KEY_SCHEDULE_KEY = "schedule_key";
    private static final String KEY_SCHEDULE_MODE = "mode";
    private static final String KEY_MINUTE_OF_DAY = "minute_of_day";
    private static final String KEY_WEEKDAYS = "weekdays";
    private static final String KEY_EVERY_N_DAYS = "every_n_days";
    private static final String KEY_ANCHOR_DAY = "anchor_day";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_CHARGING_ONLY = "charging_only";
    private static final String KEY_SINCE = "since";
    private static final String KEY_LAST_TRIGGERED = "last_triggered";
    private static final String TABLE_DOWNLOAD_SETTINGS = "feed_download_settings";
    private static final String TABLE_DOWNLOAD_HISTORY = "download_history";
    private static final String KEY_FEED_ID = "feed_id";
    private static final String KEY_ITEM_ID = "item_id";
    private static final String KEY_MODE = "mode";
    private static final String KEY_COUNT = "episode_count";
    private static final String KEY_KEEP = "keep_at_most";
    private static final String KEY_AGE_DAYS = "delete_older_than_days";
    private static final String KEY_ALLOW_DELETIONS = "allow_deletions";
    private static final String TABLE = "smart_plays";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "name";
    private static final String KEY_POSITION = "position";
    private static final String KEY_DEFINITION = "definition";

    private static SmartPlayStore instance;

    public static final class StoredSmartPlay {
        private final long id;
        private final SmartPlayDefinition definition;

        StoredSmartPlay(long id, SmartPlayDefinition definition) {
            this.id = id;
            this.definition = definition;
        }

        public long getId() {
            return id;
        }

        public SmartPlayDefinition getDefinition() {
            return definition;
        }
    }

    private SmartPlayStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized SmartPlayStore getInstance(Context context) {
        if (instance == null) {
            instance = new SmartPlayStore(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + KEY_NAME + " TEXT NOT NULL, "
                + KEY_POSITION + " INTEGER NOT NULL, "
                + KEY_DEFINITION + " TEXT NOT NULL)");
        db.insert(TABLE, null, values(SmartPlayLists.stuff(), 0));
        createDownloadTables(db);
        createScheduleTable(db);
        createLocalTables(db);
        createBookTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createDownloadTables(db);
        }
        if (oldVersion < 3) {
            createScheduleTable(db);
        }
        if (oldVersion < 4) {
            createLocalTables(db);
        }
        if (oldVersion < 5) {
            createBookTables(db);
        }
    }

    private static void createBookTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_BOOKS + " ("
                + KEY_FEED_ID + " INTEGER PRIMARY KEY, "
                + KEY_IS_BOOK + " INTEGER NOT NULL, "
                + KEY_FINISHED + " INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + " ("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + KEY_FEED_ID + " INTEGER NOT NULL, "
                + KEY_ITEM_ID + " INTEGER NOT NULL, "
                + KEY_POSITION + " INTEGER NOT NULL, "
                + KEY_NOTE + " TEXT NOT NULL, "
                + KEY_CREATED + " INTEGER NOT NULL)");
    }

    public static final class BookState {
        public final boolean isBook;
        public final boolean finished;

        BookState(boolean isBook, boolean finished) {
            this.isBook = isBook;
            this.finished = finished;
        }
    }

    public synchronized Map<Long, BookState> getBookStates() {
        Map<Long, BookState> result = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_BOOKS, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                result.put(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_FEED_ID)), new BookState(
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_IS_BOOK)) != 0,
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_FINISHED)) != 0));
            }
        }
        return result;
    }

    public synchronized void setBookState(long feedId, boolean isBook, boolean finished) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEED_ID, feedId);
        values.put(KEY_IS_BOOK, isBook ? 1 : 0);
        values.put(KEY_FINISHED, finished ? 1 : 0);
        getWritableDatabase().insertWithOnConflict(TABLE_BOOKS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static final class Bookmark {
        public final long id;
        public final long feedId;
        public final long itemId;
        public final int position;
        public final String note;
        public final long created;

        Bookmark(long id, long feedId, long itemId, int position, String note, long created) {
            this.id = id;
            this.feedId = feedId;
            this.itemId = itemId;
            this.position = position;
            this.note = note;
            this.created = created;
        }
    }

    public synchronized List<Bookmark> getBookmarks(long feedId) {
        List<Bookmark> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_BOOKMARKS, null, KEY_FEED_ID + " = ?",
                new String[] {String.valueOf(feedId)}, null, null, KEY_CREATED + " DESC")) {
            while (cursor.moveToNext()) {
                result.add(new Bookmark(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_FEED_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ITEM_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_POSITION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(KEY_NOTE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_CREATED))));
            }
        }
        return result;
    }

    public synchronized void addBookmark(long feedId, long itemId, int position, String note) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEED_ID, feedId);
        values.put(KEY_ITEM_ID, itemId);
        values.put(KEY_POSITION, position);
        values.put(KEY_NOTE, note);
        values.put(KEY_CREATED, System.currentTimeMillis());
        getWritableDatabase().insert(TABLE_BOOKMARKS, null, values);
    }

    public synchronized void deleteBookmark(long id) {
        getWritableDatabase().delete(TABLE_BOOKMARKS, KEY_ID + " = ?", new String[] {String.valueOf(id)});
    }

    private static void createLocalTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PLAY_ORDER + " ("
                + KEY_FEED_ID + " INTEGER PRIMARY KEY, "
                + KEY_PLAY_ORDER + " TEXT NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_TRACKS + " ("
                + KEY_ITEM_ID + " INTEGER PRIMARY KEY, "
                + KEY_SIZE + " INTEGER NOT NULL, "
                + KEY_TRACK + " INTEGER NOT NULL)");
    }

    public synchronized Map<Long, String> getPlayOrders() {
        Map<Long, String> result = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_PLAY_ORDER, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                result.put(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_FEED_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(KEY_PLAY_ORDER)));
            }
        }
        return result;
    }

    public synchronized void setPlayOrder(long feedId, String playOrder) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEED_ID, feedId);
        values.put(KEY_PLAY_ORDER, playOrder);
        getWritableDatabase().insertWithOnConflict(TABLE_PLAY_ORDER, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized Integer getTrack(long itemId, long size) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_TRACKS, null, KEY_ITEM_ID + " = ?",
                new String[] {String.valueOf(itemId)}, null, null, null)) {
            if (cursor.moveToFirst() && cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SIZE)) == size) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(KEY_TRACK));
            }
        }
        return null;
    }

    public synchronized void setTrack(long itemId, long size, int track) {
        ContentValues values = new ContentValues();
        values.put(KEY_ITEM_ID, itemId);
        values.put(KEY_SIZE, size);
        values.put(KEY_TRACK, track);
        getWritableDatabase().insertWithOnConflict(TABLE_TRACKS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void createScheduleTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SCHEDULES + " ("
                + KEY_SCHEDULE_KEY + " TEXT PRIMARY KEY, "
                + KEY_SCHEDULE_MODE + " TEXT NOT NULL, "
                + KEY_MINUTE_OF_DAY + " INTEGER NOT NULL, "
                + KEY_WEEKDAYS + " INTEGER NOT NULL, "
                + KEY_EVERY_N_DAYS + " INTEGER NOT NULL, "
                + KEY_ANCHOR_DAY + " INTEGER NOT NULL, "
                + KEY_WIFI_ONLY + " INTEGER NOT NULL, "
                + KEY_CHARGING_ONLY + " INTEGER NOT NULL, "
                + KEY_SINCE + " INTEGER NOT NULL, "
                + KEY_LAST_TRIGGERED + " INTEGER NOT NULL)");
    }

    public synchronized Map<String, StoredSchedule> getAllSchedules() {
        Map<String, StoredSchedule> result = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_SCHEDULES, null, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                CategoryScheduleMode mode;
                try {
                    mode = CategoryScheduleMode.valueOf(
                            cursor.getString(cursor.getColumnIndexOrThrow(KEY_SCHEDULE_MODE)));
                } catch (IllegalArgumentException e) {
                    mode = CategoryScheduleMode.DEFAULT;
                }
                UpdateSchedule schedule = new UpdateSchedule(
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_MINUTE_OF_DAY)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_WEEKDAYS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_EVERY_N_DAYS)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ANCHOR_DAY)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_WIFI_ONLY)) != 0,
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_CHARGING_ONLY)) != 0);
                String key = cursor.getString(cursor.getColumnIndexOrThrow(KEY_SCHEDULE_KEY));
                result.put(key, new StoredSchedule(key, mode, schedule,
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SINCE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(KEY_LAST_TRIGGERED))));
            }
        }
        return result;
    }

    public synchronized void setSchedule(StoredSchedule stored) {
        ContentValues values = new ContentValues();
        values.put(KEY_SCHEDULE_KEY, stored.getKey());
        values.put(KEY_SCHEDULE_MODE, stored.getMode().name());
        values.put(KEY_MINUTE_OF_DAY, stored.getSchedule().getMinuteOfDay());
        values.put(KEY_WEEKDAYS, stored.getSchedule().getWeekdays());
        values.put(KEY_EVERY_N_DAYS, stored.getSchedule().getEveryNDays());
        values.put(KEY_ANCHOR_DAY, stored.getSchedule().getAnchorDay());
        values.put(KEY_WIFI_ONLY, stored.getSchedule().isWifiOnly() ? 1 : 0);
        values.put(KEY_CHARGING_ONLY, stored.getSchedule().isChargingOnly() ? 1 : 0);
        values.put(KEY_SINCE, stored.getSince());
        values.put(KEY_LAST_TRIGGERED, stored.getLastTriggered());
        getWritableDatabase().insertWithOnConflict(TABLE_SCHEDULES, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static final class StoredSchedule {
        private final String key;
        private final CategoryScheduleMode mode;
        private final UpdateSchedule schedule;
        private final long since;
        private final long lastTriggered;

        public StoredSchedule(String key, CategoryScheduleMode mode, UpdateSchedule schedule,
                              long since, long lastTriggered) {
            this.key = key;
            this.mode = mode;
            this.schedule = schedule;
            this.since = since;
            this.lastTriggered = lastTriggered;
        }

        public String getKey() {
            return key;
        }

        public CategoryScheduleMode getMode() {
            return mode;
        }

        public UpdateSchedule getSchedule() {
            return schedule;
        }

        public long getSince() {
            return since;
        }

        public long getLastTriggered() {
            return lastTriggered;
        }

        public StoredSchedule withLastTriggered(long time) {
            return new StoredSchedule(key, mode, schedule, since, time);
        }
    }

    private static void createDownloadTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_DOWNLOAD_SETTINGS + " ("
                + KEY_FEED_ID + " INTEGER PRIMARY KEY, "
                + KEY_MODE + " TEXT NOT NULL, "
                + KEY_COUNT + " INTEGER NOT NULL, "
                + KEY_KEEP + " INTEGER NOT NULL, "
                + KEY_AGE_DAYS + " INTEGER NOT NULL, "
                + KEY_ALLOW_DELETIONS + " INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE " + TABLE_DOWNLOAD_HISTORY + " ("
                + KEY_ITEM_ID + " INTEGER PRIMARY KEY, "
                + KEY_FEED_ID + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX download_history_feed ON " + TABLE_DOWNLOAD_HISTORY + " (" + KEY_FEED_ID + ")");
    }

    public synchronized Map<Long, ShowDownloadSettings> getAllDownloadSettings() {
        Map<Long, ShowDownloadSettings> result = new HashMap<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_DOWNLOAD_SETTINGS, null, null, null,
                null, null, null)) {
            while (cursor.moveToNext()) {
                DownloadMode mode;
                try {
                    mode = DownloadMode.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(KEY_MODE)));
                } catch (IllegalArgumentException e) {
                    mode = DownloadMode.MANUAL;
                }
                result.put(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_FEED_ID)), new ShowDownloadSettings(mode,
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_COUNT)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_KEEP)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_AGE_DAYS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ALLOW_DELETIONS)) != 0));
            }
        }
        return result;
    }

    public synchronized void setDownloadSettings(long feedId, ShowDownloadSettings settings) {
        ContentValues values = new ContentValues();
        values.put(KEY_FEED_ID, feedId);
        values.put(KEY_MODE, settings.getMode().name());
        values.put(KEY_COUNT, settings.getEpisodeCount());
        values.put(KEY_KEEP, settings.getKeepAtMost());
        values.put(KEY_AGE_DAYS, settings.getDeleteOlderThanDays());
        values.put(KEY_ALLOW_DELETIONS, settings.isAllowDeletions() ? 1 : 0);
        getWritableDatabase().insertWithOnConflict(TABLE_DOWNLOAD_SETTINGS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized Set<Long> getDownloadHistory(long feedId) {
        Set<Long> result = new HashSet<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_DOWNLOAD_HISTORY, new String[] {KEY_ITEM_ID},
                KEY_FEED_ID + " = ?", new String[] {String.valueOf(feedId)}, null, null, null)) {
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0));
            }
        }
        return result;
    }

    public synchronized void addDownloadHistory(long feedId, Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (long itemId : itemIds) {
                ContentValues values = new ContentValues();
                values.put(KEY_ITEM_ID, itemId);
                values.put(KEY_FEED_ID, feedId);
                db.insertWithOnConflict(TABLE_DOWNLOAD_HISTORY, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized void clearDownloadHistory(long feedId) {
        getWritableDatabase().delete(TABLE_DOWNLOAD_HISTORY, KEY_FEED_ID + " = ?",
                new String[] {String.valueOf(feedId)});
    }

    public synchronized List<StoredSmartPlay> getAll() {
        List<StoredSmartPlay> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
                KEY_POSITION + " ASC, " + KEY_ID + " ASC")) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    public synchronized StoredSmartPlay get(long id) {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, KEY_ID + " = ?",
                new String[] {String.valueOf(id)}, null, null, null)) {
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    public synchronized long insert(SmartPlayDefinition definition) {
        int position = getAll().size();
        return getWritableDatabase().insert(TABLE, null, values(definition, position));
    }

    public synchronized void update(long id, SmartPlayDefinition definition) {
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, definition.getName());
        values.put(KEY_DEFINITION, SmartPlayJson.toJson(definition));
        getWritableDatabase().update(TABLE, values, KEY_ID + " = ?", new String[] {String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete(TABLE, KEY_ID + " = ?", new String[] {String.valueOf(id)});
    }

    private static ContentValues values(SmartPlayDefinition definition, int position) {
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, definition.getName());
        values.put(KEY_POSITION, position);
        values.put(KEY_DEFINITION, SmartPlayJson.toJson(definition));
        return values;
    }

    private static StoredSmartPlay fromCursor(Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME));
        String json = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEFINITION));
        return new StoredSmartPlay(id, SmartPlayJson.fromJson(name, json));
    }
}
