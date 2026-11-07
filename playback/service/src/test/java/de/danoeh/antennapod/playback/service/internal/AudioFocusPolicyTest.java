package de.danoeh.antennapod.playback.service.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioFocusPolicyTest {

    @Test
    public void pausedYonderReleasesFocus() {
        assertFalse(AudioFocusPolicy.holdsFocus(false));
        assertTrue(AudioFocusPolicy.holdsFocus(true));
    }

    @Test
    public void pausedYonderLeavesButtonsToThePlayingApp() {
        assertFalse(AudioFocusPolicy.takesButtons(false, true));
    }

    @Test
    public void playingYonderTakesButtons() {
        assertTrue(AudioFocusPolicy.takesButtons(true, true));
        assertTrue(AudioFocusPolicy.takesButtons(true, false));
    }

    @Test
    public void pausedYonderTakesButtonsWhenNothingElsePlays() {
        assertTrue(AudioFocusPolicy.takesButtons(false, false));
    }

    @Test
    public void stoppedYonderDoesNotResumeOverAnotherApp() {
        assertFalse(AudioFocusPolicy.resumesFromStopped(true));
        assertTrue(AudioFocusPolicy.resumesFromStopped(false));
    }

    @Test
    public void reconnectResumesOnlyAfterDisconnectPause() {
        assertTrue(AudioFocusPolicy.resumesOnReconnect(true, true, false, true, false));
        assertFalse(AudioFocusPolicy.resumesOnReconnect(true, false, false, true, false));
        assertFalse(AudioFocusPolicy.resumesOnReconnect(false, true, false, true, false));
        assertFalse(AudioFocusPolicy.resumesOnReconnect(true, true, true, true, false));
        assertFalse(AudioFocusPolicy.resumesOnReconnect(true, true, false, false, false));
    }

    @Test
    public void reconnectDoesNotResumeOverAnotherApp() {
        assertFalse(AudioFocusPolicy.resumesOnReconnect(true, true, false, true, true));
    }

    @Test
    public void permanentFocusLossIsNotADisconnectPause() {
        assertFalse(AudioFocusPolicy.pausedByDisconnect(false, false));
        assertTrue(AudioFocusPolicy.pausedByDisconnect(false, true));
        assertFalse(AudioFocusPolicy.pausedByDisconnect(true, false));
    }

    @Test
    public void transientLossResumesTwoSecondsBack() {
        assertEquals(58_000, AudioFocusPolicy.seekAfterInterruption(true, true, 60_000, 2_000));
        assertEquals(0, AudioFocusPolicy.seekAfterInterruption(true, true, 1_000, 2_000));
    }

    @Test
    public void permanentLossAfterTransientDoesNotSeek() {
        assertEquals(AudioFocusPolicy.NO_SEEK, AudioFocusPolicy.seekAfterInterruption(true, false, 60_000, 2_000));
    }

    @Test
    public void noSeekWithoutTransientLoss() {
        assertEquals(AudioFocusPolicy.NO_SEEK, AudioFocusPolicy.seekAfterInterruption(false, true, 60_000, 2_000));
        assertEquals(AudioFocusPolicy.NO_SEEK, AudioFocusPolicy.seekAfterInterruption(true, true, 0, 2_000));
        assertEquals(AudioFocusPolicy.NO_SEEK, AudioFocusPolicy.seekAfterInterruption(true, true, 60_000, 0));
    }

    @Test
    public void notificationSoundsDuckWhenChosen() {
        assertTrue(AudioFocusPolicy.ducksForInterruption(true, false));
        assertTrue(AudioFocusPolicy.ducksForInterruption(false, false));
    }

    @Test
    public void callsAndRecordingStillPause() {
        assertFalse(AudioFocusPolicy.ducksForInterruption(false, true));
    }

    @Test
    public void duckedVolumeIsLowerAndComesBack() {
        assertEquals(AudioFocusPolicy.DUCK_VOLUME, AudioFocusPolicy.duckVolume(true), 0.0001f);
        assertEquals(1.0f, AudioFocusPolicy.duckVolume(false), 0.0001f);
        assertTrue(AudioFocusPolicy.duckVolume(true) < 1.0f);
    }
}
