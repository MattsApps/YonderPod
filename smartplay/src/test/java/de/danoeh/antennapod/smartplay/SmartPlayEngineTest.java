package de.danoeh.antennapod.smartplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

public class SmartPlayEngineTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long COMEDY = 1;
    private static final long NEWS = 2;
    private static final long DRAMA = 3;
    private static final long SCIENCE = 4;
    private static final long BUGLE = 5;

    private final SmartPlayEngine engine = new SmartPlayEngine(new Random(42));

    private static SmartPlayEpisode.Builder episode(long id, long feedId, int day) {
        return SmartPlayEpisode.builder(id, feedId).pubDate(day * DAY).downloaded(true);
    }

    private static SmartPlayEpisode downloaded(long id, long feedId, int day) {
        return episode(id, feedId, day).build();
    }

    private static List<Long> ids(List<SmartPlayEpisode> episodes) {
        List<Long> result = new ArrayList<>();
        for (SmartPlayEpisode episode : episodes) {
            result.add(episode.getId());
        }
        return result;
    }

    private static SmartPlayDefinition allFeedsLatest(int rules) {
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder("Test");
        for (int i = 0; i < rules; i++) {
            builder.rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()));
        }
        return builder.build();
    }

    @Test
    public void halfPlayedEpisodesComeFirstMostRecentlyPlayedFirst() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 10),
                episode(2, NEWS, 1).position(5000).lastPlayedTime(100).build(),
                episode(3, DRAMA, 2).position(9000).lastPlayedTime(200).build());
        List<SmartPlayEpisode> list = engine.rebuild(allFeedsLatest(1), candidates);
        assertEquals(Arrays.asList(3L, 2L, 1L), ids(list));
    }

    @Test
    public void halfPlayedEpisodesDoNotCountTowardsTheRules() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, COMEDY, 1).position(1000).build(),
                downloaded(2, COMEDY, 5),
                downloaded(3, COMEDY, 4));
        List<SmartPlayEpisode> list = engine.rebuild(allFeedsLatest(2), candidates);
        assertEquals(Arrays.asList(1L, 2L, 3L), ids(list));
    }

    @Test
    public void halfPlayedEpisodeIsKeptEvenWhenNoRuleCoversItsShow() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("News only")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(NEWS)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, COMEDY, 1).position(1000).build(),
                downloaded(2, NEWS, 2));
        assertEquals(Arrays.asList(1L, 2L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void priorityFeedEpisodesComeAfterHalfPlayedOldestFirst() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle")
                .priorityFeed(BUGLE)
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 20),
                downloaded(2, BUGLE, 3),
                downloaded(3, BUGLE, 9),
                downloaded(4, BUGLE, 6),
                episode(5, NEWS, 1).position(1000).build());
        List<SmartPlayEpisode> list = engine.rebuild(definition, candidates);
        assertEquals(Arrays.asList(5L, 2L, 4L, 3L, 1L), ids(list));
    }

    @Test
    public void priorityFeedCanBeLatestFirst() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle")
                .priorityFeed(BUGLE)
                .priorityOrder(SmartPlayDefinition.PriorityOrder.LATEST_FIRST)
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(2, BUGLE, 3),
                downloaded(3, BUGLE, 9),
                downloaded(4, BUGLE, 6));
        assertEquals(Arrays.asList(3L, 4L, 2L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void priorityFeedsSkipPlayedAndUnavailableEpisodes() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle")
                .priorityFeed(BUGLE)
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, BUGLE, 1).played(true).build(),
                episode(2, BUGLE, 2).downloaded(false).build(),
                downloaded(3, BUGLE, 3));
        assertEquals(Arrays.asList(3L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void rulesAreFollowedInOrder() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Mix")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.category("Comedy")))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.OLDEST, 1, EpisodeSource.category("News")))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 2, EpisodeSource.feed(DRAMA)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, COMEDY, 5).category("Comedy").build(),
                episode(2, COMEDY, 8).category("Comedy").build(),
                episode(3, NEWS, 2).category("News").build(),
                episode(4, NEWS, 9).category("News").build(),
                downloaded(5, DRAMA, 1),
                downloaded(6, DRAMA, 7),
                downloaded(7, DRAMA, 4));
        List<SmartPlayEpisode> list = engine.rebuild(definition, candidates);
        assertEquals(Arrays.asList(2L, 3L, 6L, 7L), ids(list));
    }

    @Test
    public void latestAndOldestPickFromTheRightEnd() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, NEWS, 3), downloaded(2, NEWS, 1), downloaded(3, NEWS, 2));
        SmartPlayDefinition latest = SmartPlayDefinition.builder("Latest")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(NEWS))).build();
        SmartPlayDefinition oldest = SmartPlayDefinition.builder("Oldest")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.OLDEST, 1, EpisodeSource.feed(NEWS))).build();
        assertEquals(Arrays.asList(1L), ids(engine.rebuild(latest, candidates)));
        assertEquals(Arrays.asList(2L), ids(engine.rebuild(oldest, candidates)));
    }

    @Test
    public void randomIsRepeatableWithTheSameSeed() {
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            candidates.add(downloaded(i, i % 2 == 0 ? COMEDY : NEWS, i));
        }
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Random")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.RANDOM, 5, EpisodeSource.feed(COMEDY)))
                .build();
        List<Long> first = ids(new SmartPlayEngine(new Random(7)).rebuild(definition, candidates));
        List<Long> second = ids(new SmartPlayEngine(new Random(7)).rebuild(definition, candidates));
        List<Long> otherSeed = ids(new SmartPlayEngine(new Random(8)).rebuild(definition, candidates));
        assertEquals(first, second);
        assertNotEquals(first, otherSeed);
        assertEquals(5, first.size());
        for (long id : first) {
            assertEquals(0, id % 2);
        }
    }

    @Test
    public void randomDoesNotDependOnCandidateOrder() {
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(downloaded(i, NEWS, i));
        }
        List<SmartPlayEpisode> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Random")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.RANDOM, 3, EpisodeSource.allFeeds()))
                .build();
        assertEquals(ids(new SmartPlayEngine(new Random(3)).rebuild(definition, candidates)),
                ids(new SmartPlayEngine(new Random(3)).rebuild(definition, reversed)));
    }

    @Test
    public void noEpisodeIsPickedTwice() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Twice")
                .priorityFeed(BUGLE)
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 2, EpisodeSource.feed(BUGLE)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 2, EpisodeSource.allFeeds()))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, BUGLE, 1), downloaded(2, BUGLE, 2),
                downloaded(3, NEWS, 3), downloaded(4, NEWS, 4), downloaded(5, NEWS, 5));
        List<Long> list = ids(engine.rebuild(definition, candidates));
        assertEquals(new HashSet<>(list).size(), list.size());
        assertEquals(Arrays.asList(1L, 2L, 5L, 4L, 3L), list);
    }

    @Test
    public void emptyRuleIsSkipped() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Skip")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(COMEDY)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(SCIENCE)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(NEWS)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 1), downloaded(2, NEWS, 2));
        assertEquals(Arrays.asList(1L, 2L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void listLengthIsTheTotalOfTheRuleCounts() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Counts")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 2, EpisodeSource.feed(COMEDY)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 3, EpisodeSource.feed(NEWS)))
                .build();
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            candidates.add(downloaded(i, COMEDY, i));
            candidates.add(downloaded(100 + i, NEWS, i));
        }
        assertEquals(5, definition.getTargetLength());
        assertEquals(Arrays.asList(10L, 9L, 110L, 109L, 108L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void shortListIsFilledByWorkingThroughTheRulesAgain() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Refill")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(COMEDY)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(SCIENCE)))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(NEWS)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 1), downloaded(2, COMEDY, 2), downloaded(3, COMEDY, 3),
                downloaded(4, NEWS, 4));
        assertEquals(Arrays.asList(3L, 4L, 2L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void fillingStopsWhenNothingEligibleIsLeft() {
        List<SmartPlayEpisode> candidates = Arrays.asList(downloaded(1, COMEDY, 1), downloaded(2, NEWS, 2));
        assertEquals(Arrays.asList(2L, 1L), ids(engine.rebuild(allFeedsLatest(10), candidates)));
    }

    @Test
    public void showsNotCoveredByAnyRuleAreNeverUsed() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Comedy")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 5, EpisodeSource.feed(COMEDY)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 1), downloaded(2, NEWS, 2), downloaded(3, DRAMA, 3));
        assertEquals(Arrays.asList(1L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void onlyDownloadedOrMobileStreamableEpisodesAreUsed() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 1),
                episode(2, NEWS, 2).downloaded(false).build(),
                episode(3, DRAMA, 3).downloaded(false).streamableOnMobile(true).build());
        assertEquals(Arrays.asList(3L, 1L), ids(engine.rebuild(allFeedsLatest(5), candidates)));
    }

    @Test
    public void playedEpisodesAreNeverUsed() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 1),
                episode(2, NEWS, 2).played(true).build(),
                episode(3, DRAMA, 3).played(true).position(500).build());
        assertEquals(Arrays.asList(1L), ids(engine.rebuild(allFeedsLatest(5), candidates)));
    }

    @Test
    public void newDownloadsLeaveTheListAloneExceptPriorityEpisodes() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle")
                .priorityFeed(BUGLE)
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 3, EpisodeSource.allFeeds()))
                .build();
        SmartPlayEpisode halfPlayed = episode(1, NEWS, 1).position(1000).build();
        List<SmartPlayEpisode> current = Arrays.asList(halfPlayed, downloaded(2, COMEDY, 2), downloaded(3, DRAMA, 3));
        List<SmartPlayEpisode> candidates = Arrays.asList(
                halfPlayed, downloaded(2, COMEDY, 2), downloaded(3, DRAMA, 3),
                downloaded(4, COMEDY, 10), downloaded(5, BUGLE, 9), downloaded(6, BUGLE, 8));
        List<SmartPlayEpisode> list = engine.onNewDownloads(definition, candidates, current);
        assertEquals(Arrays.asList(1L, 6L, 5L, 2L, 3L), ids(list));
    }

    @Test
    public void newDownloadsGoToTheTopWhenNothingIsHalfPlayed() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle").priorityFeed(BUGLE).build();
        List<SmartPlayEpisode> current = Arrays.asList(downloaded(2, COMEDY, 2));
        List<SmartPlayEpisode> candidates = Arrays.asList(downloaded(2, COMEDY, 2), downloaded(5, BUGLE, 9));
        assertEquals(Arrays.asList(5L, 2L), ids(engine.onNewDownloads(definition, candidates, current)));
    }

    @Test
    public void newDownloadsDropPlayedEpisodesAndDoNotRepeatPriorityEpisodes() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("With Bugle").priorityFeed(BUGLE).build();
        List<SmartPlayEpisode> current = Arrays.asList(
                downloaded(5, BUGLE, 9), downloaded(2, COMEDY, 2), downloaded(3, DRAMA, 3));
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(5, BUGLE, 9), episode(2, COMEDY, 2).played(true).build(), downloaded(3, DRAMA, 3));
        assertEquals(Arrays.asList(5L, 3L), ids(engine.onNewDownloads(definition, candidates, current)));
    }

    @Test
    public void newDownloadsRebuildWhenTheListHasRunEmpty() {
        List<SmartPlayEpisode> current = Arrays.asList(episode(1, COMEDY, 1).played(true).build());
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, COMEDY, 1).played(true).build(), downloaded(2, NEWS, 2), downloaded(3, DRAMA, 3));
        List<SmartPlayEpisode> list = engine.onNewDownloads(allFeedsLatest(2), candidates, current);
        assertEquals(Arrays.asList(3L, 2L), ids(list));
    }

    @Test
    public void reproducesBeyondPodStuffList() {
        SmartPlayDefinition stuff = allFeedsLatest(20);
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        long id = 1;
        for (long feed = 10; feed < 16; feed++) {
            for (int i = 0; i < 6; i++) {
                int day = (int) (feed * 7 + i * 5);
                candidates.add(downloaded(id++, feed, day));
            }
        }
        candidates.add(episode(id++, 10, 500).played(true).build());
        candidates.add(episode(id++, 11, 501).downloaded(false).build());
        long halfPlayedId = id++;
        candidates.add(episode(halfPlayedId, 12, 2).position(60000).build());

        List<SmartPlayEpisode> list = engine.rebuild(stuff, candidates);

        assertEquals(21, list.size());
        assertEquals(halfPlayedId, list.get(0).getId());
        List<SmartPlayEpisode> expected = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (episode.isAvailable() && !episode.isHalfPlayed()) {
                expected.add(episode);
            }
        }
        expected.sort((a, b) -> Long.compare(b.getPubDate(), a.getPubDate()));
        assertEquals(ids(expected.subList(0, 20)), ids(list.subList(1, 21)));
        Set<Long> feeds = new HashSet<>();
        boolean repeated = false;
        for (SmartPlayEpisode episode : list.subList(1, 21)) {
            repeated |= !feeds.add(episode.getFeedId());
        }
        assertTrue(repeated);
        assertFalse(ids(list).contains(id - 3));
        assertFalse(ids(list).contains(id - 2));
    }
}
