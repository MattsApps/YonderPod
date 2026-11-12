package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;

public class ShowOverlapTest {
    private static final String ZONE = "Pacific/Auckland";
    private static final int WEEKDAYS = 0b0111110; // Monday to Friday
    private static final long NOW;

    static {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(ZONE));
        calendar.set(2026, Calendar.NOVEMBER, 13, 12, 0, 0);
        NOW = calendar.getTimeInMillis();
    }

    private static ShowTimes.Spec show(int startHour, int startMinute, int endHour, int endMinute) {
        return new ShowTimes.Spec(ZONE, WEEKDAYS, startHour * 60 + startMinute, endHour * 60 + endMinute);
    }

    private static ShowOverlap.Result fit(ShowTimes.Spec spec, ShowTimes.Spec... others) {
        return ShowOverlap.fit(spec, Arrays.asList(others), NOW);
    }

    @Test
    public void threeMinutesIsFine() {
        ShowOverlap.Result result = fit(show(10, 2, 11, 0), show(9, 0, 10, 5));
        assertFalse(result.changed());
        assertEquals(10 * 60 + 2, result.startMinute);
        assertEquals(-1, result.blockedBy);
    }

    @Test
    public void fiveMinutesAtBothEndsIsFine() {
        assertFalse(fit(show(9, 55, 11, 5), show(9, 0, 10, 0), show(11, 0, 12, 0)).changed());
    }

    @Test
    public void fifteenMinutesStartsWhereTheOtherEnds() {
        ShowOverlap.Result result = fit(show(9, 50, 11, 0), show(9, 0, 10, 5));
        assertTrue(result.changed());
        assertEquals(Collections.singletonList(0), result.fittedAround);
        assertEquals(10 * 60 + 5, result.startMinute);
        assertEquals(11 * 60, result.endMinute);
    }

    @Test
    public void lengtheningIntoTheNextShowEndsWhereItStarts() {
        ShowOverlap.Result result = fit(show(9, 0, 10, 20), show(10, 0, 11, 0));
        assertEquals(9 * 60, result.startMinute);
        assertEquals(10 * 60, result.endMinute);
    }

    @Test
    public void squeezedBetweenTwo() {
        ShowOverlap.Result result = fit(show(8, 30, 11, 30), show(8, 0, 9, 0), show(11, 0, 12, 0));
        assertEquals(9 * 60, result.startMinute);
        assertEquals(11 * 60, result.endMinute);
        assertEquals(2, result.fittedAround.size());
    }

    @Test
    public void aShowInsideKeepsTheLongerPart() {
        ShowOverlap.Result result = fit(show(9, 0, 12, 0), show(9, 30, 10, 0));
        assertEquals(10 * 60, result.startMinute);
        assertEquals(12 * 60, result.endMinute);
    }

    @Test
    public void entirelyCoveredCantBeFitted() {
        ShowOverlap.Result result = fit(show(9, 15, 9, 45), show(9, 0, 10, 0));
        assertEquals(0, result.blockedBy);
    }

    @Test
    public void onlyClashingDaysCountButTheTimesMoveForAllDays() {
        ShowTimes.Spec mondays = new ShowTimes.Spec(ZONE, 0b0000010, 9 * 60, 10 * 60 + 30);
        ShowOverlap.Result result = fit(show(10, 0, 11, 0), mondays);
        assertEquals(10 * 60 + 30, result.startMinute);
        ShowTimes.Spec sundays = new ShowTimes.Spec(ZONE, 0b0000001, 9 * 60, 10 * 60 + 30);
        assertFalse(fit(show(10, 0, 11, 0), sundays).changed());
    }

    @Test
    public void overnightShows() {
        ShowOverlap.Result result = fit(show(23, 0, 1, 0), show(0, 30, 2, 0));
        assertEquals(23 * 60, result.startMinute);
        assertEquals(30, result.endMinute);
    }

    @Test
    public void differentTimeZonesCompareRealTimes() {
        // Sydney is two hours behind Auckland in November.
        ShowTimes.Spec sydney = new ShowTimes.Spec("Australia/Sydney", WEEKDAYS, 7 * 60, 8 * 60);
        ShowOverlap.Result result = fit(show(9, 30, 11, 0), sydney);
        assertEquals(10 * 60, result.startMinute);
    }

    @Test
    public void noOtherShows() {
        List<ShowTimes.Spec> none = Collections.emptyList();
        assertFalse(ShowOverlap.fit(show(9, 0, 10, 0), none, NOW).changed());
    }
}
