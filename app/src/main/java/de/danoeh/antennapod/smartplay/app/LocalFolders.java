package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.NaturalOrder;

public final class LocalFolders {
    private static final String TAG = "LocalFolders";
    private static final long STEP_MS = 60 * 1000;
    public static final String ADDED_FILES_URL = Feed.ADDED_FILES_DOWNLOAD_URL;

    public enum PlayOrder {
        FILE_NAME,
        TRACK,
        OLDEST,
        NEWEST
    }

    private LocalFolders() {
    }

    public static boolean isAddedFiles(Feed feed) {
        return feed != null && ADDED_FILES_URL.equals(feed.getDownloadUrl());
    }

    public static PlayOrder getPlayOrder(Context context, long feedId) {
        String stored = SmartPlayStore.getInstance(context).getPlayOrders().get(feedId);
        if (stored != null) {
            try {
                return PlayOrder.valueOf(stored);
            } catch (IllegalArgumentException e) {
                return PlayOrder.FILE_NAME;
            }
        }
        return PlayOrder.FILE_NAME;
    }

    public static void setPlayOrder(Context context, long feedId, PlayOrder order) {
        SmartPlayStore.getInstance(context).setPlayOrder(feedId, order.name());
        SortOrder sortOrder;
        switch (order) {
            case OLDEST:
                sortOrder = SortOrder.DATE_OLD_NEW;
                break;
            case NEWEST:
                sortOrder = SortOrder.DATE_NEW_OLD;
                break;
            case FILE_NAME:
            case TRACK:
            default:
                sortOrder = SortOrder.EPISODE_FILENAME_A_Z;
                break;
        }
        DBWriter.setFeedItemSortOrder(feedId, sortOrder);
    }

    public static void ensureDefaults(Context context, Feed feed) {
        if (feed.isLocalFeed() && !SmartPlayStore.getInstance(context).getPlayOrders().containsKey(feed.getId())) {
            setPlayOrder(context, feed.getId(), PlayOrder.FILE_NAME);
        }
    }

    public static Map<Long, Long> playOrderDates(Context context, Feed feed) {
        Map<Long, Long> result = new HashMap<>();
        PlayOrder order = getPlayOrder(context, feed.getId());
        if (order != PlayOrder.FILE_NAME && order != PlayOrder.TRACK) {
            return result;
        }
        List<FeedItem> items = new ArrayList<>(DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE));
        if (items.isEmpty()) {
            return result;
        }
        Comparator<FeedItem> byName = (a, b) -> NaturalOrder.INSTANCE.compare(a.getLink(), b.getLink());
        if (order == PlayOrder.TRACK) {
            Map<Long, Integer> tracks = new HashMap<>();
            for (FeedItem item : items) {
                tracks.put(item.getId(), track(context, item));
            }
            items.sort(Comparator.<FeedItem>comparingInt(item -> tracks.get(item.getId())).thenComparing(byName));
        } else {
            items.sort(byName);
        }
        long base = Long.MAX_VALUE;
        for (FeedItem item : items) {
            Date pubDate = item.getPubDate();
            base = Math.min(base, pubDate != null ? pubDate.getTime() : 0);
        }
        for (int i = 0; i < items.size(); i++) {
            result.put(items.get(i).getId(), base + i * STEP_MS);
        }
        return result;
    }

    private static int track(Context context, FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || media.getDownloadUrl() == null) {
            return Integer.MAX_VALUE;
        }
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        Integer cached = store.getTrack(item.getId(), media.getSize());
        if (cached != null) {
            return cached;
        }
        int track = Integer.MAX_VALUE;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.parse(media.getDownloadUrl()));
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            if (value != null) {
                String number = value.split("/")[0].trim();
                track = Integer.parseInt(number);
            }
        } catch (Exception e) {
            Log.d(TAG, "No track number for " + item.getLink());
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
                Log.d(TAG, "Could not release retriever");
            }
        }
        store.setTrack(item.getId(), media.getSize(), track);
        return track;
    }
}
