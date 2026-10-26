package de.danoeh.antennapod.smartplay.download;

public final class ShowDownloadSettings {
    public static final int NO_LIMIT = 0;

    private final DownloadMode mode;
    private final int episodeCount;
    private final int keepAtMost;
    private final int deleteOlderThanDays;
    private final boolean allowDeletions;

    public ShowDownloadSettings(DownloadMode mode, int episodeCount, int keepAtMost,
                                int deleteOlderThanDays, boolean allowDeletions) {
        this.mode = mode;
        this.episodeCount = Math.max(1, episodeCount);
        this.keepAtMost = Math.max(NO_LIMIT, keepAtMost);
        this.deleteOlderThanDays = Math.max(NO_LIMIT, deleteOlderThanDays);
        this.allowDeletions = allowDeletions;
    }

    public static ShowDownloadSettings beyondPodDefaults() {
        return new ShowDownloadSettings(DownloadMode.LATEST, 1, 5, NO_LIMIT, true);
    }

    public DownloadMode getMode() {
        return mode;
    }

    public int getEpisodeCount() {
        return episodeCount;
    }

    public int getKeepAtMost() {
        return keepAtMost;
    }

    public int getDeleteOlderThanDays() {
        return deleteOlderThanDays;
    }

    public boolean isAllowDeletions() {
        return allowDeletions;
    }
}
