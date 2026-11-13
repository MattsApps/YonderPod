package de.danoeh.antennapod.smartplay.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import androidx.annotation.Nullable;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.ShowOverlap;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.smartplay.schedule.DayRanges;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;

public final class Recordings {
    private static final String TAG = "Recordings";
    static final String KEY_SHOW = "show";
    static final String KEY_START = "start";
    static final String KEY_END = "end";
    static final String KEY_PENDING = "pending";
    private static final String WORK_BURST = "recordingBurst-";
    private static final String WORK_FIRST = "recordingFirst-";
    private static final String WORK_PUBLISH = "recordingPublish-";
    private static final String GUID_PREFIX = "yonderpod-recording:";
    private static final String PREFS = "YonderRecordings";
    private static final String KEY_NAMES_UPDATED = "namesUpdated";
    private static final String KEY_TIME_SHIFT_ONLY = "timeShiftOnly";
    private static final String KEY_CATEGORIES_TIDIED = "categoriesTidied";
    private static final String KEY_SMART_PLAYS_TIDIED = "smartPlaysTidied";
    private static final String FOLDER = "recordings";
    private static final long SWEEP_MARGIN_MILLIS = 5_000L;
    private static final Set<Long> CREATING = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean leftovers = true;

    private Recordings() {
    }

    public static boolean isRecordingsFeed(Feed feed) {
        return feed != null && feed.getDownloadUrl() != null
                && feed.getDownloadUrl().startsWith(Feed.RECORDINGS_URL_PREFIX);
    }

    public static Map<Long, Set<Long>> playsInByFeed(Context context) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (RecordingStore.Show show : RecordingStore.getInstance(context).getShows()) {
            result.put(show.feedId, playsIn(context, show));
        }
        return result;
    }

    public static synchronized Set<Long> playsIn(Context context, RecordingStore.Show show) {
        if (show.playsIn != null) {
            return show.playsIn;
        }
        Set<Long> chosen = new LinkedHashSet<>();
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            for (SmartPlayRule rule : stored.getDefinition().getRules()) {
                EpisodeSource source = rule.getSource();
                if ((source.getType() == EpisodeSource.Type.FEED && source.getFeedId() == show.feedId)
                        || (show.mixIn && source.getType() == EpisodeSource.Type.ALL_FEEDS)) {
                    chosen.add(stored.getId());
                }
            }
        }
        show.playsIn = chosen;
        RecordingStore.getInstance(context).update(show);
        Log.d(TAG, "Carried over " + show.name + ": plays in " + chosen);
        return chosen;
    }

    public static SmartPlayStore.StoredSmartPlay ownSmartPlay(Context context, long feedId) {
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            SmartPlayDefinition definition = stored.getDefinition();
            if (definition.getRules().isEmpty() || !definition.getPriorityFeeds().isEmpty()) {
                continue;
            }
            boolean own = true;
            for (SmartPlayRule rule : definition.getRules()) {
                if (rule.getSource().getType() != EpisodeSource.Type.FEED
                        || rule.getSource().getFeedId() != feedId) {
                    own = false;
                }
            }
            if (own) {
                return stored;
            }
        }
        return null;
    }

    public static void setPlaysIn(Context context, RecordingStore.Show show, boolean own, Set<Long> others) {
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
        Set<Long> chosen = new LinkedHashSet<>(others);
        SmartPlayStore.StoredSmartPlay existing = ownSmartPlay(context, show.feedId);
        if (own && existing == null) {
            chosen.add(store.insert(SmartPlayDefinition.builder(show.name)
                    .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(show.feedId)))
                    .build()));
        } else if (own) {
            chosen.add(existing.getId());
        }
        show.playsIn = chosen;
        RecordingStore.getInstance(context).update(show);
        if (!own && existing != null) {
            store.delete(existing.getId());
            SmartPlayHours.set(context, existing.getId(), null);
            SmartPlayHours.reschedule(context);
            manager.onSmartPlayDeleted(existing.getId());
        } else {
            manager.onSmartPlaysChanged();
        }
        manager.rebuildNow();
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    // The listener tag is always sent: it's there so stations get accurate listener figures.
    // An old "prefRadioTellStations" setting left on a phone from 0.9.13 is ignored.
    static String userAgent(Context context, RecordingStore.Show show) {
        return "Yonder/" + UserAgentInterceptor.YONDER_VERSION
                + " (radio recording; +https://mattsapps.github.io/yonder/; listener "
                + listenerTag(context, show) + ")";
    }

    static String listenerTag(Context context, RecordingStore.Show show) {
        if (show.listenerTag == null || show.listenerTag.isEmpty()) {
            show.listenerTag = newListenerTag();
            if (show.id > 0) {
                RecordingStore.getInstance(context).setListenerTag(show.id, show.listenerTag);
            }
        }
        return show.listenerTag;
    }

    static String newListenerTag() {
        byte[] bytes = new byte[4];
        new SecureRandom().nextBytes(bytes);
        StringBuilder tag = new StringBuilder();
        for (byte b : bytes) {
            tag.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return tag.toString();
    }

    public static RecordingStore.Show create(Context context, FeedItem station, RecordingStore.Show show) {
        show.listenerTag = newListenerTag();
        Feed feed = new Feed(Feed.RECORDINGS_URL_PREFIX + UUID.randomUUID(), null, show.name);
        feed.setAuthor(timesLine(context, show));
        feed.setItems(new ArrayList<>());
        feed.setImageUrl(station.getImageUrl());
        feed.setSortOrder(SortOrder.DATE_NEW_OLD);
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
        CREATING.add(saved.getId());
        FeedPreferences preferences = saved.getPreferences();
        if (preferences != null) {
            preferences.setKeepUpdated(false);
            preferences.getTags().add(station.getTitle());
            try {
                DBWriter.setFeedPreferences(preferences).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not save show settings", e);
            }
        }
        DownloadSettingsRepository.getInstance(context).set(saved.getId(), DownloadSettingsRepository.localDefaults());
        show.stationItemId = station.getId();
        show.feedId = saved.getId();
        checkMethod(context, show);
        RecordingStore.getInstance(context).insert(show);
        CREATING.remove(saved.getId());
        EventBus.getDefault().post(new Radio.ChangedEvent());
        schedule(context, show);
        tryNow(context, show);
        EventBus.getDefault().post(new Radio.ChangedEvent());
        return show;
    }

    public static void update(Context context, RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
        if (feed != null && !show.name.equals(feed.getTitle())) {
            feed.setCustomTitle(show.name);
            DBWriter.setFeedCustomTitle(feed);
        }
        updateTimesLine(context, show);
        checkMethod(context, show);
        RecordingStore.getInstance(context).update(show);
        schedule(context, show);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /**
     * Saves changed settings for a show from "change times" (the record dialog in edit mode).
     * A new name also goes to the show's saved recordings and to its own Smart Play, unless that Smart Play
     * was renamed by hand. A running recording takes the new end time. The listener tag stays the same.
     */
    public static void change(Context context, RecordingStore.Show show, String oldName, int oldEndMinute,
                              String oldZoneId) {
        update(context, show);
        if (!show.name.equals(oldName)) {
            renameRecordings(context, show);
            SmartPlayStore.StoredSmartPlay own = ownSmartPlay(context, show.feedId);
            if (own != null && own.getDefinition().getName().equals(oldName)) {
                SmartPlayStore.getInstance(context).update(own.getId(),
                        SmartPlayJson.fromJson(show.name, SmartPlayJson.toJson(own.getDefinition())));
                SmartPlayQueueManager.getInstance().onSmartPlaysChanged();
            }
        }
        long start = RecordingService.recordingStart(show.id);
        if (start > 0 && (show.endMinute != oldEndMinute || !show.zoneId.equals(oldZoneId))) {
            RecordingService.changeEnd(show.id, endAfter(show, start));
        }
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /** The first time after start that the show's end time comes round, in the show's time zone. */
    static long endAfter(RecordingStore.Show show, long start) {
        Calendar end = Calendar.getInstance(show.spec().timeZone());
        end.setTimeInMillis(start);
        end.set(Calendar.HOUR_OF_DAY, show.endMinute / 60);
        end.set(Calendar.MINUTE, show.endMinute % 60);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        if (end.getTimeInMillis() <= start) {
            end.add(Calendar.DAY_OF_MONTH, 1);
        }
        return end.getTimeInMillis();
    }

    private static void renameRecordings(Context context, RecordingStore.Show show) {
        for (FeedItem item : recordingItems(show)) {
            if (startOf(item) <= 0) {
                continue;
            }
            long[] tail = tailTimes(item.getItemIdentifier());
            String title = title(context, show, startOf(item), tail != null ? tail[0] : 0, tail != null ? tail[1] : 0);
            if (title.equals(item.getTitle())) {
                continue;
            }
            item.setTitle(title);
            try {
                DBWriter.setFeedItem(item, false).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not rename " + item.getTitle(), e);
            }
        }
    }

    /**
     * Removes a recorded show: its schedule and every recording, saved or waiting. A recording running now
     * stops at once and its partial file is deleted. Every way of deleting a recorded show comes here
     * ("delete this show" on the station page and on the show's ⋮ menu).
     */
    public static void remove(Context context, RecordingStore.Show show) {
        RecordingService.discard(show.id);
        RecordingStore store = RecordingStore.getInstance(context);
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            FileUtils.deleteQuietly(new File(pending.file));
            store.deletePending(pending.id);
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PUBLISH + pending.id);
        }
        CREATING.add(show.feedId);
        try {
            for (FeedItem item : recordingItems(show)) {
                removeItem(context, item);
            }
            Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
            if (feed != null) {
                DBWriter.deleteFeed(context, feed.getId()).get();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not remove " + show.name, e);
        } finally {
            CREATING.remove(show.feedId);
        }
        forget(context, show);
    }

    /**
     * "stop today's recording": stops a live recording now and keeps what it has as a normal recording
     * (it plays once and deletes itself like any other). The schedule carries on, so the next airing records
     * as usual. The long press and "stop recording this show" both come here.
     */
    public static void stopToday(Context context, long showId) {
        long start = RecordingService.recordingStart(showId);
        if (start <= 0) {
            return;
        }
        // So opening Yonder later in the same airing doesn't start recording it again.
        RecordingStore.getInstance(context).setSkipAiring(showId, start);
        RecordingService.stopNow(showId);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /** True while this show is recording live (never for a show saved after the airing). */
    public static boolean isRecordingNow(long showId) {
        return RecordingService.recordingStart(showId) > 0;
    }

    /**
     * "stop recording this show": nothing records until the listener starts it again. A recording running
     * now stops and keeps what it has. Saved recordings stay until they'd normally delete themselves, and the
     * listener tag stays. The long press and the show's ⋮ menu both come here.
     */
    public static void stopShow(Context context, RecordingStore.Show show) {
        RecordingStore.getInstance(context).setStopped(show.id, true);
        stopToday(context, show.id);
        WorkManager.getInstance(context).cancelUniqueWork(WORK_FIRST + show.id);
        cancel(context, show);
        Log.d(TAG, "Stopped recording " + show.name);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /** "start recording this show again": the schedule comes back as it was. */
    public static void startShow(Context context, RecordingStore.Show show) {
        RecordingStore store = RecordingStore.getInstance(context);
        store.setStopped(show.id, false);
        RecordingStore.Show current = store.getShow(show.id);
        if (current != null) {
            schedule(context, current);
            Log.d(TAG, "Recording " + current.name + " again");
        }
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /**
     * Forgets a show whose page or station has gone: a running recording is thrown away, and the schedule
     * goes. Saved recordings are left to the sweep, which deletes them on time.
     */
    static void forget(Context context, RecordingStore.Show show) {
        RecordingService.discard(show.id);
        WorkManager.getInstance(context).cancelUniqueWork(WORK_FIRST + show.id);
        cancel(context, show);
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            if (releaseNow(context, pending.id) == null) {
                FileUtils.deleteQuietly(new File(pending.file));
            }
        }
        RecordingStore.getInstance(context).delete(show.id);
        Books.removeFromSmartPlays(context, show.feedId);
        sweep(context);
        dropDeadEntries();
        UpdateScheduler.forgetUnusedCategories(context);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    static void stopLeftovers(Context context) {
        for (RecordingStore.Show show : RecordingStore.getInstance(context).getShows()) {
            if (isLeftOver(show)) {
                Log.d(TAG, "Stopping " + show.name + ": its show or station is gone");
                forget(context, show);
            }
        }
    }

    private static void forgetGoneShowsInSmartPlays(Context context, RecordingStore store) {
        Set<Long> existing = new HashSet<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (!isRecordingsFeed(feed) || store.showForFeed(feed.getId()) != null) {
                existing.add(feed.getId());
            }
        }
        Set<Long> gone = new HashSet<>();
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            SmartPlayDefinition definition = stored.getDefinition();
            for (SmartPlayDefinition.PriorityFeed priority : definition.getPriorityFeeds()) {
                if (!existing.contains(priority.getFeedId())) {
                    gone.add(priority.getFeedId());
                }
            }
            for (SmartPlayRule rule : definition.getRules()) {
                if (rule.getSource().getType() == EpisodeSource.Type.FEED
                        && !existing.contains(rule.getSource().getFeedId())) {
                    gone.add(rule.getSource().getFeedId());
                }
            }
        }
        for (long feedId : gone) {
            Log.d(TAG, "Removing show " + feedId + " from Smart Plays: it no longer exists");
            Books.removeFromSmartPlays(context, feedId);
        }
    }

    static boolean worthSweeping(Context context) {
        return leftovers || !RecordingStore.getInstance(context).getShows().isEmpty();
    }

    private static long sweepLeftovers(Context context, RecordingStore store, long now) {
        long next = Long.MAX_VALUE;
        boolean removed = false;
        boolean remaining = false;
        for (Feed listed : DBReader.getFeedList()) {
            if (!isRecordingsFeed(listed) || CREATING.contains(listed.getId())
                    || store.showForFeed(listed.getId()) != null) {
                continue;
            }
            Feed feed = DBReader.getFeed(listed.getId(), false, 0, Integer.MAX_VALUE);
            if (feed == null) {
                continue;
            }
            List<RecordingRules.Recording> recordings = new ArrayList<>();
            List<FeedItem> items = new ArrayList<>();
            for (FeedItem item : feed.getItems() != null ? feed.getItems() : new ArrayList<FeedItem>()) {
                FeedMedia media = item.getMedia();
                if (media == null || media.getLocalFileUrl() == null || !new File(media.getLocalFileUrl()).exists()) {
                    removeItem(context, item);
                    continue;
                }
                recordings.add(new RecordingRules.Recording(item.getId(), startOf(item), finishedAt(context, item),
                        media.getDownloadDate(), item.isPlayed(), isPlaying(media)));
                items.add(item);
            }
            List<Long> going = RecordingRules.toDelete(recordings, now);
            int kept = 0;
            for (FeedItem item : items) {
                if (going.contains(item.getId())) {
                    Log.d(TAG, "Deleting " + item.getTitle() + (item.isPlayed() ? " (played)" : ""));
                    removeItem(context, item);
                } else {
                    kept++;
                }
            }
            if (kept > 0) {
                remaining = true;
                next = Math.min(next, RecordingRules.nextDeadline(recordings, now));
                continue;
            }
            Log.d(TAG, "Removing " + feed.getTitle() + ": not recorded any more and nothing left to hear");
            try {
                DBWriter.deleteFeed(context, feed.getId()).get();
                removed = true;
                dropDeadEntries();
            } catch (Exception e) {
                Log.e(TAG, "Could not remove " + feed.getTitle(), e);
            }
        }
        leftovers = remaining;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SMART_PLAYS_TIDIED, false)) {
            forgetGoneShowsInSmartPlays(context, store);
            prefs.edit().putBoolean(KEY_SMART_PLAYS_TIDIED, true).apply();
        }
        if (removed || !prefs.getBoolean(KEY_CATEGORIES_TIDIED, false)) {
            UpdateScheduler.forgetUnusedCategories(context);
            prefs.edit().putBoolean(KEY_CATEGORIES_TIDIED, true).apply();
        }
        if (removed) {
            EventBus.getDefault().post(new Radio.ChangedEvent());
        }
        return next;
    }

    static void checkMethod(Context context, RecordingStore.Show show) {
        FeedItem station = DBReader.getFeedItem(show.stationItemId);
        if (station == null || station.getMedia() == null) {
            return;
        }
        String url = station.getMedia().getDownloadUrl();
        List<String> candidates = new ArrayList<>();
        candidates.add(url);
        candidates.addAll(RecordingRules.rewindableVersions(url));
        String[] chosen = {url};
        RewindWindow window;
        RecordingHttp.begin(userAgent(context, show));
        try {
            window = Recorder.bestWindow(candidates, chosen);
        } finally {
            RecordingHttp.end();
        }
        show.sourceUrl = chosen[0];
        show.rewindSeconds = window != null && window.getKind() == RewindWindow.Kind.REWIND ? window.getSeconds() : 0;
        show.method = RecordingRules.chooseMethod(window, show.spec().durationMillis()).name();
    }

    static boolean isLeftOver(RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
        return feed == null || feed.getState() != Feed.STATE_SUBSCRIBED
                || DBReader.getFeedItem(show.stationItemId) == null;
    }

    public static void rescheduleAll(Context context) {
        // Finish and explain any recording cut short last time, before scheduling (which may record the rest).
        RecordingRecovery.recoverOnce(context);
        RecordingStore store = RecordingStore.getInstance(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean renameDefaults = !prefs.getBoolean(KEY_NAMES_UPDATED, false);
        for (RecordingStore.Show show : store.getShows()) {
            if (isLeftOver(show)) {
                Log.d(TAG, "Stopping " + show.name + ": its show or station is gone");
                forget(context, show);
                continue;
            }
            if (renameDefaults) {
                FeedItem station = DBReader.getFeedItem(show.stationItemId);
                if (station != null && show.name.equals(station.getTitle())) {
                    show.name = defaultName(context, station.getTitle(), show);
                    Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
                    feed.setCustomTitle(show.name);
                    DBWriter.setFeedCustomTitle(feed);
                    store.update(show);
                }
            }
            updateTimesLine(context, show);
            schedule(context, show);
        }
        prefs.edit().putBoolean(KEY_NAMES_UPDATED, true).apply();
        for (RecordingStore.Pending pending : store.getPending()) {
            enqueuePublish(context, pending);
        }
        sweep(context);
    }

    private static void cancel(Context context, RecordingStore.Show show) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_BURST + show.id);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(alarmIntent(context, show.id, 0, 0));
        }
    }

    public static boolean usesLive(RecordingStore.Show show) {
        return show.preferLiveNext || show.method() == RecordingRules.Method.LIVE;
    }

    public static void schedule(Context context, RecordingStore.Show show) {
        scheduleAfter(context, show, System.currentTimeMillis());
    }

    public static void scheduleAfter(Context context, RecordingStore.Show show, long after) {
        cancel(context, show);
        // Read from the store, as the show passed in may have been loaded before it was stopped.
        RecordingStore.Show stored = RecordingStore.getInstance(context).getShow(show.id);
        if (stored != null && stored.stopped) {
            return;
        }
        long skipAiring = stored != null ? stored.skipAiring : 0;
        long now = System.currentTimeMillis();
        ShowTimes.Airing onNow = ShowTimes.nextEnd(show.spec(), Math.max(now, after));
        if (onNow != null && onNow.getStart() <= now && recordExtraPart(context, show, onNow, skipAiring)) {
            return;
        }
        if (usesLive(show)) {
            ShowTimes.Airing airing = ShowTimes.nextEnd(show.spec(), Math.max(now, after));
            if (airing != null && airing.getStart() <= skipAiring) {
                airing = ShowTimes.nextEnd(show.spec(), airing.getEnd());
            }
            if (airing == null) {
                return;
            }
            long startAt = airing.getStart() - RecordingRules.LIVE_PADDING_MILLIS;
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            PendingIntent intent = alarmIntent(context, show.id, airing.getStart(), airing.getEnd());
            if (startAt <= now) {
                RecordingService.start(context, show.id, airing.getStart(), airing.getEnd());
            } else if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt, intent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startAt, intent);
            }
        } else {
            ShowTimes.Airing airing = ShowTimes.nextEnd(show.spec(), Math.max(now, after));
            if (airing != null) {
                enqueueBurst(context, WORK_BURST + show.id, show, airing,
                        Math.max(0, airing.getEnd() + RecordingRules.BURST_DELAY_MILLIS - now));
            }
        }
    }

    /** The shortest extra part worth recording. */
    private static final long MIN_EXTRA_MILLIS = 60_000L;

    /**
     * For an airing on now that's already been recorded in part (the show was lengthened after its recording
     * finished, or Yonder was closed part-way and opened again): records only the part no recording on the
     * station covers yet, as an extra part of that airing, then carries on with the next airing. Returns false
     * if nothing of this airing is recorded yet, so it's recorded as usual.
     */
    private static boolean recordExtraPart(Context context, RecordingStore.Show show, ShowTimes.Airing airing,
                                           long skipAiring) {
        if (RecordingService.recordingStart(show.id) > 0) {
            return false; // a running recording simply takes the new end (see change)
        }
        long[] coverage = newestCoverage(context, show);
        long now = System.currentTimeMillis();
        boolean recorded;
        long groupStart;
        long from;
        if (coverage != null && coverage[1] > airing.getStart()) {
            groupStart = coverage[0];
            from = Math.max(now, coverage[1]);
            recorded = true;
        } else if (show.lastSavedAiring >= airing.getStart()) {
            // Recorded and already played (so deleted): only what's still to come.
            groupStart = show.lastSavedAiring;
            from = now;
            recorded = true;
        } else {
            return false;
        }
        if (groupStart <= skipAiring) {
            // The listener stopped today's recording: nothing more of this airing.
            scheduleAfter(context, show, airing.getEnd());
            return true;
        }
        long[] part = uncovered(context, show, from, airing.getEnd());
        if (recorded && part != null && part[1] - part[0] >= MIN_EXTRA_MILLIS) {
            Log.d(TAG, show.name + ": recording the extra part, " + new Date(part[0]) + " to " + new Date(part[1]));
            RecordingService.startExtra(context, show.id, groupStart, airing.getEnd(), part[0], part[1]);
        } else {
            scheduleAfter(context, show, airing.getEnd());
        }
        return true;
    }

    /**
     * The part of from–to that no other show on the same station records (by their scheduled times), or null.
     * Other shows at the start push it later; other shows at the end pull it earlier.
     */
    private static long[] uncovered(Context context, RecordingStore.Show show, long from, long to) {
        long start = from;
        long end = to;
        boolean changed = true;
        for (int round = 0; changed && round < 10; round++) {
            changed = false;
            for (RecordingStore.Show other : showsFor(context, show.stationItemId)) {
                if (other.id == show.id || other.stopped) {
                    continue;
                }
                for (ShowTimes.Airing airing : ShowOverlap.airingsBetween(other.spec(), start - 86_400_000L, end)) {
                    if (airing.getStart() <= start && airing.getEnd() > start) {
                        start = airing.getEnd();
                        changed = true;
                    } else if (airing.getStart() > start && airing.getStart() < end) {
                        end = airing.getStart();
                        changed = true;
                    }
                }
            }
        }
        return end > start ? new long[] {start, end} : null;
    }

    public static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private static PendingIntent alarmIntent(Context context, long showId, long start, long end) {
        Intent intent = new Intent(context, RecordingAlarmReceiver.class);
        intent.setData(Uri.parse("yonderpod-recording://" + showId));
        intent.putExtra(KEY_SHOW, showId);
        intent.putExtra(KEY_START, start);
        intent.putExtra(KEY_END, end);
        return PendingIntent.getBroadcast(context, (int) showId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void tryNow(Context context, RecordingStore.Show show) {
        if (show.stopped || show.method() != RecordingRules.Method.QUICK) {
            return;
        }
        long now = System.currentTimeMillis();
        ShowTimes.Airing latest = ShowTimes.latestEnded(show.spec(), now);
        if (latest != null && latest.getStart() > show.lastSavedAiring
                && RecordingRules.stillInWindow(latest, show.rewindSeconds, now)) {
            enqueueBurst(context, WORK_FIRST + show.id, show, latest, 0);
        }
    }

    private static void enqueueBurst(Context context, String name, RecordingStore.Show show,
                                     ShowTimes.Airing airing, long delay) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(show.allowMobile ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RecordingBurstWorker.class)
                .setInputData(new Data.Builder().putLong(KEY_SHOW, show.id).putLong(KEY_START, airing.getStart())
                        .putLong(KEY_END, airing.getEnd()).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request);
    }

    /** Every show's recordings live in a folder of its own in here, kept out of backups. */
    static File recordingsRoot(Context context) {
        return new File(context.getNoBackupFilesDir(), FOLDER);
    }

    static File folder(Context context, RecordingStore.Show show) {
        File folder = new File(recordingsRoot(context), String.valueOf(show.id));
        if (!folder.exists() && !folder.mkdirs()) {
            Log.e(TAG, "Could not create " + folder);
        }
        return folder;
    }

    static String fileName(RecordingStore.Show show, ShowTimes.Airing airing) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT);
        format.setTimeZone(show.spec().timeZone());
        return format.format(new Date(airing.getStart()));
    }

    /** The file name for the extra part of a lengthened airing, e.g. 2026-11-13_0900_from_1005. */
    static String tailFileName(RecordingStore.Show show, ShowTimes.Airing airing, long tailStart) {
        SimpleDateFormat format = new SimpleDateFormat("HHmmss", Locale.ROOT);
        format.setTimeZone(show.spec().timeZone());
        return fileName(show, airing) + "_from_" + format.format(new Date(tailStart));
    }

    static int maxBitrate(RecordingStore.Show show) {
        return RadioPreferences.maxBitrate(show.stationItemId);
    }

    /**
     * Saves a finished recording (already encrypted). finished is when it actually finished recording; its
     * delete times count from it. covered is where its audio ends (for a quick save, the airing's end plus
     * padding), so lengthening the show records only what comes after.
     */
    static void saved(Context context, RecordingStore.Show show, Recorder.Saved saved, ShowTimes.Airing airing,
                      long finished, long covered) {
        saved(context, show, saved, airing, finished, covered, false);
    }

    /**
     * The same. playNow saves it ready to play straight away, even while its show is still on: for a
     * recording cut short on a show set to play as soon as it's saved, so it never looks lost.
     */
    static void saved(Context context, RecordingStore.Show show, Recorder.Saved saved, ShowTimes.Airing airing,
                      long finished, long covered, boolean playNow) {
        show.lastSavedAiring = Math.max(show.lastSavedAiring, airing.getStart());
        show.status = "";
        show.preferLiveNext = false;
        RecordingStore.getInstance(context).update(show);
        long availableAt = ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(), show.sameLocalTime);
        if (!playNow && RecordingRules.waiting(availableAt, System.currentTimeMillis())) {
            long id = RecordingStore.getInstance(context).addPending(show.id, saved.file.getAbsolutePath(),
                    saved.mimeType, airing.getStart(), airing.getEnd(), finished, 0, 0, covered);
            for (RecordingStore.Pending pending : RecordingStore.getInstance(context).getPending()) {
                if (pending.id == id) {
                    enqueuePublish(context, pending);
                }
            }
            sweep(context);
            EventBus.getDefault().post(new Radio.ChangedEvent());
        } else {
            addRecording(context, show, saved.file, saved.mimeType, airing, finished, covered, 0, 0);
        }
    }

    /**
     * Saves the extra part of a lengthened airing (recorded after the airing's own recording had finished) as
     * a recording of its own. It's listed with the show and plays straight after the main recording. It plays
     * once, and otherwise goes when the main recording goes: it takes the main recording's finish time, so its
     * delete times are the same. If the main recording is still held to play at the same local time, so is it.
     */
    static void savedTail(Context context, RecordingStore.Show show, Recorder.Saved saved, ShowTimes.Airing airing,
                          long tailStart, long tailEnd, long covered) {
        RecordingStore store = RecordingStore.getInstance(context);
        long mainFinished = 0;
        boolean mainHeld = false;
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            if (pending.airingStart == airing.getStart() && !pending.isTail()) {
                mainHeld = true;
                mainFinished = finishedOf(context, pending);
            }
        }
        if (!mainHeld) {
            FeedItem main = itemWithGuid(show, guid(show, airing.getStart(), 0, 0));
            if (main != null) {
                mainFinished = finishedAt(context, main);
            }
        }
        long finished = mainFinished > 0 ? mainFinished : covered;
        long availableAt = ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(), show.sameLocalTime);
        if (mainHeld || RecordingRules.waiting(availableAt, System.currentTimeMillis())) {
            long id = store.addPending(show.id, saved.file.getAbsolutePath(), saved.mimeType, airing.getStart(),
                    airing.getEnd(), finished, tailStart, tailEnd, covered);
            for (RecordingStore.Pending pending : store.getPending()) {
                if (pending.id == id) {
                    enqueuePublish(context, pending);
                }
            }
            sweep(context);
        } else {
            addRecording(context, show, saved.file, saved.mimeType, airing, finished, covered, tailStart, tailEnd);
        }
        Log.d(TAG, "Saved the extra part of " + show.name);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /**
     * Explains a short or missing recording on the show's page and its row on the station page, e.g.
     * "Cut short — recording stopped at 9:42 because Yonder was closed". It stays until a later airing saves.
     */
    static void note(Context context, long showId, long airingStart, String text) {
        RecordingStore store = RecordingStore.getInstance(context);
        RecordingStore.Show show = store.getShow(showId);
        if (show == null) {
            return;
        }
        show.status = text;
        show.lastMissedAiring = Math.max(show.lastMissedAiring, airingStart);
        store.update(show);
        EventBus.getDefault().post(new Radio.ChangedEvent());
    }

    /** The note on the newest airing, or null if there isn't one. */
    static String currentNote(RecordingStore.Show show) {
        if (show.status == null || show.status.isEmpty() || show.lastMissedAiring <= 0
                || show.lastMissedAiring < show.lastSavedAiring) {
            return null;
        }
        return show.status;
    }

    static void missed(Context context, RecordingStore.Show show, ShowTimes.Airing airing, int reason,
                       boolean fallBackToLive) {
        show.status = context.getString(reason, dayLabel(context, show, airing));
        show.lastMissedAiring = Math.max(show.lastMissedAiring, airing.getStart());
        show.preferLiveNext = fallBackToLive;
        RecordingStore.getInstance(context).update(show);
    }

    private static void enqueuePublish(Context context, RecordingStore.Pending pending) {
        RecordingStore.Show show = RecordingStore.getInstance(context).getShow(pending.showId);
        if (show == null) {
            RecordingStore.getInstance(context).deletePending(pending.id);
            return;
        }
        long availableAt = ShowTimes.availableAt(show.spec(),
                new ShowTimes.Airing(pending.airingStart, pending.airingEnd), TimeZone.getDefault(),
                show.sameLocalTime);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(RecordingPublishWorker.class)
                .setInputData(new Data.Builder().putLong(KEY_PENDING, pending.id).build())
                .setInitialDelay(Math.max(0, availableAt - System.currentTimeMillis()), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_PUBLISH + pending.id, ExistingWorkPolicy.REPLACE,
                request);
    }

    static void publishPending(Context context, long pendingId) {
        RecordingStore store = RecordingStore.getInstance(context);
        for (RecordingStore.Pending pending : store.getPending()) {
            if (pending.id != pendingId) {
                continue;
            }
            RecordingStore.Show show = store.getShow(pending.showId);
            File file = new File(pending.file);
            if (show == null || !file.exists()) {
                store.deletePending(pending.id);
                return;
            }
            ShowTimes.Airing airing = new ShowTimes.Airing(pending.airingStart, pending.airingEnd);
            long availableAt = ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(), show.sameLocalTime);
            if (RecordingRules.waiting(availableAt, System.currentTimeMillis())) {
                enqueuePublish(context, pending);
                return;
            }
            addRecording(context, show, file, pending.mimeType, airing, finishedOf(context, pending),
                    pending.covered, pending.tailStart, pending.tailEnd);
            store.deletePending(pending.id);
        }
    }

    private static void addRecording(Context context, RecordingStore.Show show, File file, String mimeType,
                                     ShowTimes.Airing airing, long finished, long covered, long tailStart,
                                     long tailEnd) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, Integer.MAX_VALUE);
        if (feed == null) {
            return;
        }
        if (feed.getItems() == null) {
            feed.setItems(new ArrayList<>());
        }
        String guid = guid(show, airing.getStart(), tailStart, tailEnd);
        for (FeedItem existing : feed.getItems()) {
            if (guid.equals(existing.getItemIdentifier())) {
                Log.w(TAG, "Already have " + guid + "; deleting the copy");
                FileUtils.deleteQuietly(file);
                return;
            }
        }
        RecordingStore.getInstance(context).setFinished(guid, finished, covered);
        String title = title(context, show, airing.getStart(), tailStart, tailEnd);
        // A tail is dated just before its airing so it's listed under the main recording (newest first) and a
        // "latest" rule picks the main recording first. Its own times are in its title and guid.
        Date date = new Date(tailStart > 0
                ? airing.getStart() - 1000 + Math.min(999, (tailStart - airing.getStart()) / 60_000L)
                : airing.getStart());
        FeedItem item = new FeedItem(0, title, guid, null, date, FeedItem.UNPLAYED, feed);
        item.disableAutoDownload();
        FeedMedia media = new FeedMedia(0, item, durationOf(context, file), 0, file.length(), mimeType,
                file.getAbsolutePath(), Uri.fromFile(file).toString(), System.currentTimeMillis(), null, 0, 0);
        item.setMedia(media);
        feed.getItems().add(item);
        FeedDatabaseWriter.updateFeed(context, feed, false);
        sweep(context);
        FeedItem added = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, Uri.fromFile(file).toString());
        if (added != null) {
            EventBus.getDefault().post(new FeedItemEvent(Collections.singletonList(added), false));
        }
    }

    public static synchronized void sweep(Context context) {
        upgradeOnce(context);
        RecordingStore store = RecordingStore.getInstance(context);
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;
        for (RecordingStore.Show show : store.getShows()) {
            List<RecordingRules.Recording> recordings = new ArrayList<>();
            List<FeedItem> items = new ArrayList<>();
            for (FeedItem item : recordingItems(show)) {
                FeedMedia media = item.getMedia();
                if (media == null || media.getLocalFileUrl() == null || !new File(media.getLocalFileUrl()).exists()) {
                    Log.d(TAG, "Removing " + item.getTitle() + ": its file is gone");
                    removeItem(context, item);
                    continue;
                }
                recordings.add(new RecordingRules.Recording(item.getId(), startOf(item), finishedAt(context, item),
                        media.getDownloadDate(), item.isPlayed(), isPlaying(media)));
                items.add(item);
            }
            List<RecordingStore.Pending> held = new ArrayList<>();
            for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
                if (!new File(pending.file).exists()) {
                    store.deletePending(pending.id);
                    continue;
                }
                recordings.add(new RecordingRules.Recording(-pending.id, pending.airingStart,
                        finishedOf(context, pending), playsAt(context, pending), false, false));
                held.add(pending);
            }
            List<Long> going = RecordingRules.toDelete(recordings, now);
            for (FeedItem item : items) {
                if (going.contains(item.getId())) {
                    Log.d(TAG, "Deleting " + item.getTitle() + (item.isPlayed() ? " (played)" : ""));
                    removeItem(context, item);
                }
            }
            for (RecordingStore.Pending pending : held) {
                if (going.contains(-pending.id)) {
                    Log.d(TAG, "Deleting held recording of " + show.name);
                    FileUtils.deleteQuietly(new File(pending.file));
                    store.deletePending(pending.id);
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_PUBLISH + pending.id);
                }
            }
            long deadline = RecordingRules.nextDeadline(recordings, now);
            if (deadline != Long.MAX_VALUE) {
                Log.d(TAG, show.name + ": deletes at " + new Date(deadline));
            }
            next = Math.min(next, deadline);
        }
        next = Math.min(next, sweepLeftovers(context, store, now));
        store.pruneFinished(now - TimeUnit.DAYS.toMillis(7));
        scheduleSweep(context, next);
    }

    public static long deletesAt(Context context, RecordingStore.Show show) {
        List<RecordingRules.Recording> recordings = new ArrayList<>();
        for (FeedItem item : recordingItems(show)) {
            if (item.getMedia() != null) {
                recordings.add(new RecordingRules.Recording(item.getId(), startOf(item), finishedAt(context, item),
                        item.getMedia().getDownloadDate(), item.isPlayed(), false));
            }
        }
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            recordings.add(new RecordingRules.Recording(-pending.id, pending.airingStart,
                    finishedOf(context, pending), playsAt(context, pending), false, false));
        }
        return RecordingRules.nextDeadline(recordings, System.currentTimeMillis());
    }

    private static List<FeedItem> recordingItems(RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, Integer.MAX_VALUE);
        return feed == null || feed.getItems() == null ? new ArrayList<>() : feed.getItems();
    }

    private static FeedItem itemWithGuid(RecordingStore.Show show, String guid) {
        for (FeedItem item : recordingItems(show)) {
            if (guid.equals(item.getItemIdentifier())) {
                return item;
            }
        }
        return null;
    }

    /** True if any recording of the airing starting then is saved or held. */
    static boolean hasRecordingOf(Context context, RecordingStore.Show show, long airingStart) {
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            if (pending.airingStart == airingStart) {
                return true;
            }
        }
        for (FeedItem item : recordingItems(show)) {
            if (startOf(item) == airingStart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Where the newest airing's recordings (its main recording and any extra parts, saved or held) reach:
     * {airing start, end of the audio}, or null if there are none. Lengthening a show records from here.
     */
    private static long[] newestCoverage(Context context, RecordingStore.Show show) {
        long airingStart = 0;
        long covered = 0;
        for (FeedItem item : recordingItems(show)) {
            long start = startOf(item);
            if (start <= 0 || start < airingStart) {
                continue;
            }
            long reach = RecordingStore.getInstance(context).getCovered(item.getItemIdentifier());
            if (reach <= 0) {
                reach = finishedAt(context, item);
            }
            covered = start > airingStart ? reach : Math.max(covered, reach);
            airingStart = start;
        }
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            if (pending.airingStart < airingStart) {
                continue;
            }
            long reach = pending.covered > 0 ? pending.covered : finishedOf(context, pending);
            covered = pending.airingStart > airingStart ? reach : Math.max(covered, reach);
            airingStart = pending.airingStart;
        }
        return airingStart > 0 ? new long[] {airingStart, covered} : null;
    }

    /**
     * Puts each extra part straight after its airing's main recording in the playlist, in time order, so a
     * lengthened show plays through. If an extra part was picked but its main recording wasn't, the main
     * recording comes first. Everything else keeps its place.
     */
    static List<FeedItem> tailsAfterMain(List<FeedItem> queue, Iterable<FeedItem> available) {
        Map<String, List<FeedItem>> tails = new HashMap<>();
        Map<String, FeedItem> mains = new HashMap<>();
        for (FeedItem item : available) {
            String guid = item.getItemIdentifier();
            if (isTailGuid(guid)) {
                List<FeedItem> list = tails.get(mainGuidOf(guid));
                if (list == null) {
                    list = new ArrayList<>();
                    tails.put(mainGuidOf(guid), list);
                }
                list.add(item);
            } else if (isRecordingGuid(guid)) {
                mains.put(guid, item);
            }
        }
        if (tails.isEmpty()) {
            return queue;
        }
        for (List<FeedItem> list : tails.values()) {
            Collections.sort(list, (a, b) -> Long.compare(tailTimes(a.getItemIdentifier())[0],
                    tailTimes(b.getItemIdentifier())[0]));
        }
        List<FeedItem> result = new ArrayList<>();
        Set<Long> placed = new HashSet<>();
        for (FeedItem item : queue) {
            if (placed.contains(item.getId())) {
                continue;
            }
            String guid = item.getItemIdentifier();
            String main = isTailGuid(guid) ? mainGuidOf(guid) : guid;
            List<FeedItem> extra = main != null ? tails.get(main) : null;
            if (extra == null) {
                placed.add(item.getId());
                result.add(item);
                continue;
            }
            FeedItem mainItem = isTailGuid(guid) ? mains.get(main) : item;
            if (mainItem != null && placed.add(mainItem.getId())) {
                result.add(mainItem);
            }
            for (FeedItem tail : extra) {
                if (placed.add(tail.getId())) {
                    result.add(tail);
                }
            }
        }
        return result;
    }

    /**
     * When this saved recording actually finished recording; its delete times count from this, never from
     * the show's schedule. A recording saved before 0.9.20 wasn't stamped, so it's stamped once here: from
     * its file's time (written as it finished), or failing that when it was saved.
     */
    private static long finishedAt(Context context, FeedItem item) {
        RecordingStore store = RecordingStore.getInstance(context);
        String guid = item.getItemIdentifier();
        long at = store.getFinished(guid);
        if (at > 0) {
            return at;
        }
        FeedMedia media = item.getMedia();
        String from = "file time";
        at = media != null && media.getLocalFileUrl() != null ? new File(media.getLocalFileUrl()).lastModified() : 0;
        if (at <= 0 && media != null && media.getDownloadDate() > 0) {
            at = media.getDownloadDate();
            from = "saved time";
        }
        if (at <= 0) {
            at = startOf(item) + (media != null ? media.getDuration() : 0);
            from = "start plus length";
        }
        store.setFinished(guid, at);
        Log.d(TAG, "Stamped " + item.getTitle() + " as finished at " + new Date(at) + " (" + from + ")");
        return at;
    }

    /** The same for a recording waiting to play at the same local time. */
    private static long finishedOf(Context context, RecordingStore.Pending pending) {
        if (pending.finished > 0) {
            return pending.finished;
        }
        long at = new File(pending.file).lastModified();
        String from = "file time";
        if (at <= 0) {
            at = pending.airingEnd;
            from = "airing end";
        }
        RecordingStore.getInstance(context).setPendingFinished(pending.id, at);
        Log.d(TAG, "Stamped a held recording as finished at " + new Date(at) + " (" + from + ")");
        return at;
    }

    /** The start of the airing a recording belongs to (an extra part belongs to its airing too). */
    private static long startOf(FeedItem item) {
        String guid = item.getItemIdentifier();
        if (isRecordingGuid(guid)) {
            String[] parts = guid.substring(GUID_PREFIX.length()).split(":");
            if (parts.length >= 2) {
                try {
                    return Long.parseLong(parts[1]);
                } catch (NumberFormatException ignored) {
                    // fall back to the date
                }
            }
        }
        return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
    }

    /**
     * A recording's guid: show and airing ("yonderpod-recording:12:1794470400000"), plus the extra part's start
     * and end for a lengthened airing (":1794474300000-1794475200000").
     */
    private static String guid(RecordingStore.Show show, long airingStart, long tailStart, long tailEnd) {
        return GUID_PREFIX + show.id + ":" + airingStart + (tailStart > 0 ? ":" + tailStart + "-" + tailEnd : "");
    }

    private static String title(Context context, RecordingStore.Show show, long airingStart, long tailStart,
                                long tailEnd) {
        String title = context.getString(R.string.recording_title, show.name,
                dayLabel(context, show, new ShowTimes.Airing(airingStart, airingStart)));
        if (tailStart <= 0) {
            return title;
        }
        return context.getString(R.string.recording_tail_title, title, stationTime(context, show, tailStart),
                stationTime(context, show, tailEnd > tailStart ? tailEnd : tailStart));
    }

    /** A time as the station lists it, like the show's own times. */
    private static String stationTime(Context context, RecordingStore.Show show, long millis) {
        Calendar calendar = Calendar.getInstance(show.spec().timeZone());
        calendar.setTimeInMillis(millis);
        return ScheduleText.time(context, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    /** True for the extra part of a lengthened airing. */
    static boolean isTailGuid(String guid) {
        return tailTimes(guid) != null;
    }

    /** The guid of the main recording an extra part belongs with. */
    static String mainGuidOf(String tailGuid) {
        return tailGuid.substring(0, tailGuid.lastIndexOf(':'));
    }

    /** An extra part's start and end, from its guid, or null if it isn't one. */
    static long[] tailTimes(String guid) {
        if (!isRecordingGuid(guid)) {
            return null;
        }
        String[] parts = guid.substring(GUID_PREFIX.length()).split(":");
        if (parts.length < 3) {
            return null;
        }
        String[] times = parts[2].split("-");
        try {
            return new long[] {Long.parseLong(times[0]), times.length > 1 ? Long.parseLong(times[1]) : 0};
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private static boolean isPlaying(FeedMedia media) {
        return PlaybackService.isRunning && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId()
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
    }

    static void removeItem(Context context, FeedItem item) {
        if (item.getMedia() != null && item.getMedia().getLocalFileUrl() != null) {
            FileUtils.deleteQuietly(new File(item.getMedia().getLocalFileUrl()));
        }
        if (isRecordingGuid(item.getItemIdentifier())) {
            RecordingStore.getInstance(context).deleteFinished(item.getItemIdentifier());
        }
        try {
            DBWriter.deleteFeedItems(context, Collections.singletonList(item)).get();
        } catch (Exception e) {
            Log.e(TAG, "Could not remove recording", e);
        }
        dropDeadEntries();
    }

    private static boolean isRecordingGuid(String guid) {
        return guid != null && guid.startsWith(GUID_PREFIX);
    }

    static void dropDeadEntries() {
        SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
        if (manager != null) {
            manager.purgeDeadEntries();
        }
    }

    private static void scheduleSweep(Context context, long at) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, RecordingSweepReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (at == Long.MAX_VALUE) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at + SWEEP_MARGIN_MILLIS, pendingIntent);
    }

    private static synchronized void upgradeOnce(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_TIME_SHIFT_ONLY, false)) {
            return;
        }
        RecordingStore store = RecordingStore.getInstance(context);
        for (RecordingStore.Show show : store.getShows()) {
            FeedItem keepItem = null;
            RecordingStore.Pending keepPending = null;
            long newest = Long.MIN_VALUE;
            List<FeedItem> items = recordingItems(show);
            List<RecordingStore.Pending> held = pendingFor(context, show.id);
            for (FeedItem item : items) {
                FeedMedia media = item.getMedia();
                boolean present = media != null && media.getLocalFileUrl() != null
                        && new File(media.getLocalFileUrl()).exists();
                if (present && !item.isPlayed() && startOf(item) > newest) {
                    newest = startOf(item);
                    keepItem = item;
                }
            }
            for (RecordingStore.Pending pending : held) {
                if (new File(pending.file).exists() && pending.airingStart > newest) {
                    newest = pending.airingStart;
                    keepPending = pending;
                    keepItem = null;
                }
            }
            for (FeedItem item : items) {
                if (item != keepItem) {
                    removeItem(context, item);
                }
            }
            for (RecordingStore.Pending pending : held) {
                if (pending != keepPending) {
                    FileUtils.deleteQuietly(new File(pending.file));
                    store.deletePending(pending.id);
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_PUBLISH + pending.id);
                }
            }
            // An unencrypted recording from an older build is deleted, never encrypted from a plain copy.
            if (keepItem != null && !RecordingCipher.isEncrypted(keepItem.getMedia().getLocalFileUrl())) {
                removeItem(context, keepItem);
            }
            if (keepPending != null && !RecordingCipher.isEncrypted(keepPending.file)) {
                FileUtils.deleteQuietly(new File(keepPending.file));
                store.deletePending(keepPending.id);
            }
        }
        prefs.edit().putBoolean(KEY_TIME_SHIFT_ONLY, true).apply();
        Log.d(TAG, "Recordings are now time-shift only");
    }

    private static int durationOf(Context context, File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        RecordingCipher.Reader reader = null;
        try {
            if (RecordingCipher.isEncrypted(file.getPath())) {
                reader = RecordingCipher.open(file, RecordingKeys.get());
                retriever.setDataSource(new RecordingSource(reader));
            } else {
                retriever.setDataSource(file.getAbsolutePath());
            }
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration != null ? (int) Long.parseLong(duration) : 0;
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                Log.d(TAG, "Could not release retriever");
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    Log.d(TAG, "Could not close recording");
                }
            }
        }
    }

    static String dayLabel(Context context, RecordingStore.Show show, ShowTimes.Airing airing) {
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE d MMM");
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.getDefault());
        format.setTimeZone(show.spec().timeZone());
        return format.format(new Date(airing.getStart()));
    }

    public static String summary(Context context, RecordingStore.Show show) {
        long now = System.currentTimeMillis();
        List<String> parts = new ArrayList<>();
        RecordingStore.Pending held = null;
        for (RecordingStore.Pending pending : RecordingStore.getInstance(context).getPending()) {
            if (pending.showId == show.id && (held == null || pending.airingStart > held.airingStart)) {
                held = pending;
            }
        }
        ShowTimes.Airing latest = ShowTimes.latestEnded(show.spec(), now);
        if (held != null) {
            parts.add(heldLine(context, show, held));
        } else if (!usesLive(show) && latest != null && latest.getStart() > show.lastSavedAiring
                && latest.getStart() > show.lastMissedAiring
                && RecordingRules.stillInWindow(latest, show.rewindSeconds, now)) {
            parts.add(context.getString(show.allowMobile ? R.string.recording_status_saving_soon
                    : R.string.recording_status_waiting_wifi, dayTime(context, latest.getStart())));
        } else if (currentNote(show) != null) {
            parts.add(currentNote(show));
        } else if (show.lastSavedAiring > 0) {
            parts.add(context.getString(R.string.recording_status_last_saved, dayTime(context, show.lastSavedAiring)));
        } else {
            parts.add(context.getString(R.string.recording_status_nothing_yet));
        }
        ShowTimes.Airing next = show.stopped ? null : ShowTimes.nextEnd(show.spec(), now);
        if (next != null && next.getStart() <= now) {
            parts.add(context.getString(R.string.recording_status_on_now, ScheduleText.time(context, next.getEnd())));
        } else if (next != null) {
            parts.add(context.getString(R.string.recording_status_next, dayTime(context, next.getStart())));
        }
        return TextUtils.join(context.getString(R.string.detail_separator), parts);
    }

    public static String pageInfo(Context context, RecordingStore.Show show) {
        List<String> lines = new ArrayList<>();
        if (show != null && currentNote(show) != null) {
            lines.add(currentNote(show));
        }
        RecordingStore.Pending held = null;
        for (RecordingStore.Pending pending : show != null ? pendingFor(context, show.id)
                : new ArrayList<RecordingStore.Pending>()) {
            if (held == null || pending.airingStart > held.airingStart) {
                held = pending;
            }
        }
        if (held != null && !isCutShortRecordingRest(show, held)) {
            lines.add(playsAtText(context, playsAt(context, held)));
        }
        long deletesAt = show != null ? deletesAt(context, show) : Long.MAX_VALUE;
        if (deletesAt != Long.MAX_VALUE) {
            lines.add(context.getString(R.string.recording_deletes_at, dayTime(context, deletesAt)));
        }
        lines.add(context.getString(R.string.recording_time_shift));
        return TextUtils.join("\n", lines);
    }

    public static String playsAtText(Context context, long millis) {
        return context.getString(R.string.recording_plays_at, playsAtTime(context, millis));
    }

    /** A time today ("1:00 pm"), or a day and time ("Sat 1:00 pm"). */
    private static String playsAtTime(Context context, long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        boolean sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        return sameDay ? ScheduleText.time(context, millis) : dayTime(context, millis);
    }

    /**
     * True for the main recording of an airing that was cut short (Yonder closed, a crash, the phone off)
     * while the show is still on and the rest of it is recording now. It's held to play at its time.
     */
    static boolean isCutShortRecordingRest(RecordingStore.Show show, RecordingStore.Pending pending) {
        return show != null && !pending.isTail() && pending.covered > 0
                && pending.covered < pending.airingEnd - RecordingRecovery.SHORT_BY_MILLIS
                && RecordingService.recordingStart(show.id) == pending.airingStart;
    }

    /**
     * The line for a held recording: "Cut short at 12:15 · recording the rest · plays here at 1:00 pm" while
     * the rest of a cut-short airing records, otherwise "Saved · plays here at 1:00 pm".
     */
    public static String heldLine(Context context, RecordingStore.Show show, RecordingStore.Pending pending) {
        long playsAt = playsAt(context, pending);
        if (isCutShortRecordingRest(show, pending)) {
            return context.getString(R.string.recording_cut_short_rest, ScheduleText.time(context, pending.covered),
                    playsAtTime(context, playsAt));
        }
        return playsAtText(context, playsAt);
    }

    /**
     * The newest held recording of the show if it's a cut-short part whose rest is recording now, as
     * {title, line}, so it can be listed with the show's recordings (not playable yet). Otherwise null.
     */
    @Nullable
    public static String[] cutShortRow(Context context, RecordingStore.Show show) {
        if (show == null) {
            return null;
        }
        RecordingStore.Pending held = null;
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            if (held == null || pending.airingStart > held.airingStart) {
                held = pending;
            }
        }
        if (held == null || !isCutShortRecordingRest(show, held)) {
            return null;
        }
        return new String[] {title(context, show, held.airingStart, 0, 0), heldLine(context, show, held)};
    }

    public static List<RecordingStore.Pending> pendingFor(Context context, long showId) {
        List<RecordingStore.Pending> result = new ArrayList<>();
        for (RecordingStore.Pending pending : RecordingStore.getInstance(context).getPending()) {
            if (pending.showId == showId) {
                result.add(pending);
            }
        }
        return result;
    }

    public static long playsAt(Context context, RecordingStore.Pending pending) {
        RecordingStore.Show show = RecordingStore.getInstance(context).getShow(pending.showId);
        if (show == null) {
            return pending.airingEnd;
        }
        return ShowTimes.availableAt(show.spec(), new ShowTimes.Airing(pending.airingStart, pending.airingEnd),
                TimeZone.getDefault(), show.sameLocalTime);
    }

    public static FeedItem releaseNow(Context context, long pendingId) {
        RecordingStore store = RecordingStore.getInstance(context);
        for (RecordingStore.Pending pending : store.getPending()) {
            if (pending.id != pendingId) {
                continue;
            }
            RecordingStore.Show show = store.getShow(pending.showId);
            File file = new File(pending.file);
            if (show == null || !file.exists()) {
                store.deletePending(pending.id);
                return null;
            }
            ShowTimes.Airing airing = new ShowTimes.Airing(pending.airingStart, pending.airingEnd);
            addRecording(context, show, file, pending.mimeType, airing, finishedOf(context, pending),
                    pending.covered, pending.tailStart, pending.tailEnd);
            store.deletePending(pending.id);
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PUBLISH + pending.id);
            if (!pending.isTail()) {
                // "listen now anyway" brings the airing's extra parts along too, to play after it.
                for (RecordingStore.Pending tail : pendingFor(context, show.id)) {
                    if (tail.isTail() && tail.airingStart == pending.airingStart) {
                        releaseNow(context, tail.id);
                    }
                }
            }
            EventBus.getDefault().post(new Radio.ChangedEvent());
            return DBReader.getFeedItemByGuidOrEpisodeUrl(
                    guid(show, airing.getStart(), pending.tailStart, pending.tailEnd), Uri.fromFile(file).toString());
        }
        return null;
    }

    static String dayTime(Context context, long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return context.getString(R.string.day_and_time, ScheduleText.shortDay(calendar.get(Calendar.DAY_OF_WEEK)),
                ScheduleText.time(context, millis));
    }

    public static String days(Context context, int weekdays) {
        if ((weekdays & ShowTimes.ALL_DAYS) == ShowTimes.ALL_DAYS) {
            return context.getString(R.string.schedule_every_day);
        }
        return DayRanges.text(weekdays, DateFormatSymbols.getInstance().getShortWeekdays());
    }

    public static String defaultName(Context context, String stationTitle, RecordingStore.Show show) {
        return context.getString(R.string.recording_default_name, stationTitle, days(context, show.weekdays),
                ScheduleText.time(context, show.startMinute / 60, show.startMinute % 60));
    }

    public static String timesLine(Context context, RecordingStore.Show show) {
        String zone = show.zoneId.substring(show.zoneId.lastIndexOf('/') + 1).replace('_', ' ');
        return context.getString(R.string.recording_times_line, days(context, show.weekdays),
                ScheduleText.time(context, show.startMinute / 60, show.startMinute % 60),
                ScheduleText.time(context, show.endMinute / 60, show.endMinute % 60), zone);
    }

    private static void updateTimesLine(Context context, RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
        String line = timesLine(context, show);
        if (feed == null || line.equals(feed.getAuthor())) {
            return;
        }
        feed.setAuthor(line);
        feed.setItems(new ArrayList<>());
        FeedDatabaseWriter.updateFeed(context, feed, false);
    }

    public static List<RecordingStore.Show> showsFor(Context context, long stationItemId) {
        List<RecordingStore.Show> result = new ArrayList<>();
        for (RecordingStore.Show show : RecordingStore.getInstance(context).getShows()) {
            if (show.stationItemId == stationItemId) {
                result.add(show);
            }
        }
        return result;
    }

    public static ShowTimes.Airing airingNow(Context context, long stationItemId) {
        long now = System.currentTimeMillis();
        for (RecordingStore.Show show : showsFor(context, stationItemId)) {
            ShowTimes.Airing airing = ShowTimes.nextEnd(show.spec(), now);
            if (airing != null && airing.getStart() <= now) {
                return airing;
            }
        }
        return null;
    }
}
