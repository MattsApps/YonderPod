package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class ControlsPreferences {
    public static final String PREFS = "YonderControls";
    public static final String RESUME_OFF = "off";
    public static final String RESUME_ANY = "any";
    public static final String RESUME_CAR = "car";
    public static final int TIERS = 3;
    private static final int[] DEFAULT_THRESHOLD_MINUTES = {1, 60, 24 * 60};
    private static final int[] DEFAULT_REWIND_SECONDS = {5, 15, 30};
    private static final int MAX_LOG = 50;

    private static final String KEY_BUTTON = "button_";
    private static final String KEY_REWIND_ENABLED = "rewindEnabled";
    private static final String KEY_THRESHOLD = "rewindThreshold_";
    private static final String KEY_REWIND = "rewindSeconds_";
    private static final String KEY_INTERRUPTION_BACK = "interruptionBackSeconds";
    private static final String KEY_DUCK = "duckNotifications";
    private static final String KEY_RESUME = "resumeOnReconnect";
    private static final String KEY_CAR_DEVICES = "carDevices";
    private static final String KEY_KNOWN_DEVICES = "knownDevices";
    private static final String KEY_FADE_IN = "fadeIn";
    private static final String KEY_KEEP_SCREEN_ON = "keepScreenOn";
    private static final String KEY_SHOW_SPEED = "showSpeedButton";
    private static final String KEY_CAR_MODE_AUTO = "carModeAuto";
    private static final String KEY_CAR_MODE_DEVICES = "carModeDevices";

    private static SharedPreferences prefs;
    private static volatile boolean testing = false;
    private static final List<String> LOG = new ArrayList<>();
    private static final RewindOnce REWIND_ONCE = new RewindOnce();

    private ControlsPreferences() {
    }

    public static void init(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ButtonAction getButtonAction(ButtonPress press) {
        try {
            return ButtonAction.valueOf(prefs.getString(KEY_BUTTON + press.name(), press.defaultAction.name()));
        } catch (IllegalArgumentException e) {
            return press.defaultAction;
        }
    }

    public static void setButtonAction(ButtonPress press, ButtonAction action) {
        prefs.edit().putString(KEY_BUTTON + press.name(), action.name()).apply();
    }

    public static boolean isRewindEnabled() {
        return prefs.getBoolean(KEY_REWIND_ENABLED, true);
    }

    public static void setRewindEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REWIND_ENABLED, enabled).apply();
    }

    public static int getRewindThresholdMinutes(int tier) {
        return prefs.getInt(KEY_THRESHOLD + tier, DEFAULT_THRESHOLD_MINUTES[tier]);
    }

    public static void setRewindThresholdMinutes(int tier, int minutes) {
        prefs.edit().putInt(KEY_THRESHOLD + tier, minutes).apply();
    }

    public static int getRewindSeconds(int tier) {
        return prefs.getInt(KEY_REWIND + tier, DEFAULT_REWIND_SECONDS[tier]);
    }

    public static void setRewindSeconds(int tier, int seconds) {
        prefs.edit().putInt(KEY_REWIND + tier, seconds).apply();
    }

    public static long rewindMillisAfter(long pausedMillis) {
        if (prefs == null || !isRewindEnabled() || pausedMillis <= 0) {
            return 0;
        }
        long rewind = 0;
        long bestThreshold = -1;
        for (int tier = 0; tier < TIERS; tier++) {
            long threshold = TimeUnit.MINUTES.toMillis(getRewindThresholdMinutes(tier));
            if (pausedMillis >= threshold && threshold > bestThreshold) {
                bestThreshold = threshold;
                rewind = TimeUnit.SECONDS.toMillis(getRewindSeconds(tier));
            }
        }
        return rewind;
    }

    public static int positionWithRewind(long mediaId, int position, long lastPlayedTimeStatistics) {
        return REWIND_ONCE.resumePosition(mediaId, position, lastPlayedTimeStatistics,
                System.currentTimeMillis(), ControlsPreferences::rewindMillisAfter);
    }

    public static int getInterruptionBackSeconds() {
        return prefs.getInt(KEY_INTERRUPTION_BACK, 2);
    }

    public static void setInterruptionBackSeconds(int seconds) {
        prefs.edit().putInt(KEY_INTERRUPTION_BACK, seconds).apply();
    }

    public static boolean duckNotifications() {
        return prefs != null && prefs.getBoolean(KEY_DUCK, false);
    }

    public static void setDuckNotifications(boolean duck) {
        prefs.edit().putBoolean(KEY_DUCK, duck).apply();
    }

    public static String getResumeOnReconnect() {
        return prefs.getString(KEY_RESUME, RESUME_OFF);
    }

    public static void setResumeOnReconnect(String mode) {
        prefs.edit().putString(KEY_RESUME, mode).apply();
    }

    public static Set<String> getCarDevices() {
        return new HashSet<>(prefs.getStringSet(KEY_CAR_DEVICES, Collections.emptySet()));
    }

    public static void setCarDevices(Set<String> devices) {
        prefs.edit().putStringSet(KEY_CAR_DEVICES, new HashSet<>(devices)).apply();
    }

    public static Set<String> getKnownDevices() {
        return new HashSet<>(prefs.getStringSet(KEY_KNOWN_DEVICES, Collections.emptySet()));
    }

    public static void rememberDevice(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        Set<String> known = getKnownDevices();
        if (known.add(name)) {
            prefs.edit().putStringSet(KEY_KNOWN_DEVICES, known).apply();
        }
    }

    public static boolean fadeIn() {
        return prefs != null && prefs.getBoolean(KEY_FADE_IN, false);
    }

    public static void setFadeIn(boolean fadeIn) {
        prefs.edit().putBoolean(KEY_FADE_IN, fadeIn).apply();
    }

    public static boolean keepScreenOn() {
        return prefs != null && prefs.getBoolean(KEY_KEEP_SCREEN_ON, false);
    }

    public static void setKeepScreenOn(boolean keepScreenOn) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn).apply();
    }

    public static boolean showSpeedButton() {
        return prefs == null || prefs.getBoolean(KEY_SHOW_SPEED, true);
    }

    public static void setShowSpeedButton(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_SPEED, show).apply();
    }

    public static boolean carModeAuto() {
        return prefs != null && prefs.getBoolean(KEY_CAR_MODE_AUTO, false);
    }

    public static void setCarModeAuto(boolean auto) {
        prefs.edit().putBoolean(KEY_CAR_MODE_AUTO, auto).apply();
    }

    public static Set<String> getCarModeDevices() {
        return new HashSet<>(prefs.getStringSet(KEY_CAR_MODE_DEVICES, Collections.emptySet()));
    }

    public static void setCarModeDevices(Set<String> devices) {
        prefs.edit().putStringSet(KEY_CAR_MODE_DEVICES, new HashSet<>(devices)).apply();
    }

    public static boolean isTesting() {
        return testing;
    }

    public static void setTesting(boolean isTesting) {
        testing = isTesting;
    }

    public static final String LOG_SEPARATOR = "\u001F";

    public static void log(String friendly, String raw) {
        log(friendly + LOG_SEPARATOR + raw);
    }

    public static void log(String line) {
        synchronized (LOG) {
            LOG.add(0, line);
            while (LOG.size() > MAX_LOG) {
                LOG.remove(LOG.size() - 1);
            }
        }
    }

    public static List<String> getLog() {
        synchronized (LOG) {
            return new ArrayList<>(LOG);
        }
    }

    public static void clearLog() {
        synchronized (LOG) {
            LOG.clear();
        }
    }
}
