package de.danoeh.antennapod.smartplay.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

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
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.radio.RecordingCipher;
import de.danoeh.antennapod.smartplay.radio.RecordingKeys;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.smartplay.schedule.DayRanges;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

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

    public static RecordingStore.Show create(Context context, FeedItem station, RecordingStore.Show show) {
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

    public static void stop(Context context, RecordingStore.Show show) {
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
                stop(context, show);
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
                long start = startOf(item);
                recordings.add(new RecordingRules.Recording(item.getId(), start, start + media.getDuration(),
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
        RewindWindow window = Recorder.bestWindow(candidates, chosen);
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
        RecordingStore store = RecordingStore.getInstance(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean renameDefaults = !prefs.getBoolean(KEY_NAMES_UPDATED, false);
        for (RecordingStore.Show show : store.getShows()) {
            if (isLeftOver(show)) {
                Log.d(TAG, "Stopping " + show.name + ": its show or station is gone");
                stop(context, show);
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
        long now = System.currentTimeMillis();
        if (usesLive(show)) {
            ShowTimes.Airing airing = ShowTimes.nextEnd(show.spec(), Math.max(now, after));
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
        if (show.method() != RecordingRules.Method.QUICK) {
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

    static File folder(Context context, RecordingStore.Show show) {
        File folder = new File(new File(context.getNoBackupFilesDir(), FOLDER), String.valueOf(show.id));
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

    static int maxBitrate(RecordingStore.Show show) {
        return RadioPreferences.maxBitrate(show.stationItemId);
    }

    static void saved(Context context, RecordingStore.Show show, Recorder.Saved plain, ShowTimes.Airing airing)
            throws IOException {
        Recorder.Saved saved = new Recorder.Saved(encrypt(plain.file), plain.mimeType);
        show.lastSavedAiring = Math.max(show.lastSavedAiring, airing.getStart());
        show.status = "";
        show.preferLiveNext = false;
        RecordingStore.getInstance(context).update(show);
        long availableAt = ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(), show.sameLocalTime);
        if (RecordingRules.waiting(availableAt, System.currentTimeMillis())) {
            long id = RecordingStore.getInstance(context).addPending(show.id, saved.file.getAbsolutePath(),
                    saved.mimeType, airing.getStart(), airing.getEnd());
            for (RecordingStore.Pending pending : RecordingStore.getInstance(context).getPending()) {
                if (pending.id == id) {
                    enqueuePublish(context, pending);
                }
            }
            sweep(context);
            EventBus.getDefault().post(new Radio.ChangedEvent());
        } else {
            addRecording(context, show, saved.file, saved.mimeType, airing);
        }
    }

    private static File encrypt(File plain) throws IOException {
        String name = plain.getName();
        int dot = name.lastIndexOf('.');
        File encrypted = new File(plain.getParentFile(), (dot > 0 ? name.substring(0, dot) : name)
                + RecordingCipher.EXTENSION);
        try {
            RecordingCipher.encrypt(plain, encrypted, RecordingKeys.get());
        } catch (IOException e) {
            FileUtils.deleteQuietly(encrypted);
            throw e;
        } finally {
            FileUtils.deleteQuietly(plain);
        }
        return encrypted;
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
            addRecording(context, show, file, pending.mimeType, airing);
            store.deletePending(pending.id);
        }
    }

    private static void addRecording(Context context, RecordingStore.Show show, File file, String mimeType,
                                     ShowTimes.Airing airing) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, Integer.MAX_VALUE);
        if (feed == null) {
            return;
        }
        if (feed.getItems() == null) {
            feed.setItems(new ArrayList<>());
        }
        String guid = GUID_PREFIX + show.id + ":" + airing.getStart();
        for (FeedItem existing : feed.getItems()) {
            if (guid.equals(existing.getItemIdentifier())) {
                return;
            }
        }
        String title = context.getString(R.string.recording_title, show.name, dayLabel(context, show, airing));
        FeedItem item = new FeedItem(0, title, guid, null, new Date(airing.getStart()), FeedItem.UNPLAYED, feed);
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
                long start = startOf(item);
                recordings.add(new RecordingRules.Recording(item.getId(), start,
                        start + show.spec().durationMillis(), media.getDownloadDate(), item.isPlayed(),
                        isPlaying(media)));
                items.add(item);
            }
            List<RecordingStore.Pending> held = new ArrayList<>();
            for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
                if (!new File(pending.file).exists()) {
                    store.deletePending(pending.id);
                    continue;
                }
                recordings.add(new RecordingRules.Recording(-pending.id, pending.airingStart, pending.airingEnd,
                        playsAt(context, pending), false, false));
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
        scheduleSweep(context, next);
    }

    public static long deletesAt(Context context, RecordingStore.Show show) {
        List<RecordingRules.Recording> recordings = new ArrayList<>();
        for (FeedItem item : recordingItems(show)) {
            if (item.getMedia() != null) {
                long start = startOf(item);
                recordings.add(new RecordingRules.Recording(item.getId(), start,
                        start + show.spec().durationMillis(), item.getMedia().getDownloadDate(), item.isPlayed(),
                        false));
            }
        }
        for (RecordingStore.Pending pending : pendingFor(context, show.id)) {
            recordings.add(new RecordingRules.Recording(-pending.id, pending.airingStart, pending.airingEnd,
                    playsAt(context, pending), false, false));
        }
        return RecordingRules.nextDeadline(recordings, System.currentTimeMillis());
    }

    private static List<FeedItem> recordingItems(RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, Integer.MAX_VALUE);
        return feed == null || feed.getItems() == null ? new ArrayList<>() : feed.getItems();
    }

    private static long startOf(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
    }

    private static boolean isPlaying(FeedMedia media) {
        return PlaybackService.isRunning && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId()
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
    }

    private static void removeItem(Context context, FeedItem item) {
        if (item.getMedia() != null && item.getMedia().getLocalFileUrl() != null) {
            FileUtils.deleteQuietly(new File(item.getMedia().getLocalFileUrl()));
        }
        try {
            DBWriter.deleteFeedItems(context, Collections.singletonList(item)).get();
        } catch (Exception e) {
            Log.e(TAG, "Could not remove recording", e);
        }
        dropDeadEntries();
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
        boolean done = true;
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
            try {
                if (keepItem != null && !RecordingCipher.isEncrypted(keepItem.getMedia().getLocalFileUrl())) {
                    File moved = moveAndEncrypt(context, show, new File(keepItem.getMedia().getLocalFileUrl()));
                    FeedMedia media = keepItem.getMedia();
                    media.setLocalFileUrl(moved.getAbsolutePath());
                    media.setSize(moved.length());
                    DBWriter.setMediaDownloadInformation(media).get();
                }
                if (keepPending != null && !RecordingCipher.isEncrypted(keepPending.file)) {
                    File moved = moveAndEncrypt(context, show, new File(keepPending.file));
                    store.setPendingFile(keepPending.id, moved.getAbsolutePath());
                }
                FileUtils.deleteQuietly(new File(UserPreferences.getDataFolder("Recordings"), String.valueOf(show.id)));
            } catch (Exception e) {
                Log.e(TAG, "Could not encrypt the recording of " + show.name + ", trying again next time", e);
                done = false;
            }
        }
        if (done) {
            prefs.edit().putBoolean(KEY_TIME_SHIFT_ONLY, true).apply();
            Log.d(TAG, "Recordings are now time-shift only");
        }
    }

    private static File moveAndEncrypt(Context context, RecordingStore.Show show, File plain) throws IOException {
        File copy = new File(folder(context, show), plain.getName());
        if (!plain.getAbsoluteFile().equals(copy.getAbsoluteFile())) {
            FileUtils.copyFile(plain, copy);
        }
        File encrypted = encrypt(copy);
        FileUtils.deleteQuietly(plain);
        return encrypted;
    }

    private static int durationOf(Context context, File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        RecordingCipher.Reader reader = null;
        try {
            if (RecordingCipher.isEncrypted(file.getPath())) {
                reader = RecordingCipher.open(file, RecordingKeys.get());
                retriever.setDataSource(new DecryptedSource(reader));
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

    private static final class DecryptedSource extends MediaDataSource {
        private final RecordingCipher.Reader reader;

        DecryptedSource(RecordingCipher.Reader reader) {
            this.reader = reader;
        }

        @Override
        public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            return reader.read(position, buffer, offset, size);
        }

        @Override
        public long getSize() throws IOException {
            return reader.length();
        }

        @Override
        public void close() {
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
            ShowTimes.Airing airing = new ShowTimes.Airing(held.airingStart, held.airingEnd);
            parts.add(playsAtText(context, ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(),
                    show.sameLocalTime)));
        } else if (!usesLive(show) && latest != null && latest.getStart() > show.lastSavedAiring
                && latest.getStart() > show.lastMissedAiring
                && RecordingRules.stillInWindow(latest, show.rewindSeconds, now)) {
            parts.add(context.getString(show.allowMobile ? R.string.recording_status_saving_soon
                    : R.string.recording_status_waiting_wifi, dayTime(context, latest.getStart())));
        } else if (show.lastMissedAiring > show.lastSavedAiring && show.status != null && !show.status.isEmpty()) {
            parts.add(show.status);
        } else if (show.lastSavedAiring > 0) {
            parts.add(context.getString(R.string.recording_status_last_saved, dayTime(context, show.lastSavedAiring)));
        } else {
            parts.add(context.getString(R.string.recording_status_nothing_yet));
        }
        ShowTimes.Airing next = ShowTimes.nextEnd(show.spec(), now);
        if (next != null && next.getStart() <= now) {
            parts.add(context.getString(R.string.recording_status_on_now, ScheduleText.time(context, next.getEnd())));
        } else if (next != null) {
            parts.add(context.getString(R.string.recording_status_next, dayTime(context, next.getStart())));
        }
        return TextUtils.join(context.getString(R.string.detail_separator), parts);
    }

    public static String pageInfo(Context context, RecordingStore.Show show) {
        List<String> lines = new ArrayList<>();
        RecordingStore.Pending held = null;
        for (RecordingStore.Pending pending : show != null ? pendingFor(context, show.id)
                : new ArrayList<RecordingStore.Pending>()) {
            if (held == null || pending.airingStart > held.airingStart) {
                held = pending;
            }
        }
        if (held != null) {
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
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        boolean sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        return context.getString(R.string.recording_plays_at,
                sameDay ? ScheduleText.time(context, millis) : dayTime(context, millis));
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
            addRecording(context, show, file, pending.mimeType, airing);
            store.deletePending(pending.id);
            WorkManager.getInstance(context).cancelUniqueWork(WORK_PUBLISH + pending.id);
            EventBus.getDefault().post(new Radio.ChangedEvent());
            return DBReader.getFeedItemByGuidOrEpisodeUrl(GUID_PREFIX + show.id + ":" + airing.getStart(),
                    Uri.fromFile(file).toString());
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
