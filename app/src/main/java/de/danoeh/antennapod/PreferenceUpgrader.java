package de.danoeh.antennapod;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.smartplay.app.DeletedShows;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.system.CrashReportWriter;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences.EnqueueLocation;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import de.danoeh.antennapod.ui.swipeactions.SwipeAction;
import de.danoeh.antennapod.ui.swipeactions.SwipeActions;

public class PreferenceUpgrader {
    private static final String PREF_CONFIGURED_VERSION = "version_code";
    private static final String PREF_NAME = "app_version";

    private static SharedPreferences prefs;

    public static void checkUpgrades(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences upgraderPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int oldVersion = upgraderPrefs.getInt(PREF_CONFIGURED_VERSION, -1);
        int newVersion = BuildConfig.VERSION_CODE;

        if (oldVersion != newVersion) {
            CrashReportWriter.getFile().delete();

            upgrade(oldVersion, newVersion, context);
            upgraderPrefs.edit().putInt(PREF_CONFIGURED_VERSION, newVersion).apply();
        }
    }

    private static void upgrade(int oldVersion, int newVersion, Context context) {
        if (oldVersion == -1) {
            //New installation
            return;
        }
        if (oldVersion < 1070196) {
            // migrate episode cleanup value (unit changed from days to hours)
            int oldValueInDays = UserPreferences.getEpisodeCleanupValue();
            if (oldValueInDays > 0) {
                UserPreferences.setEpisodeCleanupValue(oldValueInDays * 24);
            } // else 0 or special negative values, no change needed
        }
        if (oldVersion < 1070197) {
            if (prefs.getBoolean("prefMobileUpdate", false)) {
                prefs.edit().putString("prefMobileUpdateAllowed", "everything").apply();
            }
        }
        if (oldVersion < 1070300) {
            if (prefs.getBoolean("prefEnableAutoDownloadOnMobile", false)) {
                UserPreferences.setAllowMobileAutoDownload(true);
            }
            switch (prefs.getString("prefMobileUpdateAllowed", "images")) {
                case "everything":
                    UserPreferences.setAllowMobileFeedRefresh(true);
                    UserPreferences.setAllowMobileEpisodeDownload(true);
                    UserPreferences.setAllowMobileImages(true);
                    break;
                default: // Fall-through to "images"
                case "images":
                    UserPreferences.setAllowMobileImages(true);
                    break;
                case "nothing":
                    UserPreferences.setAllowMobileImages(false);
                    break;
            }
        }
        if (oldVersion < 1070400) {
            UserPreferences.ThemePreference theme = UserPreferences.getTheme();
            if (theme == UserPreferences.ThemePreference.LIGHT) {
                prefs.edit().putString(UserPreferences.PREF_THEME, "system").apply();
            }

            UserPreferences.setQueueLocked(false);
            UserPreferences.setStreamOverDownload(false);

            if (!prefs.contains(UserPreferences.PREF_ENQUEUE_LOCATION)) {
                final String keyOldPrefEnqueueFront = "prefQueueAddToFront";
                boolean enqueueAtFront = prefs.getBoolean(keyOldPrefEnqueueFront, false);
                EnqueueLocation enqueueLocation = enqueueAtFront ? EnqueueLocation.FRONT : EnqueueLocation.BACK;
                UserPreferences.setEnqueueLocation(enqueueLocation);
            }
        }
        if (oldVersion < 2010300) {
            // Migrate hardware button preferences
            if (prefs.getBoolean("prefHardwareForwardButtonSkips", false)) {
                prefs.edit().putString(UserPreferences.PREF_HARDWARE_FORWARD_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_NEXT)).apply();
            }
            if (prefs.getBoolean("prefHardwarePreviousButtonRestarts", false)) {
                prefs.edit().putString(UserPreferences.PREF_HARDWARE_PREVIOUS_BUTTON,
                        String.valueOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS)).apply();
            }
        }
        if (oldVersion < 2040000) {
            SharedPreferences swipePrefs = context.getSharedPreferences(SwipeActions.PREF_NAME, Context.MODE_PRIVATE);
            swipePrefs.edit().putString(SwipeActions.KEY_PREFIX_SWIPEACTIONS + QueueFragment.TAG,
                    SwipeAction.REMOVE_FROM_QUEUE + "," + SwipeAction.REMOVE_FROM_QUEUE).apply();
        }
        if (oldVersion < 2050000) {
            prefs.edit().putBoolean(UserPreferences.PREF_PAUSE_PLAYBACK_FOR_FOCUS_LOSS, true).apply();
        }
        if (oldVersion < 2080000) {
            // Migrate drawer feed counter setting to reflect removal of
            // "unplayed and in inbox" (0), by changing it to "unplayed" (2)
            String feedCounterSetting = prefs.getString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "1");
            if (feedCounterSetting.equals("0")) {
                prefs.edit().putString(UserPreferences.PREF_DRAWER_FEED_COUNTER, "2").apply();
            }

            SharedPreferences sleepTimerPreferences =
                    context.getSharedPreferences(SleepTimerPreferences.PREF_NAME, Context.MODE_PRIVATE);
            TimeUnit[] timeUnits = { TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS };
            long value = Long.parseLong(SleepTimerPreferences.lastTimerValue());
            TimeUnit unit = timeUnits[sleepTimerPreferences.getInt("LastTimeUnit", 1)];
            SleepTimerPreferences.setLastTimer(String.valueOf(unit.toMinutes(value)));

            if (prefs.getString(UserPreferences.PREF_EPISODE_CACHE_SIZE, "20")
                    .equals(context.getString(R.string.pref_episode_cache_unlimited))) {
                prefs.edit().putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                        "" + UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED).apply();
            }
        }
        if (oldVersion < 3000007) {
            if (prefs.getString("prefBackButtonBehavior", "").equals("drawer")) {
                prefs.edit().putBoolean(UserPreferences.PREF_BACK_OPENS_DRAWER, true).apply();
            }
        }
        if (oldVersion < 3010000) {
            if (prefs.getString(UserPreferences.PREF_THEME, "system").equals("2")) {
                prefs.edit()
                        .putString(UserPreferences.PREF_THEME, "1")
                        .putBoolean(UserPreferences.PREF_THEME_BLACK, true)
                        .apply();
            }
            if (prefs.getString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, ":").contains(":")) {
                // Unset or "time of day"
                prefs.edit().putString(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES, "12").apply();
            }
        }
        if (oldVersion < 3020000) {
            NotificationManagerCompat.from(context).deleteNotificationChannel("auto_download");
        }

        if (oldVersion < 3030000) {
            SharedPreferences allEpisodesPreferences =
                    context.getSharedPreferences(AllEpisodesFragment.PREF_NAME, Context.MODE_PRIVATE);
            String oldEpisodeSort = allEpisodesPreferences.getString(UserPreferences.PREF_SORT_ALL_EPISODES, "");
            if (!StringUtils.isAllEmpty(oldEpisodeSort)) {
                prefs.edit().putString(UserPreferences.PREF_SORT_ALL_EPISODES, oldEpisodeSort).apply();
            }

            String oldEpisodeFilter = allEpisodesPreferences.getString("filter", "");
            if (!StringUtils.isAllEmpty(oldEpisodeFilter)) {
                prefs.edit().putString(UserPreferences.PREF_FILTER_ALL_EPISODES, oldEpisodeFilter).apply();
            }
        }
        if (oldVersion < 3070000) {
            // If autodownloads are enabled, we will start deleting episodes.
            // To prevent accidents, force off the deletions.
            if (!UserPreferences.isEnableAutodownloadGlobal()) {
                prefs.edit().putString(UserPreferences.PREF_EPISODE_CLEANUP,
                        "" + UserPreferences.EPISODE_CLEANUP_NULL).apply();
            }
        }
        if (newVersion == 3070003) {
            // Enable bottom navigation for beta users, so only this exact app version
            UserPreferences.setBottomNavigationEnabled(true);
        }
        if (oldVersion < 3100000) {
            // Migrate refresh interval from hours to minutes
            UserPreferences.setUpdateInterval(60L * UserPreferences.getUpdateInterval());
            FeedUpdateManager.getInstance().restartUpdateAlarm(context, true);
        }
        if (oldVersion < 3130000) {
            UserPreferences.setBottomNavigationEnabled(true);
        }
        if (oldVersion < 4000935) {
            removeOldSync(context);
        }
        if (oldVersion < 4000936) {
            deleteArchivedShows(context);
        }
    }

    /**
     * 0.9.34 removed archiving (Matt, 18 Nov: archived shows should be deleted). On the first start after
     * updating, any show archived with AntennaPod's old sheet is deleted like any other deleted show: its
     * episodes leave Up Next, its downloads are deleted and the show goes. Its name is kept for Smart Play
     * rules that point at it. Runs off the main thread, once.
     */
    private static void deleteArchivedShows(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                for (Feed feed : DBReader.getFeedList()) {
                    if (feed.getState() != Feed.STATE_ARCHIVED) {
                        continue;
                    }
                    Log.d("PreferenceUpgrader", "Deleting archived show " + feed.getTitle());
                    DeletedShows.remember(app, feed.getId(), feed.getTitle());
                    DBWriter.deleteFeed(app, feed.getId()).get();
                }
            } catch (Exception e) {
                Log.e("PreferenceUpgrader", "Could not delete archived shows", e);
            }
        }, "DeleteArchivedShows").start();
    }

    /**
     * 0.9.33 removed gpodder.net/Nextcloud sync. On the first start after updating, delete what it
     * left on the phone, once: the saved login (gpodder.net), the chosen provider, timestamps and
     * queued episode actions (synchronization), its notification switch, "sync" in the mobile data
     * list, and its scheduled job.
     */
    private static void removeOldSync(Context context) {
        for (String name : new String[] {"gpodder.net", "synchronization"}) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
            context.deleteSharedPreferences(name);
        }
        SharedPreferences.Editor editor = prefs.edit().remove("pref_gpodnet_notifications");
        Set<String> mobile = prefs.getStringSet(UserPreferences.PREF_MOBILE_UPDATE, null);
        if (mobile != null && mobile.contains("sync")) {
            Set<String> kept = new HashSet<>(mobile);
            kept.remove("sync");
            editor.putStringSet(UserPreferences.PREF_MOBILE_UPDATE, kept);
        }
        editor.apply();
        try {
            WorkManager.getInstance(context).cancelUniqueWork("SyncServiceWorkId");
        } catch (Exception e) {
            Log.e("PreferenceUpgrader", "Could not cancel the old sync job", e);
        }
    }
}
