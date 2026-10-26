package de.danoeh.antennapod.smartplay.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateWorker;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.smartplay.schedule.ScheduleRunPlanner;
import de.danoeh.antennapod.smartplay.schedule.ScheduleTimes;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;
import de.danoeh.antennapod.storage.database.DBReader;

public final class UpdateScheduler {
    private static final String TAG = "UpdateScheduler";
    public static final String DEFAULT_KEY = "";
    private static final String PREFS = "YonderSchedule";
    private static final String KEY_LAST_UPDATED = "lastUpdated";
    private static final String KEY_BATTERY_PROMPT_SHOWN = "batteryPromptShown";
    private static final String WORK_TICK = "yonderScheduleTick";
    private static final String WORK_RUN_PREFIX = "yonderScheduledUpdate-";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateScheduler() {
    }

    public static void init(Context context) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            reschedule(app);
            checkDue(app);
        });
    }

    public static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(app);
        workManager.cancelUniqueWork(FeedUpdateManagerImpl.WORK_ID_FEED_UPDATE);
        PeriodicWorkRequest tick = new PeriodicWorkRequest.Builder(ScheduleTickWorker.class, 1, TimeUnit.HOURS)
                .build();
        workManager.enqueueUniquePeriodicWork(WORK_TICK, ExistingPeriodicWorkPolicy.KEEP, tick);
        armAlarm(app);
    }

    public static synchronized SmartPlayStore.StoredSchedule getDefault(Context context) {
        SmartPlayStore.StoredSchedule stored = SmartPlayStore.getInstance(context).getAllSchedules().get(DEFAULT_KEY);
        if (stored == null) {
            stored = new SmartPlayStore.StoredSchedule(DEFAULT_KEY, CategoryScheduleMode.OWN,
                    UpdateSchedule.defaults(), System.currentTimeMillis(), 0);
            SmartPlayStore.getInstance(context).setSchedule(stored);
        }
        return stored;
    }

    public static synchronized SmartPlayStore.StoredSchedule getCategory(Context context, String category) {
        SmartPlayStore.StoredSchedule stored = SmartPlayStore.getInstance(context).getAllSchedules().get(category);
        if (stored == null) {
            return new SmartPlayStore.StoredSchedule(category, CategoryScheduleMode.DEFAULT,
                    getDefault(context).getSchedule(), 0, 0);
        }
        return stored;
    }

    public static synchronized void save(Context context, String key, CategoryScheduleMode mode,
                                         UpdateSchedule schedule) {
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        SmartPlayStore.StoredSchedule old = store.getAllSchedules().get(key);
        long lastTriggered = old != null ? old.getLastTriggered() : 0;
        store.setSchedule(new SmartPlayStore.StoredSchedule(key, mode, schedule,
                System.currentTimeMillis(), lastTriggered));
        armAlarm(context.getApplicationContext());
    }

    public static List<String> getCategories() {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Set<String> categories : feedCategories().values()) {
            result.addAll(categories);
        }
        return new ArrayList<>(result);
    }

    public static int countShows(String category) {
        return ScheduleRunPlanner.feedsForCategory(category, feedCategories()).size();
    }

    public static List<Long> showsIn(String category) {
        return ScheduleRunPlanner.feedsForCategory(category, feedCategories());
    }

    public static synchronized void checkDue(Context context) {
        try {
            Context app = context.getApplicationContext();
            long now = System.currentTimeMillis();
            TimeZone timeZone = TimeZone.getDefault();
            SmartPlayStore store = SmartPlayStore.getInstance(app);
            getDefault(app);
            Map<String, SmartPlayStore.StoredSchedule> all = store.getAllSchedules();
            Map<Long, Set<String>> feeds = feedCategories();
            Map<String, CategoryScheduleMode> modes = new HashMap<>();
            for (SmartPlayStore.StoredSchedule stored : all.values()) {
                if (!DEFAULT_KEY.equals(stored.getKey())) {
                    modes.put(stored.getKey(), stored.getMode());
                }
            }
            for (SmartPlayStore.StoredSchedule stored : all.values()) {
                boolean isDefault = DEFAULT_KEY.equals(stored.getKey());
                if (!isDefault && stored.getMode() != CategoryScheduleMode.OWN) {
                    continue;
                }
                if (!ScheduleTimes.isDue(stored.getSchedule(), stored.getLastTriggered(), stored.getSince(),
                        now, timeZone)) {
                    continue;
                }
                long previous = ScheduleTimes.previousStart(stored.getSchedule(), now, timeZone);
                store.setSchedule(stored.withLastTriggered(previous));
                List<Long> feedIds = isDefault ? ScheduleRunPlanner.feedsForDefault(feeds, modes)
                        : ScheduleRunPlanner.feedsForCategory(stored.getKey(), feeds);
                if (!feedIds.isEmpty()) {
                    enqueueRun(app, stored.getKey(), stored.getSchedule(), feedIds);
                }
            }
            armAlarm(app);
        } catch (Exception e) {
            Log.e(TAG, "Checking schedules failed", e);
        }
    }

    public static long nextRun(Context context) {
        long now = System.currentTimeMillis();
        long next = ScheduleTimes.NONE;
        for (SmartPlayStore.StoredSchedule stored : SmartPlayStore.getInstance(context).getAllSchedules().values()) {
            if (!DEFAULT_KEY.equals(stored.getKey()) && stored.getMode() != CategoryScheduleMode.OWN) {
                continue;
            }
            long start = ScheduleTimes.nextStart(stored.getSchedule(), now, TimeZone.getDefault());
            if (start != ScheduleTimes.NONE && (next == ScheduleTimes.NONE || start < next)) {
                next = start;
            }
        }
        return next;
    }

    public static void recordUpdated(Context context, long time) {
        prefs(context).edit().putLong(KEY_LAST_UPDATED, time).apply();
    }

    public static long lastUpdated(Context context) {
        return prefs(context).getLong(KEY_LAST_UPDATED, 0);
    }

    public static boolean isBatteryOptimised(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    public static boolean takeBatteryPrompt(Context context) {
        if (prefs(context).getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)) {
            return false;
        }
        prefs(context).edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, true).apply();
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Map<Long, Set<String>> feedCategories() {
        Map<Long, Set<String>> result = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            Set<String> categories = new HashSet<>();
            if (feed.getPreferences() != null) {
                for (String tag : feed.getPreferences().getTags()) {
                    if (!FeedPreferences.TAG_ROOT.equals(tag)) {
                        categories.add(tag);
                    }
                }
            }
            result.put(feed.getId(), categories);
        }
        return result;
    }

    private static void enqueueRun(Context context, String key, UpdateSchedule schedule, List<Long> feedIds) {
        long[] ids = new long[feedIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = feedIds.get(i);
        }
        Data data = new Data.Builder()
                .putLongArray(FeedUpdateManagerImpl.EXTRA_FEED_IDS, ids)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_EVEN_ON_MOBILE, true)
                .putBoolean(FeedUpdateManagerImpl.EXTRA_ALLOW_MOBILE, !schedule.isWifiOnly())
                .build();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(schedule.isWifiOnly() ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .setRequiresCharging(schedule.isChargingOnly())
                .build();
        OneTimeWorkRequest.Builder request = new OneTimeWorkRequest.Builder(FeedUpdateWorker.class)
                .setInputData(data)
                .setConstraints(constraints);
        if (!schedule.isChargingOnly()) {
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        }
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_RUN_PREFIX + key,
                ExistingWorkPolicy.KEEP, request.build());
    }

    private static void armAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        Intent intent = new Intent(context, ScheduleAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long next = nextRun(context);
        if (next == ScheduleTimes.NONE) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent);
    }
}
