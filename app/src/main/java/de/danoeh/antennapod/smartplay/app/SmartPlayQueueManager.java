package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Collection;
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
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.playback.RebuildSmartPlayEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.smartplay.ManualEdits;
import de.danoeh.antennapod.smartplay.PlaylistMemory;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayEngine;
import de.danoeh.antennapod.smartplay.SmartPlayEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadPlanner;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.playback.service.NextUpRule;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class SmartPlayQueueManager {
    private static final String TAG = "SmartPlayQueue";
    private static final long UPDATE_DELAY_MS = 3000;
    private static final String PREF_ENQUEUE_DOWNLOADED = "prefEnqueueDownloaded";
    private static final String PREF_SKIP_KEEPS_EPISODE = "prefSkipKeepsEpisode";
    private static final String STATE_PREFS = "SmartPlayState";
    private static final String KEY_ACTIVE_ID = "activeId";
    private static final String KEY_ACTIVE_NAME = "activeName";
    private static final String KEY_PREVIOUS_ID = "previousId";
    /** 1.0.5: the Smart Play the Podcasts world plays from (the last visible one highlighted). */
    private static final String KEY_PODCASTS_ID = "podcastsId";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_FEED_QUEUE_ACTION_CLEARED = "feedQueueActionCleared";
    private static final String KEY_COUNTER_SET = "subscriptionCounterSet";
    /** The Smart Play whose list is in the playlist right now (0.9.36). */
    private static final String KEY_QUEUE_OWNER = "queueOwner";
    private static final String KEY_UPGRADED_0936 = "upgraded0936";
    private static final String MEMORY_PREFS = "SmartPlayPlaylists";
    private static final String KEY_MEMORY = "memory";

    private static SmartPlayQueueManager instance;

    private final Context context;
    private final ManualEditStore editStore;
    private final SmartPlayEngine engine = new SmartPlayEngine();
    private final SmartPlayStore store;
    private final SharedPreferences state;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pendingUpdate;
    private volatile String activeName = "";
    private final SharedPreferences memoryPrefs;
    private final Object memoryLock = new Object();
    private PlaylistMemory memory;

    private SmartPlayQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.editStore = new ManualEditStore(this.context);
        this.store = SmartPlayStore.getInstance(this.context);
        this.state = this.context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        this.memoryPrefs = this.context.getSharedPreferences(MEMORY_PREFS, Context.MODE_PRIVATE);
        this.memory = PlaylistMemory.parse(memoryPrefs.getString(KEY_MEMORY, ""));
    }

    private interface MemoryEdit {
        void apply(PlaylistMemory memory);
    }

    private void editMemory(MemoryEdit edit) {
        synchronized (memoryLock) {
            edit.apply(memory);
            memoryPrefs.edit().putString(KEY_MEMORY, memory.serialize()).apply();
        }
    }

    private PlaylistMemory memoryCopy() {
        synchronized (memoryLock) {
            return PlaylistMemory.parse(memory.serialize());
        }
    }

    public static synchronized void init(Context context) {
        if (instance != null) {
            return;
        }
        instance = new SmartPlayQueueManager(context);
        Context app = context.getApplicationContext();
        NextUpRule.setWorldNext((finished, world) -> {
            FeedItem next = WorldOf.next(app, finished, world);
            if (next != null && next.getMedia() != null) {
                NextUpPreferences.startWorld(next.getMedia().getId(), world);
                return next;
            }
            if (!NextUpPreferences.keepPlaying()) {
                return null;
            }
            next = instance.podcastsNext();
            if (next != null && next.getMedia() != null) {
                NextUpPreferences.startWorld(next.getMedia().getId(), NextUpPreferences.WORLD_SMART_PLAY);
            }
            return next;
        });
        instance.start();
    }

    public static synchronized SmartPlayQueueManager getInstance() {
        return instance;
    }

    private void start() {
        disableConflictingQueueSettings();
        EventBus.getDefault().register(this);
        executor.execute(this::activeSmartPlay);
        executor.execute(this::upgradeOnce);
        executor.execute(this::purge);
        executor.execute(this::clearPerFeedQueueActionOnce);
        scheduleUpdate();
    }

    /**
     * 0.9.36: the hand-added list every Smart Play shared moves into the highlighted Smart Play, in its order.
     * Half-played episodes stay on top of the highlighted Smart Play. Nothing else changes.
     */
    private void upgradeOnce() {
        if (state.getBoolean(KEY_UPGRADED_0936, false)) {
            return;
        }
        try {
            long active = state.getLong(KEY_ACTIVE_ID, -1);
            List<Long> queueIds = new ArrayList<>();
            for (FeedItem item : DBReader.getQueue()) {
                queueIds.add(item.getId());
            }
            Set<Long> shared = editStore.load().getHandAddedIds();
            List<Long> halfPlayed = new ArrayList<>();
            for (FeedItem item : DBReader.getEpisodes(0, Integer.MAX_VALUE,
                    new FeedItemFilter(FeedItemFilter.PAUSED, FeedItemFilter.UNPLAYED), SortOrder.DATE_NEW_OLD)) {
                halfPlayed.add(item.getId());
            }
            FeedMedia loaded = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            long loadedId = loaded != null && !loaded.isLiveRadio() ? loaded.getItemId() : -1;
            PlaylistMemory upgraded = PlaylistMemory.upgradeFrom0935(active, queueIds, shared, halfPlayed, loadedId);
            editMemory(m -> memory = upgraded);
            editStore.clearHandAdded();
            state.edit().putLong(KEY_QUEUE_OWNER, active).putBoolean(KEY_UPGRADED_0936, true).apply();
            Log.d(TAG, "0.9.36: moved " + shared.size() + " hand-added episode(s) into Smart Play " + active);
        } catch (Exception e) {
            Log.e(TAG, "Could not move the hand-added list", e);
        }
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
        UserPreferences.setEnqueueLocation(UserPreferences.EnqueueLocation.AFTER_CURRENTLY_PLAYING);
        if (!state.getBoolean(KEY_COUNTER_SET, false)) {
            UserPreferences.setFeedCounterSetting(FeedCounter.SHOW_DOWNLOADED);
            state.edit().putBoolean(KEY_COUNTER_SET, true).apply();
        }
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

    /**
     * "Playing from": moves the highlight to this Smart Play. Nothing you can hear changes: the loaded episode
     * carries on, shown at the top of this Smart Play's list, and when it ends this Smart Play takes over. It
     * isn't written into any Smart Play tapped through on the way.
     */
    public void highlight(long smartPlayId) {
        executor.execute(() -> switchTo(smartPlayId));
    }

    /** Switches and starts playing (audiobooks: Listen). */
    public void activate(long smartPlayId) {
        activate(smartPlayId, null);
    }

    public void activate(long smartPlayId, FeedMedia fallback) {
        executor.execute(() -> {
            switchWorldTo(smartPlayId);
            List<FeedItem> queue = DBReader.getQueue();
            FeedMedia media = !queue.isEmpty() ? queue.get(0).getMedia() : fallback;
            if (media != null) {
                NextUpPreferences.startWorld(media.getId(), NextUpPreferences.WORLD_SMART_PLAY);
                new Handler(Looper.getMainLooper()).post(() -> new PlaybackServiceStarter(context, media)
                        .callEvenIfRunning(true)
                        .start());
            }
        });
    }

    public void activateAndPlay(long smartPlayId, FeedItem chapter) {
        executor.execute(() -> {
            switchWorldTo(smartPlayId);
            try {
                List<FeedItem> newQueue = new ArrayList<>();
                newQueue.add(chapter);
                for (FeedItem item : DBReader.getQueue()) {
                    if (item.getId() != chapter.getId()) {
                        newQueue.add(item);
                    }
                }
                DBWriter.setQueue(newQueue).get();
            } catch (Exception e) {
                Log.e(TAG, "Could not put book first", e);
            }
            NextUpPreferences.startWorld(chapter.getMedia().getId(), NextUpPreferences.WORLD_SMART_PLAY);
            new Handler(Looper.getMainLooper()).post(() -> new PlaybackServiceStarter(context, chapter.getMedia())
                    .callEvenIfRunning(true)
                    .start());
        });
    }

    /**
     * 1.0.5: play on the Podcasts circle switches to the Podcasts Smart Play and plays media (null: its first).
     * Whatever another world was playing stays paused where it was and doesn't come back by itself.
     */
    public void playPodcasts(@Nullable FeedMedia media) {
        executor.execute(() -> {
            long id = podcastsId();
            if (id != state.getLong(KEY_ACTIVE_ID, -1)) {
                switchWorldTo(id);
            }
            FeedMedia play = media;
            if (play == null) {
                FeedItem first = NextUpRule.firstInPlaylist(DBReader.getQueue());
                if (first == null) {
                    update(true);
                    first = NextUpRule.firstInPlaylist(DBReader.getQueue());
                }
                play = first != null ? first.getMedia() : null;
            }
            if (play == null) {
                return;
            }
            NextUpPreferences.startWorld(play.getId(), NextUpPreferences.WORLD_SMART_PLAY);
            FeedMedia start = play;
            new Handler(Looper.getMainLooper()).post(() -> new PlaybackServiceStarter(context, start)
                    .callEvenIfRunning(true)
                    .start());
        });
    }

    /**
     * 1.0.5: Radio or added files ran out in "keep playing": switch to the Podcasts Smart Play and return its
     * first episode. Waits for the switch, so never call it on the executor.
     */
    @Nullable
    FeedItem podcastsNext() {
        try {
            return executor.submit(() -> {
                long id = podcastsId();
                if (id != state.getLong(KEY_ACTIVE_ID, -1)) {
                    switchWorldTo(id);
                }
                FeedItem first = NextUpRule.firstInPlaylist(DBReader.getQueue());
                if (first == null) {
                    update(true);
                    first = NextUpRule.firstInPlaylist(DBReader.getQueue());
                }
                return first;
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Could not move on to Podcasts", e);
            return null;
        }
    }

    /** The Podcasts world's Smart Play: the last visible one highlighted, else the first visible one. */
    public long podcastsId() {
        List<SmartPlayStore.StoredSmartPlay> visible = AutoSmartPlays.visible(context, store.getAll());
        if (visible.isEmpty()) {
            synchronized (this) {
                visible = AutoSmartPlays.visible(context, store.getAll());
                if (visible.isEmpty()) {
                    store.insert(SmartPlayLists.stuff());
                    visible = AutoSmartPlays.visible(context, store.getAll());
                }
            }
        }
        long saved = state.getLong(KEY_PODCASTS_ID, -1);
        for (SmartPlayStore.StoredSmartPlay stored : visible) {
            if (stored.getId() == saved) {
                return saved;
            }
        }
        return visible.isEmpty() ? -1 : visible.get(0).getId();
    }

    public String podcastsName() {
        SmartPlayStore.StoredSmartPlay stored = store.get(podcastsId());
        return stored != null ? stored.getDefinition().getName() : "";
    }

    /** Active hours: moves the highlight at the boundary, without interrupting the loaded episode. */
    public void switchByHours(long smartPlayId) {
        executor.execute(() -> {
            if (smartPlayId == state.getLong(KEY_ACTIVE_ID, -1)) {
                return;
            }
            switchTo(smartPlayId);
        });
    }

    /** On the executor. Keeps the old Smart Play's hand-added slots, moves the highlight, builds the new list. */
    private void switchTo(long smartPlayId) {
        noteLoaded();
        long old = state.getLong(KEY_ACTIVE_ID, -1);
        if (old >= 0 && old != smartPlayId && state.getLong(KEY_QUEUE_OWNER, -1) == old) {
            List<Long> queueIds = queueIds();
            editMemory(m -> m.snapshot(old, queueIds));
        }
        setActive(smartPlayId);
        if (old != smartPlayId) {
            editMemory(m -> m.onHighlightMoved(smartPlayId));
        }
        update(true);
    }

    /** 1.0.5: switching world. Like switchTo, but the loaded episode isn't carried into the new list. */
    private void switchWorldTo(long smartPlayId) {
        noteLoaded();
        long old = state.getLong(KEY_ACTIVE_ID, -1);
        if (old >= 0 && old != smartPlayId && state.getLong(KEY_QUEUE_OWNER, -1) == old) {
            List<Long> queueIds = queueIds();
            editMemory(m -> m.snapshot(old, queueIds));
        }
        setActive(smartPlayId);
        editMemory(PlaylistMemory::dropCarry);
        update(true);
    }

    private void setActive(long smartPlayId) {
        long current = state.getLong(KEY_ACTIVE_ID, -1);
        SharedPreferences.Editor editor = state.edit().putLong(KEY_ACTIVE_ID, smartPlayId);
        if (current >= 0 && current != smartPlayId) {
            editor.putLong(KEY_PREVIOUS_ID, current);
        }
        SmartPlayStore.StoredSmartPlay stored = store.get(smartPlayId);
        if (stored != null && !AutoSmartPlays.isHidden(context, stored)) {
            editor.putLong(KEY_PODCASTS_ID, smartPlayId);
        }
        editor.apply();
    }

    public void onSmartPlayDeleted(long smartPlayId) {
        executor.execute(() -> {
            editMemory(m -> m.forgetSmartPlay(smartPlayId));
            if (smartPlayId != state.getLong(KEY_ACTIVE_ID, -1)) {
                activeSmartPlay();
                return;
            }
            long previous = state.getLong(KEY_PREVIOUS_ID, -1);
            long next = -1;
            for (SmartPlayStore.StoredSmartPlay stored : store.getAll()) {
                if (stored.getId() == previous) {
                    next = previous;
                    break;
                }
                if (next < 0) {
                    next = stored.getId();
                }
            }
            state.edit().putLong(KEY_ACTIVE_ID, next).remove(KEY_PREVIOUS_ID).putLong(KEY_QUEUE_OWNER, -1).apply();
            long highlighted = next;
            editMemory(m -> m.onHighlightMoved(highlighted));
            update(true);
        });
    }

    public void onSmartPlaysChanged() {
        executor.execute(this::activeSmartPlay);
    }

    /** Play next: straight after the current episode, in the highlighted Smart Play. */
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
                editStore.unmarkRemoved(item.getId());
                long active = state.getLong(KEY_ACTIVE_ID, -1);
                int slot = target;
                editMemory(m -> m.handAdd(active, item.getId(), slot));
                if (existing >= 0) {
                    DBWriter.moveQueueItem(existing, target, true).get();
                } else {
                    DBWriter.addQueueItemAt(context, item.getId(), target).get();
                }
            } catch (Exception e) {
                Log.e(TAG, "Play next failed", e);
            }
        });
    }

    /**
     * Add to playlist: next to play in that Smart Play, after any episodes already hand-added at its top, so
     * several play in the order added. The highlighted Smart Play's list changes now; another one's when it's
     * next highlighted.
     */
    public void addToPlaylist(long smartPlayId, List<FeedItem> items) {
        executor.execute(() -> {
            long active = state.getLong(KEY_ACTIVE_ID, -1);
            for (FeedItem item : items) {
                if (item.getMedia() == null || item.getMedia().isLiveRadio()) {
                    continue;
                }
                editStore.unmarkRemoved(item.getId());
                if (smartPlayId != active) {
                    editMemory(m -> m.addToPlaylist(smartPlayId, item.getId()));
                    continue;
                }
                try {
                    List<FeedItem> queue = DBReader.getQueue();
                    int existing = -1;
                    for (int i = 0; i < queue.size(); i++) {
                        if (queue.get(i).getId() == item.getId()) {
                            existing = i;
                        }
                    }
                    if (existing >= 0) {
                        queue.remove(existing);
                    }
                    PlaylistMemory now = memoryCopy();
                    long loadedItem = now.getLoaded();
                    int target = !queue.isEmpty() && queue.get(0).getId() == loadedItem ? 1 : 0;
                    while (target < queue.size() && now.isHandAdded(active, queue.get(target).getId())) {
                        target++;
                    }
                    int slot = target;
                    editMemory(m -> m.handAdd(active, item.getId(), slot));
                    if (existing >= 0) {
                        DBWriter.moveQueueItem(existing, target, true).get();
                    } else {
                        DBWriter.addQueueItemAt(context, item.getId(), target).get();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Add to playlist failed", e);
                }
            }
        });
    }

    /**
     * The play button on a playlist row: plays it now, at the top of the list. The episode it interrupted
     * keeps its normal place: on top of its own Smart Play if it's half-played there, otherwise at the slot
     * this Smart Play's rules give it (or out of this list, if they wouldn't pick it).
     */
    public void playFromList(FeedItem item) {
        executor.execute(() -> {
            try {
                noteLoaded();
                long active = state.getLong(KEY_ACTIVE_ID, -1);
                PlaylistMemory before = memoryCopy();
                long interrupted = before.getLoaded() != item.getId() && before.getLoaded() != before.getDetour()
                        ? before.getLoaded() : -1;
                editMemory(m -> m.onLoaded(item.getId(), true, active));
                List<FeedItem> queue = DBReader.getQueue();
                List<FeedItem> newQueue = new ArrayList<>();
                FeedItem fresh = item;
                for (FeedItem queued : queue) {
                    if (queued.getId() == item.getId()) {
                        fresh = queued;
                    }
                }
                newQueue.add(fresh);
                for (FeedItem queued : queue) {
                    if (queued.getId() != item.getId()) {
                        newQueue.add(queued);
                    }
                }
                PlaylistMemory after = memoryCopy();
                if (interrupted >= 0 && !after.onTopIn(active).contains(interrupted)
                        && !after.isHandAdded(active, interrupted)) {
                    newQueue = placeAtRuleSlot(newQueue, interrupted);
                }
                DBWriter.setQueue(newQueue).get();
                FeedMedia media = fresh.getMedia();
                if (media != null) {
                    new Handler(Looper.getMainLooper()).post(() -> new PlaybackServiceStarter(context, media)
                            .callEvenIfRunning(true)
                            .start());
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not play from the list", e);
            }
        });
    }

    /** Moves an episode to the slot this Smart Play's rules give it, or out of the list if they wouldn't. */
    private List<FeedItem> placeAtRuleSlot(List<FeedItem> list, long episodeId) {
        List<FeedItem> without = new ArrayList<>();
        FeedItem moving = null;
        for (FeedItem item : list) {
            if (item.getId() == episodeId) {
                moving = item;
            } else {
                without.add(item);
            }
        }
        if (moving == null) {
            return list;
        }
        List<Long> fresh = ruleOrder();
        int at = fresh.indexOf(episodeId);
        if (at < 0) {
            Log.d(TAG, "Interrupted " + moving.getTitle() + ": this Smart Play wouldn't pick it");
            return without;
        }
        int target = Math.min(Math.max(1, at), without.size());
        without.add(target, moving);
        Log.d(TAG, "Interrupted " + moving.getTitle() + ": back to slot " + target);
        return without;
    }

    /** Which episodes the highlighted Smart Play's rules would pick, in order, ignoring hand-added ones. */
    private List<Long> ruleOrder() {
        List<Long> ids = new ArrayList<>();
        try {
            SmartPlayDefinition definition = activeSmartPlay();
            Set<Long> streamFeeds = new HashSet<>();
            Map<Long, FeedItem> itemsById = loadCandidates(new ArrayList<>(), streamFeeds);
            List<SmartPlayEpisode> candidates = convertAll(itemsById, streamFeeds);
            long active = state.getLong(KEY_ACTIVE_ID, -1);
            Set<Long> onTop = memoryCopy().onTopIn(active);
            for (SmartPlayEpisode episode : engine.rebuild(definition, candidates, new ArrayList<>(),
                    new ManualEdits(new HashSet<>(), editStore.load().getRemovedIds()), onTop)) {
                ids.add(episode.getId());
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not work out the rule order", e);
        }
        return ids;
    }

    /** Notes which episode is loaded, and whether it came from the highlighted list or is a detour. */
    private void noteLoaded() {
        long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        if (mediaId == PlaybackPreferences.NO_MEDIA_PLAYING) {
            if (memoryCopy().getLoaded() >= 0) {
                editMemory(PlaylistMemory::onUnloaded);
            }
            return;
        }
        FeedMedia media = DBReader.getFeedMedia(mediaId);
        if (media == null || media.isLiveRadio() || media.getItem() == null) {
            return;
        }
        long itemId = media.getItemId();
        if (memoryCopy().getLoaded() == itemId) {
            return;
        }
        boolean fromPlaylist = DBReader.getQueueIDList().contains(itemId);
        long active = state.getLong(KEY_ACTIVE_ID, -1);
        editMemory(m -> m.onLoaded(itemId, fromPlaylist, active));
        Log.d(TAG, "Loaded " + media.getEpisodeTitle() + (fromPlaylist ? " from the playlist" : ", a detour"));
    }

    private SmartPlayDefinition activeSmartPlay() {
        List<SmartPlayStore.StoredSmartPlay> all = store.getAll();
        if (all.isEmpty() || AutoSmartPlays.visible(context, all).isEmpty()) {
            podcastsId();
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
        if (!activeName.equals(state.getString(KEY_ACTIVE_NAME, null))) {
            state.edit().putString(KEY_ACTIVE_NAME, activeName).apply();
        }
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
        long active = state.getLong(KEY_ACTIVE_ID, -1);
        switch (event.action) {
            case ADDED:
                if (event.item != null) {
                    long id = event.item.getId();
                    int slot = event.position;
                    editStore.unmarkRemoved(id);
                    editMemory(m -> {
                        if (!m.isHandAdded(active, id)) {
                            m.handAdd(active, id, slot);
                        }
                    });
                }
                break;
            case REMOVED:
                if (event.item != null) {
                    long id = event.item.getId();
                    editStore.markRemoved(id);
                    editMemory(m -> m.removeHandAdded(active, id));
                }
                scheduleUpdate();
                break;
            case CLEARED:
                editMemory(m -> m.clearHandAdded(active));
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
    public void onPlayerStatus(PlayerStatusEvent event) {
        executor.execute(this::noteLoaded);
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onRebuildSmartPlay(RebuildSmartPlayEvent event) {
        rebuildNow();
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
            purge();
            return;
        }
        try {
            noteLoaded();
            SmartPlayDefinition definition = activeSmartPlay();
            long active = state.getLong(KEY_ACTIVE_ID, -1);
            boolean switched = state.getLong(KEY_QUEUE_OWNER, -1) != active;
            PlaylistMemory now = memoryCopy();
            List<Long> handAddedIds = now.handAddedIds(active);
            long carried = now.getCarriedTo() == active ? now.getCarriedEpisode() : -1;
            long loaded = now.getLoaded() != now.getDetour() ? now.getLoaded() : -1;
            long front = carried >= 0 ? carried
                    : switched && loaded >= 0 && now.originOf(loaded) == active ? loaded : -1;

            List<FeedItem> queue = DBReader.getQueue();
            Set<Long> streamFeeds = new HashSet<>();
            Map<Long, FeedItem> itemsById = loadCandidates(queue, streamFeeds);
            List<Long> extra = new ArrayList<>(handAddedIds);
            if (front >= 0) {
                extra.add(front);
            }
            for (long id : extra) {
                if (!itemsById.containsKey(id)) {
                    FeedItem item = DBReader.getFeedItem(id);
                    if (item != null && item.getMedia() != null) {
                        itemsById.put(id, item);
                    }
                }
            }
            long playingItemId = playingItemId(itemsById);
            Map<Long, Set<Long>> playsIn = Recordings.playsInByFeed(context);
            Set<Long> detour = detourKept(queue);
            Set<Long> dead = deadIds(itemsById.values(), playingItemId, playsIn, detour);
            itemsById.keySet().removeAll(dead);
            editStore.forget(dead);
            List<SmartPlayEpisode> candidates = convertAll(itemsById, streamFeeds);
            Map<Long, SmartPlayEpisode> byId = new HashMap<>();
            for (SmartPlayEpisode episode : candidates) {
                byId.put(episode.getId(), episode);
            }
            List<SmartPlayEpisode> current = new ArrayList<>();
            if (!switched) {
                for (FeedItem item : queue) {
                    SmartPlayEpisode episode = byId.get(item.getId());
                    if (episode != null && !dead.contains(item.getId())) {
                        current.add(episode);
                    }
                }
            }

            Set<Long> removed = new HashSet<>(editStore.load().getRemovedIds());
            removed.removeAll(handAddedIds);
            ManualEdits edits = new ManualEdits(handAddedIds, removed);
            Set<Long> onTop = now.onTopIn(active);
            List<SmartPlayEpisode> result;
            if (switched) {
                List<SmartPlayEpisode> handAdded = new ArrayList<>();
                for (long id : handAddedIds) {
                    if (byId.containsKey(id)) {
                        handAdded.add(byId.get(id));
                    }
                }
                result = engine.rebuildWithSlots(definition, candidates, handAdded, now.handAddedSlots(active),
                        edits, onTop);
            } else if (fullRebuild) {
                result = engine.rebuild(definition, candidates, current, edits, onTop);
            } else {
                result = engine.onNewDownloads(definition, candidates, current, edits, onTop);
            }

            pruneEdits(itemsById);

            List<Long> currentIds = new ArrayList<>();
            for (FeedItem item : queue) {
                currentIds.add(item.getId());
            }
            // The loaded episode, carried here by moving the highlight, shows at the top while it's loaded.
            FeedItem frontItem = front >= 0 ? itemsById.get(front) : null;
            boolean putFront = frontItem != null && !frontItem.isPlayed()
                    && (switched || fullRebuild || currentIds.contains(front));
            List<FeedItem> newQueue = new ArrayList<>();
            if (putFront) {
                newQueue.add(frontItem);
            }
            for (SmartPlayEpisode episode : result) {
                FeedItem item = itemsById.get(episode.getId());
                if (item != null && !(putFront && item.getId() == front)) {
                    newQueue.add(item);
                }
            }
            // A lengthened show's extra part plays straight after its main recording.
            newQueue = Recordings.tailsAfterMain(newQueue, itemsById.values());
            List<Long> resultIds = new ArrayList<>();
            for (FeedItem item : newQueue) {
                resultIds.add(item.getId());
            }
            if (!resultIds.equals(currentIds)) {
                DBWriter.setQueue(newQueue).get();
            }
            if (switched) {
                state.edit().putLong(KEY_QUEUE_OWNER, active).apply();
                Log.d(TAG, "Playing from " + definition.getName() + (front >= 0 ? ", loaded episode on top" : ""));
            }
            Set<Long> halfPlayed = new HashSet<>();
            for (FeedItem item : itemsById.values()) {
                if (item.isInProgress()) {
                    halfPlayed.add(item.getId());
                }
            }
            editMemory(m -> {
                m.snapshot(active, resultIds);
                m.retainOrigins(halfPlayed);
            });
        } catch (Exception e) {
            Log.e(TAG, "Smart Play update failed", e);
        }
    }

    private List<SmartPlayEpisode> convertAll(Map<Long, FeedItem> itemsById, Set<Long> streamFeeds) {
        boolean streamingAllowed = NetworkUtils.isStreamingAllowed();
        Map<Long, Long> playOrderDates = localPlayOrderDates(itemsById);
        List<Feed> allFeeds = DBReader.getFeedList();
        Books.fixListOrderOnce(context, allFeeds);
        Books.detectNewBooks(context, allFeeds);
        Set<Long> books = Books.bookIds(context);
        Map<Long, Long> bookLastPlayed = Books.lastPlayedPerBook(context, books);
        Map<Long, Set<Long>> playsIn = Recordings.playsInByFeed(context);
        Set<Long> notInAllFeeds = new HashSet<>(books);
        for (FeedItem item : itemsById.values()) {
            if (Recordings.isRecordingsFeed(item.getFeed()) && !chosenHere(playsIn, item.getFeedId())) {
                notInAllFeeds.add(item.getFeedId());
            }
        }
        List<SmartPlayEpisode> candidates = new ArrayList<>();
        for (FeedItem item : itemsById.values()) {
            SmartPlayEpisode episode = SmartPlayEpisodeConverter.convert(item,
                    streamFeeds.contains(item.getFeedId()), streamingAllowed, playOrderDates.get(item.getId()),
                    notInAllFeeds.contains(item.getFeedId()), lastPlayed(bookLastPlayed, item.getFeedId()));
            if (episode != null) {
                candidates.add(episode);
            }
        }
        return candidates;
    }

    private static List<Long> queueIds() {
        List<Long> ids = new ArrayList<>();
        for (long id : DBReader.getQueueIDList().toArray()) {
            ids.add(id);
        }
        return ids;
    }

    public void purgeDeadEntries() {
        executor.execute(this::purge);
    }

    private void purge() {
        try {
            activeSmartPlay();
            List<FeedItem> queue = DBReader.getQueue();
            Map<Long, FeedItem> inQueue = new LinkedHashMap<>();
            for (FeedItem item : queue) {
                inQueue.put(item.getId(), item);
            }
            long playingItemId = playingItemId(inQueue);
            Map<Long, Set<Long>> playsIn = Recordings.playsInByFeed(context);
            Set<Long> detour = detourKept(queue);
            Set<Long> dead = deadIds(queue, playingItemId, playsIn, detour);
            Set<Long> forget = new HashSet<>(dead);
            List<FeedItem> remembered = new ArrayList<>();
            for (long id : memoryCopy().allHandAddedIds()) {
                if (!inQueue.containsKey(id)) {
                    FeedItem item = DBReader.getFeedItem(id);
                    if (item == null || item.isPlayed()) {
                        forget.add(id);
                    } else {
                        remembered.add(item);
                    }
                }
            }
            // Hand-added to another Smart Play: whether a recording plays in the highlighted one doesn't matter.
            Set<Long> rememberedIds = new HashSet<>();
            for (FeedItem item : remembered) {
                rememberedIds.add(item.getId());
            }
            forget.addAll(deadIds(remembered, playingItemId, playsIn, rememberedIds));
            editStore.forget(forget);
            editMemory(m -> m.forget(forget));
            if (!dead.isEmpty()) {
                List<FeedItem> kept = new ArrayList<>();
                for (FeedItem item : queue) {
                    if (!dead.contains(item.getId())) {
                        kept.add(item);
                    }
                }
                Log.d(TAG, "Dropping " + dead.size() + " dead entries from the list");
                DBWriter.setQueue(kept).get();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not drop dead entries", e);
        }
    }

    private static Set<Long> detourKept(List<FeedItem> queue) {
        Set<Long> saved = NextUpPreferences.getDetourKept();
        Set<Long> kept = new HashSet<>();
        for (FeedItem item : queue) {
            if (saved.contains(item.getId())) {
                kept.add(item.getId());
            }
        }
        return kept;
    }

    private Set<Long> deadIds(Collection<FeedItem> items, long playingItemId, Map<Long, Set<Long>> playsIn,
                              Set<Long> kept) {
        Set<Long> feedIds = new HashSet<>();
        for (Feed feed : DBReader.getFeedList()) {
            feedIds.add(feed.getId());
        }
        Set<Long> dead = new HashSet<>();
        for (FeedItem item : items) {
            FeedMedia media = item.getMedia();
            Feed feed = item.getFeed();
            boolean gone = media == null || !feedIds.contains(item.getFeedId()) || media.isLiveRadio()
                    || Radio.isRadioFeed(feed);
            if (!gone && Recordings.isRecordingsFeed(feed)) {
                gone = !media.localFileAvailable() || !media.fileExists()
                        || (item.getId() != playingItemId && !kept.contains(item.getId())
                                && !chosenHere(playsIn, item.getFeedId()));
            }
            if (gone) {
                dead.add(item.getId());
            }
        }
        return dead;
    }

    private boolean chosenHere(Map<Long, Set<Long>> playsIn, long feedId) {
        Set<Long> chosen = playsIn.get(feedId);
        return chosen != null && chosen.contains(state.getLong(KEY_ACTIVE_ID, -1));
    }

    private static long playingItemId(Map<Long, FeedItem> itemsById) {
        if (PlaybackPreferences.getCurrentPlayerStatus() != PlaybackPreferences.PLAYER_STATUS_PLAYING) {
            return -1;
        }
        long playingMediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
        for (FeedItem item : itemsById.values()) {
            if (item.getMedia() != null && item.getMedia().getId() == playingMediaId) {
                return item.getId();
            }
        }
        return -1;
    }

    private static long lastPlayed(Map<Long, Long> bookLastPlayed, long feedId) {
        Long value = bookLastPlayed.get(feedId);
        return value != null ? value : 0;
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
            if (show == null || !show.getMode().streams() || feed.getState() != Feed.STATE_SUBSCRIBED
                    || Recordings.isRecordingsFeed(feed)) {
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

    /** Hand-removed episodes are remembered until they're played or gone. */
    private void pruneEdits(Map<Long, FeedItem> itemsById) {
        Set<Long> keepRemoved = new HashSet<>();
        for (long id : editStore.load().getRemovedIds()) {
            FeedItem item = itemsById.get(id);
            if (item != null && !item.isPlayed()) {
                keepRemoved.add(id);
            }
        }
        editStore.prune(new HashSet<>(), keepRemoved);
    }
}
