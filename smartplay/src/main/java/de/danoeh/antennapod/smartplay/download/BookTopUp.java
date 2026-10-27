package de.danoeh.antennapod.smartplay.download;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;

public final class BookTopUp {
    public static final int DEFAULT_KEEP_AHEAD = 3;
    public static final int MAX_KEEP_AHEAD = 10;

    private static final Comparator<DownloadEpisode> BOOK_ORDER =
            Comparator.comparingLong(DownloadEpisode::getPubDate).thenComparingLong(DownloadEpisode::getId);

    public static final class CategorySchedule {
        final CategoryScheduleMode mode;
        final boolean wifiOnly;

        public CategorySchedule(CategoryScheduleMode mode, boolean wifiOnly) {
            this.mode = mode;
            this.wifiOnly = wifiOnly;
        }
    }

    private BookTopUp() {
    }

    public static List<DownloadEpisode> chaptersToDownload(List<DownloadEpisode> chapters, int keepAhead) {
        List<DownloadEpisode> sorted = new ArrayList<>(chapters);
        sorted.sort(BOOK_ORDER);
        List<DownloadEpisode> result = new ArrayList<>();
        int ahead = 0;
        for (DownloadEpisode chapter : sorted) {
            if (ahead >= keepAhead) {
                break;
            }
            if (chapter.isPlayed()) {
                continue;
            }
            ahead++;
            if (!chapter.isDownloaded() && chapter.isDownloadable()) {
                result.add(chapter);
            }
        }
        return result;
    }

    public static boolean mayUseMobileData(boolean defaultWifiOnly, List<CategorySchedule> categories) {
        if (categories.isEmpty()) {
            return !defaultWifiOnly;
        }
        for (CategorySchedule category : categories) {
            switch (category.mode) {
                case OWN:
                    if (!category.wifiOnly) {
                        return true;
                    }
                    break;
                case DEFAULT:
                    if (!defaultWifiOnly) {
                        return true;
                    }
                    break;
                case MANUAL:
                default:
                    break;
            }
        }
        return false;
    }

    public static int clampKeepAhead(int keepAhead) {
        return Math.max(1, Math.min(MAX_KEEP_AHEAD, keepAhead));
    }
}
