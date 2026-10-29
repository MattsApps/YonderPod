package de.danoeh.antennapod.smartplay.radio;

import java.util.Calendar;
import java.util.TimeZone;

public final class ShowTimes {
    public static final int ALL_DAYS = 0x7F;
    private static final int SEARCH_DAYS = 15;

    public static final class Spec {
        private final String zoneId;
        private final int weekdays;
        private final int startMinute;
        private final int endMinute;

        public Spec(String zoneId, int weekdays, int startMinute, int endMinute) {
            this.zoneId = zoneId;
            this.weekdays = weekdays & ALL_DAYS;
            this.startMinute = Math.max(0, Math.min(24 * 60 - 1, startMinute));
            this.endMinute = Math.max(0, Math.min(24 * 60 - 1, endMinute));
        }

        public String getZoneId() {
            return zoneId;
        }

        public int getWeekdays() {
            return weekdays;
        }

        public int getStartMinute() {
            return startMinute;
        }

        public int getEndMinute() {
            return endMinute;
        }

        public TimeZone timeZone() {
            return TimeZone.getTimeZone(zoneId);
        }

        public long durationMillis() {
            int minutes = endMinute > startMinute ? endMinute - startMinute : endMinute + 24 * 60 - startMinute;
            return minutes * 60_000L;
        }
    }

    public static final class Airing {
        private final long start;
        private final long end;

        public Airing(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Airing && ((Airing) o).start == start && ((Airing) o).end == end;
        }

        @Override
        public int hashCode() {
            return (int) (start ^ (start >>> 32)) * 31 + (int) (end ^ (end >>> 32));
        }
    }

    private ShowTimes() {
    }

    public static int dayBit(int calendarDayOfWeek) {
        return 1 << (calendarDayOfWeek - 1);
    }

    private static Airing airingStartingOn(Spec spec, Calendar day) {
        Calendar start = (Calendar) day.clone();
        start.set(Calendar.HOUR_OF_DAY, spec.getStartMinute() / 60);
        start.set(Calendar.MINUTE, spec.getStartMinute() % 60);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) day.clone();
        if (spec.getEndMinute() <= spec.getStartMinute()) {
            end.add(Calendar.DAY_OF_MONTH, 1);
        }
        end.set(Calendar.HOUR_OF_DAY, spec.getEndMinute() / 60);
        end.set(Calendar.MINUTE, spec.getEndMinute() % 60);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        return new Airing(start.getTimeInMillis(), end.getTimeInMillis());
    }

    private static Calendar noonOf(long time, TimeZone zone) {
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(time);
        day.set(Calendar.HOUR_OF_DAY, 12);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static boolean airsOn(Spec spec, Calendar day) {
        return (spec.getWeekdays() & dayBit(day.get(Calendar.DAY_OF_WEEK))) != 0;
    }

    public static Airing nextStart(Spec spec, long after) {
        Calendar day = noonOf(after, spec.timeZone());
        day.add(Calendar.DAY_OF_MONTH, -1);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            if (airsOn(spec, day)) {
                Airing airing = airingStartingOn(spec, day);
                if (airing.getStart() > after) {
                    return airing;
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    public static Airing nextEnd(Spec spec, long after) {
        Calendar day = noonOf(after, spec.timeZone());
        day.add(Calendar.DAY_OF_MONTH, -2);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            if (airsOn(spec, day)) {
                Airing airing = airingStartingOn(spec, day);
                if (airing.getEnd() > after) {
                    return airing;
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return null;
    }

    public static Airing latestEnded(Spec spec, long now) {
        Calendar day = noonOf(now, spec.timeZone());
        day.add(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < SEARCH_DAYS; i++) {
            if (airsOn(spec, day)) {
                Airing airing = airingStartingOn(spec, day);
                if (airing.getEnd() <= now) {
                    return airing;
                }
            }
            day.add(Calendar.DAY_OF_MONTH, -1);
        }
        return null;
    }

    public static boolean sameClock(TimeZone phone, TimeZone station) {
        return phone.getID().equals(station.getID()) || phone.hasSameRules(station);
    }

    public static long availableAt(Spec spec, Airing airing, TimeZone phone, boolean sameLocalTime) {
        if (!sameLocalTime || sameClock(phone, spec.timeZone())) {
            return airing.getEnd();
        }
        Calendar candidate = Calendar.getInstance(phone);
        candidate.setTimeInMillis(airing.getEnd());
        candidate.set(Calendar.HOUR_OF_DAY, spec.getStartMinute() / 60);
        candidate.set(Calendar.MINUTE, spec.getStartMinute() % 60);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        while (candidate.getTimeInMillis() < airing.getEnd()) {
            candidate.add(Calendar.DAY_OF_MONTH, 1);
        }
        return candidate.getTimeInMillis();
    }
}
