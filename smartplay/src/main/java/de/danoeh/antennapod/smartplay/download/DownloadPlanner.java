package de.danoeh.antennapod.smartplay.download;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DownloadPlanner {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final Comparator<DownloadEpisode> OLDEST_FIRST =
            Comparator.comparingLong(DownloadEpisode::getPubDate).thenComparingLong(DownloadEpisode::getId);
    private static final Comparator<DownloadEpisode> NEWEST_FIRST = OLDEST_FIRST.reversed();

    private DownloadPlanner() {
    }

    public static DownloadPlan plan(ShowDownloadSettings settings, List<DownloadEpisode> episodes, long now) {
        List<DownloadEpisode> toDelete = new ArrayList<>();
        Set<Long> deleted = new HashSet<>();
        long cutoff = settings.getDeleteOlderThanDays() > 0
                ? now - settings.getDeleteOlderThanDays() * DAY_MS : Long.MIN_VALUE;

        for (DownloadEpisode episode : episodes) {
            if (episode.isDownloaded() && episode.getPubDate() < cutoff && mayDelete(settings, episode)) {
                toDelete.add(episode);
                deleted.add(episode.getId());
            }
        }

        List<DownloadEpisode> wanted = wanted(settings, episodes, cutoff);

        if (settings.getKeepAtMost() > 0) {
            List<DownloadEpisode> remaining = new ArrayList<>();
            for (DownloadEpisode episode : episodes) {
                if (episode.isDownloaded() && !deleted.contains(episode.getId())) {
                    remaining.add(episode);
                }
            }
            int excess = remaining.size() + wanted.size() - settings.getKeepAtMost();
            List<DownloadEpisode> played = new ArrayList<>();
            List<DownloadEpisode> unplayed = new ArrayList<>();
            for (DownloadEpisode episode : remaining) {
                if (mayDelete(settings, episode)) {
                    if (episode.isPlayed()) {
                        played.add(episode);
                    } else {
                        unplayed.add(episode);
                    }
                }
            }
            played.sort(OLDEST_FIRST);
            unplayed.sort(settings.getMode() == DownloadMode.IN_ORDER ? NEWEST_FIRST : OLDEST_FIRST);

            excess -= take(played, excess, toDelete);
            if (settings.getMode() == DownloadMode.LATEST) {
                excess -= take(unplayed, excess, toDelete);
                excess -= trim(wanted, excess);
            } else {
                excess -= trim(wanted, excess);
                take(unplayed, excess, toDelete);
            }
        }
        return new DownloadPlan(wanted, toDelete);
    }

    public static List<DownloadEpisode> streamable(ShowDownloadSettings settings, List<DownloadEpisode> episodes) {
        List<DownloadEpisode> result = new ArrayList<>();
        if (!settings.getMode().streams()) {
            return result;
        }
        List<DownloadEpisode> sorted = new ArrayList<>(episodes);
        sorted.sort(settings.getMode() == DownloadMode.STREAM_LATEST ? NEWEST_FIRST : OLDEST_FIRST);
        for (DownloadEpisode episode : sorted) {
            if (result.size() >= settings.getEpisodeCount()) {
                break;
            }
            if (!episode.isPlayed()) {
                result.add(episode);
            }
        }
        return result;
    }

    public static boolean mayDelete(ShowDownloadSettings settings, DownloadEpisode episode) {
        return settings.isAllowDeletions() && !episode.isProtected();
    }

    private static List<DownloadEpisode> wanted(ShowDownloadSettings settings, List<DownloadEpisode> episodes,
                                                long cutoff) {
        List<DownloadEpisode> result = new ArrayList<>();
        int count = settings.getEpisodeCount();
        List<DownloadEpisode> sorted = new ArrayList<>(episodes);
        switch (settings.getMode()) {
            case LATEST:
                sorted.sort(NEWEST_FIRST);
                for (int i = 0; i < sorted.size() && i < count; i++) {
                    if (isWanted(sorted.get(i), cutoff)) {
                        result.add(sorted.get(i));
                    }
                }
                return result;
            case IN_ORDER:
                sorted.sort(OLDEST_FIRST);
                break;
            case BACKWARDS:
                sorted.sort(NEWEST_FIRST);
                break;
            default:
                return result;
        }
        for (DownloadEpisode episode : sorted) {
            if (result.size() >= count) {
                break;
            }
            if (isWanted(episode, cutoff)) {
                result.add(episode);
            }
        }
        return result;
    }

    private static boolean isWanted(DownloadEpisode episode, long cutoff) {
        return !episode.isDownloaded() && !episode.isPlayed() && !episode.isInHistory()
                && episode.isDownloadable() && episode.getPubDate() >= cutoff;
    }

    private static int take(List<DownloadEpisode> from, int count, List<DownloadEpisode> into) {
        int taken = 0;
        while (taken < count && !from.isEmpty()) {
            into.add(from.remove(0));
            taken++;
        }
        return taken;
    }

    private static int trim(List<DownloadEpisode> wanted, int count) {
        int trimmed = 0;
        while (trimmed < count && !wanted.isEmpty()) {
            wanted.remove(wanted.size() - 1);
            trimmed++;
        }
        return trimmed;
    }
}
