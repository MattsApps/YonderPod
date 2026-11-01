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

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.smartplay.schedule.DayRanges;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
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

    private Recordings() {
    }

    public static boolean isRecordingsFeed(Feed feed) {
        return feed != null && feed.getDownloadUrl() != null
                && feed.getDownloadUrl().startsWith(Feed.RECORDINGS_URL_PREFIX);
    }

    public static RecordingStore.Show create(Context context, FeedItem station, RecordingStore.Show show) {
        Feed feed = new Feed(Feed.RECORDINGS_URL_PREFIX + UUID.randomUUID(), null, show.name);
        feed.setAuthor(timesLine(context, show));
        feed.setItems(new ArrayList<>());
        feed.setImageUrl(station.getImageUrl());
        feed.setSortOrder(SortOrder.DATE_NEW_OLD);
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
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
        RecordingStore.getInstance(context).delete(show.id);
        EventBus.getDefault().post(new Radio.ChangedEvent());
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

    public static void rescheduleAll(Context context) {
        RecordingStore store = RecordingStore.getInstance(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean renameDefaults = !prefs.getBoolean(KEY_NAMES_UPDATED, false);
        for (RecordingStore.Show show : store.getShows()) {
            if (DBReader.getFeed(show.feedId, false, 0, 0) == null) {
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

    static File folder(RecordingStore.Show show) {
        File folder = new File(UserPreferences.getDataFolder("Recordings"), String.valueOf(show.id));
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

    static void saved(Context context, RecordingStore.Show show, Recorder.Saved saved, ShowTimes.Airing airing) {
        show.lastSavedAiring = Math.max(show.lastSavedAiring, airing.getStart());
        show.status = "";
        show.preferLiveNext = false;
        RecordingStore.getInstance(context).update(show);
        long availableAt = ShowTimes.availableAt(show.spec(), airing, TimeZone.getDefault(), show.sameLocalTime);
        if (availableAt > System.currentTimeMillis() + 30_000) {
            long id = RecordingStore.getInstance(context).addPending(show.id, saved.file.getAbsolutePath(),
                    saved.mimeType, airing.getStart(), airing.getEnd());
            for (RecordingStore.Pending pending : RecordingStore.getInstance(context).getPending()) {
                if (pending.id == id) {
                    enqueuePublish(context, pending);
                }
            }
        } else {
            addRecording(context, show, saved.file, saved.mimeType, airing);
        }
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
            if (availableAt > System.currentTimeMillis() + 60_000) {
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
        applyKeep(context, show);
        FeedItem added = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, Uri.fromFile(file).toString());
        if (added != null) {
            EventBus.getDefault().post(new FeedItemEvent(Collections.singletonList(added), false));
        }
    }

    static void applyKeep(Context context, RecordingStore.Show show) {
        Feed feed = DBReader.getFeed(show.feedId, false, 0, Integer.MAX_VALUE);
        if (feed == null || feed.getItems() == null) {
            return;
        }
        List<RecordingRules.Recording> recordings = new ArrayList<>();
        for (FeedItem item : feed.getItems()) {
            FeedMedia media = item.getMedia();
            recordings.add(new RecordingRules.Recording(item.getId(),
                    item.getPubDate() != null ? item.getPubDate().getTime() : 0,
                    item.isTagged(FeedItem.TAG_FAVORITE),
                    media != null && media.getPosition() > 0 && !item.isPlayed()));
        }
        List<Long> delete = RecordingRules.toDelete(recordings, show.keep);
        for (FeedItem item : feed.getItems()) {
            if (!delete.contains(item.getId())) {
                continue;
            }
            if (item.getMedia() != null && item.getMedia().getLocalFileUrl() != null) {
                if (!new File(item.getMedia().getLocalFileUrl()).delete()) {
                    Log.d(TAG, "Could not delete " + item.getMedia().getLocalFileUrl());
                }
            }
            try {
                DBWriter.deleteFeedItems(context, Collections.singletonList(item)).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not remove old recording", e);
            }
        }
    }

    private static int durationOf(Context context, File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
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
        return TextUtils.join(" · ", parts);
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
        return ScheduleText.shortDay(calendar.get(Calendar.DAY_OF_WEEK)) + " " + ScheduleText.time(context, millis);
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
