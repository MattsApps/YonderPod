package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class ShowTimesTest {
    private static final TimeZone LONDON = TimeZone.getTimeZone("Europe/London");
    private static final TimeZone AUCKLAND = TimeZone.getTimeZone("Pacific/Auckland");

    private static long at(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static int weekdays() {
        int bits = 0;
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            bits |= ShowTimes.dayBit(day);
        }
        return bits;
    }

    private static final ShowTimes.Spec EVENING = new ShowTimes.Spec("Europe/London", weekdays(), 18 * 60, 21 * 60);

    @Test
    public void nextAiringIsInLondonTime() {
        long nowInAuckland = at(AUCKLAND, 2026, 10, 29, 22, 0);
        ShowTimes.Airing next = ShowTimes.nextStart(EVENING, nowInAuckland);
        assertEquals(at(LONDON, 2026, 10, 29, 18, 0), next.getStart());
        assertEquals(at(LONDON, 2026, 10, 29, 21, 0), next.getEnd());
    }

    @Test
    public void weekendsAreSkipped() {
        long fridayNight = at(LONDON, 2026, 10, 30, 22, 0);
        assertEquals(at(LONDON, 2026, 11, 2, 18, 0), ShowTimes.nextStart(EVENING, fridayNight).getStart());
    }

    @Test
    public void ukClockChangeIsFollowed() {
        long beforeChange = at(LONDON, 2026, 10, 23, 22, 0);
        long afterChange = at(LONDON, 2026, 10, 26, 12, 0);
        long friday = ShowTimes.nextStart(EVENING, at(LONDON, 2026, 10, 23, 12, 0)).getStart();
        long monday = ShowTimes.nextStart(EVENING, afterChange).getStart();
        assertEquals(at(LONDON, 2026, 10, 23, 18, 0), friday);
        assertEquals(at(LONDON, 2026, 10, 26, 18, 0), monday);
        assertEquals(3 * 24 * 3600_000L + 3600_000L, monday - friday);
        assertTrue(beforeChange < afterChange);
    }

    @Test
    public void latestEndedAndNextEnd() {
        long during = at(LONDON, 2026, 10, 29, 19, 30);
        assertEquals(at(LONDON, 2026, 10, 28, 18, 0), ShowTimes.latestEnded(EVENING, during).getStart());
        assertEquals(at(LONDON, 2026, 10, 29, 18, 0), ShowTimes.nextEnd(EVENING, during).getStart());
    }

    @Test
    public void showsCanCrossMidnight() {
        ShowTimes.Spec late = new ShowTimes.Spec("Europe/London", ShowTimes.ALL_DAYS, 23 * 60, 60);
        ShowTimes.Airing airing = ShowTimes.nextStart(late, at(LONDON, 2026, 10, 29, 12, 0));
        assertEquals(at(LONDON, 2026, 10, 29, 23, 0), airing.getStart());
        assertEquals(at(LONDON, 2026, 10, 30, 1, 0), airing.getEnd());
        assertEquals(2 * 3600_000L, late.durationMillis());
    }

    @Test
    public void availableAtTheSameLocalTimeInAuckland() {
        ShowTimes.Airing airing = new ShowTimes.Airing(at(LONDON, 2026, 10, 29, 18, 0),
                at(LONDON, 2026, 10, 29, 21, 0));
        assertEquals(at(AUCKLAND, 2026, 10, 30, 18, 0), ShowTimes.availableAt(EVENING, airing, AUCKLAND, true));
        assertEquals(airing.getEnd(), ShowTimes.availableAt(EVENING, airing, AUCKLAND, false));
        assertEquals(airing.getEnd(), ShowTimes.availableAt(EVENING, airing, LONDON, true));
    }

    @Test
    public void noDaysMeansNoAiring() {
        ShowTimes.Spec never = new ShowTimes.Spec("Europe/London", 0, 18 * 60, 21 * 60);
        assertNull(ShowTimes.nextStart(never, at(LONDON, 2026, 10, 29, 12, 0)));
    }
}
