package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.smartplay.ManualEdits;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayEngine;
import de.danoeh.antennapod.smartplay.SmartPlayEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadPlanner;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class SmartPlayQueueManager {
    private static final String TAG = "SmartPlayQueue";
    private static final long UPDATE_DELAY_MS = 3000;
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    private static final String PREF_SKIP_KEEPS_EPISODE = "prefSkipKeepsEpisode";
    private static final String STATE_PREFS = "SmartPlayState";
    private static final String KEY_ACTIVE_ID = "activeId";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_FEED_QUEUE_ACTION_CLEARED = "feedQueueActionCleared";

    private static SmartPlayQueueManager instance;

    private final Context context;
    private final ManualEditStore editStore;
    private final SmartPlayEngine engine = new SmartPlayEngine();
    private final SmartPlayStore store;
    private final SharedPreferences state;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingUpdate;
    private volatile String activeName = "";

    private SmartPlayQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.editStore = new ManualEditStore(this.context);
        this.store = SmartPlayStore.getInstance(this.context);
        this.state = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void init(Context context) {
        if (instance != null) {
            return;
        }
        instance = new SmartPlayQueueManager(context);
        instance.start();
    }

    public static synchronized SmartPlayQueueManager getInstance() {
        return instance;
    }

    private void start() {
        disableConflictingQueueSettings();
        EventBus.getDefault().register(this);
        executor.execute(this::activeSmartPlay);
        executor.execute(this::clearPerFeedQueueActionOnce);
        scheduleUpdate();
    }

    private void clearPerFeedQueueActionOnce() {
        if (state.getBoolean(KEY_FEED_QUEUE_ACTION_CLEARED, false)) {
            return;
        }
        try {
            for (Feed feed : DBReader.getFeedList()) {
                FeedPreferences preferences = feed.getPreferences();
                if (preferences != null
                        && preferences.getNewEpisodesAction() == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE) {
                    preferences.setNewEpisodesAction(FeedPreferences.NewEpisodesAction.GLOBAL);
                    DBWriter.setFeedPreferences(preferences).get();
                }
            }
            state.edit().putBoolean(KEY_FEED_QUEUE_ACTION_CLEARED, true).apply();
        } catch (Exception e) {
            Log.e(TAG, "Could not switch off per-show add-to-queue", e);
        }
    }

    private void disableConflictingQueueSettings() {
        UserPreferences.setQueueKeepSorted(false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(PREF_ENQUEUE_DOWNLOADED, false)
                .putBoolean(PREF_SKIP_KEEPS_EPISODE, true);
        if (UserPreferences.getNewEpisodesAction() == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE) {
            editor.putString(UserPreferences.PREF_NEW_EPISODES_ACTION,
                    String.valueOf(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX.code));
        }
        editor.apply();
    }

    public void rebuildNow() {
        executor.execute(() -> update(true));
    }

    public String getActiveName() {
        return activeName;
    }

    public long getActiveId() {
        return state.getLong(KEY_ACTIVE_ID, -1);
    }

    public boolean isPaused() {
        return state.getBoolean(KEY_PAUSED, false);
    }

    public void activate(long smartPlayId) {
        executor.execute(() -> {
            state.edit().putLong(KEY_ACTIVE_ID, smartPlayId).apply();
            update(true);
            List<FeedItem> queue = DBReader.getQueue();
            if (!queue.isEmpty() && queue.get(0).getMedia() != null) {
                FeedMedia media = queue.get(0).getMedia();
                new Handler(Looper.getMainLooper()).post(() -> new PlaybackServiceStarter(context, media)
                        .callEvenIfRunning(true)
                        .start());
            }
        });
    }

    public void onSmartPlaysChanged() {
        executor.execute(this::activeSmartPlay);
    }

    public void playNext(FeedItem item) {
        executor.execute(() -> {
            try {
                List<FeedItem> queue = DBReader.getQueue();
                long playingMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
                int existing = -1;
                for (int i = 0; i < queue.size(); i++) {
                    if (queue.get(i).getId() == item.getId()) {
                        existing = i;
                    }
                }
                if (existing >= 0) {
                    queue.remove(existing);
                }
                int target = 0;
                for (int i = 0; i < queue.size(); i++) {
                    FeedMedia media = queue.get(i).getMedia();
                    if (media != null && media.getId() == playingMediaId) {
                        target = i + 1;
                    }
                }
                if (existing >= 0) {
                    editStore.markHandAdded(item.getId());
                    DBWriter.moveQueueItem(existing, target, true).get();
                } else {
                    DBWriter.addQueueItemAt(context, item.getId(), target).get();
                }
            } catch (Exception e) {
                Log.e(TAG, "Play next failed", e);
            }
        });
    }

    private SmartPlayDefinition activeSmartPlay() {
        List<SmartPlayStore.StoredSmartPlay> all = store.getAll();
        if (all.isEmpty()) {
            store.insert(SmartPlayLists.stuff());
            all = store.getAll();
        }
        long activeId = state.getLong(KEY_ACTIVE_ID, -1);
        SmartPlayStore.StoredSmartPlay active = all.get(0);
        for (SmartPlayStore.StoredSmartPlay stored : all) {
            if (stored.getId() == activeId) {
                active = stored;
            }
        }
        if (active.getId() != activeId) {
            state.edit().putLong(KEY_ACTIVE_ID, active.getId()).apply();
        }
        activeName = active.getDefinition().getName();
        return active.getDefinition();
    }

    private synchronized void scheduleUpdate() {
        if (pendingUpdate != null) {
            pendingUpdate.cancel(false);
        }
        pendingUpdate = executor.schedule(() -> update(false), UPDATE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onQueueEvent(QueueEvent event) {
        switch (event.action) {
            case ADDED:
                if (event.item != null) {
                    editStore.markHandAdded(event.item.getId());
                }
                break;
            case REMOVED:
                if (event.item != null) {
                    editStore.markRemoved(event.item.getId());
                }
                scheduleUpdate();
                break;
            case CLEARED:
                editStore.clearHandAdded();
                state.edit().putBoolean(KEY_PAUSED, true).apply();
                break;
            case IRREVERSIBLE_REMOVED:
                scheduleUpdate();
                break;
            default:
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onFeedItemEvent(FeedItemEvent event) {
        for (FeedItem item : event.items) {
            if (item.getMedia() != null && item.getMedia().isDownloaded()) {
                scheduleUpdate();
                return;
            }
        }
    }

    private void update(boolean fullRebuild) {
        if (fullRebuild) {
            state.edit().putBoolean(KEY_PAUSED, false).apply();
        } else if (isPaused()) {
            return;
        }
        try {
            SmartPlayDefinition definition = activeSmartPlay();
            List<FeedItem> queue = DBReader.getQueue();
            Set<Long> streamFeeds = new HashSet<>();
            Map<Long, FeedItem> itemsById = loadCandidates(queue, streamFeeds);
            boolean streamingAllowed = NetworkUtils.isStreamingAllowed();
            Map<Long, Long> playOrderDates = localPlayOrderDates(itemsById);

            List<SmartPlayEpisode> candidates = new ArrayList<>();
            for (FeedItem item : itemsById.values()) {
                SmartPlayEpisode episode = SmartPlayEpisodeConverter.convert(item,
                        streamFeeds.contains(item.getFeedId()), streamingAllowed, playOrderDates.get(item.getId()));
                if (episode != null) {
                    candidates.add(episode);
                }
            }
            List<SmartPlayEpisode> current = new ArrayList<>();
            for (FeedItem item : queue) {
                SmartPlayEpisode episode = SmartPlayEpisodeConverter.convert(item,
                        streamFeeds.contains(item.getFeedId()), streamingAllowed, playOrderDates.get(item.getId()));
                if (episode != null) {
                    current.add(episode);
                }
            }

            ManualEdits edits = editStore.load();
            List<SmartPlayEpisode> result = fullRebuild
                    ? engine.rebuild(definition, candidates, current, edits)
                    : engine.onNewDownloads(definition, candidates, current, edits);

            pruneEdits(edits, queue, itemsById);

            List<Long> currentIds = new ArrayList<>();
            for (FeedItem item : queue) {
                currentIds.add(item.getId());
            }
            List<Long> resultIds = new ArrayList<>();
            List<FeedItem> newQueue = new ArrayList<>();
            for (SmartPlayEpisode episode : result) {
                FeedItem item = itemsById.get(episode.getId());
                if (item != null) {
                    resultIds.add(item.getId());
                    newQueue.add(item);
                }
            }
            if (!resultIds.equals(currentIds)) {
                DBWriter.setQueue(newQueue).get();
            }
        } catch (Exception e) {
            Log.e(TAG, "Smart Play update failed", e);
        }
    }

    private Map<Long, FeedItem> loadCandidates(List<FeedItem> queue, Set<Long> streamFeeds) {
        Map<Long, FeedItem> itemsById = new LinkedHashMap<>();
        for (FeedItem item : queue) {
            itemsById.put(item.getId(), item);
        }
        addSubscribed(itemsById, DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.DOWNLOADED, FeedItemFilter.UNPLAYED), SortOrder.DATE_NEW_OLD));
        addSubscribed(itemsById, DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.PAUSED, FeedItemFilter.UNPLAYED), SortOrder.DATE_NEW_OLD));
        addStreamable(itemsById, streamFeeds);
        return itemsById;
    }

    private Map<Long, Long> localPlayOrderDates(Map<Long, FeedItem> itemsById) {
        Map<Long, Long> result = new HashMap<>();
        Set<Long> localFeeds = new HashSet<>();
        for (FeedItem item : itemsById.values()) {
            if (item.getFeed() != null && item.getFeed().isLocalFeed()) {
                localFeeds.add(item.getFeedId());
            }
        }
        if (localFeeds.isEmpty()) {
            return result;
        }
        for (Feed feed : DBReader.getFeedList()) {
            if (localFeeds.contains(feed.getId())) {
                LocalFolders.ensureDefaults(context, feed);
                result.putAll(LocalFolders.playOrderDates(context, feed));
            }
        }
        return result;
    }

    private void addStreamable(Map<Long, FeedItem> itemsById, Set<Long> streamFeeds) {
        Map<Long, ShowDownloadSettings> settings = DownloadSettingsRepository.getInstance(context).getAll();
        for (Feed feed : DBReader.getFeedList()) {
            ShowDownloadSettings show = settings.get(feed.getId());
            if (show == null || !show.getMode().streams() || feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            streamFeeds.add(feed.getId());
            List<FeedItem> items = DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.UNPLAYED),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
            Map<Long, FeedItem> byId = new HashMap<>();
            List<DownloadEpisode> episodes = new ArrayList<>();
            for (FeedItem item : items) {
                if (item.getMedia() != null) {
                    byId.put(item.getId(), item);
                    episodes.add(DownloadEpisode.builder(item.getId(),
                            item.getPubDate() != null ? item.getPubDate().getTime() : 0)
                            .played(item.isPlayed()).build());
                }
            }
            for (DownloadEpisode episode : DownloadPlanner.streamable(show, episodes)) {
                if (!itemsById.containsKey(episode.getId())) {
                    itemsById.put(episode.getId(), byId.get(episode.getId()));
                }
            }
        }
    }

    private static void addSubscribed(Map<Long, FeedItem> itemsById, List<FeedItem> items) {
        for (FeedItem item : items) {
            Feed feed = item.getFeed();
            if (feed != null && feed.getState() == Feed.STATE_SUBSCRIBED) {
                itemsById.put(item.getId(), item);
            }
        }
    }

    private void pruneEdits(ManualEdits edits, List<FeedItem> queue, Map<Long, FeedItem> itemsById) {
        Set<Long> keepHandAdded = new HashSet<>();
        for (FeedItem item : queue) {
            if (edits.isHandAdded(item.getId())) {
                keepHandAdded.add(item.getId());
            }
        }
        Set<Long> keepRemoved = new HashSet<>();
        Map<Long, FeedItem> lookup = new HashMap<>(itemsById);
        for (long id : edits.getRemovedIds()) {
            FeedItem item = lookup.get(id);
            if (item != null && !item.isPlayed()) {
                keepRemoved.add(id);
            }
        }
        editStore.prune(keepHandAdded, keepRemoved);
    }
}
