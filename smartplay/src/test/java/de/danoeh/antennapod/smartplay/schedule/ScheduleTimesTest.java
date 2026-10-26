package de.danoeh.antennapod.smartplay.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.junit.Test;

public class ScheduleTimesTest {
    private static final TimeZone NZ = TimeZone.getTimeZone("Pacific/Auckland");

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(NZ);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void nextIsLaterTodayOrTomorrow() {
        UpdateSchedule fourAm = UpdateSchedule.defaults();
        assertEquals(at(2026, 10, 27, 4, 0), ScheduleTimes.nextStart(fourAm, at(2026, 10, 26, 17, 30), NZ));
        assertEquals(at(2026, 10, 26, 4, 0), ScheduleTimes.nextStart(fourAm, at(2026, 10, 26, 3, 59), NZ));
    }

    @Test
    public void previousIsTheLastStartBeforeNow() {
        UpdateSchedule fourAm = UpdateSchedule.defaults();
        assertEquals(at(2026, 10, 26, 4, 0), ScheduleTimes.previousStart(fourAm, at(2026, 10, 26, 17, 30), NZ));
        assertEquals(at(2026, 10, 25, 4, 0), ScheduleTimes.previousStart(fourAm, at(2026, 10, 26, 3, 0), NZ));
    }

    @Test
    public void chosenWeekdaysOnly() {
        int tuesdays = UpdateSchedule.dayBit(Calendar.TUESDAY);
        UpdateSchedule schedule = UpdateSchedule.defaults().withWeekdays(tuesdays);
        assertEquals(at(2026, 10, 27, 4, 0), ScheduleTimes.nextStart(schedule, at(2026, 10, 26, 17, 0), NZ));
        assertEquals(at(2026, 11, 3, 4, 0), ScheduleTimes.nextStart(schedule, at(2026, 10, 27, 5, 0), NZ));
    }

    @Test
    public void everyThreeDaysFromTheAnchor() {
        long anchor = ScheduleTimes.epochDay(at(2026, 10, 26, 12, 0), NZ);
        UpdateSchedule schedule = UpdateSchedule.defaults().withEveryNDays(3, anchor);
        assertEquals(at(2026, 10, 29, 4, 0), ScheduleTimes.nextStart(schedule, at(2026, 10, 26, 17, 0), NZ));
        assertEquals(at(2026, 10, 26, 4, 0), ScheduleTimes.previousStart(schedule, at(2026, 10, 28, 17, 0), NZ));
    }

    @Test
    public void dueWhenAStartWasMissed() {
        UpdateSchedule fourAm = UpdateSchedule.defaults();
        long lastRun = at(2026, 10, 25, 4, 5);
        assertTrue(ScheduleTimes.isDue(fourAm, lastRun, 0, at(2026, 10, 26, 9, 0), NZ));
        assertFalse(ScheduleTimes.isDue(fourAm, at(2026, 10, 26, 4, 0), 0, at(2026, 10, 26, 9, 0), NZ));
    }

    @Test
    public void notDueRightAfterSavingASchedule() {
        UpdateSchedule fourAm = UpdateSchedule.defaults();
        long saved = at(2026, 10, 26, 17, 30);
        assertFalse(ScheduleTimes.isDue(fourAm, 0, saved, at(2026, 10, 26, 17, 31), NZ));
        assertTrue(ScheduleTimes.isDue(fourAm, 0, saved, at(2026, 10, 27, 4, 1), NZ));
    }

    @Test
    public void phoneOffForDaysCatchesUpOnce() {
        UpdateSchedule fourAm = UpdateSchedule.defaults();
        long now = at(2026, 10, 30, 8, 0);
        assertTrue(ScheduleTimes.isDue(fourAm, at(2026, 10, 26, 4, 0), 0, now, NZ));
        long previous = ScheduleTimes.previousStart(fourAm, now, NZ);
        assertFalse(ScheduleTimes.isDue(fourAm, previous, 0, now, NZ));
    }

    @Test
    public void handlesDaylightSavingChange() {
        UpdateSchedule twoThirty = UpdateSchedule.defaults().withTime(4, 0);
        assertEquals(at(2026, 9, 27, 4, 0), ScheduleTimes.nextStart(twoThirty, at(2026, 9, 26, 12, 0), NZ));
    }

    @Test
    public void defaultRunTakesUncategorisedAndDefaultCategories() {
        Map<Long, Set<String>> feeds = new HashMap<>();
        feeds.put(1L, Collections.emptySet());
        feeds.put(2L, set("Politics"));
        feeds.put(3L, set("Comedy"));
        feeds.put(4L, set("Archive"));
        feeds.put(5L, set("Politics", "Comedy"));
        Map<String, CategoryScheduleMode> modes = new HashMap<>();
        modes.put("Politics", CategoryScheduleMode.OWN);
        modes.put("Archive", CategoryScheduleMode.MANUAL);
        List<Long> defaultRun = ScheduleRunPlanner.feedsForDefault(feeds, modes);
        Collections.sort(defaultRun);
        assertEquals(Arrays.asList(1L, 3L, 5L), defaultRun);
        List<Long> politics = ScheduleRunPlanner.feedsForCategory("Politics", feeds);
        Collections.sort(politics);
        assertEquals(Arrays.asList(2L, 5L), politics);
    }

    private static Set<String> set(String... names) {
        return new HashSet<>(Arrays.asList(names));
    }
}
