package de.danoeh.antennapod.smartplay;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SmartPlayBookTest {
    private static final long PODCAST = 1;
    private static final long PRIDE = 2;
    private static final long EMMA = 3;
    private static final String AUDIOBOOKS = "Audiobooks";

    private final SmartPlayEngine engine = new SmartPlayEngine();

    private static SmartPlayEpisode.Builder chapter(long id, long feedId, long order) {
        return SmartPlayEpisode.builder(id, feedId).pubDate(order).downloaded(true).book(true).category(AUDIOBOOKS);
    }

    private static SmartPlayEpisode podcast(long id, long pubDate) {
        return SmartPlayEpisode.builder(id, PODCAST).pubDate(pubDate).downloaded(true).build();
    }

    private static List<Long> ids(List<SmartPlayEpisode> episodes) {
        List<Long> result = new ArrayList<>();
        for (SmartPlayEpisode episode : episodes) {
            result.add(episode.getId());
        }
        return result;
    }

    private static SmartPlayDefinition single(SmartPlayRule.Order order, int count, EpisodeSource source) {
        return SmartPlayDefinition.builder("Test").rule(SmartPlayRule.of(order, count, source)).build();
    }

    @Test
    public void allFeedsLeavesBooksOut() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(10, PRIDE, 1).build(), chapter(11, PRIDE, 2).build(), podcast(1, 5));
        SmartPlayDefinition latest = single(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds());
        assertEquals(Collections.singletonList(1L), ids(engine.rebuild(latest, candidates)));
    }

    @Test
    public void halfPlayedChapterStaysOutOfAllFeedsList() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(10, PRIDE, 1).position(5000).lastPlayedTime(100).build(), podcast(1, 5));
        SmartPlayDefinition latest = single(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds());
        assertEquals(Collections.singletonList(1L), ids(engine.rebuild(latest, candidates)));
    }

    @Test
    public void nextChapterFromAudiobooksPlaysInBookOrder() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(12, PRIDE, 3).build(), chapter(10, PRIDE, 1).build(), chapter(11, PRIDE, 2).build(),
                podcast(1, 5));
        SmartPlayDefinition books = single(SmartPlayRule.Order.NEXT_CHAPTER, 2,
                EpisodeSource.category(AUDIOBOOKS));
        assertEquals(Arrays.asList(10L, 11L), ids(engine.rebuild(books, candidates)));
    }

    @Test
    public void nextChapterCarriesOnWithTheBookPlayedMostRecently() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(10, PRIDE, 1).showLastPlayed(100).build(),
                chapter(20, EMMA, 1).showLastPlayed(900).build(),
                chapter(21, EMMA, 2).showLastPlayed(900).build());
        SmartPlayDefinition books = single(SmartPlayRule.Order.NEXT_CHAPTER, 1,
                EpisodeSource.category(AUDIOBOOKS));
        assertEquals(Collections.singletonList(20L), ids(engine.rebuild(books, candidates)));
    }

    @Test
    public void ruleNamingTheBookPicksItsNextChapters() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(10, PRIDE, 1).played(true).build(), chapter(11, PRIDE, 2).build(),
                chapter(12, PRIDE, 3).build(), chapter(20, EMMA, 1).build());
        SmartPlayDefinition pride = single(SmartPlayRule.Order.NEXT_CHAPTER, 3, EpisodeSource.feed(PRIDE));
        assertEquals(Arrays.asList(11L, 12L), ids(engine.rebuild(pride, candidates)));
    }

    @Test
    public void halfPlayedChapterLeadsItsBookList() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                chapter(11, PRIDE, 2).position(5000).lastPlayedTime(100).build(), chapter(12, PRIDE, 3).build());
        SmartPlayDefinition pride = single(SmartPlayRule.Order.NEXT_CHAPTER, 3, EpisodeSource.feed(PRIDE));
        assertEquals(Arrays.asList(11L, 12L), ids(engine.rebuild(pride, candidates)));
    }

    @Test
    public void bookListLeavesOtherHalfPlayedEpisodesOut() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                SmartPlayEpisode.builder(1, PODCAST).pubDate(5).downloaded(true).position(9000)
                        .lastPlayedTime(500).build(),
                chapter(10, PRIDE, 1).build());
        SmartPlayDefinition pride = single(SmartPlayRule.Order.NEXT_CHAPTER, 3, EpisodeSource.feed(PRIDE));
        assertEquals(Collections.singletonList(10L), ids(engine.rebuild(pride, candidates)));
    }

    @Test
    public void ordinaryListStillKeepsHalfPlayedPodcasts() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                SmartPlayEpisode.builder(1, PODCAST).pubDate(5).downloaded(true).position(9000).build(),
                chapter(10, PRIDE, 1).build(), chapter(11, PRIDE, 2).build());
        SmartPlayDefinition mixed = SmartPlayDefinition.builder("Mixed")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(99)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.NEXT_CHAPTER, 1, EpisodeSource.category(AUDIOBOOKS)))
                .build();
        assertEquals(Arrays.asList(1L, 10L, 11L), ids(engine.rebuild(mixed, candidates)));
    }

    @Test
    public void coverageKnowsBooksAreNotInAllFeeds() {
        SmartPlayDefinition latest = single(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds());
        assertFalse(latest.coversFeed(PRIDE, Collections.singleton(AUDIOBOOKS), true));
        assertTrue(latest.coversFeed(PODCAST, Collections.emptySet(), false));
        SmartPlayDefinition books = single(SmartPlayRule.Order.NEXT_CHAPTER, 1,
                EpisodeSource.category(AUDIOBOOKS));
        assertTrue(books.coversFeed(PRIDE, Collections.singleton(AUDIOBOOKS), true));
        assertTrue(books.isBookList());
        assertFalse(latest.isBookList());
    }
}
