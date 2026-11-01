package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;

public final class Follow {
    private Follow() {
    }

    public static Set<String> subscribedUrls() {
        Set<String> urls = new HashSet<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED && feed.getDownloadUrl() != null) {
                urls.add(feed.getDownloadUrl());
            }
        }
        return urls;
    }

    public static Set<String> subscribedTitles() {
        Set<String> titles = new HashSet<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED && feed.getTitle() != null) {
                titles.add(feed.getTitle().trim());
            }
        }
        return titles;
    }

    public static Feed subscribe(Context context, String url, String title, @Nullable String imageUrl)
            throws Exception {
        for (Feed feed : DBReader.getFeedList()) {
            if (url.equals(feed.getDownloadUrl())) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED) {
                    DBWriter.setFeedState(context, feed, Feed.STATE_SUBSCRIBED).get();
                    FeedUpdateManager.getInstance().runOnce(context, feed);
                }
                return feed;
            }
        }
        Feed feed = new Feed(url, null, title);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            feed.setImageUrl(imageUrl);
        }
        feed.setItems(new ArrayList<>());
        Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
        CategoryPrompt.request(context, saved.getId());
        FeedUpdateManager.getInstance().runOnce(context, saved);
        return saved;
    }
}
