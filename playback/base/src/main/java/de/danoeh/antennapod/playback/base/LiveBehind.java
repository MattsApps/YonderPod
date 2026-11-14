package de.danoeh.antennapod.playback.base;

/**
 * Live pause (0.9.26), as the player service last set it: whether a live station is paused or playing
 * behind live, and how many whole minutes behind. Read by the radio screen and the widget; written only by
 * the player service. Nothing here is saved: after Android kills Yonder, there's no buffer.
 */
public final class LiveBehind {
    /** The most a listener can be behind live. */
    public static final long LIMIT_MILLIS = 60 * 60_000L;

    private static volatile boolean active;
    private static volatile boolean paused;
    private static volatile int minutes;

    private LiveBehind() {
    }

    /** True while a live station is paused with a buffer running, or playing from that buffer. */
    public static boolean isActive() {
        return active;
    }

    /** True while paused; false while playing behind live. Only meaningful while active. */
    public static boolean isPaused() {
        return paused;
    }

    /** Whole minutes behind live. Only meaningful while active. */
    public static int minutes() {
        return minutes;
    }

    /** The player service only. */
    public static void set(boolean isActive, boolean isPaused, int minutesBehind) {
        paused = isPaused;
        minutes = Math.max(0, minutesBehind);
        active = isActive;
    }
}
