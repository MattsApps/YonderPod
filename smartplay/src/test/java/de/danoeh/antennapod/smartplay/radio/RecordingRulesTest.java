package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

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

    @Test
    public void keepsTheNewestAndProtectsFavouritesAndHalfPlayed() {
        assertEquals(Arrays.asList(2L, 1L), RecordingRules.toDelete(Arrays.asList(
                new RecordingRules.Recording(1, 100, false, false),
                new RecordingRules.Recording(2, 200, false, false),
                new RecordingRules.Recording(3, 300, false, false),
                new RecordingRules.Recording(4, 400, false, false)), 2));
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(Arrays.asList(
                new RecordingRules.Recording(1, 100, false, false),
                new RecordingRules.Recording(2, 200, true, false),
                new RecordingRules.Recording(3, 300, false, false),
                new RecordingRules.Recording(4, 400, false, false)), 2));
        assertEquals(Collections.singletonList(1L), RecordingRules.toDelete(Arrays.asList(
                new RecordingRules.Recording(1, 100, false, false),
                new RecordingRules.Recording(2, 200, false, true),
                new RecordingRules.Recording(3, 300, false, false),
                new RecordingRules.Recording(4, 400, false, false)), 2));
    }

    @Test
    public void norewindLinksHaveARewindableVersion() {
        assertEquals(Collections.singletonList("https://x/bbc_6music.isml/bbc_6music-audio=96000.m3u8"),
                RecordingRules.rewindableVersions("https://x/bbc_6music.isml/bbc_6music-audio=96000.norewind.m3u8"));
        assertTrue(RecordingRules.rewindableVersions("https://x/live.m3u8").isEmpty());
    }
}
