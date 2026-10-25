package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import de.danoeh.antennapod.smartplay.ManualEdits;

public class ManualEditStore {
    private static final String PREFS_NAME = "SmartPlay";
    private static final String KEY_HAND_ADDED = "handAdded";
    private static final String KEY_REMOVED = "removed";

    private final SharedPreferences prefs;

    public ManualEditStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public synchronized void markHandAdded(long episodeId) {
        Set<String> handAdded = read(KEY_HAND_ADDED);
        Set<String> removed = read(KEY_REMOVED);
        handAdded.add(String.valueOf(episodeId));
        removed.remove(String.valueOf(episodeId));
        write(handAdded, removed);
    }

    public synchronized void markRemoved(long episodeId) {
        Set<String> handAdded = read(KEY_HAND_ADDED);
        Set<String> removed = read(KEY_REMOVED);
        handAdded.remove(String.valueOf(episodeId));
        removed.add(String.valueOf(episodeId));
        write(handAdded, removed);
    }

    public synchronized void clearHandAdded() {
        write(new HashSet<>(), read(KEY_REMOVED));
    }

    public synchronized ManualEdits load() {
        return new ManualEdits(toIds(read(KEY_HAND_ADDED)), toIds(read(KEY_REMOVED)));
    }

    public synchronized void prune(Collection<Long> keepHandAdded, Collection<Long> keepRemoved) {
        Set<String> handAdded = read(KEY_HAND_ADDED);
        Set<String> removed = read(KEY_REMOVED);
        handAdded.retainAll(toStrings(keepHandAdded));
        removed.retainAll(toStrings(keepRemoved));
        write(handAdded, removed);
    }

    private Set<String> read(String key) {
        return new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
    }

    private void write(Set<String> handAdded, Set<String> removed) {
        prefs.edit()
                .putStringSet(KEY_HAND_ADDED, handAdded)
                .putStringSet(KEY_REMOVED, removed)
                .apply();
    }

    private static Set<Long> toIds(Set<String> values) {
        Set<Long> ids = new HashSet<>();
        for (String value : values) {
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }

    private static Set<String> toStrings(Collection<Long> ids) {
        Set<String> values = new HashSet<>();
        for (long id : ids) {
            values.add(String.valueOf(id));
        }
        return values;
    }
}
