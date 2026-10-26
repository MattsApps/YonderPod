package de.danoeh.antennapod.smartplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

public class SmartPlayExclusionsTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long COMEDY = 1;
    private static final long NEWS = 2;
    private static final long SAVED = 3;
    private static final long DRAMA = 4;

    private final SmartPlayEngine engine = new SmartPlayEngine(new Random(1));

    private static SmartPlayEpisode.Builder episode(long id, long feedId, int day) {
        SmartPlayEpisode.Builder builder = SmartPlayEpisode.builder(id, feedId).pubDate(day * DAY).downloaded(true);
        if (feedId == COMEDY) {
            builder.category("Comedy");
        } else if (feedId == SAVED) {
            builder.category("Saved");
        } else if (feedId == NEWS) {
            builder.category("News");
        }
        return builder;
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

    private static Exclusions categories(String... names) {
        return new Exclusions(Arrays.asList(names), Collections.emptyList());
    }

    private static Exclusions shows(Long... feedIds) {
        return new Exclusions(Collections.emptyList(), Arrays.asList(feedIds));
    }

    private static SmartPlayRule rule(SmartPlayRule.Order order, int count, EpisodeSource source,
                                      Exclusions exclusions, int maxPerShow) {
        return SmartPlayRule.builder().order(order).count(count).source(source)
                .exclusions(exclusions).maxPerShow(maxPerShow).build();
    }

    @Test
    public void allFeedsExceptCategoryLeavesThatCategoryOut() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Not saved")
                .rule(rule(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds(), categories("Saved"), 0))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, SAVED, 9), downloaded(2, NEWS, 8), downloaded(3, SAVED, 7), downloaded(4, COMEDY, 6));
        assertEquals(Arrays.asList(2L, 4L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void categoryExceptShowLeavesThatShowOut() {
        SmartPlayEpisode otherComedy = SmartPlayEpisode.builder(5, DRAMA).category("Comedy")
                .pubDate(5 * DAY).downloaded(true).build();
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Comedy but not that one")
                .rule(rule(SmartPlayRule.Order.LATEST, 5, EpisodeSource.category("Comedy"), shows(COMEDY), 0))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(downloaded(1, COMEDY, 9), otherComedy);
        assertEquals(Arrays.asList(5L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void exclusionsMixCategoriesAndShows() {
        Exclusions mixed = new Exclusions(Collections.singletonList("Saved"), Collections.singletonList(COMEDY));
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Mixed")
                .rule(rule(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds(), mixed, 0))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, SAVED, 9), downloaded(2, COMEDY, 8), downloaded(3, NEWS, 7), downloaded(4, DRAMA, 6));
        assertEquals(Arrays.asList(3L, 4L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void neverIncludeOverridesRulesAndPriorityShows() {
        SmartPlayDefinition work = SmartPlayDefinition.builder("Work")
                .priorityFeed(COMEDY)
                .neverInclude(categories("Comedy"))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds()))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 5, EpisodeSource.feed(COMEDY)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 9), downloaded(2, COMEDY, 8), downloaded(3, NEWS, 7));
        assertEquals(Arrays.asList(3L), ids(engine.rebuild(work, candidates)));
    }

    @Test
    public void blockedHalfPlayedEpisodeStaysOutOfThatListOnly() {
        SmartPlayEpisode halfPlayedComedy = episode(1, COMEDY, 1).position(5000).build();
        List<SmartPlayEpisode> candidates = Arrays.asList(halfPlayedComedy, downloaded(2, NEWS, 5));
        SmartPlayDefinition work = SmartPlayDefinition.builder("Work")
                .neverInclude(categories("Comedy"))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        SmartPlayDefinition home = SmartPlayDefinition.builder("Home")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        assertEquals(Arrays.asList(2L), ids(engine.rebuild(work, candidates)));
        assertEquals(Arrays.asList(1L, 2L), ids(engine.rebuild(home, candidates)));
    }

    @Test
    public void handAddedEpisodeFromBlockedShowIsKept() {
        SmartPlayEpisode comedy = downloaded(1, COMEDY, 9);
        SmartPlayDefinition work = SmartPlayDefinition.builder("Work")
                .neverInclude(categories("Comedy"))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        ManualEdits edits = new ManualEdits(Collections.singletonList(1L), Collections.emptyList());
        List<SmartPlayEpisode> list = engine.rebuild(work, Arrays.asList(comedy, downloaded(2, NEWS, 5)),
                Collections.singletonList(comedy), edits);
        assertEquals(Arrays.asList(1L, 2L), ids(list));
    }

    @Test
    public void newDownloadsDoNotInsertBlockedPriorityEpisodes() {
        SmartPlayDefinition work = SmartPlayDefinition.builder("Work")
                .priorityFeed(COMEDY)
                .neverInclude(categories("Comedy"))
                .build();
        List<SmartPlayEpisode> current = Collections.singletonList(downloaded(3, NEWS, 1));
        List<SmartPlayEpisode> candidates = Arrays.asList(downloaded(3, NEWS, 1), downloaded(1, COMEDY, 9));
        assertEquals(Arrays.asList(3L), ids(engine.onNewDownloads(work, candidates, current)));
    }

    @Test
    public void maxPerShowLimitsEachShow() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Max two")
                .rule(rule(SmartPlayRule.Order.LATEST, 20, EpisodeSource.allFeeds(), Exclusions.none(), 2))
                .build();
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        long id = 1;
        for (long feed = 1; feed <= 4; feed++) {
            for (int i = 0; i < 5; i++) {
                candidates.add(downloaded(id++, feed, (int) (feed * 10 + i)));
            }
        }
        List<SmartPlayEpisode> list = engine.rebuild(definition, candidates);
        assertEquals(8, list.size());
        Map<Long, Integer> perShow = new HashMap<>();
        for (SmartPlayEpisode episode : list) {
            perShow.merge(episode.getFeedId(), 1, Integer::sum);
        }
        for (int count : perShow.values()) {
            assertEquals(2, count);
        }
    }

    @Test
    public void maxPerShowHoldsAcrossTheRefillPass() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Refill")
                .rule(rule(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(COMEDY), Exclusions.none(), 1))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(SAVED)))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 9), downloaded(2, COMEDY, 8), downloaded(3, COMEDY, 7));
        assertEquals(Arrays.asList(1L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void maxPerShowCountsOnlyThatRulesOwnPicks() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Two rules")
                .rule(rule(SmartPlayRule.Order.LATEST, 2, EpisodeSource.feed(COMEDY), Exclusions.none(), 1))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, COMEDY, 9), downloaded(2, COMEDY, 8), downloaded(3, NEWS, 1));
        assertEquals(Arrays.asList(1L, 2L, 3L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void excludedShowsAreNotUsedByTheRefillPass() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Refill except")
                .rule(rule(SmartPlayRule.Order.LATEST, 3, EpisodeSource.allFeeds(), categories("Saved"), 0))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, NEWS, 9), downloaded(2, SAVED, 8), downloaded(3, SAVED, 7));
        assertEquals(Arrays.asList(1L), ids(engine.rebuild(definition, candidates)));
    }

    @Test
    public void randomRuleCanAddOneFromAnExcludedCategory() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Sneak one in")
                .rule(rule(SmartPlayRule.Order.LATEST, 3, EpisodeSource.allFeeds(), categories("Saved"), 0))
                .rule(SmartPlayRule.of(SmartPlayRule.Order.RANDOM, 1, EpisodeSource.category("Saved")))
                .build();
        List<SmartPlayEpisode> candidates = Arrays.asList(
                downloaded(1, NEWS, 9), downloaded(2, NEWS, 8), downloaded(3, NEWS, 7),
                downloaded(4, SAVED, 6), downloaded(5, SAVED, 5));
        List<SmartPlayEpisode> list = engine.rebuild(definition, candidates);
        assertEquals(4, list.size());
        assertEquals(SAVED, list.get(3).getFeedId());
    }

    @Test
    public void coverageRespectsExclusionsAndNeverInclude() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Coverage")
                .neverInclude(shows(DRAMA))
                .rule(rule(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds(), categories("Saved"), 0))
                .build();
        assertTrue(definition.coversFeed(NEWS, new HashSet<>(Collections.singletonList("News"))));
        assertFalse(definition.coversFeed(SAVED, new HashSet<>(Collections.singletonList("Saved"))));
        assertFalse(definition.coversFeed(DRAMA, Collections.emptySet()));
    }
}
