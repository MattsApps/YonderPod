package de.danoeh.antennapod;

import de.danoeh.antennapod.playback.base.ColdStart;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import de.danoeh.antennapod.playback.service.LiveBuffer;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.UsageStatistics;
import de.danoeh.antennapod.net.common.UserAgentInterceptor;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.net.download.service.feed.DownloadServiceInterfaceImpl;
import de.danoeh.antennapod.net.download.service.feed.FeedUpdateWorker;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.ssl.SslProviderInstaller;
import de.danoeh.antennapod.smartplay.app.BookTopUpWorker;
import de.danoeh.antennapod.smartplay.app.SmartPlayQueueManager;
import de.danoeh.antennapod.smartplay.app.ImportedPlayState;
import de.danoeh.antennapod.smartplay.app.UpdateScheduler;
import de.danoeh.antennapod.smartplay.app.YonderAutoDownloadManager;
import de.danoeh.antennapod.smartplay.app.YonderFeedUpdateManager;
import de.danoeh.antennapod.storage.database.PodDBAdapter;

import de.danoeh.antennapod.ui.notifications.NotificationUtils;
import de.danoeh.antennapod.ui.widget.WidgetUpdaterWorker;
import java.io.File;

public class ClientConfigurator {
    private static boolean initialized = false;

    public static synchronized void initialize(Context context) {
        if (initialized) {
            return;
        }
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            UserAgentInterceptor.USER_AGENT = "AntennaPod/" + packageInfo.versionName;
            UserAgentInterceptor.YONDER_VERSION = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        long t = ColdStart.now();
        PodDBAdapter.init(context);
        ColdStart.timed("database", t);
        t = ColdStart.now();
        UserPreferences.init(context);
        UsageStatistics.init(context);
        PlaybackPreferences.init(context);
        boolean wasPlaying = PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
        if (wasPlaying) {
            PlaybackPreferences.setCurrentPlayerStatus(PlaybackPreferences.PLAYER_STATUS_PAUSED);
        }
        ColdStart.timed("preferences", t);
        t = ColdStart.now();
        SslProviderInstaller.install(context);
        ColdStart.timed("security provider install", t);
        t = ColdStart.now();
        NetworkUtils.init(context);
        DownloadServiceInterface.setImpl(new DownloadServiceInterfaceImpl());
        FeedUpdateManager.setInstance(new YonderFeedUpdateManager());
        FeedUpdateWorker.setRefreshesWithAll(feed -> UpdateScheduler.refreshesWithAll(context, feed));
        AutoDownloadManager.setInstance(new YonderAutoDownloadManager(context));
        AntennapodHttpClient.setCacheDirectory(new File(context.getCacheDir(), "okhttp"));
        AntennapodHttpClient.setProxyConfig(UserPreferences.getProxyConfig());
        ColdStart.timed("network and downloads setup", t);
        t = ColdStart.now();
        SleepTimerPreferences.init(context);
        ControlsPreferences.init(context);
        NextUpPreferences.init(context);
        RadioPreferences.init(context);
        ColdStart.timed("more preferences", t);
        t = ColdStart.now();
        NotificationUtils.createChannels(context);
        ColdStart.timed("notification channels", t);
        t = ColdStart.now();
        SmartPlayQueueManager.init(context);
        ColdStart.timed("smart play manager", t);
        t = ColdStart.now();
        BookTopUpWorker.init(context);
        UpdateScheduler.init(context);
        ColdStart.timed("background jobs (book top-up, refresh schedule)", t);
        t = ColdStart.now();
        ImportedPlayState.init(context);
        // Killed during live pause (paused or behind live): the widget still shows the live pause layout,
        // so redraw it as the station paused at live, as after any kill.
        boolean wasLivePause = LiveBuffer.deleteLeftovers(context);
        if (wasPlaying || wasLivePause) {
            WidgetUpdaterWorker.enqueueWork(context);
        }
        ColdStart.timed("imported play state, live pause leftovers", t);
        initialized = true;
    }
}
