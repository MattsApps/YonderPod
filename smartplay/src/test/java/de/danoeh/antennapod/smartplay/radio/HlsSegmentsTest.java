package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HlsSegmentsTest {

    private static final String TIMED = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n"
            + "#EXT-X-MEDIA-SEQUENCE:1000\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2026-10-29T18:00:00.000Z\n#EXTINF:6.4,\na-1000.ts\n"
            + "#EXTINF:6.4,\na-1001.ts\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2026-10-29T18:00:12.800+00:00\n#EXTINF:6.4,\na-1002.ts\n";

    @Test
    public void clockTimesFollowProgramDateTime() {
        HlsSegments.Playlist playlist = HlsSegments.parse(TIMED, 0);
        assertTrue(playlist.hasClockTimes());
        long start = HlsSegments.parseTime("2026-10-29T18:00:00Z");
        assertEquals(3, playlist.getSegments().size());
        assertEquals(start, playlist.getSegments().get(0).getStart());
        assertEquals(start + 6400, playlist.getSegments().get(1).getStart());
        assertEquals(start + 12800, playlist.getSegments().get(2).getStart());
        assertEquals(1002, playlist.getSegments().get(2).getSequence());
    }

    @Test
    public void segmentsAreCutByBroadcastTime() {
        HlsSegments.Playlist playlist = HlsSegments.parse(TIMED, 0);
        long start = HlsSegments.parseTime("2026-10-29T18:00:00Z");
        assertEquals(2, playlist.between(start + 7000, start + 13000).size());
        assertEquals("a-1001.ts", playlist.between(start + 7000, start + 13000).get(0).getUri());
        assertTrue(playlist.covers(start, start + 19000));
        assertFalse(playlist.covers(start - 1000, start + 19000));
    }

    @Test
    public void untimedPlaylistEndsNow() {
        String text = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nx.aac\n#EXTINF:10,\ny.aac\n";
        HlsSegments.Playlist playlist = HlsSegments.parse(text, 100_000);
        assertFalse(playlist.hasClockTimes());
        assertEquals(80_000, playlist.getSegments().get(0).getStart());
        assertEquals(100_000, playlist.getSegments().get(1).getEnd());
        assertEquals(10_000, playlist.getTargetDurationMillis());
    }

    @Test
    public void initSegmentAndEncryptionAreNoticed() {
        String text = "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"k\"\n"
                + "#EXTINF:4,\nf1.m4s\n";
        HlsSegments.Playlist playlist = HlsSegments.parse(text, 0);
        assertEquals("init.mp4", playlist.getInitUri());
        assertTrue(playlist.isEncrypted());
        assertFalse(HlsSegments.parse(TIMED, 0).isEncrypted());
    }

    @Test
    public void variantFollowsTheQualityCap() {
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=339200,CODECS=\"mp4a.40.2\"\nhigh.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=135680\nmid.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=56000\nlow.m3u8\n";
        assertEquals("mid.m3u8", HlsSegments.chooseVariant(master, 160_000));
        assertEquals("low.m3u8", HlsSegments.chooseVariant(master, 80_000));
        assertEquals("high.m3u8", HlsSegments.chooseVariant(master, Integer.MAX_VALUE));
        assertEquals("low.m3u8", HlsSegments.chooseVariant(master, 1000));
    }
}
