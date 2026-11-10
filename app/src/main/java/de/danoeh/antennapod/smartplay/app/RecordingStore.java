package de.danoeh.antennapod.smartplay.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;

public class RecordingStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "recordings.db";
    private static final int DATABASE_VERSION = 4;
    private static final String SHOWS = "shows";
    private static final String PENDING = "pending";

    private static RecordingStore instance;

    public static final class Show {
        public long id;
        public long stationItemId;
        public String name;
        public String zoneId;
        public int weekdays;
        public int startMinute;
        public int endMinute;
        public boolean sameLocalTime = true;
        public boolean allowMobile;
        public int keep = RecordingRules.DEFAULT_KEEP;
        public long feedId = -1;
        public String sourceUrl = "";
        public String method = RecordingRules.Method.LIVE.name();
        public long rewindSeconds;
        public String status = "";
        public boolean preferLiveNext;
        public long lastSavedAiring;
        public long lastMissedAiring;
        public boolean mixIn;
        public Set<Long> playsIn;

        public ShowTimes.Spec spec() {
            return new ShowTimes.Spec(zoneId, weekdays, startMinute, endMinute);
        }

        public RecordingRules.Method method() {
            try {
                return RecordingRules.Method.valueOf(method);
            } catch (IllegalArgumentException e) {
                return RecordingRules.Method.LIVE;
            }
        }
    }

    public static final class Pending {
        public final long id;
        public final long showId;
        public final String file;
        public final String mimeType;
        public final long airingStart;
        public final long airingEnd;

        Pending(long id, long showId, String file, String mimeType, long airingStart, long airingEnd) {
            this.id = id;
            this.showId = showId;
            this.file = file;
            this.mimeType = mimeType;
            this.airingStart = airingStart;
            this.airingEnd = airingEnd;
        }
    }

    private RecordingStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized RecordingStore getInstance(Context context) {
        if (instance == null) {
            instance = new RecordingStore(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + SHOWS + " (id INTEGER PRIMARY KEY AUTOINCREMENT, station INTEGER NOT NULL, "
                + "name TEXT NOT NULL, zone TEXT NOT NULL, weekdays INTEGER NOT NULL, start_minute INTEGER NOT NULL, "
                + "end_minute INTEGER NOT NULL, same_local INTEGER NOT NULL, mobile INTEGER NOT NULL, keep INTEGER NOT NULL, "
                + "feed INTEGER NOT NULL, source TEXT NOT NULL, method TEXT NOT NULL, rewind INTEGER NOT NULL, "
                + "status TEXT NOT NULL, prefer_live INTEGER NOT NULL, last_saved INTEGER NOT NULL, "
                + "last_missed INTEGER NOT NULL DEFAULT 0, mix_in INTEGER NOT NULL DEFAULT 0, plays_in TEXT)");
        db.execSQL("CREATE TABLE " + PENDING + " (id INTEGER PRIMARY KEY AUTOINCREMENT, show INTEGER NOT NULL, "
                + "file TEXT NOT NULL, mime TEXT, airing_start INTEGER NOT NULL, airing_end INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + SHOWS + " ADD COLUMN last_missed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE " + SHOWS + " SET last_missed = last_saved + 1 WHERE status != ''");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + SHOWS + " ADD COLUMN mix_in INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + SHOWS + " ADD COLUMN plays_in TEXT");
        }
    }

    public synchronized List<Show> getShows() {
        List<Show> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(SHOWS, null, null, null, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor));
            }
        }
        return result;
    }

    public synchronized Show getShow(long id) {
        try (Cursor cursor = getReadableDatabase().query(SHOWS, null, "id = ?", new String[] {String.valueOf(id)},
                null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public synchronized Show showForFeed(long feedId) {
        try (Cursor cursor = getReadableDatabase().query(SHOWS, null, "feed = ?",
                new String[] {String.valueOf(feedId)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public synchronized long insert(Show show) {
        show.id = getWritableDatabase().insert(SHOWS, null, values(show));
        return show.id;
    }

    public synchronized void update(Show show) {
        getWritableDatabase().update(SHOWS, values(show), "id = ?", new String[] {String.valueOf(show.id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete(SHOWS, "id = ?", new String[] {String.valueOf(id)});
    }

    public synchronized long addPending(long showId, String file, String mimeType, long airingStart,
                                        long airingEnd) {
        ContentValues values = new ContentValues();
        values.put("show", showId);
        values.put("file", file);
        values.put("mime", mimeType);
        values.put("airing_start", airingStart);
        values.put("airing_end", airingEnd);
        return getWritableDatabase().insert(PENDING, null, values);
    }

    public synchronized List<Pending> getPending() {
        List<Pending> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(PENDING, null, null, null, null, null, "id ASC")) {
            while (cursor.moveToNext()) {
                result.add(new Pending(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("show")),
                        cursor.getString(cursor.getColumnIndexOrThrow("file")),
                        cursor.getString(cursor.getColumnIndexOrThrow("mime")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("airing_start")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("airing_end"))));
            }
        }
        return result;
    }

    public synchronized void setPendingFile(long id, String file) {
        ContentValues values = new ContentValues();
        values.put("file", file);
        getWritableDatabase().update(PENDING, values, "id = ?", new String[] {String.valueOf(id)});
    }

    public synchronized void deletePending(long id) {
        getWritableDatabase().delete(PENDING, "id = ?", new String[] {String.valueOf(id)});
    }

    private static ContentValues values(Show show) {
        ContentValues values = new ContentValues();
        values.put("station", show.stationItemId);
        values.put("name", show.name);
        values.put("zone", show.zoneId);
        values.put("weekdays", show.weekdays);
        values.put("start_minute", show.startMinute);
        values.put("end_minute", show.endMinute);
        values.put("same_local", show.sameLocalTime ? 1 : 0);
        values.put("mobile", show.allowMobile ? 1 : 0);
        values.put("keep", show.keep);
        values.put("feed", show.feedId);
        values.put("source", show.sourceUrl);
        values.put("method", show.method);
        values.put("rewind", show.rewindSeconds);
        values.put("status", show.status);
        values.put("prefer_live", show.preferLiveNext ? 1 : 0);
        values.put("last_saved", show.lastSavedAiring);
        values.put("last_missed", show.lastMissedAiring);
        values.put("mix_in", show.mixIn ? 1 : 0);
        if (show.playsIn == null) {
            values.putNull("plays_in");
        } else {
            StringBuilder ids = new StringBuilder();
            for (long id : show.playsIn) {
                ids.append(ids.length() > 0 ? "," : "").append(id);
            }
            values.put("plays_in", ids.toString());
        }
        return values;
    }

    private static Show fromCursor(Cursor cursor) {
        Show show = new Show();
        show.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        show.stationItemId = cursor.getLong(cursor.getColumnIndexOrThrow("station"));
        show.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        show.zoneId = cursor.getString(cursor.getColumnIndexOrThrow("zone"));
        show.weekdays = cursor.getInt(cursor.getColumnIndexOrThrow("weekdays"));
        show.startMinute = cursor.getInt(cursor.getColumnIndexOrThrow("start_minute"));
        show.endMinute = cursor.getInt(cursor.getColumnIndexOrThrow("end_minute"));
        show.sameLocalTime = cursor.getInt(cursor.getColumnIndexOrThrow("same_local")) != 0;
        show.allowMobile = cursor.getInt(cursor.getColumnIndexOrThrow("mobile")) != 0;
        show.keep = cursor.getInt(cursor.getColumnIndexOrThrow("keep"));
        show.feedId = cursor.getLong(cursor.getColumnIndexOrThrow("feed"));
        show.sourceUrl = cursor.getString(cursor.getColumnIndexOrThrow("source"));
        show.method = cursor.getString(cursor.getColumnIndexOrThrow("method"));
        show.rewindSeconds = cursor.getLong(cursor.getColumnIndexOrThrow("rewind"));
        show.status = cursor.getString(cursor.getColumnIndexOrThrow("status"));
        show.preferLiveNext = cursor.getInt(cursor.getColumnIndexOrThrow("prefer_live")) != 0;
        show.lastSavedAiring = cursor.getLong(cursor.getColumnIndexOrThrow("last_saved"));
        show.lastMissedAiring = cursor.getLong(cursor.getColumnIndexOrThrow("last_missed"));
        show.mixIn = cursor.getInt(cursor.getColumnIndexOrThrow("mix_in")) != 0;
        int playsIn = cursor.getColumnIndexOrThrow("plays_in");
        if (!cursor.isNull(playsIn)) {
            show.playsIn = new LinkedHashSet<>();
            for (String id : cursor.getString(playsIn).split(",")) {
                try {
                    show.playsIn.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        return show;
    }
}
