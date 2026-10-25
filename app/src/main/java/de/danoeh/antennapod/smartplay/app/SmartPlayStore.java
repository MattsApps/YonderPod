package de.danoeh.antennapod.smartplay.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.smartplay.SmartPlayDefinition;

public class SmartPlayStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "smartplay.db";
    private static final int DATABASE_VERSION = 1;
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
