package de.danoeh.antennapod.smartplay.moving;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;

public final class BeyondPodMapping {
    private static final int NEVER_DAYS = 99999;
    private static final String UNCATEGORIZED = "uncategorized";

    private BeyondPodMapping() {
    }

    public static DownloadMode mode(Integer value) {
        if (value == null) {
            return DownloadMode.LATEST;
        }
        switch (value) {
            case 0:
                return DownloadMode.MANUAL;
            case 2:
                return DownloadMode.STREAM_LATEST;
            case 3:
                return DownloadMode.STREAM_IN_ORDER;
            case 4:
                return DownloadMode.IN_ORDER;
            case 5:
                return DownloadMode.IGNORE;
            case 1:
            default:
                return DownloadMode.LATEST;
        }
    }

    public static ShowDownloadSettings settings(Integer mode, Integer download, Integer keep, Integer maxAgeDays,
                                                ShowDownloadSettings defaults) {
        int count = download != null && download > 0 ? download : defaults.getEpisodeCount();
        int keepAtMost = keep != null && keep > 0 ? keep : defaults.getKeepAtMost();
        int age = maxAgeDays == null || maxAgeDays <= 0 || maxAgeDays >= NEVER_DAYS
                ? defaults.getDeleteOlderThanDays() : maxAgeDays;
        return new ShowDownloadSettings(mode(mode), count, keepAtMost, age, defaults.isAllowDeletions());
    }

    public static ShowDownloadSettings defaults(String download, String keep, String maxAgeDays,
                                                ShowDownloadSettings fallback) {
        int count = number(download, fallback.getEpisodeCount());
        int keepAtMost = number(keep, fallback.getKeepAtMost());
        int age = number(maxAgeDays, 0);
        return new ShowDownloadSettings(fallback.getMode(), count, keepAtMost,
                age <= 0 || age >= NEVER_DAYS ? ShowDownloadSettings.NO_LIMIT : age, fallback.isAllowDeletions());
    }

    public static List<String> categories(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split("\\|")) {
            String name = part.replace("^0", "").trim();
            if (name.isEmpty() || name.toLowerCase(Locale.ROOT).equals(UNCATEGORIZED)) {
                continue;
            }
            boolean seen = false;
            for (String existing : result) {
                seen |= existing.equalsIgnoreCase(name);
            }
            if (!seen) {
                result.add(name);
            }
        }
        return result;
    }

    public static SmartPlayRule.Order order(Integer episodeFilter) {
        if (episodeFilter != null && episodeFilter == -1) {
            return SmartPlayRule.Order.OLDEST;
        }
        if (episodeFilter != null && episodeFilter == -3) {
            return SmartPlayRule.Order.RANDOM;
        }
        return SmartPlayRule.Order.LATEST;
    }

    public static int count(Integer numEpisodes) {
        return numEpisodes != null && numEpisodes > 0 ? numEpisodes : 1;
    }

    public static boolean isAllFeeds(String feedId, String category) {
        return (feedId == null || feedId.isEmpty())
                && (category == null || category.isEmpty() || categories(category).isEmpty());
    }

    public static int minuteOfDay(long startTime, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(startTime);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    public static int number(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean isPrivate(String url, String username) {
        if (username != null && !username.isEmpty()) {
            return true;
        }
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        return lower.contains("patreon.com") || lower.contains("token=") || lower.contains("auth=")
                || lower.contains("key=") || lower.matches("^[a-z]+://[^/]*@.*");
    }
}
