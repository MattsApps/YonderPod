package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class RecordingRulesTest {
    private static final long HOUR = 3600_000L;

    private static String playlist(int segments, int seconds) {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-TARGETDURATION:" + seconds + "\n");
        for (int i = 0; i < segments; i++) {
            text.append("#EXTINF:").append(seconds).append(",\ns").append(i).append(".ts\n");
        }
        return text.toString();
    }

    @Test
    public void quickBurstNeedsTheShowPlusHalfAnHour() {
        long threeHours = 3 * HOUR;
        assertEquals(RecordingRules.Method.QUICK, RecordingRules.chooseMethod(
                RewindWindow.fromHlsMediaPlaylist(playlist(3600, 6)), threeHours));
        assertEquals(RecordingRules.Method.LIVE, RecordingRules.chooseMethod(
                RewindWindow.fromHlsMediaPlaylist(playlist(1900, 6)), threeHours));
        assertEquals(RecordingRules.Method.LIVE, RecordingRules.chooseMethod(RewindWindow.liveOnly(), HOUR));
        assertEquals(RecordingRules.Method.LIVE, RecordingRules.chooseMethod(null, HOUR));
    }

    @Test
    public void airingStillInWindow() {
        long now = 10 * HOUR;
        assertTrue(RecordingRules.stillInWindow(new ShowTimes.Airing(5 * HOUR, 8 * HOUR), 6 * 3600, now));
        assertFalse(RecordingRules.stillInWindow(new ShowTimes.Airing(3 * HOUR, 6 * HOUR), 6 * 3600, now));
    }

    private static RecordingRules.Recording recording(long id, long start, boolean played, boolean playing) {
        return new RecordingRules.Recording(id, start, start + HOUR, start + HOUR, played, playing);
    }

    @Test
    public void deletedOncePlayedWhetherReleasedEarlyOrOnTime() {
        long start = 10 * HOUR;
        RecordingRules.Recording early = new RecordingRules.Recording(1, start, start + HOUR, start + HOUR, true,
                false);
        RecordingRules.Recording onTime = new RecordingRules.Recording(1, start, start + HOUR, start + 13 * HOUR,
                true, false);
        assertEquals(Collections.singletonList(1L),
                RecordingRules.toDelete(Collections.singletonList(early), start + 2 * HOUR));
        assertEquals(Collections.singletonList(1L),
                RecordingRules.toDelete(Collections.singletonList(onTime), start + 14 * HOUR));
    }

    @Test
    public void halfPlayedIsKeptUntilARuleRemovesIt() {
        long start = 10 * HOUR;
        RecordingRules.Recording halfPlayed = recording(1, start, false, false);
        assertTrue(RecordingRules.toDelete(Collections.singletonList(halfPlayed), start + 2 * HOUR).isEmpty());
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(Arrays.asList(halfPlayed,
                recording(2, start + 24 * HOUR, false, false)), start + 25 * HOUR + HOUR / 2));
    }

    @Test
    public void theNextAiringReplacesTheOldOne() {
        assertEquals(Arrays.asList(1L, 2L), RecordingRules.toDelete(Arrays.asList(
                recording(1, 0, false, false),
                recording(2, 24 * HOUR, false, false),
                recording(3, 48 * HOUR, false, false)), 49 * HOUR));
    }

    @Test
    public void goesTwentyFiveHoursAfterTheRecordingEnded() {
        long start = 10 * HOUR;
        long ended = start + HOUR;
        List<RecordingRules.Recording> one = Collections.singletonList(recording(1, start, false, false));
        assertEquals(ended + 25 * HOUR, RecordingRules.deletesAt(ended, ended + 5 * HOUR));
        assertTrue(RecordingRules.toDelete(one, ended + 25 * HOUR - 1).isEmpty());
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(one, ended + 25 * HOUR));
        assertEquals(ended + 25 * HOUR, RecordingRules.nextDeadline(one, ended));
        assertEquals(Long.MAX_VALUE, RecordingRules.nextDeadline(one, ended + 25 * HOUR));
    }

    @Test
    public void aPlayingRecordingFinishesFirst() {
        long start = 10 * HOUR;
        long late = start + 30 * HOUR;
        assertTrue(RecordingRules.toDelete(Collections.singletonList(recording(1, start, false, true)), late)
                .isEmpty());
        assertTrue(RecordingRules.toDelete(Collections.singletonList(recording(1, start, true, true)), start)
                .isEmpty());
        assertTrue(RecordingRules.toDelete(Arrays.asList(recording(1, start, false, true),
                recording(2, start + 24 * HOUR, false, false)), start + 25 * HOUR).isEmpty());
        assertEquals(Collections.singletonList(1L),
                RecordingRules.toDelete(Collections.singletonList(recording(1, start, false, false)), late));
    }

    @Test
    public void evenAPlayingRecordingGoesFortyEightHoursAfterItEnded() {
        long start = 10 * HOUR;
        long ended = start + HOUR;
        List<RecordingRules.Recording> playing = Collections.singletonList(recording(1, start, false, true));
        assertTrue(RecordingRules.toDelete(playing, ended + 48 * HOUR - 1).isEmpty());
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(playing, ended + 48 * HOUR));
        assertEquals(ended + 48 * HOUR, RecordingRules.nextDeadline(playing, ended + 30 * HOUR));
        assertEquals(ended + 25 * HOUR, RecordingRules.nextDeadline(playing, ended));
    }

    @Test
    public void favouritesNoLongerProtectRecordings() {
        long start = 10 * HOUR;
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(Arrays.asList(
                recording(1, start, false, false), recording(2, start + 24 * HOUR, false, false)),
                start + 25 * HOUR));
    }

    @Test
    public void heldRecordingWaitsUntilItsTimeOrListenNow() {
        long availableAt = 20 * HOUR;
        assertTrue(RecordingRules.waiting(availableAt, availableAt - HOUR));
        assertFalse(RecordingRules.waiting(availableAt, availableAt));
        assertFalse(RecordingRules.waiting(availableAt, availableAt - RecordingRules.RELEASE_SLACK_MILLIS));
    }

    @Test
    public void norewindLinksHaveARewindableVersion() {
        assertEquals(Collections.singletonList("https://x/bbc_6music.isml/bbc_6music-audio=96000.m3u8"),
                RecordingRules.rewindableVersions("https://x/bbc_6music.isml/bbc_6music-audio=96000.norewind.m3u8"));
        assertTrue(RecordingRules.rewindableVersions("https://x/live.m3u8").isEmpty());
    }
}
