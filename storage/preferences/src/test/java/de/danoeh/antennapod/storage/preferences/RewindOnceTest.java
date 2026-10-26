package de.danoeh.antennapod.storage.preferences;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RewindOnceTest {
    private static final long MINUTE = 60 * 1000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long NOW = 100 * DAY;
    private static final long EPISODE = 7;
    private static final int POSITION = 600_000;

    private static final RewindOnce.RewindAmount DEFAULTS = paused -> {
        if (paused >= DAY) {
            return 30_000;
        } else if (paused >= HOUR) {
            return 15_000;
        } else if (paused >= MINUTE) {
            return 5_000;
        }
        return 0;
    };

    private static int playButton(RewindOnce rewind, int position, long lastPlayed) {
        return rewind.resumePosition(EPISODE, position, lastPlayed, NOW, DEFAULTS);
    }

    private static int loadEpisode(RewindOnce rewind, int position, long lastPlayed) {
        return rewind.resumePosition(EPISODE, position, lastPlayed, NOW, DEFAULTS);
    }

    @Test
    public void playButtonRewindsOnce() {
        RewindOnce rewind = new RewindOnce();
        assertEquals(POSITION - 30_000, playButton(rewind, POSITION, NOW - 2 * DAY));
    }

    @Test
    public void headsetAndNotificationRewindOnce() {
        RewindOnce rewind = new RewindOnce();
        int resumed = playButton(rewind, POSITION, NOW - 2 * HOUR);
        assertEquals(POSITION - 15_000, resumed);
        assertEquals(resumed, playButton(rewind, resumed, NOW - 2 * HOUR));
    }

    @Test
    public void tappingEpisodeInListLoadsThenPlaysButRewindsOnce() {
        RewindOnce rewind = new RewindOnce();
        int loaded = loadEpisode(rewind, POSITION, NOW - 2 * DAY);
        int played = playButton(rewind, loaded, NOW - 2 * DAY);
        assertEquals(POSITION - 30_000, played);
    }

    @Test
    public void smartPlayStartLoadsThenPlaysButRewindsOnce() {
        RewindOnce rewind = new RewindOnce();
        int loaded = loadEpisode(rewind, POSITION, NOW - 3 * HOUR);
        assertEquals(POSITION - 15_000, playButton(rewind, loaded, NOW - 3 * HOUR));
    }

    @Test
    public void carModeRewindsOnce() {
        RewindOnce rewind = new RewindOnce();
        int resumed = playButton(rewind, POSITION, NOW - 10 * MINUTE);
        assertEquals(POSITION - 5_000, resumed);
        assertEquals(resumed, playButton(rewind, resumed, NOW - 10 * MINUTE));
    }

    @Test
    public void aNewPauseRewindsAgain() {
        RewindOnce rewind = new RewindOnce();
        assertEquals(POSITION - 15_000, playButton(rewind, POSITION, NOW - 2 * HOUR));
        assertEquals(POSITION - 30_000, playButton(rewind, POSITION, NOW - 2 * DAY));
    }

    @Test
    public void anotherEpisodeIsNotBlocked() {
        RewindOnce rewind = new RewindOnce();
        playButton(rewind, POSITION, NOW - 2 * DAY);
        assertEquals(POSITION - 30_000, rewind.resumePosition(8, POSITION, NOW - 2 * DAY, NOW, DEFAULTS));
    }

    @Test
    public void shortPausesAndTheStartAreNotRewound() {
        RewindOnce rewind = new RewindOnce();
        assertEquals(POSITION, playButton(rewind, POSITION, NOW - 20_000));
        assertEquals(0, playButton(rewind, 0, NOW - 2 * DAY));
        assertEquals(2_000, rewind.resumePosition(9, 12_000, NOW - 2 * DAY, NOW, paused -> 10_000));
        assertEquals(0, rewind.resumePosition(10, 3_000, NOW - 2 * DAY, NOW, paused -> 10_000));
    }
}
