package de.danoeh.antennapod.smartplay.schedule;

import java.util.Calendar;
import java.util.TimeZone;

public final class ScheduleTimes {
    public static final long NONE = Long.MIN_VALUE;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int SEARCH_DAYS = 400;

    private ScheduleTimes() {
    }

    public static long epochDay(long time, TimeZone timeZone) {
        long local = time + timeZone.getOffset(time);
        long day = local / DAY_MS;
        return local % DAY_MS < 0 ? day - 1 : day;
    }

    public static long previousStart(UpdateSchedule schedule, long now, TimeZone timeZone) {
        Calendar day = startOfDay(now, timeZone);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            long start = startOn(day, schedule);
            if (start <= now && matches(schedule, day, timeZone)) {
                return start;
            }
            day.add(Calendar.DAY_OF_MONTH, -1);
        }
        return NONE;
    }

    public static long nextStart(UpdateSchedule schedule, long now, TimeZone timeZone) {
        Calendar day = startOfDay(now, timeZone);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            long start = startOn(day, schedule);
            if (start > now && matches(schedule, day, timeZone)) {
                return start;
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return NONE;
    }

    public static boolean isDue(UpdateSchedule schedule, long lastTriggered, long since, long now,
                                TimeZone timeZone) {
        long previous = previousStart(schedule, now, timeZone);
        return previous != NONE && previous > Math.max(lastTriggered, since);
    }

    private static Calendar startOfDay(long time, TimeZone timeZone) {
        Calendar day = Calendar.getInstance(timeZone);
        day.setTimeInMillis(time);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static long startOn(Calendar day, UpdateSchedule schedule) {
        Calendar start = (Calendar) day.clone();
        start.set(Calendar.HOUR_OF_DAY, schedule.getHour());
        start.set(Calendar.MINUTE, schedule.getMinute());
        return start.getTimeInMillis();
    }

    private static boolean matches(UpdateSchedule schedule, Calendar day, TimeZone timeZone) {
        if (schedule.getEveryNDays() >= 2) {
            long days = epochDay(day.getTimeInMillis(), timeZone) - schedule.getAnchorDay();
            return ((days % schedule.getEveryNDays()) + schedule.getEveryNDays()) % schedule.getEveryNDays() == 0;
        }
        return schedule.runsOn(day.get(Calendar.DAY_OF_WEEK));
    }
}
