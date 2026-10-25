package de.danoeh.antennapod.smartplay;

public final class SmartPlayRule {

    public enum Order {
        LATEST,
        OLDEST,
        RANDOM
    }

    private final Order order;
    private final int count;
    private final EpisodeSource source;

    private SmartPlayRule(Builder builder) {
        this.order = builder.order;
        this.count = builder.count;
        this.source = builder.source;
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

        public SmartPlayRule build() {
            return new SmartPlayRule(this);
        }
    }
}
