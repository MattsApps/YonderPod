package de.danoeh.antennapod.smartplay;

public abstract class EpisodeSource {

    public enum Type {
        ALL_FEEDS,
        FEED,
        CATEGORY
    }

    public abstract boolean covers(SmartPlayEpisode episode);

    public abstract Type getType();

    public long getFeedId() {
        return -1;
    }

    public String getCategory() {
        return null;
    }

    public static EpisodeSource allFeeds() {
        return new AllFeeds();
    }

    public static EpisodeSource feed(long feedId) {
        return new SingleFeed(feedId);
    }

    public static EpisodeSource category(String category) {
        return new Category(category);
    }

    private static final class AllFeeds extends EpisodeSource {
        @Override
        public boolean covers(SmartPlayEpisode episode) {
            return true;
        }

        @Override
        public Type getType() {
            return Type.ALL_FEEDS;
        }

        @Override
        public String toString() {
            return "All feeds";
        }
    }

    private static final class SingleFeed extends EpisodeSource {
        private final long feedId;

        private SingleFeed(long feedId) {
            this.feedId = feedId;
        }

        @Override
        public boolean covers(SmartPlayEpisode episode) {
            return episode.getFeedId() == feedId;
        }

        @Override
        public Type getType() {
            return Type.FEED;
        }

        @Override
        public long getFeedId() {
            return feedId;
        }

        @Override
        public String toString() {
            return "Feed " + feedId;
        }
    }

    private static final class Category extends EpisodeSource {
        private final String category;

        private Category(String category) {
            this.category = category;
        }

        @Override
        public boolean covers(SmartPlayEpisode episode) {
            return episode.getCategories().contains(category);
        }

        @Override
        public Type getType() {
            return Type.CATEGORY;
        }

        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public String toString() {
            return "Category " + category;
        }
    }
}
