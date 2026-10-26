package de.danoeh.antennapod.storage.preferences;

public final class RewindOnce {
    public interface RewindAmount {
        long millisAfter(long pausedMillis);
    }

    private long appliedMediaId = -1;
    private long appliedLastPlayed = -1;

    public synchronized int resumePosition(long mediaId, int position, long lastPlayedTime, long now,
                                           RewindAmount amount) {
        if (position <= 0 || lastPlayedTime <= 0) {
            return position;
        }
        if (mediaId == appliedMediaId && lastPlayedTime == appliedLastPlayed) {
            return position;
        }
        long rewind = amount.millisAfter(now - lastPlayedTime);
        if (rewind <= 0) {
            return position;
        }
        appliedMediaId = mediaId;
        appliedLastPlayed = lastPlayedTime;
        return (int) Math.max(0, position - rewind);
    }
}
