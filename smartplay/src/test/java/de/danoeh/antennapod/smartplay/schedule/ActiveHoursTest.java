package de.danoeh.antennapod.smartplay.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

public class ActiveHoursTest {
    private static final TimeZone NZ = TimeZone.getTimeZone("Pacific/Auckland");

    private static long at(int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(NZ);
        calendar.clear();
        calendar.set(2026, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static int weekdays() {
        int bits = 0;
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            bits |= ActiveHours.dayBit(day);
        }
        return bits;
    }

    private static final ActiveHours.Window MORNINGS = new ActiveHours.Window(weekdays(), 6 * 60, 12 * 60);
    private static final ActiveHours.Window AFTERNOONS = new ActiveHours.Window(ActiveHours.ALL_DAYS, 12 * 60, 17 * 60);
    private static final ActiveHours.Window EVENINGS = new ActiveHours.Window(ActiveHours.ALL_DAYS, 17 * 60, 6 * 60);

    @Test
    public void picksTheWindowCoveringNow() {
        List<ActiveHours.Window> windows = Arrays.asList(MORNINGS, AFTERNOONS, EVENINGS);
        assertEquals(0, ActiveHours.activeIndex(windows, at(11, 2, 9, 0), NZ));
        assertEquals(1, ActiveHours.activeIndex(windows, at(11, 2, 13, 0), NZ));
        assertEquals(2, ActiveHours.activeIndex(windows, at(11, 2, 23, 0), NZ));
        assertEquals(2, ActiveHours.activeIndex(windows, at(11, 3, 2, 0), NZ));
    }

    @Test
    public void weekendMorningsHaveNoActiveList() {
        List<ActiveHours.Window> windows = Arrays.asList(MORNINGS, AFTERNOONS, EVENINGS);
        assertEquals(-1, ActiveHours.activeIndex(windows, at(11, 1, 9, 0), NZ));
    }

    @Test
    public void listsWithoutHoursAreSkipped() {
        assertEquals(1, ActiveHours.activeIndex(Arrays.asList(null, AFTERNOONS), at(11, 2, 13, 0), NZ));
    }

    @Test
    public void nextBoundaryIsTheNextSwitch() {
        List<ActiveHours.Window> windows = Arrays.asList(MORNINGS, AFTERNOONS, EVENINGS);
        assertEquals(at(11, 2, 12, 0), ActiveHours.nextBoundary(windows, at(11, 2, 9, 30), NZ));
        assertEquals(at(11, 3, 6, 0), ActiveHours.nextBoundary(windows, at(11, 2, 23, 0), NZ));
    }

    @Test
    public void boundariesAFewMinutesApart() {
        ActiveHours.Window a = new ActiveHours.Window(ActiveHours.ALL_DAYS, 22 * 60 + 40, 22 * 60 + 45);
        ActiveHours.Window b = new ActiveHours.Window(ActiveHours.ALL_DAYS, 22 * 60 + 45, 22 * 60 + 50);
        List<ActiveHours.Window> windows = Arrays.asList(a, b);
        assertEquals(at(11, 1, 22, 40), ActiveHours.nextBoundary(windows, at(11, 1, 22, 35), NZ));
        assertEquals(0, ActiveHours.activeIndex(windows, at(11, 1, 22, 41), NZ));
        assertEquals(at(11, 1, 22, 45), ActiveHours.nextBoundary(windows, at(11, 1, 22, 41), NZ));
        assertEquals(1, ActiveHours.activeIndex(windows, at(11, 1, 22, 45), NZ));
    }

    @Test
    public void overlapsAreFound() {
        assertTrue(ActiveHours.overlap(MORNINGS, new ActiveHours.Window(ActiveHours.ALL_DAYS, 11 * 60, 13 * 60)));
        assertFalse(ActiveHours.overlap(MORNINGS, AFTERNOONS));
        assertFalse(ActiveHours.overlap(AFTERNOONS, EVENINGS));
        assertTrue(ActiveHours.overlap(EVENINGS, new ActiveHours.Window(ActiveHours.ALL_DAYS, 60, 120)));
    }
}
