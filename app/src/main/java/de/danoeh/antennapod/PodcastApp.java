package de.danoeh.antennapod;

import de.danoeh.antennapod.playback.base.ColdStart;
import android.app.Application;
import android.util.Log;

import com.google.android.material.color.DynamicColors;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

/** Main application class. */
public class PodcastApp extends Application {
    private static final String TAG = "PodcastApp";

    @Override
    public void onCreate() {
        // 1.0.8: cold-start timing. Before this, Android starts the process and runs the content providers
        // (WorkManager's start-up among them), so this line's time is how long that took.
        ColdStart.stage("Application setup starts (process start is 0 ms)");
        long setupStart = ColdStart.now();
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashReportExceptionHandler());
        RxJavaErrorHandlerSetup.setupRxJavaErrorHandler();

        try {
            // Robolectric calls onCreate for every test, which causes problems with static members
            EventBus.builder()
                    .logNoSubscriberMessages(false)
                    .sendNoSubscriberEvent(false)
                    .installDefaultEventBus();
        } catch (EventBusException e) {
            Log.d(TAG, e.getMessage());
        }

        DynamicColors.applyToActivitiesIfAvailable(this);
        ClientConfigurator.initialize(this);
        long upgrades = ColdStart.now();
        PreferenceUpgrader.checkUpgrades(this);
        ColdStart.timed("preference upgrades", upgrades);
        ColdStart.timed("Application setup ends", setupStart);
    }
}
