package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class DownloadSettingsRepository {
    private static final String PREFS = "YonderDownloadDefaults";
    private static final String KEY_MODE = "mode";
    private static final String KEY_COUNT = "count";
    private static final String KEY_KEEP = "keep";
    private static final String KEY_AGE = "age";
    private static final String KEY_ALLOW_DELETIONS = "allowDeletions";
    private static final String KEY_MIGRATED = "migrated";

    private static volatile DownloadSettingsRepository instance;

    private final SmartPlayStore store;
    private final SharedPreferences prefs;
    private volatile Map<Long, ShowDownloadSettings> cache;

    private DownloadSettingsRepository(Context context) {
        this.store = SmartPlayStore.getInstance(context);
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized DownloadSettingsRepository getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadSettingsRepository(context);
        }
        return instance;
    }

    public ShowDownloadSettings getDefaults() {
        ShowDownloadSettings beyondPod = ShowDownloadSettings.beyondPodDefaults();
        DownloadMode mode;
        try {
            mode = DownloadMode.valueOf(prefs.getString(KEY_MODE, beyondPod.getMode().name()));
        } catch (IllegalArgumentException e) {
            mode = beyondPod.getMode();
        }
        return new ShowDownloadSettings(mode,
                prefs.getInt(KEY_COUNT, beyondPod.getEpisodeCount()),
                prefs.getInt(KEY_KEEP, beyondPod.getKeepAtMost()),
                prefs.getInt(KEY_AGE, beyondPod.getDeleteOlderThanDays()),
                prefs.getBoolean(KEY_ALLOW_DELETIONS, beyondPod.isAllowDeletions()));
    }

    public void setDefaults(ShowDownloadSettings settings) {
        prefs.edit()
                .putString(KEY_MODE, settings.getMode().name())
                .putInt(KEY_COUNT, settings.getEpisodeCount())
                .putInt(KEY_KEEP, settings.getKeepAtMost())
                .putInt(KEY_AGE, settings.getDeleteOlderThanDays())
                .putBoolean(KEY_ALLOW_DELETIONS, settings.isAllowDeletions())
                .apply();
    }

    public synchronized Map<Long, ShowDownloadSettings> getAll() {
        if (cache == null) {
            cache = new ConcurrentHashMap<>(store.getAllDownloadSettings());
        }
        if (!prefs.getBoolean(KEY_MIGRATED, false)) {
            migrateExistingShows();
        }
        return new HashMap<>(cache);
    }

    public synchronized ShowDownloadSettings get(long feedId) {
        ShowDownloadSettings settings = getAll().get(feedId);
        if (settings == null) {
            settings = getDefaults();
            set(feedId, settings);
        }
        return settings;
    }

    public synchronized Map<Long, ShowDownloadSettings> getForFeeds(List<Feed> feeds) {
        Map<Long, ShowDownloadSettings> all = getAll();
        for (Feed feed : feeds) {
            if (!all.containsKey(feed.getId())) {
                ShowDownloadSettings defaults = getDefaults();
                set(feed.getId(), defaults);
                all.put(feed.getId(), defaults);
            }
        }
        return all;
    }

    public synchronized void set(long feedId, ShowDownloadSettings settings) {
        store.setDownloadSettings(feedId, settings);
        if (cache != null) {
            cache.put(feedId, settings);
        }
    }

    public static boolean isStreamShow(long feedId) {
        DownloadSettingsRepository repository = instance;
        Map<Long, ShowDownloadSettings> settings = repository != null ? repository.cache : null;
        ShowDownloadSettings show = settings != null ? settings.get(feedId) : null;
        return show != null && show.getMode().streams();
    }

    public boolean isDeletionAllowed(long feedId) {
        ShowDownloadSettings settings = getAll().get(feedId);
        return settings == null || settings.isAllowDeletions();
    }

    public void markAllAsDownloaded(long feedId) {
        Feed feed = DBReader.getFeed(feedId, false, 0, 0);
        if (feed == null) {
            return;
        }
        List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
        List<Long> ids = new ArrayList<>();
        for (FeedItem item : items) {
            ids.add(item.getId());
        }
        store.addDownloadHistory(feedId, ids);
    }

    public void clearDownloadHistory(long feedId) {
        store.clearDownloadHistory(feedId);
    }

    private void migrateExistingShows() {
        boolean globalAutoDownload = UserPreferences.isEnableAutodownloadGlobal();
        for (Feed feed : DBReader.getFeedList()) {
            if (cache.containsKey(feed.getId())) {
                continue;
            }
            boolean autoDownload = feed.getPreferences() != null
                    && feed.getPreferences().isAutoDownload(globalAutoDownload);
            ShowDownloadSettings settings = new ShowDownloadSettings(
                    autoDownload ? DownloadMode.LATEST : DownloadMode.MANUAL, 1,
                    ShowDownloadSettings.NO_LIMIT, ShowDownloadSettings.NO_LIMIT, true);
            store.setDownloadSettings(feed.getId(), settings);
            cache.put(feed.getId(), settings);
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply();
    }
}
