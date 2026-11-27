package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What plays next, and the two questions about it.
 *
 * <p>"Keep playing" or "I'll choose" is AntennaPod's own continuous playback setting
 * ({@link UserPreferences#isFollowQueue()}), reused; this file only records whether the listener has been
 * asked. Until they have, Yonder stops at the end of each episode. "Include half-played episodes when moving
 * on by itself" lives here; until it's asked, half-played episodes are included, as before.
 */
public final class NextUpPreferences {
    public static final String PREFS = "YonderNextUp";
    /** Keep playing, or I'll choose? */
    public static final int QUESTION_CONTINUOUS = 1;
    /** Asked once more, on the first Smart Play, after "I'll choose". */
    public static final int QUESTION_CONTINUOUS_AGAIN = 2;
    /** Include half-played episodes when moving on by itself? */
    public static final int QUESTION_HALF_PLAYED = 4;

    private static final String KEY_CONTINUOUS_ASKED = "continuousAsked";
    private static final String KEY_CONTINUOUS_ASKED_AGAIN = "continuousAskedAgain";
    private static final String KEY_HALF_ASKED = "halfPlayedAsked";
    private static final String KEY_INCLUDE_HALF = "includeHalfPlayed";
    private static final String KEY_PENDING = "pendingQuestions";
    private static final String KEY_DETOUR_RESUME = "detourResumeMediaId";
    private static final String KEY_LAST_FINISHED = "lastFinishedItemId";
    private static final String KEY_NOTHING_NEW = "nothingNewShow";
    private static final String KEY_DETOUR_KEPT = "detourKeptItemIds";
    private static final String KEY_WORLD = "playingWorld";
    private static final String KEY_WORLD_START = "worldStartMediaId";
    private static final String KEY_RADIO_LAST = "radioLastMediaId";
    private static final String KEY_PODCAST_RECORDINGS = "podcastRecordingItemIds";
    /** Recordings are gone within 48 hours, so a short memory is plenty. */
    private static final int PODCAST_RECORDINGS_KEPT = 30;
    /** Played from a Smart Play (Podcasts, or a book's or folder's own). */
    public static final int WORLD_SMART_PLAY = 0;
    /** Started from the Radio screen: a station or a recording. */
    public static final int WORLD_RADIO = 1;
    /** An added file started from the Files screen. */
    public static final int WORLD_ADDED_FILES = 2;

    private static SharedPreferences prefs;

    private NextUpPreferences() {
    }

    public static void init(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isContinuousAsked() {
        return prefs != null && prefs.getBoolean(KEY_CONTINUOUS_ASKED, false);
    }

    /** True only once the listener chose "keep playing". Until they're asked, Yonder stops. */
    public static boolean keepPlaying() {
        return isContinuousAsked() && UserPreferences.isFollowQueue();
    }

    /** The answer, from the question or from Tune. */
    public static void setKeepPlaying(boolean keep, boolean fromQuestion) {
        UserPreferences.setFollowQueue(keep);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_CONTINUOUS_ASKED, true);
        if (!fromQuestion || keep) {
            // Chosen in Tune, or "keep playing": no need to ask again on the first Smart Play.
            editor.putBoolean(KEY_CONTINUOUS_ASKED_AGAIN, true);
        }
        editor.apply();
        clearPending(QUESTION_CONTINUOUS | QUESTION_CONTINUOUS_AGAIN);
    }

    public static boolean isContinuousAskedAgain() {
        return prefs != null && prefs.getBoolean(KEY_CONTINUOUS_ASKED_AGAIN, false);
    }

    public static void setContinuousAskedAgain() {
        prefs.edit().putBoolean(KEY_CONTINUOUS_ASKED_AGAIN, true).apply();
    }

    public static boolean isHalfPlayedAsked() {
        return prefs != null && prefs.getBoolean(KEY_HALF_ASKED, false);
    }

    /** True until the listener turns it off. */
    public static boolean includeHalfPlayed() {
        return prefs == null || prefs.getBoolean(KEY_INCLUDE_HALF, true);
    }

    public static void setIncludeHalfPlayed(boolean include) {
        prefs.edit().putBoolean(KEY_HALF_ASKED, true).putBoolean(KEY_INCLUDE_HALF, include).apply();
        clearPending(QUESTION_HALF_PLAYED);
    }

    /** Questions waiting for an answer, shown when Yonder is next opened. */
    public static int getPending() {
        return prefs != null ? prefs.getInt(KEY_PENDING, 0) : 0;
    }

    public static void addPending(int questions) {
        prefs.edit().putInt(KEY_PENDING, getPending() | questions).apply();
    }

    public static void clearPending(int questions) {
        prefs.edit().putInt(KEY_PENDING, getPending() & ~questions).apply();
    }

    /** The episode a detour interrupted: it always resumes, half-played or not. */
    public static long getDetourResume() {
        return prefs != null ? prefs.getLong(KEY_DETOUR_RESUME, -1) : -1;
    }

    public static void setDetourResume(long mediaId) {
        prefs.edit().putLong(KEY_DETOUR_RESUME, mediaId).apply();
    }

    public static long getLastFinished() {
        return prefs != null ? prefs.getLong(KEY_LAST_FINISHED, -1) : -1;
    }

    public static void setLastFinished(long itemId) {
        prefs.edit().putLong(KEY_LAST_FINISHED, itemId).apply();
    }

    /** The show with nothing new, for "Nothing new in <show>" when play is pressed. */
    @Nullable
    public static String getNothingNew() {
        return prefs != null ? prefs.getString(KEY_NOTHING_NEW, null) : null;
    }

    public static void setNothingNew(@Nullable String show) {
        prefs.edit().putString(KEY_NOTHING_NEW, show).apply();
    }

    public static Set<Long> getDetourKept() {
        Set<Long> ids = new HashSet<>();
        String saved = prefs != null ? prefs.getString(KEY_DETOUR_KEPT, "") : "";
        for (String id : saved.split(",")) {
            if (!id.isEmpty()) {
                ids.add(Long.parseLong(id));
            }
        }
        return ids;
    }

    public static void setDetourKept(Set<Long> ids) {
        StringBuilder saved = new StringBuilder();
        for (long id : ids) {
            saved.append(saved.length() > 0 ? "," : "").append(id);
        }
        prefs.edit().putString(KEY_DETOUR_KEPT, saved.toString()).commit();
    }

    /**
     * 1.0.5: pressing play in a world makes it the one played from. What was playing is left where it was and
     * never comes back by itself, so a detour back to it is forgotten.
     */
    public static void startWorld(long mediaId, int world) {
        SharedPreferences.Editor editor = prefs.edit().putInt(KEY_WORLD, world).putLong(KEY_WORLD_START, mediaId)
                .putLong(KEY_DETOUR_RESUME, -1);
        if (world == WORLD_RADIO) {
            editor.putLong(KEY_RADIO_LAST, mediaId);
        }
        editor.commit();
    }

    public static int getWorld() {
        return prefs != null ? prefs.getInt(KEY_WORLD, WORLD_SMART_PLAY) : WORLD_SMART_PLAY;
    }

    public static long getWorldStart() {
        return prefs != null ? prefs.getLong(KEY_WORLD_START, -1) : -1;
    }

    /**
     * 1.0.7: radio recordings belong to Radio unless played inside a podcast smart play. Noted once, when a
     * recording starts playing (any route: Radio, a show's page, the notification, the widget, Android Auto):
     * played from a smart play's list it counts as a podcast; played any other way it's Radio's last played.
     */
    public static void noteRecordingStarted(long itemId, long mediaId, boolean inSmartPlay) {
        List<Long> ids = podcastRecordings();
        ids.remove(Long.valueOf(itemId));
        SharedPreferences.Editor editor = prefs.edit();
        if (inSmartPlay) {
            ids.add(0, itemId);
            while (ids.size() > PODCAST_RECORDINGS_KEPT) {
                ids.remove(ids.size() - 1);
            }
        } else {
            editor.putLong(KEY_RADIO_LAST, mediaId);
        }
        StringBuilder saved = new StringBuilder();
        for (long id : ids) {
            saved.append(saved.length() > 0 ? "," : "").append(id);
        }
        editor.putString(KEY_PODCAST_RECORDINGS, saved.toString()).commit();
    }

    /** 1.0.7: whether this recording was last played inside a podcast smart play. */
    public static boolean isPodcastRecording(long itemId) {
        return podcastRecordings().contains(itemId);
    }

    private static List<Long> podcastRecordings() {
        List<Long> ids = new ArrayList<>();
        String saved = prefs != null ? prefs.getString(KEY_PODCAST_RECORDINGS, "") : "";
        for (String id : saved.split(",")) {
            if (!id.isEmpty()) {
                ids.add(Long.parseLong(id));
            }
        }
        return ids;
    }

    /** The last thing played from the Radio screen: a station or a recording. */
    public static long getRadioLast() {
        return prefs != null ? prefs.getLong(KEY_RADIO_LAST, -1) : -1;
    }
}
