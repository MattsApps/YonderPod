package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
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
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.smartplay.ManualEdits;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayEngine;
import de.danoeh.antennapod.smartplay.SmartPlayEpisode;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class SmartPlayQueueManager {
    private static final String TAG = "SmartPlayQueue";
    private static final long UPDATE_DELAY_MS = 3000;
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    private static final boolean FEEDS_MAY_STREAM = false;

    private static SmartPlayQueueManager instance;

    private final Context context;
    private final ManualEditStore editStore;
    private final SmartPlayEngine engine = new SmartPlayEngine();
    private final SmartPlayDefinition definition = SmartPlayLists.stuff();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingUpdate;

    private SmartPlayQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.editStore = new ManualEditStore(this.context);
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
        scheduleUpdate();
    }

    private void disableConflictingQueueSettings() {
        UserPreferences.setQueueKeepSorted(false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit().putBoolean(PREF_ENQUEUE_DOWNLOADED, false);
        if (UserPreferences.getNewEpisodesAction() == FeedPreferences.NewEpisodesAction.ADD_TO_QUEUE) {
            editor.putString(UserPreferences.PREF_NEW_EPISODES_ACTION,
                    String.valueOf(FeedPreferences.NewEpisodesAction.ADD_TO_INBOX.code));
        }
        editor.apply();
    }

    public void rebuildNow() {
        executor.execute(() -> update(true));
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
                scheduleUpdate();
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
        try {
            List<FeedItem> queue = DBReader.getQueue();
            Map<Long, FeedItem> itemsById = loadCandidates(queue);
            boolean mobileStreaming = UserPreferences.isAllowMobileStreaming();

            List<SmartPlayEpisode> candidates = new ArrayList<>();
            for (FeedItem item : itemsById.values()) {
                SmartPlayEpisode episode = SmartPlayEpisodeConverter.convert(item, FEEDS_MAY_STREAM, mobileStreaming);
                if (episode != null) {
                    candidates.add(episode);
                }
            }
            List<SmartPlayEpisode> current = new ArrayList<>();
            for (FeedItem item : queue) {
                SmartPlayEpisode episode = SmartPlayEpisodeConverter.convert(item, FEEDS_MAY_STREAM, mobileStreaming);
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

    private Map<Long, FeedItem> loadCandidates(List<FeedItem> queue) {
        Map<Long, FeedItem> itemsById = new LinkedHashMap<>();
        for (FeedItem item : queue) {
            itemsById.put(item.getId(), item);
        }
        addSubscribed(itemsById, DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.DOWNLOADED, FeedItemFilter.UNPLAYED), SortOrder.DATE_NEW_OLD));
        addSubscribed(itemsById, DBReader.getEpisodes(0, Integer.MAX_VALUE,
                new FeedItemFilter(FeedItemFilter.PAUSED, FeedItemFilter.UNPLAYED), SortOrder.DATE_NEW_OLD));
        return itemsById;
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
