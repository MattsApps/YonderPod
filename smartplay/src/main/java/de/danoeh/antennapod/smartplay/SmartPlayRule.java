package de.danoeh.antennapod.smartplay;

import java.util.Set;

public final class SmartPlayRule {

    public enum Order {
        LATEST,
        OLDEST,
        RANDOM,
        NEXT_CHAPTER
    }

    private final Order order;
    private final int count;
    private final EpisodeSource source;
    private final Exclusions exclusions;
    private final int maxPerShow;

    private SmartPlayRule(Builder builder) {
        this.order = builder.order;
        this.count = builder.count;
        this.source = builder.source;
        this.exclusions = builder.exclusions;
        this.maxPerShow = builder.maxPerShow;
    }

    public Exclusions getExclusions() {
        return exclusions;
    }

    public int getMaxPerShow() {
        return maxPerShow;
    }

    public boolean covers(SmartPlayEpisode episode) {
        return source.covers(episode) && !exclusions.matches(episode);
    }

    public boolean coversFeed(long feedId, Set<String> categories) {
        return source.coversFeed(feedId, categories) && !exclusions.matchesFeed(feedId, categories);
    }

    public boolean coversFeed(long feedId, Set<String> categories, boolean book) {
        return source.coversFeed(feedId, categories, book) && !exclusions.matchesFeed(feedId, categories);
    }

    public Order getOrder() {
        return order;
    }

    public int getCount() {
        return count;
    }

    public EpisodeSource getSource() {
        return source;
    }

    public static SmartPlayRule of(Order order, int count, EpisodeSource source) {
        return builder().order(order).count(count).source(source).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Order order = Order.LATEST;
        private int count = 1;
        private EpisodeSource source = EpisodeSource.allFeeds();
        private Exclusions exclusions = Exclusions.none();
        private int maxPerShow = 0;

        private Builder() {
        }

        public Builder order(Order order) {
            this.order = order;
            return this;
        }

        public Builder count(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1");
            }
            this.count = count;
            return this;
        }

        public Builder source(EpisodeSource source) {
            this.source = source;
            return this;
        }

        public Builder exclusions(Exclusions exclusions) {
            this.exclusions = exclusions;
            return this;
        }

        public Builder maxPerShow(int maxPerShow) {
            this.maxPerShow = Math.max(0, maxPerShow);
            return this;
        }

        public SmartPlayRule build() {
            return new SmartPlayRule(this);
        }
    }
}
