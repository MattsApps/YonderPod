package de.danoeh.antennapod.smartplay.schedule;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class ActiveHours {
    public static final int ALL_DAYS = 0x7F;
    private static final int WEEK_MINUTES = 7 * 24 * 60;

    public static final class Window {
        private final int days;
        private final int fromMinute;
        private final int toMinute;

        public Window(int days, int fromMinute, int toMinute) {
            this.days = days & ALL_DAYS;
            this.fromMinute = Math.max(0, Math.min(24 * 60 - 1, fromMinute));
            this.toMinute = Math.max(0, Math.min(24 * 60 - 1, toMinute));
        }

        public int getDays() {
            return days;
        }

        public int getFromMinute() {
            return fromMinute;
        }

        public int getToMinute() {
            return toMinute;
        }

        boolean coversWeekMinute(int weekMinute) {
            int day = weekMinute / (24 * 60);
            int minute = weekMinute % (24 * 60);
            if (fromMinute == toMinute) {
                return false;
            }
            if (fromMinute < toMinute) {
                return runsOn(day) && minute >= fromMinute && minute < toMinute;
            }
            int yesterday = (day + 6) % 7;
            return (runsOn(day) && minute >= fromMinute) || (runsOn(yesterday) && minute < toMinute);
        }

        private boolean runsOn(int dayIndex) {
            return (days & (1 << dayIndex)) != 0;
        }
    }

    private ActiveHours() {
    }

    public static int dayBit(int calendarDayOfWeek) {
        return 1 << (calendarDayOfWeek - 1);
    }

    private static int weekMinute(long time, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(time);
        return (calendar.get(Calendar.DAY_OF_WEEK) - 1) * 24 * 60
                + calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    public static int activeIndex(List<Window> windows, long now, TimeZone timeZone) {
        int minute = weekMinute(now, timeZone);
        for (int i = 0; i < windows.size(); i++) {
            Window window = windows.get(i);
            if (window != null && window.coversWeekMinute(minute)) {
                return i;
            }
        }
        return -1;
    }

    public static long nextBoundary(List<Window> windows, long now, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int current = activeIndex(windows, calendar.getTimeInMillis(), timeZone);
        for (int i = 0; i < WEEK_MINUTES + 1; i++) {
            calendar.add(Calendar.MINUTE, 1);
            long time = calendar.getTimeInMillis();
            if (activeIndex(windows, time, timeZone) != current) {
                return time;
            }
        }
        return Long.MIN_VALUE;
    }

    public static boolean overlap(Window a, Window b) {
        if (a == null || b == null) {
            return false;
        }
        for (int minute = 0; minute < WEEK_MINUTES; minute++) {
            if (a.coversWeekMinute(minute) && b.coversWeekMinute(minute)) {
                return true;
            }
        }
        return false;
    }
}
