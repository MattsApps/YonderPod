package de.danoeh.antennapod.smartplay.download;

import java.util.Collections;
import java.util.List;

public final class DownloadPlan {
    private final List<DownloadEpisode> toDownload;
    private final List<DownloadEpisode> toDelete;

    DownloadPlan(List<DownloadEpisode> toDownload, List<DownloadEpisode> toDelete) {
        this.toDownload = Collections.unmodifiableList(toDownload);
        this.toDelete = Collections.unmodifiableList(toDelete);
    }

    public List<DownloadEpisode> getToDownload() {
        return toDownload;
    }

    public List<DownloadEpisode> getToDelete() {
        return toDelete;
    }
}
