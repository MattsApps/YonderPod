package de.danoeh.antennapod.net.download.serviceinterface;

import android.content.Context;

import java.util.concurrent.Future;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

public abstract class AutoDownloadManager {
    private static AutoDownloadManager instance;

    public static AutoDownloadManager getInstance() {
        return instance;
    }

    public static void setInstance(AutoDownloadManager instance) {
        AutoDownloadManager.instance = instance;
    }

    /**
     * Looks for non-downloaded episodes in the queue or list of unread items and request a download if
     * 1. Network is available
     * 2. The device is charging or the user allows auto download on battery
     * 3. There is free space in the episode cache
     * This method is executed on an internal single thread executor.
     *
     * @param context  Used for accessing the DB.
     * @return A Future that can be used for waiting for the methods completion.
     */
    public abstract Future<?> autodownloadUndownloadedItems(final Context context);

    /**
     * Removed downloaded episodes outside of the queue if the episode cache is full. Episodes with a smaller
     * 'lastPlayedTimeHistory'-value will be deleted first.
     * <p/>
     * This method should NOT be executed on the GUI thread.
     *
     * @param context Used for accessing the DB.
     */
    public abstract void performAutoCleanup(final Context context);

    public Future<?> autodownloadAfterRefresh(Context context, long[] refreshedFeedIds, boolean scheduled,
                                              boolean allFeeds, boolean allowMobile) {
        return autodownloadUndownloadedItems(context);
    }

    public static boolean mayAutoDelete(FeedItem item) {
        return instance != null ? instance.mayDelete(item) : isSafeToDelete(item);
    }

    public boolean mayDelete(FeedItem item) {
        return isSafeToDelete(item);
    }

    public boolean isDeletionAllowed(long feedId) {
        return true;
    }

    public static boolean isSafeToDelete(FeedItem item) {
        FeedMedia media = item.getMedia();
        boolean halfPlayed = media != null && media.getPosition() > 0 && !item.isPlayed();
        return !halfPlayed && !item.isTagged(FeedItem.TAG_FAVORITE) && !item.isTagged(FeedItem.TAG_QUEUE);
    }
}
