package de.danoeh.antennapod.smartplay.worlds;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldPagesTest {

    @Test
    public void allWorldsNoShelves() {
        WorldPages pages = new WorldPages(true, true, 0, 0);
        assertEquals(3, pages.size());
        assertEquals(WorldPages.BOOKS, pages.pageAt(0));
        assertEquals(WorldPages.PODCASTS, pages.pageAt(1));
        assertEquals(WorldPages.RADIO, pages.pageAt(2));
        assertEquals(WorldPages.NONE, pages.leftOf(WorldPages.BOOKS));
        assertEquals(WorldPages.NONE, pages.rightOf(WorldPages.RADIO));
    }

    @Test
    public void hiddenBooksLeavesPodcastsFirst() {
        WorldPages pages = new WorldPages(false, true, 0, 0);
        assertEquals(2, pages.size());
        assertEquals(0, pages.positionOf(WorldPages.PODCASTS));
        assertEquals(WorldPages.NONE, pages.leftOf(WorldPages.PODCASTS));
        assertFalse(pages.contains(WorldPages.BOOKS));
    }

    @Test
    public void hiddenWorldFallsBackToPodcasts() {
        WorldPages pages = new WorldPages(false, false, 0, 0);
        assertEquals(1, pages.size());
        assertEquals(0, pages.positionOf(WorldPages.BOOKS));
        assertEquals(0, pages.positionOf(WorldPages.RADIO));
    }

    @Test
    public void shelvesSitOutsideTheWorlds() {
        WorldPages pages = new WorldPages(true, true, 2, 1);
        assertEquals(5, pages.size());
        assertEquals(WorldPages.LEFT_SHELF, pages.pageAt(0));
        assertEquals(WorldPages.RIGHT_SHELF, pages.pageAt(4));
        assertEquals(WorldPages.LEFT_SHELF, pages.leftOf(WorldPages.BOOKS));
        assertEquals(WorldPages.RIGHT_SHELF, pages.rightOf(WorldPages.RADIO));
        assertEquals(WorldPages.BOOKS, pages.worldBeside(WorldPages.LEFT_SHELF));
        assertEquals(WorldPages.RADIO, pages.worldBeside(WorldPages.RIGHT_SHELF));
    }

    @Test
    public void shelfBesideHiddenWorld() {
        WorldPages pages = new WorldPages(true, false, 0, 3);
        assertEquals(WorldPages.RIGHT_SHELF, pages.rightOf(WorldPages.PODCASTS));
        assertEquals(WorldPages.PODCASTS, pages.worldBeside(WorldPages.RIGHT_SHELF));
    }

    @Test
    public void oneAppOpensDirectly() {
        WorldPages pages = new WorldPages(true, true, 3, 1);
        assertTrue(pages.opensDirectly(WorldPages.RIGHT_SHELF));
        assertFalse(pages.opensDirectly(WorldPages.LEFT_SHELF));
        assertFalse(pages.opensDirectly(WorldPages.PODCASTS));
        assertEquals(3, pages.appsOn(WorldPages.LEFT_SHELF));
    }

    @Test
    public void emptyShelfHasNoPage() {
        WorldPages pages = new WorldPages(true, true, 0, 0);
        assertFalse(pages.contains(WorldPages.LEFT_SHELF));
        assertFalse(pages.contains(WorldPages.RIGHT_SHELF));
    }

    @Test
    public void sameAsComparesLayoutAndCounts() {
        assertTrue(new WorldPages(true, true, 2, 0).sameAs(new WorldPages(true, true, 2, 0)));
        assertFalse(new WorldPages(true, true, 2, 0).sameAs(new WorldPages(true, true, 3, 0)));
        assertFalse(new WorldPages(true, true, 0, 0).sameAs(new WorldPages(false, true, 0, 0)));
    }
}
