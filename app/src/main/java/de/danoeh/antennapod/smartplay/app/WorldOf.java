package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

/**
 * Which world something belongs to (1.0.2: each world offers its own). Live radio is Radio. Books, own-audio
 * folders and Added files are Files while the Files world is shown (with it hidden they join Podcasts, as the
 * Podcasts screen already did for books). Everything else is Podcasts, including radio recordings, which play
 * from Smart Plays like episodes. Runs off the main thread: it may read the database.
 */
final class WorldOf {
    static final int NONE = -1;
    /** How far back to look for the last podcast played. */
    private static final int RECENT = 40;

    private WorldOf() {
    }

    /** Book ids when the Files world is shown, otherwise none (books then count as podcasts). */
    static Set<Long> filesBooks(Context context) {
        return UserPreferences.isWorldShown(WorldPages.BOOKS) ? Books.bookIds(context) : new HashSet<>();
    }

    static int of(@Nullable FeedMedia media, Set<Long> books) {
        if (media == null) {
            return NONE;
        }
        if (media.isLiveRadio()) {
            return WorldSurface.RADIO;
        }
        FeedItem item = media.getItem();
        if (item != null && UserPreferences.isWorldShown(WorldPages.BOOKS)) {
            Feed feed = item.getFeed();
            if (books.contains(item.getFeedId())
                    || (feed != null && (LocalFolders.isAddedFiles(feed)
                    || (feed.isLocalFeed() && !Recordings.isRecordingsFeed(feed))))) {
                return WorldSurface.BOOKS;
            }
        }
        return WorldSurface.PODCASTS;
    }

    /** The world of what is playing right now (not paused), or NONE. */
    static int playing(Context context) {
        if (!PlaybackService.isRunning
                || PlaybackPreferences.getCurrentPlayerStatus() != PlaybackPreferences.PLAYER_STATUS_PLAYING) {
            return NONE;
        }
        long id = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        if (id == PlaybackPreferences.NO_MEDIA_PLAYING) {
            return NONE;
        }
        return of(DBReader.getFeedMedia(id), filesBooks(context));
    }

    /**
     * The podcast played most recently, if it can be resumed (not finished, show still followed); null if the
     * latest podcast was finished or none has been played, so the caller falls back to what play would play.
     */
    @Nullable
    static FeedMedia lastPodcast(Set<Long> books) {
        List<FeedItem> recent = DBReader.getRecentlyPlayed(RECENT);
        for (FeedItem item : recent) {
            FeedMedia media = item.getMedia();
            if (media == null || of(media, books) != WorldSurface.PODCASTS) {
                continue;
            }
            Feed feed = item.getFeed();
            boolean followed = feed != null && feed.getState() == Feed.STATE_SUBSCRIBED;
            return !item.isPlayed() && followed ? media : null;
        }
        return null;
    }
}
