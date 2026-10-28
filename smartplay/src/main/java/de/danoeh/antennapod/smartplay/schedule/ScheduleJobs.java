package de.danoeh.antennapod.smartplay.schedule;

import java.util.TimeZone;

public final class ScheduleJobs {

    public static final class Job {
        private final long delayMillis;
        private final boolean requiresCharging;
        private final boolean unmeteredOnly;

        Job(long delayMillis, boolean requiresCharging, boolean unmeteredOnly) {
            this.delayMillis = delayMillis;
            this.requiresCharging = requiresCharging;
            this.unmeteredOnly = unmeteredOnly;
        }

        public long getDelayMillis() {
            return delayMillis;
        }

        public boolean requiresCharging() {
            return requiresCharging;
        }

        public boolean isUnmeteredOnly() {
            return unmeteredOnly;
        }
    }

    private ScheduleJobs() {
    }

    public static boolean isActive(boolean isDefault, CategoryScheduleMode mode) {
        return isDefault || mode == CategoryScheduleMode.OWN;
    }

    public static Job nextJob(UpdateSchedule schedule, long lastTriggered, long since, long now,
                              TimeZone timeZone) {
        long delay;
        if (ScheduleTimes.isDue(schedule, lastTriggered, since, now, timeZone)) {
            delay = 0;
        } else {
            long next = ScheduleTimes.nextStart(schedule, now, timeZone);
            if (next == ScheduleTimes.NONE) {
                return null;
            }
            delay = next - now;
        }
        return new Job(delay, schedule.isChargingOnly(), schedule.isWifiOnly());
    }

    public static boolean shouldRunNow(UpdateSchedule schedule, long lastTriggered, long since, long now,
                                       TimeZone timeZone) {
        return ScheduleTimes.isDue(schedule, lastTriggered, since, now, timeZone);
    }

    public static long markRun(UpdateSchedule schedule, long now, TimeZone timeZone) {
        return ScheduleTimes.previousStart(schedule, now, timeZone);
    }
}
