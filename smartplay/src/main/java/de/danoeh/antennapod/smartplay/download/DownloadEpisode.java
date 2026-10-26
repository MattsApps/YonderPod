package de.danoeh.antennapod.smartplay.download;

public final class DownloadEpisode {
    private final long id;
    private final long pubDate;
    private final boolean downloaded;
    private final boolean played;
    private final boolean halfPlayed;
    private final boolean favourite;
    private final boolean inSmartPlay;
    private final boolean inHistory;
    private final boolean downloadable;

    private DownloadEpisode(Builder builder) {
        this.id = builder.id;
        this.pubDate = builder.pubDate;
        this.downloaded = builder.downloaded;
        this.played = builder.played;
        this.halfPlayed = builder.halfPlayed;
        this.favourite = builder.favourite;
        this.inSmartPlay = builder.inSmartPlay;
        this.inHistory = builder.inHistory;
        this.downloadable = builder.downloadable;
    }

    public static Builder builder(long id, long pubDate) {
        return new Builder(id, pubDate);
    }

    public long getId() {
        return id;
    }

    public long getPubDate() {
        return pubDate;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public boolean isPlayed() {
        return played;
    }

    public boolean isHalfPlayed() {
        return halfPlayed && !played;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public boolean isInSmartPlay() {
        return inSmartPlay;
    }

    public boolean isInHistory() {
        return inHistory;
    }

    public boolean isDownloadable() {
        return downloadable;
    }

    public boolean isProtected() {
        return isHalfPlayed() || favourite || inSmartPlay;
    }

    public static final class Builder {
        private final long id;
        private final long pubDate;
        private boolean downloaded;
        private boolean played;
        private boolean halfPlayed;
        private boolean favourite;
        private boolean inSmartPlay;
        private boolean inHistory;
        private boolean downloadable = true;

        private Builder(long id, long pubDate) {
            this.id = id;
            this.pubDate = pubDate;
        }

        public Builder downloaded(boolean downloaded) {
            this.downloaded = downloaded;
            return this;
        }

        public Builder played(boolean played) {
            this.played = played;
            return this;
        }

        public Builder halfPlayed(boolean halfPlayed) {
            this.halfPlayed = halfPlayed;
            return this;
        }

        public Builder favourite(boolean favourite) {
            this.favourite = favourite;
            return this;
        }

        public Builder inSmartPlay(boolean inSmartPlay) {
            this.inSmartPlay = inSmartPlay;
            return this;
        }

        public Builder inHistory(boolean inHistory) {
            this.inHistory = inHistory;
            return this;
        }

        public Builder downloadable(boolean downloadable) {
            this.downloadable = downloadable;
            return this;
        }

        public DownloadEpisode build() {
            return new DownloadEpisode(this);
        }
    }
}
