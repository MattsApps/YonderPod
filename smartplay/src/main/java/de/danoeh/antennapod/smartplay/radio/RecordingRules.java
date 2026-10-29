package de.danoeh.antennapod.smartplay.radio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RecordingRules {
    public static final long BURST_MARGIN_MILLIS = 30 * 60_000L;
    public static final long LIVE_PADDING_MILLIS = 60_000L;
    public static final long BURST_DELAY_MILLIS = 2 * 60_000L;
    public static final int DEFAULT_KEEP = 5;

    public enum Method {
        QUICK,
        LIVE
    }

    public static final class Recording {
        final long id;
        final long airingStart;
        final boolean favourite;
        final boolean halfPlayed;

        public Recording(long id, long airingStart, boolean favourite, boolean halfPlayed) {
            this.id = id;
            this.airingStart = airingStart;
            this.favourite = favourite;
            this.halfPlayed = halfPlayed;
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

    public static List<Long> toDelete(List<Recording> recordings, int keep) {
        List<Recording> counted = new ArrayList<>();
        for (Recording recording : recordings) {
            if (!recording.favourite) {
                counted.add(recording);
            }
        }
        counted.sort(Comparator.comparingLong((Recording r) -> r.airingStart).reversed());
        List<Long> result = new ArrayList<>();
        for (int i = Math.max(1, keep); i < counted.size(); i++) {
            if (!counted.get(i).halfPlayed) {
                result.add(counted.get(i).id);
            }
        }
        return result;
    }

    public static List<String> rewindableVersions(String url) {
        List<String> result = new ArrayList<>();
        if (url.contains(".norewind")) {
            result.add(url.replace(".norewind", ""));
        }
        return result;
    }
}
