package de.danoeh.antennapod.smartplay.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class EpisodePartsTest {
    private static int number(String title) {
        EpisodeParts.Part part = EpisodeParts.of(title);
        return part == null ? -1 : part.number;
    }

    @Test
    public void readsPartNumbers() {
        assertEquals(2, number("The fall of Rome (Part 2)"));
        assertEquals(2, number("The fall of Rome, Pt 2"));
        assertEquals(2, number("The fall of Rome, Pt. 2: Goths"));
        assertEquals(3, number("The fall of Rome pt.3"));
        assertEquals(2, number("The fall of Rome - Part II"));
        assertEquals(4, number("The fall of Rome - part IV"));
        assertEquals(2, number("The fall of Rome: Part Two"));
        assertEquals(2, number("Part #2 - The fall of Rome"));
        assertEquals(2, number("Interview 2 of 3"));
        assertEquals(2, number("Interview (2/3)"));
        assertEquals(1, number("Interview [1/3]"));
    }

    @Test
    public void ignoresTitlesWithoutParts() {
        assertNull(EpisodeParts.of("Episode 202: Counterpart 2"));
        assertNull(EpisodeParts.of("Apartment 3 stories"));
        assertNull(EpisodeParts.of("One of the best"));
        assertNull(EpisodeParts.of("Top 10 of 2025"));
        assertNull(EpisodeParts.of("Part of the problem"));
        assertNull(EpisodeParts.of("24/7 news"));
        assertNull(EpisodeParts.of(null));
    }

    @Test
    public void stemsMatchAcrossStyles() {
        assertEquals(EpisodeParts.of("Rome (Part 2)").stem, EpisodeParts.of("Rome, Pt 1").stem);
        assertEquals("", EpisodeParts.of("Part 2: The return").stem);
    }

    private static NextInShow.Episode ep(long id, String title, long date, boolean played) {
        return new NextInShow.Episode(id, title, date, played, false);
    }

    @Test
    public void nextNewerEpisode() {
        List<NextInShow.Episode> show = Arrays.asList(ep(1, "Rome (Part 1)", 10, true),
                ep(2, "Rome (Part 2)", 20, false), ep(3, "Something else", 30, false));
        assertEquals(2, NextInShow.choose(show, 1, 10).id);
    }

    @Test
    public void newestWhenNothingNewer() {
        List<NextInShow.Episode> show = Arrays.asList(ep(1, "Old", 10, false), ep(2, "Newer", 20, false),
                ep(3, "Just finished", 30, true));
        assertEquals(2, NextInShow.choose(show, 3, 30).id);
    }

    @Test
    public void nothingNew() {
        List<NextInShow.Episode> show = Arrays.asList(ep(1, "Old", 10, true), ep(2, "Just finished", 20, true));
        assertNull(NextInShow.choose(show, 2, 20));
    }

    @Test
    public void earlierPartFirst() {
        List<NextInShow.Episode> show = Arrays.asList(ep(1, "Rome (Part 1)", 10, false),
                ep(2, "Rome (Part 2)", 20, false), ep(3, "Rome (Part 3)", 30, false),
                ep(4, "Finished", 40, true));
        assertEquals(1, NextInShow.choose(show, 4, 40).id);
    }

    @Test
    public void earlierPartOfAnotherStoryDoesNotCount() {
        List<NextInShow.Episode> show = Arrays.asList(ep(1, "Carthage (Part 1)", 10, false),
                ep(2, "Rome (Part 2)", 20, false), ep(3, "Finished", 30, true));
        assertEquals(2, NextInShow.choose(show, 3, 30).id);
    }

    @Test
    public void leftOutEpisodesAreSkipped() {
        List<NextInShow.Episode> show = Arrays.asList(new NextInShow.Episode(1, "Half played", 20, false, true),
                ep(2, "Fresh", 15, false), ep(3, "Finished", 10, true));
        assertEquals(2, NextInShow.choose(show, 3, 10).id);
    }
}
