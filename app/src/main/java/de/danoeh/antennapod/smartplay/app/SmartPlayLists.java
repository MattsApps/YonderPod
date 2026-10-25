package de.danoeh.antennapod.smartplay.app;

import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;

public final class SmartPlayLists {
    private static final int STUFF_RULE_COUNT = 20;

    private SmartPlayLists() {
    }

    public static SmartPlayDefinition stuff() {
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder("Stuff");
        for (int i = 0; i < STUFF_RULE_COUNT; i++) {
            builder.rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.allFeeds()));
        }
        return builder.build();
    }
}
