package de.danoeh.antennapod.smartplay.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.ActiveHours;

public final class SmartPlayHours {
    private static final String TAG = "SmartPlayHours";
    private static final String PREFS = "SmartPlayHours";

    private SmartPlayHours() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ActiveHours.Window get(Context context, long smartPlayId) {
        String value = prefs(context).getString(String.valueOf(smartPlayId), null);
        if (value == null) {
            return null;
        }
        try {
            String[] parts = value.split(",");
            return new ActiveHours.Window(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }

    public static void set(Context context, long smartPlayId, ActiveHours.Window window) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (window == null) {
            editor.remove(String.valueOf(smartPlayId));
        } else {
            editor.putString(String.valueOf(smartPlayId), window.getDays() + "," + window.getFromMinute() + ","
                    + window.getToMinute());
        }
        editor.apply();
    }

    public static String text(Context context, ActiveHours.Window window) {
        return context.getString(R.string.smartplay_hours_text, Recordings.days(context, window.getDays()),
                ScheduleText.time(context, window.getFromMinute() / 60, window.getFromMinute() % 60),
                ScheduleText.time(context, window.getToMinute() / 60, window.getToMinute() % 60));
    }

    public static String nameWithHours(Context context, SmartPlayStore.StoredSmartPlay smartPlay) {
        ActiveHours.Window window = get(context, smartPlay.getId());
        String name = smartPlay.getDefinition().getName();
        return window == null ? name : context.getString(R.string.smartplay_with_hours, name, text(context, window));
    }

    static List<ActiveHours.Window> windows(Context context, List<SmartPlayStore.StoredSmartPlay> smartPlays) {
        List<ActiveHours.Window> windows = new ArrayList<>();
        for (SmartPlayStore.StoredSmartPlay smartPlay : smartPlays) {
            windows.add(get(context, smartPlay.getId()));
        }
        return windows;
    }

    public static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent intent = alarmIntent(app);
        alarmManager.cancel(intent);
        List<ActiveHours.Window> windows = windows(app, SmartPlayStore.getInstance(app).getAll());
        long next = ActiveHours.nextBoundary(windows, System.currentTimeMillis(), TimeZone.getDefault());
        if (next == Long.MIN_VALUE) {
            return;
        }
        if (Recordings.canScheduleExact(app)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, intent);
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next + 1000, intent);
        }
    }

    static void onBoundary(Context context) {
        List<SmartPlayStore.StoredSmartPlay> smartPlays = SmartPlayStore.getInstance(context).getAll();
        int index = ActiveHours.activeIndex(windows(context, smartPlays), System.currentTimeMillis(),
                TimeZone.getDefault());
        SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
        if (index >= 0 && manager != null) {
            Log.d(TAG, "Switching to " + smartPlays.get(index).getDefinition().getName());
            manager.switchByHours(smartPlays.get(index).getId());
        }
        reschedule(context);
    }

    private static PendingIntent alarmIntent(Context context) {
        return PendingIntent.getBroadcast(context, 0, new Intent(context, SmartPlayHoursReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
