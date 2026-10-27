package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.download.BookTopUp;
import de.danoeh.antennapod.smartplay.download.DownloadEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

public final class Books {
    private static final String TAG = "Books";
    public static final String CATEGORY = "Audiobooks";
    public static final int CHAPTERS_IN_LIST = 3;

    public static final class Progress {
        public final int chapter;
        public final int chapters;
        public final long millisLeft;
        public final FeedItem next;

        Progress(int chapter, int chapters, long millisLeft, FeedItem next) {
            this.chapter = chapter;
            this.chapters = chapters;
            this.millisLeft = millisLeft;
            this.next = next;
        }

        public boolean isFinished() {
            return next == null;
        }
    }

    private Books() {
    }

    public static boolean looksLikeLibriVox(Feed feed) {
        return feed.getDownloadUrl() != null && feed.getDownloadUrl().contains("librivox.org");
    }

    private static boolean inAudiobookCategory(Feed feed) {
        if (feed.getPreferences() == null) {
            return false;
        }
        for (String tag : feed.getPreferences().getTags()) {
            if (CATEGORY.equalsIgnoreCase(tag) || "Audiobook".equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    public static Set<Long> bookIds(Context context) {
        Set<Long> result = new HashSet<>();
        for (Map.Entry<Long, SmartPlayStore.BookState> entry
                : SmartPlayStore.getInstance(context).getBookStates().entrySet()) {
            if (entry.getValue().isBook) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static boolean isBook(Context context, long feedId) {
        SmartPlayStore.BookState state = SmartPlayStore.getInstance(context).getBookStates().get(feedId);
        return state != null && state.isBook;
    }

    public static boolean isFinished(Context context, long feedId) {
        SmartPlayStore.BookState state = SmartPlayStore.getInstance(context).getBookStates().get(feedId);
        return state != null && state.isBook && state.finished;
    }

    public static void detectNewBooks(Context context, List<Feed> feeds) {
        Map<Long, SmartPlayStore.BookState> states = SmartPlayStore.getInstance(context).getBookStates();
        for (Feed feed : feeds) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED && !states.containsKey(feed.getId())
                    && (looksLikeLibriVox(feed) || inAudiobookCategory(feed))) {
                setBook(context, feed, true);
            }
        }
    }

    public static void setBook(Context context, Feed feed, boolean book) {
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        store.setBookState(feed.getId(), book, false);
        FeedPreferences preferences = feed.getPreferences();
        try {
            if (preferences != null) {
                if (book) {
                    preferences.getTags().add(CATEGORY);
                } else {
                    preferences.getTags().remove(CATEGORY);
                }
                DBWriter.setFeedPreferences(preferences).get();
            }
            if (book && !feed.isLocalFeed()) {
                DBWriter.setFeedItemSortOrder(feed.getId(), SortOrder.DATE_OLD_NEW).get();
                applyDownloadSettings(context, feed.getId(), keepAhead(context, feed.getId()));
                BookTopUpWorker.request(context, feed.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not mark book", e);
        }
    }

    public static int keepAhead(Context context, long feedId) {
        SmartPlayStore.BookState state = SmartPlayStore.getInstance(context).getBookStates().get(feedId);
        return state != null ? state.keepAhead : BookTopUp.DEFAULT_KEEP_AHEAD;
    }

    public static void setKeepAhead(Context context, Feed feed, int keepAhead) {
        SmartPlayStore.BookState state = SmartPlayStore.getInstance(context).getBookStates().get(feed.getId());
        SmartPlayStore.getInstance(context).setBookState(feed.getId(), true,
                state != null && state.finished, keepAhead);
        if (!feed.isLocalFeed()) {
            applyDownloadSettings(context, feed.getId(), BookTopUp.clampKeepAhead(keepAhead));
            BookTopUpWorker.request(context, feed.getId());
        }
    }

    private static void applyDownloadSettings(Context context, long feedId, int keepAhead) {
        DownloadSettingsRepository.getInstance(context).set(feedId, new ShowDownloadSettings(
                DownloadMode.IN_ORDER, keepAhead, keepAhead, ShowDownloadSettings.NO_LIMIT, true));
    }

    public static boolean mayUseMobileData(Context context, Feed feed) {
        List<BookTopUp.CategorySchedule> categories = new ArrayList<>();
        if (feed.getPreferences() != null) {
            for (String tag : feed.getPreferences().getTags()) {
                if (FeedPreferences.TAG_ROOT.equals(tag)) {
                    continue;
                }
                SmartPlayStore.StoredSchedule stored = UpdateScheduler.getCategory(context, tag);
                categories.add(new BookTopUp.CategorySchedule(stored.getMode(), stored.getSchedule().isWifiOnly()));
            }
        }
        return BookTopUp.mayUseMobileData(UpdateScheduler.getDefault(context).getSchedule().isWifiOnly(),
                categories);
    }

    public static void topUp(Context context, long feedId) {
        Feed feed = DBReader.getFeed(feedId, false, 0, 0);
        if (feed == null || feed.isLocalFeed() || feed.getState() != Feed.STATE_SUBSCRIBED
                || !isBook(context, feedId)) {
            return;
        }
        Map<Long, FeedItem> byId = new HashMap<>();
        List<DownloadEpisode> chapters = new ArrayList<>();
        long order = 0;
        for (FeedItem item : chaptersInOrder(context, feed)) {
            FeedMedia media = item.getMedia();
            if (media == null) {
                continue;
            }
            byId.put(item.getId(), item);
            chapters.add(DownloadEpisode.builder(item.getId(), order++)
                    .downloaded(media.isDownloaded())
                    .played(item.isPlayed())
                    .downloadable(!DownloadServiceInterface.get().isDownloadingEpisode(media.getDownloadUrl()))
                    .build());
        }
        for (DownloadEpisode chapter : BookTopUp.chaptersToDownload(chapters, keepAhead(context, feedId))) {
            FeedItem item = byId.get(chapter.getId());
            if (item != null) {
                DownloadServiceInterface.get().downloadNow(context, item, true);
            }
        }
    }

    public static List<FeedItem> chaptersInOrder(Context context, Feed feed) {
        List<FeedItem> items = new ArrayList<>(DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE));
        Map<Long, Long> order = feed.isLocalFeed() ? LocalFolders.playOrderDates(context, feed) : new HashMap<>();
        items.sort(Comparator.<FeedItem>comparingLong(item -> {
            Long date = order.get(item.getId());
            if (date != null) {
                return date;
            }
            return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
        }).thenComparingLong(FeedItem::getId));
        return items;
    }

    public static Progress progress(Context context, Feed feed) {
        List<FeedItem> chapters = chaptersInOrder(context, feed);
        FeedItem next = null;
        int nextIndex = -1;
        long latestPlayed = 0;
        long millisLeft = 0;
        for (int i = 0; i < chapters.size(); i++) {
            FeedItem item = chapters.get(i);
            FeedMedia media = item.getMedia();
            if (item.isPlayed() || media == null) {
                continue;
            }
            millisLeft += Math.max(0, media.getDuration() - media.getPosition());
            if (next == null) {
                next = item;
                nextIndex = i;
            }
            if (media.getPosition() > 0 && media.getLastPlayedTimeStatistics() > latestPlayed) {
                latestPlayed = media.getLastPlayedTimeStatistics();
                next = item;
                nextIndex = i;
            }
        }
        return new Progress(nextIndex + 1, chapters.size(), millisLeft, next);
    }

    public static SmartPlayDefinition bookSmartPlay(Feed feed) {
        return SmartPlayDefinition.builder(feed.getTitle())
                .rule(SmartPlayRule.of(SmartPlayRule.Order.NEXT_CHAPTER, CHAPTERS_IN_LIST,
                        EpisodeSource.feed(feed.getId())))
                .build();
    }

    public static long findBookSmartPlay(Context context, Feed feed) {
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            SmartPlayDefinition definition = stored.getDefinition();
            if (!definition.isBookList() || !definition.getName().equals(feed.getTitle())) {
                continue;
            }
            boolean onlyThisBook = true;
            for (SmartPlayRule rule : definition.getRules()) {
                if (rule.getSource().getType() != EpisodeSource.Type.FEED
                        || rule.getSource().getFeedId() != feed.getId()) {
                    onlyThisBook = false;
                }
            }
            if (onlyThisBook) {
                return stored.getId();
            }
        }
        return -1;
    }

    public static void listen(Context context, Feed feed) {
        long id = findBookSmartPlay(context, feed);
        if (id < 0) {
            id = SmartPlayStore.getInstance(context).insert(bookSmartPlay(feed));
        }
        Progress progress = progress(context, feed);
        BookTopUpWorker.request(context, feed.getId());
        SmartPlayQueueManager.getInstance().activate(id,
                progress.next != null ? progress.next.getMedia() : null);
    }

    public static void markFinished(Context context, Feed feed) {
        try {
            List<FeedItem> chapters = chaptersInOrder(context, feed);
            DBWriter.markItemsPlayed(FeedItem.PLAYED, true, chapters).get();
            SmartPlayStore.getInstance(context).setBookState(feed.getId(), true, true);
        } catch (Exception e) {
            Log.e(TAG, "Could not mark book finished", e);
        }
    }

    public static void deleteBookSmartPlay(Context context, long id) {
        SmartPlayStore.getInstance(context).delete(id);
        SmartPlayQueueManager.getInstance().onSmartPlaysChanged();
        if (SmartPlayQueueManager.getInstance().getActiveId() == id) {
            SmartPlayQueueManager.getInstance().rebuildNow();
        }
    }

    public static Map<Long, Long> lastPlayedPerBook(Context context, Set<Long> books) {
        Map<Long, Long> result = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (!books.contains(feed.getId())) {
                continue;
            }
            long latest = 0;
            for (FeedItem item : DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_OLD_NEW, 0, Integer.MAX_VALUE)) {
                if (item.getMedia() != null) {
                    latest = Math.max(latest, item.getMedia().getLastPlayedTimeStatistics());
                }
            }
            result.put(feed.getId(), latest);
        }
        return result;
    }
}
