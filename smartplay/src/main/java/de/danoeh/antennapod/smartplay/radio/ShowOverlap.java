package de.danoeh.antennapod.smartplay.radio;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Fits a show being added or changed around the other shows already set up on the same station.
 * Up to five minutes' overlap at either end is fine (padding back-to-back shows). More than that and the
 * show being added or changed gives way: it starts where the other ends, or ends where the other starts.
 * Shows already set up are never changed.
 */
public final class ShowOverlap {
    public static final long ALLOWED_MILLIS = 5 * 60_000L;
    private static final long DAY = 24 * 3600_000L;

    public static final class Result {
        /** The times to use, in the show's own time zone. */
        public final int startMinute;
        public final int endMinute;
        /** Indexes (into others) of the shows it was moved to fit around, in order. Empty if unchanged. */
        public final List<Integer> fittedAround;
        /** Index of a show it can't be fitted around (it would have nothing left), or -1. */
        public final int blockedBy;

        Result(int startMinute, int endMinute, List<Integer> fittedAround, int blockedBy) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.fittedAround = fittedAround;
            this.blockedBy = blockedBy;
        }

        public boolean changed() {
            return !fittedAround.isEmpty();
        }
    }

    private static final class Clash {
        final int other;
        final ShowTimes.Airing mine;
        final ShowTimes.Airing theirs;

        Clash(int other, ShowTimes.Airing mine, ShowTimes.Airing theirs) {
            this.other = other;
            this.mine = mine;
            this.theirs = theirs;
        }
    }

    private ShowOverlap() {
    }

    public static Result fit(ShowTimes.Spec show, List<ShowTimes.Spec> others, long now) {
        int start = show.getStartMinute();
        int end = show.getEndMinute();
        List<Integer> fittedAround = new ArrayList<>();
        int rounds = 4 * (others.size() + 1);
        int lastOther = -1;
        for (int round = 0; round < rounds; round++) {
            ShowTimes.Spec spec = new ShowTimes.Spec(show.getZoneId(), show.getWeekdays(), start, end);
            Clash clash = firstClash(spec, others, now);
            if (clash == null) {
                return new Result(start, end, fittedAround, -1);
            }
            lastOther = clash.other;
            ShowTimes.Airing mine = clash.mine;
            ShowTimes.Airing theirs = clash.theirs;
            if (theirs.getStart() <= mine.getStart() && theirs.getEnd() >= mine.getEnd()) {
                return new Result(start, end, fittedAround, clash.other);
            }
            boolean moveStart;
            if (theirs.getStart() <= mine.getStart()) {
                moveStart = true;
            } else if (theirs.getEnd() >= mine.getEnd()) {
                moveStart = false;
            } else {
                // The other show sits inside this one: keep the longer part.
                moveStart = mine.getEnd() - theirs.getEnd() > theirs.getStart() - mine.getStart();
            }
            if (moveStart) {
                start = minuteOfDay(theirs.getEnd(), spec);
            } else {
                end = minuteOfDay(theirs.getStart(), spec);
            }
            if (start == end) {
                return new Result(show.getStartMinute(), show.getEndMinute(), fittedAround, clash.other);
            }
            if (!fittedAround.contains(clash.other)) {
                fittedAround.add(clash.other);
            }
        }
        return new Result(show.getStartMinute(), show.getEndMinute(), fittedAround, lastOther);
    }

    private static Clash firstClash(ShowTimes.Spec spec, List<ShowTimes.Spec> others, long now) {
        // A week and a bit covers every day each show airs.
        long from = now - DAY;
        long to = now + 8 * DAY;
        List<ShowTimes.Airing> mine = airings(spec, from, to);
        for (int i = 0; i < others.size(); i++) {
            for (ShowTimes.Airing theirs : airings(others.get(i), from, to)) {
                for (ShowTimes.Airing airing : mine) {
                    long overlap = Math.min(airing.getEnd(), theirs.getEnd())
                            - Math.max(airing.getStart(), theirs.getStart());
                    if (overlap > ALLOWED_MILLIS) {
                        return new Clash(i, airing, theirs);
                    }
                }
            }
        }
        return null;
    }

    /** Every airing of a show that ends after from and starts before to. */
    public static List<ShowTimes.Airing> airingsBetween(ShowTimes.Spec spec, long from, long to) {
        return airings(spec, from, to);
    }

    static List<ShowTimes.Airing> airings(ShowTimes.Spec spec, long from, long to) {
        List<ShowTimes.Airing> result = new ArrayList<>();
        if (spec.getWeekdays() == 0 || spec.getStartMinute() == spec.getEndMinute()) {
            return result;
        }
        ShowTimes.Airing airing = ShowTimes.nextEnd(spec, from);
        while (airing != null && airing.getStart() < to) {
            result.add(airing);
            airing = ShowTimes.nextEnd(spec, airing.getEnd());
        }
        return result;
    }

    private static int minuteOfDay(long time, ShowTimes.Spec spec) {
        Calendar calendar = Calendar.getInstance(spec.timeZone());
        calendar.setTimeInMillis(time);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }
}
