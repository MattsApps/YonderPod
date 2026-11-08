package de.danoeh.antennapod.smartplay.radio;

import java.util.ArrayList;
import java.util.List;

public final class RecordingRules {
    public static final long BURST_MARGIN_MILLIS = 30 * 60_000L;
    public static final long LIVE_PADDING_MILLIS = 60_000L;
    public static final long BURST_DELAY_MILLIS = 2 * 60_000L;
    public static final int DEFAULT_KEEP = 5;
    public static final long KEEP_AFTER_RECORDING_MILLIS = 25 * 3600_000L;
    public static final long RELEASE_SLACK_MILLIS = 30_000L;

    public enum Method {
        QUICK,
        LIVE
    }

    public static final class Recording {
        final long id;
        final long airingStart;
        final long recordingEnded;
        final long released;
        final boolean played;
        final boolean playing;

        public Recording(long id, long airingStart, long recordingEnded, long released, boolean played,
                         boolean playing) {
            this.id = id;
            this.airingStart = airingStart;
            this.recordingEnded = recordingEnded;
            this.released = released;
            this.played = played;
            this.playing = playing;
        }
    }

    private RecordingRules() {
    }

    public static Method chooseMethod(RewindWindow window, long showMillis) {
        if (window != null && window.getKind() == RewindWindow.Kind.REWIND
                && window.getSeconds() * 1000L >= showMillis + BURST_MARGIN_MILLIS) {
            return Method.QUICK;
        }
        return Method.LIVE;
    }

    public static boolean stillInWindow(ShowTimes.Airing airing, long rewindSeconds, long now) {
        return airing.getStart() - LIVE_PADDING_MILLIS >= now - rewindSeconds * 1000L;
    }

    public static long deletesAt(long recordingEnded, long released) {
        return recordingEnded + KEEP_AFTER_RECORDING_MILLIS;
    }

    public static boolean waiting(long availableAt, long now) {
        return availableAt > now + RELEASE_SLACK_MILLIS;
    }

    public static List<Long> toDelete(List<Recording> recordings, long now) {
        long newest = Long.MIN_VALUE;
        for (Recording recording : recordings) {
            newest = Math.max(newest, recording.airingStart);
        }
        List<Long> result = new ArrayList<>();
        for (Recording recording : recordings) {
            boolean replaced = recording.airingStart < newest;
            boolean expired = now >= deletesAt(recording.recordingEnded, recording.released);
            if ((recording.played || replaced || expired) && !recording.playing) {
                result.add(recording.id);
            }
        }
        return result;
    }

    public static long nextDeadline(List<Recording> recordings, long now) {
        List<Long> going = toDelete(recordings, now);
        long next = Long.MAX_VALUE;
        for (Recording recording : recordings) {
            if (!going.contains(recording.id)) {
                next = Math.min(next, deletesAt(recording.recordingEnded, recording.released));
            }
        }
        return next;
    }

    public static List<String> rewindableVersions(String url) {
        List<String> result = new ArrayList<>();
        if (url.contains(".norewind")) {
            result.add(url.replace(".norewind", ""));
        }
        return result;
    }
}
