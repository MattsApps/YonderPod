package de.danoeh.antennapod.smartplay.schedule;

public final class UpdateSchedule {
    public static final int ALL_DAYS = 0x7F;
    public static final int WINDOW_MINUTES = 60;

    private final int minuteOfDay;
    private final int weekdays;
    private final int everyNDays;
    private final long anchorDay;
    private final boolean wifiOnly;
    private final boolean chargingOnly;

    public UpdateSchedule(int minuteOfDay, int weekdays, int everyNDays, long anchorDay,
                          boolean wifiOnly, boolean chargingOnly) {
        this.minuteOfDay = Math.max(0, Math.min(24 * 60 - 1, minuteOfDay));
        this.weekdays = weekdays & ALL_DAYS;
        this.everyNDays = everyNDays >= 2 ? everyNDays : 0;
        this.anchorDay = anchorDay;
        this.wifiOnly = wifiOnly;
        this.chargingOnly = chargingOnly;
    }

    public static UpdateSchedule defaults() {
        return new UpdateSchedule(4 * 60, ALL_DAYS, 0, 0, true, true);
    }

    public static int dayBit(int calendarDayOfWeek) {
        return 1 << (calendarDayOfWeek - 1);
    }

    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public int getHour() {
        return minuteOfDay / 60;
    }

    public int getMinute() {
        return minuteOfDay % 60;
    }

    public int getWeekdays() {
        return weekdays;
    }

    public boolean isEveryDay() {
        return everyNDays == 0 && weekdays == ALL_DAYS;
    }

    public boolean runsOn(int calendarDayOfWeek) {
        return (weekdays & dayBit(calendarDayOfWeek)) != 0;
    }

    public int getEveryNDays() {
        return everyNDays;
    }

    public long getAnchorDay() {
        return anchorDay;
    }

    public boolean isWifiOnly() {
        return wifiOnly;
    }

    public boolean isChargingOnly() {
        return chargingOnly;
    }

    public UpdateSchedule withTime(int hour, int minute) {
        return new UpdateSchedule(hour * 60 + minute, weekdays, everyNDays, anchorDay, wifiOnly, chargingOnly);
    }

    public UpdateSchedule withWeekdays(int newWeekdays) {
        return new UpdateSchedule(minuteOfDay, newWeekdays, everyNDays, anchorDay, wifiOnly, chargingOnly);
    }

    public UpdateSchedule withEveryNDays(int days, long newAnchorDay) {
        return new UpdateSchedule(minuteOfDay, weekdays, days, newAnchorDay, wifiOnly, chargingOnly);
    }

    public UpdateSchedule withWifiOnly(boolean newWifiOnly) {
        return new UpdateSchedule(minuteOfDay, weekdays, everyNDays, anchorDay, newWifiOnly, chargingOnly);
    }

    public UpdateSchedule withChargingOnly(boolean newChargingOnly) {
        return new UpdateSchedule(minuteOfDay, weekdays, everyNDays, anchorDay, wifiOnly, newChargingOnly);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UpdateSchedule)) {
            return false;
        }
        UpdateSchedule other = (UpdateSchedule) o;
        return minuteOfDay == other.minuteOfDay && weekdays == other.weekdays && everyNDays == other.everyNDays
                && anchorDay == other.anchorDay && wifiOnly == other.wifiOnly && chargingOnly == other.chargingOnly;
    }

    @Override
    public int hashCode() {
        return minuteOfDay * 31 + weekdays;
    }
}
