package de.danoeh.antennapod.smartplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EpisodeSourceTest {
    private static final SmartPlayEpisode COMEDY_EPISODE =
            SmartPlayEpisode.builder(1, 7).category("Comedy").downloaded(true).build();

    @Test
    public void allFeedsDescribesItself() {
        EpisodeSource source = EpisodeSource.allFeeds();
        assertEquals(EpisodeSource.Type.ALL_FEEDS, source.getType());
        assertEquals(-1, source.getFeedId());
        assertNull(source.getCategory());
        assertTrue(source.covers(COMEDY_EPISODE));
    }

    @Test
    public void feedDescribesItself() {
        EpisodeSource source = EpisodeSource.feed(7);
        assertEquals(EpisodeSource.Type.FEED, source.getType());
        assertEquals(7, source.getFeedId());
        assertTrue(source.covers(COMEDY_EPISODE));
        assertFalse(EpisodeSource.feed(8).covers(COMEDY_EPISODE));
    }

    @Test
    public void categoryDescribesItself() {
        EpisodeSource source = EpisodeSource.category("Comedy");
        assertEquals(EpisodeSource.Type.CATEGORY, source.getType());
        assertEquals("Comedy", source.getCategory());
        assertTrue(source.covers(COMEDY_EPISODE));
        assertFalse(EpisodeSource.category("News").covers(COMEDY_EPISODE));
    }
}
