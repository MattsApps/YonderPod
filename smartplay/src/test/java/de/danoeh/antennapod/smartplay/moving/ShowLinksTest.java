package de.danoeh.antennapod.smartplay.moving;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShowLinksTest {

    @Test
    public void findsTheLinkInSharedText() {
        assertEquals("https://open.spotify.com/show/2MAi0BvDc6GTFvKFPXnkCL?si=abc",
                ShowLinks.extractUrl("Listen to The Rest Is Politics on Spotify: "
                        + "https://open.spotify.com/show/2MAi0BvDc6GTFvKFPXnkCL?si=abc"));
        assertEquals("https://podcasts.apple.com/gb/podcast/x/id1611374685",
                ShowLinks.extractUrl("(https://podcasts.apple.com/gb/podcast/x/id1611374685)."));
        assertNull(ShowLinks.extractUrl("The Rest Is Politics"));
    }

    @Test
    public void kinds() {
        assertEquals(ShowLinks.APPLE, ShowLinks.kind("https://podcasts.apple.com/nz/podcast/name/id1611374685?i=1"));
        assertEquals(ShowLinks.SPOTIFY, ShowLinks.kind("https://open.spotify.com/show/2MAi0BvDc6GTFvKFPXnkCL"));
        assertEquals(ShowLinks.SPOTIFY,
                ShowLinks.kind("https://open.spotify.com/intl-de/show/2MAi0BvDc6GTFvKFPXnkCL"));
        assertEquals(ShowLinks.FEED, ShowLinks.kind("https://feeds.example.com/show.xml"));
        assertEquals(ShowLinks.FEED, ShowLinks.kind("pcast://feeds.example.com/show.xml"));
        assertEquals(ShowLinks.NAME, ShowLinks.kind("Pod Save America"));
    }

    @Test
    public void ids() {
        assertEquals("1611374685", ShowLinks.appleId("https://podcasts.apple.com/gb/podcast/x/id1611374685"));
        assertEquals("2MAi0BvDc6GTFvKFPXnkCL",
                ShowLinks.spotifyShowId("https://open.spotify.com/show/2MAi0BvDc6GTFvKFPXnkCL?si=1"));
        assertNull(ShowLinks.spotifyShowId("https://open.spotify.com/episode/5abc"));
        assertTrue(ShowLinks.isSpotifyEpisode("https://open.spotify.com/episode/5abc"));
    }

    @Test
    public void podcastScheme() {
        assertEquals("https://feeds.example.com/a.xml",
                ShowLinks.withoutPodcastScheme("podcast://feeds.example.com/a.xml"));
        assertEquals("https://x.org/f", ShowLinks.withoutPodcastScheme("https://x.org/f"));
    }

    @Test
    public void linesSkipBlanksAndRepeats() {
        assertEquals(Arrays.asList("A", "B"), ShowLinks.lines(" A \n\nB\r\nA\n"));
    }

    @Test
    public void matchesShowNames() {
        assertTrue(ShowLinks.sameShow("The Rest Is Politics", "the rest is politics"));
        assertTrue(ShowLinks.sameShow("Pod Save America", "Pod Save America"));
        assertTrue(ShowLinks.sameShow("Café Society Podcast", "Cafe Society"));
        assertTrue(ShowLinks.sameShow("Hamish & Andy", "Hamish and Andy"));
        assertFalse(ShowLinks.sameShow("The Daily", "The Daily Show with Trevor Noah Ears Edition"));
        assertFalse(ShowLinks.sameShow("Politics", "The Rest Is Politics"));
    }
}
