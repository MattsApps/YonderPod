package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;

public final class Radio {
    private static final String TAG = "Radio";

    private Radio() {
    }

    public static boolean isRadioFeed(Feed feed) {
        return feed != null && Feed.RADIO_DOWNLOAD_URL.equals(feed.getDownloadUrl());
    }

    public static synchronized Feed radioFeed(Context context) {
        for (Feed feed : DBReader.getFeedList()) {
            if (isRadioFeed(feed)) {
                return withItems(feed.getId());
            }
        }
        Feed feed = new Feed(Feed.RADIO_DOWNLOAD_URL, null, context.getString(R.string.radio_title));
        feed.setState(Feed.STATE_NOT_SUBSCRIBED);
        feed.setItems(new ArrayList<>());
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
        FeedPreferences preferences = saved.getPreferences();
        if (preferences != null) {
            preferences.setKeepUpdated(false);
            try {
                DBWriter.setFeedPreferences(preferences).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not save preferences", e);
            }
        }
        return withItems(saved.getId());
    }

    private static Feed withItems(long feedId) {
        Feed full = DBReader.getFeed(feedId, false, 0, Integer.MAX_VALUE);
        if (full.getItems() == null) {
            full.setItems(new ArrayList<>());
        }
        return full;
    }

    public static List<FeedItem> stations(Context context) {
        List<FeedItem> result = new ArrayList<>();
        for (FeedItem item : radioFeed(context).getItems()) {
            if (item.getMedia() != null) {
                result.add(item);
            }
        }
        Collections.sort(result, (a, b) -> a.getTitle().toLowerCase(Locale.getDefault())
                .compareTo(b.getTitle().toLowerCase(Locale.getDefault())));
        return result;
    }

    public static synchronized FeedItem add(Context context, String name, String streamUrl, String mimeType,
                                            String homepage, String logoUrl, RadioPreferences.Station details) {
        Feed feed = radioFeed(context);
        for (FeedItem existing : feed.getItems()) {
            if (existing.getMedia() != null && streamUrl.equals(existing.getMedia().getDownloadUrl())) {
                return existing;
            }
        }
        String guid = Feed.RADIO_GUID_PREFIX + UUID.randomUUID();
        FeedItem item = new FeedItem(0, name, guid, homepage, new Date(), FeedItem.UNPLAYED, feed);
        item.disableAutoDownload();
        if (logoUrl != null && !logoUrl.isEmpty()) {
            item.setImageUrl(logoUrl);
        }
        FeedMedia media = new FeedMedia(0, item, 0, 0, 0, mimeType, null, streamUrl, 0, null, 0, 0);
        item.setMedia(media);
        feed.getItems().add(item);
        FeedDatabaseWriter.updateFeed(context, feed, false);
        FeedItem saved = DBReader.getFeedItemByGuidOrEpisodeUrl(guid, streamUrl);
        if (saved != null) {
            RadioPreferences.set(saved.getId(), details);
            return saved;
        }
        return item;
    }

    public static void update(FeedItem station, String name, String logoUrl, RadioPreferences.Quality quality) {
        try {
            station.setTitle(name);
            station.setImageUrl(logoUrl == null || logoUrl.isEmpty() ? null : logoUrl);
            DBWriter.setFeedItem(station, false).get();
            RadioPreferences.set(station.getId(), RadioPreferences.get(station.getId()).withQuality(quality));
        } catch (Exception e) {
            Log.e(TAG, "Could not save station", e);
        }
    }

    public static boolean delete(Context context, FeedItem station) {
        if (isPlaying(station)) {
            return false;
        }
        try {
            DBWriter.deleteFeedItems(context, Collections.singletonList(station)).get();
            RadioPreferences.remove(station.getId());
        } catch (Exception e) {
            Log.e(TAG, "Could not delete station", e);
        }
        return true;
    }

    public static boolean isPlaying(FeedItem station) {
        return station.getMedia() != null
                && PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == station.getMedia().getId();
    }
}
