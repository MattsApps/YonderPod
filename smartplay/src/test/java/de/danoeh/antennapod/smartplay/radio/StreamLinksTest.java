package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamLinksTest {

    @Test
    public void plsAndM3uLinksAreRecognised() {
        assertTrue(StreamLinks.looksLikePlaylistFile("http://example.com/listen.pls"));
        assertTrue(StreamLinks.looksLikePlaylistFile("https://example.com/radio.M3U?sid=1"));
        assertFalse(StreamLinks.looksLikePlaylistFile("https://example.com/live.m3u8"));
        assertFalse(StreamLinks.looksLikePlaylistFile("http://example.com:8000/stream"));
    }

    @Test
    public void firstStreamIsFoundInPls() {
        String pls = "[playlist]\nNumberOfEntries=2\nFile1=http://ice1.example.com:8000/live\nTitle1=One\n"
                + "File2=http://ice2.example.com/live\n";
        assertEquals("http://ice1.example.com:8000/live", StreamLinks.firstStream(pls));
    }

    @Test
    public void firstStreamIsFoundInM3uAndAsx() {
        assertEquals("https://stream.example.com/aac",
                StreamLinks.firstStream("#EXTM3U\n#EXTINF:-1,Station\nhttps://stream.example.com/aac\n"));
        assertEquals("http://example.com/s.mp3",
                StreamLinks.firstStream("<asx><entry><ref href=\"http://example.com/s.mp3\"/></entry></asx>"));
        assertNull(StreamLinks.firstStream("[playlist]\nNumberOfEntries=0\n"));
    }

    @Test
    public void relativeLinksAreResolved() {
        assertEquals("https://cdn.example.com/live/a/b-96000.m3u8",
                StreamLinks.resolve("https://cdn.example.com/live/a/master.m3u8?x=1", "b-96000.m3u8"));
        assertEquals("https://cdn.example.com/other.m3u8",
                StreamLinks.resolve("https://cdn.example.com/live/a/master.m3u8", "/other.m3u8"));
        assertEquals("https://x.example.com/v.m3u8",
                StreamLinks.resolve("https://cdn.example.com/live/a/master.m3u8", "https://x.example.com/v.m3u8"));
    }

    @Test
    public void nameComesFromTheHost() {
        assertEquals("ice1.example.com", StreamLinks.nameFromUrl("http://ice1.example.com:8000/live"));
    }
}
