package de.danoeh.antennapod.smartplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SmartPlayDefinition {

    public enum PriorityOrder {
        OLDEST_FIRST,
        LATEST_FIRST
    }

    private final String name;
    private final List<Long> priorityFeedIds;
    private final PriorityOrder priorityOrder;
    private final List<SmartPlayRule> rules;

    private SmartPlayDefinition(Builder builder) {
        this.name = builder.name;
        this.priorityFeedIds = Collections.unmodifiableList(new ArrayList<>(builder.priorityFeedIds));
        this.priorityOrder = builder.priorityOrder;
        this.rules = Collections.unmodifiableList(new ArrayList<>(builder.rules));
    }

    public String getName() {
        return name;
    }

    public List<Long> getPriorityFeedIds() {
        return priorityFeedIds;
    }

    public PriorityOrder getPriorityOrder() {
        return priorityOrder;
    }

    public List<SmartPlayRule> getRules() {
        return rules;
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
        private final List<Long> priorityFeedIds = new ArrayList<>();
        private PriorityOrder priorityOrder = PriorityOrder.OLDEST_FIRST;
        private final List<SmartPlayRule> rules = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder priorityFeed(long feedId) {
            priorityFeedIds.add(feedId);
            return this;
        }

        public Builder priorityOrder(PriorityOrder priorityOrder) {
            this.priorityOrder = priorityOrder;
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
