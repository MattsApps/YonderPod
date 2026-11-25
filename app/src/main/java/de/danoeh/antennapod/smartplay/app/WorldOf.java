package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
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
        if (item != null && isRadioRecording(media) && UserPreferences.isWorldShown(WorldPages.RADIO)) {
            return WorldSurface.RADIO;
        }
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

    /** 1.0.5: a recording started from the Radio screen (or its extra part) is Radio, not Podcasts. */
    static boolean isRadioRecording(FeedMedia media) {
        if (NextUpPreferences.getWorld() != NextUpPreferences.WORLD_RADIO || media.getItem() == null) {
            return false;
        }
        return media.getId() == NextUpPreferences.getWorldStart()
                || NextUpPreferences.getDetourKept().contains(media.getItemId());
    }

    /** 1.0.5: what carries on in Radio (the next ready recording, then the station live) or among added files. */
    @Nullable
    static FeedItem next(Context context, FeedItem finished, int world) {
        List<FeedItem> candidates = new ArrayList<>();
        if (world == NextUpPreferences.WORLD_RADIO) {
            for (RecordingStore.Show show : RecordingStore.getInstance(context).getShows()) {
                Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
                if (feed == null) {
                    continue;
                }
                for (FeedItem item : DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.UNPLAYED),
                        SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE)) {
                    if (item.getItemIdentifier() == null || !Recordings.isTailGuid(item.getItemIdentifier())) {
                        candidates.add(item);
                    }
                }
            }
            Collections.sort(candidates, (a, b) -> Long.compare(time(b), time(a)));
        } else if (world == NextUpPreferences.WORLD_ADDED_FILES) {
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() == Feed.STATE_SUBSCRIBED && LocalFolders.isAddedFiles(feed)) {
                    candidates.addAll(DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                            SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE));
                }
            }
        }
        int at = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).getId() == finished.getId()) {
                at = i;
            }
        }
        for (int step = 1; step <= candidates.size(); step++) {
            FeedItem item = candidates.get(Math.floorMod(at + step, candidates.size()));
            if (item.getId() != finished.getId() && item.getMedia() != null && !item.isPlayed()) {
                return item;
            }
        }
        if (world == NextUpPreferences.WORLD_RADIO) {
            // No more ready recordings: we're in Radio, so the station the last one came from plays live.
            RecordingStore.Show show = RecordingStore.getInstance(context).showForFeed(finished.getFeedId());
            FeedItem station = show != null ? DBReader.getFeedItem(show.stationItemId) : null;
            if (station != null && station.getMedia() != null) {
                Radio.setLastStation(context, station.getId());
                return station;
            }
        }
        return null;
    }

    private static long time(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
    }
}
