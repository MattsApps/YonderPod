package de.danoeh.antennapod.smartplay.schedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class DayRanges {
    private static final int[] MONDAY_FIRST = {
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    };

    private DayRanges() {
    }

    public static String text(int dayBits, String[] namesByCalendarDay) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < MONDAY_FIRST.length) {
            if (!has(dayBits, MONDAY_FIRST[i])) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < MONDAY_FIRST.length && has(dayBits, MONDAY_FIRST[end + 1])) {
                end++;
            }
            String first = namesByCalendarDay[MONDAY_FIRST[i]];
            String last = namesByCalendarDay[MONDAY_FIRST[end]];
            if (end - i >= 2) {
                parts.add(first + "–" + last);
            } else if (end == i) {
                parts.add(first);
            } else {
                parts.add(first);
                parts.add(last);
            }
            i = end + 1;
        }
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(part);
        }
        return text.toString();
    }

    private static boolean has(int dayBits, int calendarDay) {
        return (dayBits & (1 << (calendarDay - 1))) != 0;
    }
}
