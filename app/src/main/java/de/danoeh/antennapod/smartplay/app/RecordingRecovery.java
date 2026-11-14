package de.danoeh.antennapod.smartplay.app;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.LiveBuffer;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * Recordings that were cut short, and files that don't belong to any recording.
 *
 * <p>Every recording being written is noted (in recordings.db and in memory). When Yonder next starts,
 * any noted recording that isn't being written any more was cut short (force stop, Android's "Stop", a
 * crash, the phone turning off): it's finished, kept and explained on the show's page. Then any file in the
 * recordings folders that isn't a recording is deleted, as is anything unencrypted left by older builds.
 */
final class RecordingRecovery {
    private static final String TAG = "RecordingRecovery";
    /** A recording that stops more than this before it should have is called cut short. */
    static final long SHORT_BY_MILLIS = 30_000L;
    /** A recording that starts more than this after the airing (plus padding) is called late. */
    static final long LATE_BY_MILLIS = 2 * 60_000L;
    /** Files changed more recently than this are left alone by the tidy-up, in case they're just being saved. */
    private static final long RECENT_MILLIS = 10 * 60_000L;

    private static final Object LOCK = new Object();
    private static final Set<String> WRITING = new HashSet<>();
    private static boolean recovered;

    private RecordingRecovery() {
    }

    /** Notes a recording that's about to be written to part. Call before the file is created. */
    static void begin(Context context, File part, long showId, ShowTimes.Airing airing, long tailStart,
                      long tailEnd, boolean live) {
        synchronized (LOCK) {
            WRITING.add(part.getAbsolutePath());
        }
        RecordingStore.getInstance(context).addUnfinished(part.getAbsolutePath(), showId, airing.getStart(),
                airing.getEnd(), tailStart, tailEnd, System.currentTimeMillis(), Process.myPid(), live);
    }

    /** The recording is finished or given up: forget it, and delete its part file if it's still there. */
    static void end(Context context, File part) {
        RecordingStore.getInstance(context).deleteUnfinished(part.getAbsolutePath());
        FileUtils.deleteQuietly(part);
        synchronized (LOCK) {
            WRITING.remove(part.getAbsolutePath());
        }
    }

    /** Once each time Yonder starts, before recordings are scheduled. */
    static void recoverOnce(Context context) {
        List<RecordingStore.Unfinished> claimed = new ArrayList<>();
        synchronized (LOCK) {
            if (recovered) {
                return;
            }
            recovered = true;
            for (RecordingStore.Unfinished unfinished : RecordingStore.getInstance(context).getUnfinished()) {
                if (WRITING.add(unfinished.file)) {
                    claimed.add(unfinished);
                }
            }
        }
        for (RecordingStore.Unfinished unfinished : claimed) {
            try {
                recover(context, unfinished);
            } catch (Exception e) {
                Log.e(TAG, "Could not finish a recording that was cut short", e);
            } finally {
                end(context, new File(unfinished.file));
            }
        }
        try {
            tidy(context);
        } catch (Exception e) {
            Log.e(TAG, "Could not tidy the recordings folder", e);
        }
        // Live pause leftovers (Android killed Yonder while a live station was paused): never kept.
        LiveBuffer.deleteLeftovers(context);
        try {
            noteNotRecorded(context);
        } catch (Exception e) {
            Log.e(TAG, "Could not check for airings that weren't recorded", e);
        }
    }

    private static void recover(Context context, RecordingStore.Unfinished unfinished) throws IOException {
        File part = new File(unfinished.file);
        File target = Recorder.finalFileFor(part);
        RecordingStore.Show show = RecordingStore.getInstance(context).getShow(unfinished.showId);
        if (show == null || target == null || !unfinished.live) {
            // A quick save (after the airing) is simply tried again; its part file isn't needed.
            Log.d(TAG, "Dropping " + part.getName());
            return;
        }
        Recorder.Saved saved;
        long stoppedAt;
        if (part.exists()) {
            stoppedAt = part.lastModified();
            try {
                saved = Recorder.finish(part);
            } catch (IOException e) {
                Log.d(TAG, "Nothing to keep in " + part.getName() + ": " + e.getMessage());
                return;
            }
        } else if (target.exists()) {
            // It was finished but not saved before Yonder closed.
            stoppedAt = target.lastModified();
            saved = new Recorder.Saved(target, Recorder.sniff(target));
        } else {
            return;
        }
        ShowTimes.Airing airing = new ShowTimes.Airing(unfinished.airingStart, unfinished.airingEnd);
        Log.d(TAG, "Keeping " + show.name + ", cut short at " + new java.util.Date(stoppedAt));
        long due = unfinished.tailStart > 0 ? unfinished.tailEnd : unfinished.airingEnd;
        boolean cutShort = stoppedAt < due - SHORT_BY_MILLIS;
        if (unfinished.tailStart > 0) {
            Recordings.savedTail(context, show, saved, airing, unfinished.tailStart,
                    Math.min(unfinished.tailEnd, stoppedAt), stoppedAt);
        } else {
            // A show set to play as soon as it's saved gets its cut-short part ready to play now, even while
            // the show is still on; the rest follows as an extra part. A show held to play at the same local
            // time keeps it held, and it's listed with "Cut short at … · recording the rest · plays here at …".
            boolean playNow = cutShort && !show.sameLocalTime;
            if (playNow) {
                Log.d(TAG, show.name + " plays as soon as it's saved: the cut-short part can play now");
            }
            Recordings.saved(context, show, saved, airing, stoppedAt, stoppedAt, playNow);
        }
        if (cutShort) {
            Recordings.note(context, show.id, airing.getStart(), cutShortText(context, stoppedAt, unfinished.pid));
        }
    }

    /** "Cut short — recording stopped at 9:42 because …", as plainly as the cause can be known. */
    static String cutShortText(Context context, long stoppedAt, int pid) {
        String time = ScheduleText.time(context, stoppedAt);
        if (stoppedAt < bootTime()) {
            return context.getString(R.string.recording_cut_short_phone_off, time);
        }
        switch (exitCause(context, pid, stoppedAt - 60_000L, Long.MAX_VALUE)) {
            case CLOSED:
                return context.getString(R.string.recording_cut_short_closed, time);
            case CRASHED:
                return context.getString(R.string.recording_cut_short_crashed, time);
            case ANDROID:
                return context.getString(R.string.recording_cut_short_android, time);
            default:
                return context.getString(R.string.recording_cut_short_unknown, time);
        }
    }

    /** "Started late — recording began at 9:20 because …", or null if it wasn't late. */
    @Nullable
    static String lateText(Context context, RecordingStore.Show show, ShowTimes.Airing airing, long began) {
        if (began <= airing.getStart() + LATE_BY_MILLIS || show.created > airing.getStart()) {
            return null;
        }
        String time = ScheduleText.time(context, began);
        if (bootTime() > airing.getStart() - RecordingRules.LIVE_PADDING_MILLIS) {
            return context.getString(R.string.recording_late_phone_off, time);
        }
        if (closedBefore(context, airing.getStart())) {
            return context.getString(R.string.recording_late_closed, time);
        }
        return context.getString(R.string.recording_late_unknown, time);
    }

    /**
     * Notes the latest airing of each live show if it wasn't recorded at all and nothing else explains it:
     * "Not recorded — the phone was off".
     */
    private static void noteNotRecorded(Context context) {
        long now = System.currentTimeMillis();
        for (RecordingStore.Show show : RecordingStore.getInstance(context).getShows()) {
            if (show.stopped || !Recordings.usesLive(show) || show.created <= 0) {
                continue;
            }
            ShowTimes.Airing latest = ShowTimes.latestEnded(show.spec(), now);
            if (latest == null || latest.getStart() <= show.created
                    || latest.getStart() <= Math.max(show.lastSavedAiring, show.lastMissedAiring)
                    || latest.getStart() <= show.skipAiring
                    || Recordings.hasRecordingOf(context, show, latest.getStart())) {
                continue;
            }
            String text;
            if (bootTime() > latest.getStart() - RecordingRules.LIVE_PADDING_MILLIS) {
                text = context.getString(R.string.recording_not_recorded_phone_off);
            } else if (closedBefore(context, latest.getStart())) {
                text = context.getString(R.string.recording_not_recorded_closed);
            } else {
                text = context.getString(R.string.recording_not_recorded);
            }
            Log.d(TAG, show.name + ": " + text);
            Recordings.note(context, show.id, latest.getStart(), text);
        }
    }

    /** True if Yonder was closed by the listener before then and wasn't running again until after it. */
    private static boolean closedBefore(Context context, long time) {
        long processStarted = System.currentTimeMillis()
                - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime());
        return processStarted > time && exitCause(context, -1, 0, time) == Cause.CLOSED;
    }

    private static long bootTime() {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime();
    }

    private enum Cause { CLOSED, CRASHED, ANDROID, UNKNOWN }

    /**
     * Why Yonder's process last ended between from and to (Android 11 and newer keep a record).
     * pid, if not -1, picks that process.
     */
    private static Cause exitCause(Context context, int pid, long from, long to) {
        if (Build.VERSION.SDK_INT < 30) {
            return Cause.UNKNOWN;
        }
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) {
                return Cause.UNKNOWN;
            }
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(context.getPackageName(),
                    0, 16);
            for (ApplicationExitInfo exit : exits) {
                if ((pid != -1 && exit.getPid() != pid) || exit.getTimestamp() < from || exit.getTimestamp() > to) {
                    continue;
                }
                switch (exit.getReason()) {
                    case ApplicationExitInfo.REASON_USER_REQUESTED:
                    case ApplicationExitInfo.REASON_USER_STOPPED:
                        return Cause.CLOSED;
                    case ApplicationExitInfo.REASON_CRASH:
                    case ApplicationExitInfo.REASON_CRASH_NATIVE:
                    case ApplicationExitInfo.REASON_ANR:
                    case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                        return Cause.CRASHED;
                    case ApplicationExitInfo.REASON_LOW_MEMORY:
                    case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                    case ApplicationExitInfo.REASON_FREEZER:
                        return Cause.ANDROID;
                    default:
                        return Cause.UNKNOWN;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Can't tell why Yonder stopped: " + e.getMessage());
        }
        return Cause.UNKNOWN;
    }

    /**
     * Deletes every file in the recordings folders that isn't a saved, held or in-progress recording (such as
     * the leftover from a lengthened show in 0.9.20), and anything unencrypted from older builds.
     */
    private static void tidy(Context context) {
        File root = Recordings.recordingsRoot(context);
        // List the files first, then what's kept: a recording saved in between is then in the kept list.
        List<File> found = new ArrayList<>();
        File[] folders = root.listFiles();
        if (folders != null) {
            for (File folder : folders) {
                File[] files = folder.isDirectory() ? folder.listFiles() : new File[] {folder};
                if (files != null) {
                    for (File file : files) {
                        found.add(file);
                    }
                }
            }
        }
        Set<String> keep = new HashSet<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (!Recordings.isRecordingsFeed(feed)) {
                continue;
            }
            Feed full = DBReader.getFeed(feed.getId(), false, 0, Integer.MAX_VALUE);
            if (full == null || full.getItems() == null) {
                continue;
            }
            for (FeedItem item : full.getItems()) {
                FeedMedia media = item.getMedia();
                if (media == null || media.getLocalFileUrl() == null) {
                    continue;
                }
                if (!RecordingCipher.isEncrypted(media.getLocalFileUrl())) {
                    Log.d(TAG, "Deleting an unencrypted recording from an older build: " + item.getTitle());
                    Recordings.removeItem(context, item);
                    continue;
                }
                keep.add(new File(media.getLocalFileUrl()).getAbsolutePath());
            }
        }
        RecordingStore store = RecordingStore.getInstance(context);
        for (RecordingStore.Pending pending : store.getPending()) {
            if (!RecordingCipher.isEncrypted(pending.file)) {
                Log.d(TAG, "Deleting an unencrypted held recording from an older build");
                FileUtils.deleteQuietly(new File(pending.file));
                store.deletePending(pending.id);
                continue;
            }
            keep.add(new File(pending.file).getAbsolutePath());
        }
        Set<String> writing;
        synchronized (LOCK) {
            writing = new HashSet<>(WRITING);
        }
        long now = System.currentTimeMillis();
        for (File file : found) {
            String path = file.getAbsolutePath();
            if (keep.contains(path) || writing.contains(path)) {
                continue;
            }
            boolean plain = !RecordingCipher.isEncrypted(path);
            if (!plain && now - file.lastModified() < RECENT_MILLIS) {
                continue;
            }
            Log.d(TAG, "Deleting " + (plain ? "unencrypted " : "stray ") + "file " + file.getName());
            FileUtils.deleteQuietly(file);
        }
        if (folders != null) {
            for (File folder : folders) {
                String[] left = folder.isDirectory() ? folder.list() : null;
                if (left != null && left.length == 0 && store.getShow(parseId(folder.getName())) == null) {
                    FileUtils.deleteQuietly(folder);
                }
            }
        }
        // Where recordings were kept before they were encrypted.
        File old = UserPreferences.getDataFolder("Recordings");
        if (old != null && old.exists()) {
            Log.d(TAG, "Deleting the old recordings folder");
            FileUtils.deleteQuietly(old);
        }
    }

    private static long parseId(String name) {
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
