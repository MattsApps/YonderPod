package de.danoeh.antennapod.smartplay;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class SmartPlayEpisode {
    private final long id;
    private final long feedId;
    private final Set<String> categories;
    private final long pubDate;
    private final boolean downloaded;
    private final boolean streamableOnMobile;
    private final boolean played;
    private final int position;
    private final long lastPlayedTime;
    private final boolean book;
    private final long showLastPlayed;

    private SmartPlayEpisode(Builder builder) {
        this.id = builder.id;
        this.feedId = builder.feedId;
        this.categories = Collections.unmodifiableSet(new HashSet<>(builder.categories));
        this.pubDate = builder.pubDate;
        this.downloaded = builder.downloaded;
        this.streamableOnMobile = builder.streamableOnMobile;
        this.played = builder.played;
        this.position = builder.position;
        this.lastPlayedTime = builder.lastPlayedTime;
        this.book = builder.book;
        this.showLastPlayed = builder.showLastPlayed;
    }

    public long getId() {
        return id;
    }

    public long getFeedId() {
        return feedId;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public long getPubDate() {
        return pubDate;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public boolean isStreamableOnMobile() {
        return streamableOnMobile;
    }

    public boolean isPlayed() {
        return played;
    }

    public int getPosition() {
        return position;
    }

    public long getLastPlayedTime() {
        return lastPlayedTime;
    }

    public boolean isBook() {
        return book;
    }

    public long getShowLastPlayed() {
        return showLastPlayed;
    }

    public boolean isHalfPlayed() {
        return !played && position > 0;
    }

    public boolean isAvailable() {
        return !played && (downloaded || streamableOnMobile);
    }

    @Override
    public String toString() {
        return "Episode" + id;
    }

    public static Builder builder(long id, long feedId) {
        return new Builder(id, feedId);
    }

    public static final class Builder {
        private final long id;
        private final long feedId;
        private final Set<String> categories = new HashSet<>();
        private long pubDate;
        private boolean downloaded;
        private boolean streamableOnMobile;
        private boolean played;
        private int position;
        private long lastPlayedTime;
        private boolean book;
        private long showLastPlayed;

        private Builder(long id, long feedId) {
            this.id = id;
            this.feedId = feedId;
        }

        public Builder category(String category) {
            categories.add(category);
            return this;
        }

        public Builder pubDate(long pubDate) {
            this.pubDate = pubDate;
            return this;
        }

        public Builder downloaded(boolean downloaded) {
            this.downloaded = downloaded;
            return this;
        }

        public Builder streamableOnMobile(boolean streamableOnMobile) {
            this.streamableOnMobile = streamableOnMobile;
            return this;
        }

        public Builder played(boolean played) {
            this.played = played;
            return this;
        }

        public Builder position(int position) {
            this.position = position;
            return this;
        }

        public Builder lastPlayedTime(long lastPlayedTime) {
            this.lastPlayedTime = lastPlayedTime;
            return this;
        }

        public Builder book(boolean book) {
            this.book = book;
            return this;
        }

        public Builder showLastPlayed(long showLastPlayed) {
            this.showLastPlayed = showLastPlayed;
            return this;
        }

        public SmartPlayEpisode build() {
            return new SmartPlayEpisode(this);
        }
    }
}
