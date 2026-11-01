package de.danoeh.antennapod.smartplay.schedule;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.junit.Test;

public class DayRangesTest {
    private static final String[] NAMES = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private static int days(int... calendarDays) {
        int bits = 0;
        for (int day : calendarDays) {
            bits |= 1 << (day - 1);
        }
        return bits;
    }

    @Test
    public void weekdaysBecomeARange() {
        assertEquals("Mon–Fri", DayRanges.text(days(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY), NAMES));
    }

    @Test
    public void twoDaysAreListed() {
        assertEquals("Sat, Sun", DayRanges.text(days(Calendar.SATURDAY, Calendar.SUNDAY), NAMES));
    }

    @Test
    public void mixedDays() {
        assertEquals("Mon, Wed–Fri, Sun", DayRanges.text(days(Calendar.MONDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SUNDAY), NAMES));
    }

    @Test
    public void oneDay() {
        assertEquals("Tue", DayRanges.text(days(Calendar.TUESDAY), NAMES));
    }
}
