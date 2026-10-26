package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class SleepTimerPreferences {

    private static final String TAG = "SleepTimerPreferences";

    public static final String PREF_NAME = "SleepTimerDialog";
    private static final String PREF_VALUE_MINUTES = "LastValue";
    private static final String PREF_VALUE_EPISODES = "LastValueEpisodes";

    private static final String PREF_TIMER_TYPE = "sleepTimerType";
    private static final String PREF_VIBRATE = "Vibrate";
    private static final String PREF_SHAKE_TO_RESET = "ShakeToReset";
    private static final String PREF_AUTO_ENABLE = "AutoEnable";
    private static final String PREF_AUTO_ENABLE_FROM = "AutoEnableFrom";
    private static final String PREF_AUTO_ENABLE_TO = "AutoEnableTo";
    private static final String PREF_BEDTIME_MINUTES = "BedtimeMinutes";
    private static final String PREF_STOP_AT_ENABLED = "StopAtEnabled";
    private static final String PREF_STOP_AT_MINUTE = "StopAtMinute";
    private static final String PREF_STOP_AT_DAYS = "StopAtDays";
    private static final String PREF_STOP_AT_FINISH_EPISODE = "StopAtFinishEpisode";
    private static final String PREF_GRID_CHOICE = "GridChoice";
    public static final int EPISODE_ENDS = 0;
    public static final int GRID_EPISODE_ENDS = -2;
    public static final int GRID_EPISODES = -1;
    public static final int ALL_DAYS = 0x7F;

    private static final int DEFAULT_TIMER_TYPE = 0;
    private static final int DEFAULT_AUTO_ENABLE_FROM = 22;
    private static final int DEFAULT_AUTO_ENABLE_TO = 6;

    private static SharedPreferences prefs;

    /**
     * Sets up the UserPreferences class.
     *
     * @throws IllegalArgumentException if context is null
     */
    public static void init(@NonNull Context context) {
        Log.d(TAG, "Creating new instance of SleepTimerPreferences");
        SleepTimerPreferences.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setLastTimer(String value) {
        prefs.edit().putString(getSleepTimerType() == SleepTimerType.CLOCK
                ? PREF_VALUE_MINUTES : PREF_VALUE_EPISODES, value).apply();
    }

    public static String lastTimerValue() {
        if (getSleepTimerType() == SleepTimerType.CLOCK) {
            return prefs.getString(PREF_VALUE_MINUTES, "15");
        } else {
            return prefs.getString(PREF_VALUE_EPISODES, "1");
        }
    }

    public static long timerMillisOrEpisodes() {
        return switch (getSleepTimerType()) {
            case CLOCK -> TimeUnit.MINUTES.toMillis(Long.parseLong(lastTimerValue()));
            case EPISODES -> Long.parseLong(SleepTimerPreferences.lastTimerValue());
        };
    }

    public static SleepTimerType getSleepTimerType() {
        return SleepTimerType.fromIndex(prefs.getInt(PREF_TIMER_TYPE, DEFAULT_TIMER_TYPE));
    }

    public static void setSleepTimerType(SleepTimerType newType) {
        prefs.edit().putInt(PREF_TIMER_TYPE, newType.index).apply();
    }

    public static void setVibrate(boolean vibrate) {
        prefs.edit().putBoolean(PREF_VIBRATE, vibrate).apply();
    }

    public static boolean vibrate() {
        return prefs.getBoolean(PREF_VIBRATE, false);
    }

    public static void setShakeToReset(boolean shakeToReset) {
        prefs.edit().putBoolean(PREF_SHAKE_TO_RESET, shakeToReset).apply();
    }

    public static boolean shakeToReset() {
        return prefs.getBoolean(PREF_SHAKE_TO_RESET, true);
    }

    public static void setAutoEnable(boolean autoEnable) {
        prefs.edit().putBoolean(PREF_AUTO_ENABLE, autoEnable).apply();
    }

    public static boolean autoEnable() {
        return prefs.getBoolean(PREF_AUTO_ENABLE, false);
    }

    public static void setAutoEnableFrom(int hourOfDay) {
        prefs.edit().putInt(PREF_AUTO_ENABLE_FROM, hourOfDay).apply();
    }

    public static int autoEnableFrom() {
        return prefs.getInt(PREF_AUTO_ENABLE_FROM, DEFAULT_AUTO_ENABLE_FROM);
    }

    public static void setAutoEnableTo(int hourOfDay) {
        prefs.edit().putInt(PREF_AUTO_ENABLE_TO, hourOfDay).apply();
    }

    public static int autoEnableTo() {
        return prefs.getInt(PREF_AUTO_ENABLE_TO, DEFAULT_AUTO_ENABLE_TO);
    }

    public static int autoEnableDuration() {
        final int from = SleepTimerPreferences.autoEnableFrom();
        final int to = SleepTimerPreferences.autoEnableTo();
        if (from >= to) {
            return 24 - (from - to);
        } else {
            return to - from;
        }
    }

    public static int bedtimeMinutes() {
        return prefs.getInt(PREF_BEDTIME_MINUTES, EPISODE_ENDS);
    }

    public static void setBedtimeMinutes(int minutes) {
        prefs.edit().putInt(PREF_BEDTIME_MINUTES, minutes).apply();
    }

    public static boolean stopAtEnabled() {
        return prefs.getBoolean(PREF_STOP_AT_ENABLED, false);
    }

    public static void setStopAtEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_STOP_AT_ENABLED, enabled).apply();
    }

    public static int stopAtMinuteOfDay() {
        return prefs.getInt(PREF_STOP_AT_MINUTE, 60);
    }

    public static void setStopAtMinuteOfDay(int minuteOfDay) {
        prefs.edit().putInt(PREF_STOP_AT_MINUTE, minuteOfDay).apply();
    }

    public static int stopAtDays() {
        return prefs.getInt(PREF_STOP_AT_DAYS, ALL_DAYS);
    }

    public static void setStopAtDays(int days) {
        prefs.edit().putInt(PREF_STOP_AT_DAYS, days).apply();
    }

    public static boolean stopAtFinishEpisode() {
        return prefs.getBoolean(PREF_STOP_AT_FINISH_EPISODE, false);
    }

    public static void setStopAtFinishEpisode(boolean finishEpisode) {
        prefs.edit().putBoolean(PREF_STOP_AT_FINISH_EPISODE, finishEpisode).apply();
    }

    public static int gridChoice() {
        return prefs.getInt(PREF_GRID_CHOICE, 0);
    }

    public static void setGridChoice(int minutesOrEpisodeEnds) {
        prefs.edit().putInt(PREF_GRID_CHOICE, minutesOrEpisodeEnds).apply();
    }

    public static long stopAtTrigger(long now, long leadMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, stopAtMinuteOfDay() / 60);
        calendar.set(Calendar.MINUTE, stopAtMinuteOfDay() % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long trigger = calendar.getTimeInMillis() - leadMillis;
        if (trigger > now + 12L * 60 * 60 * 1000) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
            trigger = calendar.getTimeInMillis() - leadMillis;
        }
        int dayBit = 1 << (calendar.get(Calendar.DAY_OF_WEEK) - 1);
        return (stopAtDays() & dayBit) != 0 ? trigger : -1;
    }

    public static boolean isInTimeRange(int from, int to, int current) {
        // Range covers one day
        if (from < to) {
            return from <= current && current < to;
        }

        // Range covers two days
        if (from <= current) {
            return true;
        }

        return current < to;
    }
}
