package de.danoeh.antennapod.smartplay.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public class ScheduleJobsTest {
    private static final TimeZone NZ = TimeZone.getTimeZone("Pacific/Auckland");
    private static final long MINUTE = 60 * 1000;

    private static long at(int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(NZ);
        calendar.clear();
        calendar.set(2026, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void jobWaitsUntilTheNextRunTime() {
        long now = at(10, 28, 23, 0);
        ScheduleJobs.Job job = ScheduleJobs.nextJob(UpdateSchedule.defaults(), at(10, 28, 4, 0), 0, now, NZ);
        assertEquals(at(10, 29, 4, 0) - now, job.getDelayMillis());
    }

    @Test
    public void jobCarriesTheScheduleConditions() {
        long now = at(10, 28, 23, 0);
        ScheduleJobs.Job wifiCharging = ScheduleJobs.nextJob(UpdateSchedule.defaults(), 0, now, now, NZ);
        assertTrue(wifiCharging.requiresCharging());
        assertTrue(wifiCharging.isUnmeteredOnly());
        UpdateSchedule mobileUnplugged = UpdateSchedule.defaults().withWifiOnly(false).withChargingOnly(false);
        ScheduleJobs.Job loose = ScheduleJobs.nextJob(mobileUnplugged, 0, now, now, NZ);
        assertFalse(loose.requiresCharging());
        assertFalse(loose.isUnmeteredOnly());
    }

    @Test
    public void missedRunIsPendingStraightAway() {
        long now = at(10, 28, 9, 0);
        ScheduleJobs.Job job = ScheduleJobs.nextJob(UpdateSchedule.defaults(), at(10, 27, 4, 0), 0, now, NZ);
        assertEquals(0, job.getDelayMillis());
        assertTrue(ScheduleJobs.shouldRunNow(UpdateSchedule.defaults(), at(10, 27, 4, 0), 0, now, NZ));
    }

    @Test
    public void afterRunningTheNextJobIsTomorrow() {
        long now = at(10, 28, 9, 0);
        long marked = ScheduleJobs.markRun(UpdateSchedule.defaults(), now, NZ);
        assertEquals(at(10, 28, 4, 0), marked);
        ScheduleJobs.Job next = ScheduleJobs.nextJob(UpdateSchedule.defaults(), marked, 0, now, NZ);
        assertEquals(at(10, 29, 4, 0) - now, next.getDelayMillis());
        assertFalse(ScheduleJobs.shouldRunNow(UpdateSchedule.defaults(), marked, 0, now, NZ));
    }

    @Test
    public void scheduleSetForATimeAlreadyPassedTodayWaitsForTomorrow() {
        long now = at(10, 28, 9, 0);
        ScheduleJobs.Job job = ScheduleJobs.nextJob(UpdateSchedule.defaults(), 0, now, now, NZ);
        assertEquals(at(10, 29, 4, 0) - now, job.getDelayMillis());
    }

    @Test
    public void scheduleAFewMinutesAheadRunsThen() {
        long now = at(10, 28, 23, 0);
        UpdateSchedule soon = UpdateSchedule.defaults().withTime(23, 5);
        assertEquals(5 * MINUTE, ScheduleJobs.nextJob(soon, 0, now, now, NZ).getDelayMillis());
    }

    @Test
    public void earlyWakeUpDoesNotRun() {
        long now = at(10, 28, 23, 0);
        UpdateSchedule soon = UpdateSchedule.defaults().withTime(23, 5);
        assertFalse(ScheduleJobs.shouldRunNow(soon, 0, at(10, 28, 22, 0), now, NZ));
    }

    @Test
    public void skippedDaysAreNotScheduled() {
        long saturdayNight = at(10, 31, 23, 0);
        int weekdays = 0;
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            weekdays |= UpdateSchedule.dayBit(day);
        }
        UpdateSchedule weekdaysOnly = UpdateSchedule.defaults().withWeekdays(weekdays);
        ScheduleJobs.Job job = ScheduleJobs.nextJob(weekdaysOnly, at(10, 30, 4, 0), 0, saturdayNight, NZ);
        assertEquals(at(11, 2, 4, 0) - saturdayNight, job.getDelayMillis());
    }

    @Test
    public void scheduleWithNoDaysHasNoJob() {
        long now = at(10, 28, 23, 0);
        assertNull(ScheduleJobs.nextJob(UpdateSchedule.defaults().withWeekdays(0), 0, 0, now, NZ));
    }

    @Test
    public void onlyDefaultAndOwnSchedulesGetJobs() {
        assertTrue(ScheduleJobs.isActive(true, CategoryScheduleMode.OWN));
        assertTrue(ScheduleJobs.isActive(false, CategoryScheduleMode.OWN));
        assertFalse(ScheduleJobs.isActive(false, CategoryScheduleMode.DEFAULT));
        assertFalse(ScheduleJobs.isActive(false, CategoryScheduleMode.MANUAL));
    }
}
