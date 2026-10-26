package de.danoeh.antennapod.smartplay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class SmartPlayCoverageTest {
    private static final Set<String> COMEDY = new HashSet<>(Collections.singletonList("Comedy"));
    private static final Set<String> NONE = Collections.emptySet();

    @Test
    public void allFeedsCoversEveryShow() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("All")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()))
                .build();
        assertTrue(definition.coversFeed(5, NONE));
    }

    @Test
    public void feedRuleCoversOnlyThatShow() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("One")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(5)))
                .build();
        assertTrue(definition.coversFeed(5, NONE));
        assertFalse(definition.coversFeed(6, NONE));
    }

    @Test
    public void categoryRuleCoversShowsInThatCategory() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Comedy")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.category("Comedy")))
                .build();
        assertTrue(definition.coversFeed(5, COMEDY));
        assertFalse(definition.coversFeed(5, NONE));
    }

    @Test
    public void priorityShowIsCoveredWithoutAnyRule() {
        SmartPlayDefinition definition = SmartPlayDefinition.builder("Bugle").priorityFeed(9).build();
        assertTrue(definition.coversFeed(9, NONE));
        assertFalse(definition.coversFeed(8, COMEDY));
    }
}
