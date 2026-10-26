package de.danoeh.antennapod.smartplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class SmartPlayDefinition {

    public enum PriorityOrder {
        OLDEST_FIRST,
        LATEST_FIRST
    }

    public static final class PriorityFeed {
        private final long feedId;
        private final PriorityOrder order;

        private PriorityFeed(long feedId, PriorityOrder order) {
            this.feedId = feedId;
            this.order = order;
        }

        public long getFeedId() {
            return feedId;
        }

        public PriorityOrder getOrder() {
            return order;
        }
    }

    private final String name;
    private final List<PriorityFeed> priorityFeeds;
    private final List<SmartPlayRule> rules;

    private SmartPlayDefinition(Builder builder) {
        this.name = builder.name;
        this.priorityFeeds = Collections.unmodifiableList(new ArrayList<>(builder.priorityFeeds));
        this.rules = Collections.unmodifiableList(new ArrayList<>(builder.rules));
    }

    public String getName() {
        return name;
    }

    public List<PriorityFeed> getPriorityFeeds() {
        return priorityFeeds;
    }

    public List<SmartPlayRule> getRules() {
        return rules;
    }

    public boolean coversFeed(long feedId, Set<String> categories) {
        for (PriorityFeed priorityFeed : priorityFeeds) {
            if (priorityFeed.getFeedId() == feedId) {
                return true;
            }
        }
        for (SmartPlayRule rule : rules) {
            EpisodeSource source = rule.getSource();
            switch (source.getType()) {
                case ALL_FEEDS:
                    return true;
                case FEED:
                    if (source.getFeedId() == feedId) {
                        return true;
                    }
                    break;
                case CATEGORY:
                    if (categories.contains(source.getCategory())) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    public int getTargetLength() {
        int total = 0;
        for (SmartPlayRule rule : rules) {
            total += rule.getCount();
        }
        return total;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<PriorityFeed> priorityFeeds = new ArrayList<>();
        private final List<SmartPlayRule> rules = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder priorityFeed(long feedId) {
            return priorityFeed(feedId, PriorityOrder.OLDEST_FIRST);
        }

        public Builder priorityFeed(long feedId, PriorityOrder order) {
            priorityFeeds.add(new PriorityFeed(feedId, order));
            return this;
        }

        public Builder rule(SmartPlayRule rule) {
            rules.add(rule);
            return this;
        }

        public SmartPlayDefinition build() {
            return new SmartPlayDefinition(this);
        }
    }
}
