package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewindWindowTest {

    private static String hls(int segments, double seconds) {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:7\n"
                + "#EXT-X-MEDIA-SEQUENCE:123456\n");
        for (int i = 0; i < segments; i++) {
            text.append("#EXT-X-PROGRAM-DATE-TIME:2026-10-29T07:00:00Z\n#EXTINF:").append(seconds)
                    .append(",\nsegment-").append(i).append(".ts\n");
        }
        return text.toString();
    }

    @Test
    public void sixHourHlsWindowIsRead() {
        RewindWindow window = RewindWindow.fromHlsMediaPlaylist(hls(3600, 6.0));
        assertEquals(RewindWindow.Kind.REWIND, window.getKind());
        assertEquals(6 * 3600, window.getSeconds());
    }

    @Test
    public void shortHlsPlaylistIsLiveOnly() {
        RewindWindow window = RewindWindow.fromHlsMediaPlaylist(hls(5, 6.4));
        assertEquals(RewindWindow.Kind.LIVE_ONLY, window.getKind());
        assertEquals(32, window.getSeconds());
    }

    @Test
    public void finishedHlsPlaylistIsNotLive() {
        assertEquals(RewindWindow.Kind.NOT_LIVE,
                RewindWindow.fromHlsMediaPlaylist(hls(3, 6) + "#EXT-X-ENDLIST\n").getKind());
    }

    @Test
    public void masterPlaylistGivesItsFirstVariant() {
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=101760,CODECS=\"mp4a.40.5\"\n"
                + "bbc_6music-audio=96000.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=339200\n"
                + "bbc_6music-audio=320000.m3u8\n";
        assertTrue(RewindWindow.isHlsMaster(master));
        assertEquals("bbc_6music-audio=96000.m3u8", RewindWindow.firstVariant(master));
        assertFalse(RewindWindow.isHlsMaster(hls(2, 6)));
        assertNull(RewindWindow.firstVariant(hls(2, 6)));
    }

    @Test
    public void dashTimeShiftDepthIsRead() {
        String mpd = "<?xml version=\"1.0\"?>\n<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"dynamic\"\n"
                + "  availabilityStartTime=\"1970-01-01T00:00:00Z\" timeShiftBufferDepth=\"PT6H0M0.00S\"\n"
                + "  minimumUpdatePeriod=\"PT1H\">\n</MPD>";
        RewindWindow window = RewindWindow.fromDashManifest(mpd);
        assertEquals(RewindWindow.Kind.REWIND, window.getKind());
        assertEquals(6 * 3600, window.getSeconds());
    }

    @Test
    public void dashWithoutDepthIsUnknownAndStaticIsNotLive() {
        assertEquals(RewindWindow.Kind.UNKNOWN,
                RewindWindow.fromDashManifest("<MPD type=\"dynamic\" profiles=\"x\"></MPD>").getKind());
        assertEquals(RewindWindow.Kind.NOT_LIVE,
                RewindWindow.fromDashManifest("<MPD type=\"static\"></MPD>").getKind());
        assertEquals(RewindWindow.Kind.LIVE_ONLY,
                RewindWindow.fromDashManifest("<MPD type=\"dynamic\" timeShiftBufferDepth=\"PT30S\">").getKind());
    }

    @Test
    public void isoDurationsAreParsed() {
        assertEquals(21600, RewindWindow.parseIsoDuration("PT6H"));
        assertEquals(90061, RewindWindow.parseIsoDuration("P1DT1H1M1S"));
        assertEquals(30, RewindWindow.parseIsoDuration("PT29.6S"));
        assertEquals(-1, RewindWindow.parseIsoDuration("six hours"));
        assertEquals(-1, RewindWindow.parseIsoDuration("PT"));
    }

    @Test
    public void notAPlaylistIsUnknown() {
        assertEquals(RewindWindow.Kind.UNKNOWN, RewindWindow.fromHlsMediaPlaylist("ID3...binary").getKind());
    }
}
