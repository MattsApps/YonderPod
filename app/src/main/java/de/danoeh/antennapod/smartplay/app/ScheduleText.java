package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;

import androidx.core.os.ConfigurationCompat;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.ScheduleTimes;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;

public final class ScheduleText {
    public static final int[] DAYS_FROM_MONDAY = {
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    };

    private ScheduleText() {
    }

    public static String time(Context context, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis((hour * 60L + minute) * 60000L);
        Locale locale = ConfigurationCompat.getLocales(context.getResources().getConfiguration()).get(0);
        return DateFormat.format(DateFormat.getBestDateTimePattern(locale == null ? Locale.getDefault() : locale,
                DateFormat.is24HourFormat(context) ? "Hm" : "hma"), calendar).toString();
    }

    public static String time(Context context, long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return time(context, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    public static String shortDay(int calendarDayOfWeek) {
        return DateFormatSymbols.getInstance().getShortWeekdays()[calendarDayOfWeek];
    }

    public static String days(Context context, UpdateSchedule schedule) {
        if (schedule.getEveryNDays() >= 2) {
            return context.getResources().getQuantityString(R.plurals.schedule_every_n_days, schedule.getEveryNDays(),
                    schedule.getEveryNDays());
        }
        if (schedule.isEveryDay()) {
            return context.getString(R.string.schedule_every_day);
        }
        List<String> names = new ArrayList<>();
        for (int day : DAYS_FROM_MONDAY) {
            if (schedule.runsOn(day)) {
                names.add(shortDay(day));
            }
        }
        if (names.isEmpty()) {
            return context.getString(R.string.schedule_no_days);
        }
        return TextUtils.join(context.getString(R.string.list_separator), names);
    }

    public static String summary(Context context, UpdateSchedule schedule) {
        String text = context.getString(R.string.schedule_summary, days(context, schedule),
                time(context, schedule.getHour(), schedule.getMinute()));
        text = context.getString(R.string.detail_joined, text, context.getString(schedule.isWifiOnly()
                ? R.string.schedule_condition_wifi : R.string.schedule_condition_mobile));
        if (schedule.isChargingOnly()) {
            text = context.getString(R.string.detail_joined, text,
                    context.getString(R.string.schedule_condition_charging));
        }
        return text;
    }

    public static String window(Context context, UpdateSchedule schedule) {
        int end = schedule.getMinuteOfDay() + UpdateSchedule.WINDOW_MINUTES;
        return context.getString(R.string.schedule_window,
                time(context, schedule.getHour(), schedule.getMinute()),
                time(context, (end / 60) % 24, end % 60));
    }

    public static String updated(Context context) {
        long last = UpdateScheduler.lastUpdated(context);
        if (last <= 0) {
            return context.getString(R.string.schedule_status_never);
        }
        String text = time(context, last);
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(last);
        Calendar today = Calendar.getInstance();
        if (then.get(Calendar.YEAR) != today.get(Calendar.YEAR)
                || then.get(Calendar.DAY_OF_YEAR) != today.get(Calendar.DAY_OF_YEAR)) {
            text = context.getString(R.string.time_and_day, text, shortDay(then.get(Calendar.DAY_OF_WEEK)));
        }
        return context.getString(R.string.schedule_status_updated, text);
    }

    public static String next(Context context) {
        long next = UpdateScheduler.nextRun(context);
        if (next == ScheduleTimes.NONE) {
            return context.getString(R.string.schedule_status_none);
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(next);
        return context.getString(R.string.schedule_status_next,
                context.getString(R.string.time_and_day, time(context, next),
                        shortDay(calendar.get(Calendar.DAY_OF_WEEK))));
    }
}
