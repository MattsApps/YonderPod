package de.danoeh.antennapod.playback.service.internal;

public final class AudioFocusPolicy {
    public static final long NO_SEEK = -1;
    public static final float DUCK_VOLUME = 0.2f;

    private AudioFocusPolicy() {
    }

    public static boolean holdsFocus(boolean playWhenReady) {
        return playWhenReady;
    }

    public static boolean takesButtons(boolean playWhenReady, boolean musicActive) {
        return playWhenReady || !musicActive;
    }

    public static boolean resumesFromStopped(boolean musicActive) {
        return !musicActive;
    }

    public static boolean resumesOnReconnect(boolean allowed, boolean pausedByDisconnect, boolean playWhenReady,
                                             boolean hasMedia, boolean musicActive) {
        return allowed && pausedByDisconnect && !playWhenReady && hasMedia && !musicActive;
    }

    public static boolean pausedByDisconnect(boolean playWhenReady, boolean becomingNoisy) {
        return !playWhenReady && becomingNoisy;
    }

    public static boolean ducksForInterruption(boolean canDuck, boolean callOrRecording) {
        return canDuck || !callOrRecording;
    }

    public static float duckVolume(boolean ducked) {
        return ducked ? DUCK_VOLUME : 1.0f;
    }

    public static long seekAfterInterruption(boolean wasTransientLoss, boolean playWhenReady,
                                             long position, long backMillis) {
        if (!wasTransientLoss || !playWhenReady || backMillis <= 0 || position <= 0) {
            return NO_SEEK;
        }
        return Math.max(0, position - backMillis);
    }
}
