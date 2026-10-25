package de.danoeh.antennapod.smartplay.app;

import androidx.annotation.Nullable;

import java.util.Date;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.smartplay.SmartPlayEpisode;

public final class SmartPlayEpisodeConverter {

    private SmartPlayEpisodeConverter() {
    }

    @Nullable
    public static SmartPlayEpisode convert(FeedItem item, boolean feedMayStream, boolean mobileStreamingAllowed) {
        FeedMedia media = item.getMedia();
        if (media == null) {
            return null;
        }
        Date pubDate = item.getPubDate();
        Date lastPlayed = media.getLastPlayedTimeHistory();
        SmartPlayEpisode.Builder builder = SmartPlayEpisode.builder(item.getId(), item.getFeedId())
                .pubDate(pubDate != null ? pubDate.getTime() : 0)
                .downloaded(media.isDownloaded())
                .streamableOnMobile(feedMayStream && mobileStreamingAllowed)
                .played(item.isPlayed())
                .position(media.getPosition())
                .lastPlayedTime(lastPlayed != null ? lastPlayed.getTime() : media.getLastPlayedTimeStatistics());
        Feed feed = item.getFeed();
        FeedPreferences preferences = feed != null ? feed.getPreferences() : null;
        if (preferences != null) {
            for (String tag : preferences.getTags()) {
                if (!FeedPreferences.TAG_ROOT.equals(tag)) {
                    builder.category(tag);
                }
            }
        }
        return builder.build();
    }
}
