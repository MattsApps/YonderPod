package de.danoeh.antennapod.smartplay;

import java.util.Set;

public abstract class EpisodeSource {

    public enum Type {
        ALL_FEEDS,
        FEED,
        CATEGORY
    }

    public boolean covers(SmartPlayEpisode episode) {
        return coversFeed(episode.getFeedId(), episode.getCategories(), episode.isBook());
    }

    public abstract boolean coversFeed(long feedId, Set<String> categories);

    public boolean coversFeed(long feedId, Set<String> categories, boolean book) {
        return coversFeed(feedId, categories);
    }

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
        public boolean coversFeed(long feedId, Set<String> categories) {
            return true;
        }

        @Override
        public boolean coversFeed(long feedId, Set<String> categories, boolean book) {
            return !book;
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
        public boolean coversFeed(long otherFeedId, Set<String> categories) {
            return otherFeedId == feedId;
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
        public boolean coversFeed(long feedId, Set<String> categories) {
            return categories.contains(category);
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
