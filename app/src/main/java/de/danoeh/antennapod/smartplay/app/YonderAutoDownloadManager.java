package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.service.episode.autodownload.AutomaticDownloadAlgorithm;
import de.danoeh.antennapod.net.download.service.episode.autodownload.EpisodeCleanupAlgorithmFactory;
import de.danoeh.antennapod.net.download.serviceinterface.AutoDownloadManager;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadServiceInterface;
import de.danoeh.antennapod.smartplay.download.DownloadEpisode;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.DownloadPlan;
import de.danoeh.antennapod.smartplay.download.DownloadPlanner;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public class YonderAutoDownloadManager extends AutoDownloadManager {
    private static final String TAG = "YonderAutoDownload";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Context context;
    private final SmartPlayStore store;
    private final DownloadSettingsRepository settings;

    public YonderAutoDownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.store = SmartPlayStore.getInstance(this.context);
        this.settings = DownloadSettingsRepository.getInstance(this.context);
        if (UserPreferences.getEpisodeCacheSize() != UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED) {
            PreferenceManager.getDefaultSharedPreferences(this.context).edit()
                    .putString(UserPreferences.PREF_EPISODE_CACHE_SIZE,
                            String.valueOf(UserPreferences.EPISODE_CACHE_SIZE_UNLIMITED))
                    .apply();
        }
        EventBus.getDefault().register(this);
    }

    @Override
    public Future<?> autodownloadUndownloadedItems(Context context) {
        return executor.submit(() -> run(null, false, false));
    }

    @Override
    public Future<?> autodownloadAfterRefresh(Context context, long[] refreshedFeedIds, boolean scheduled,
                                              boolean allFeeds, boolean allowMobile) {
        if (allFeeds) {
            UpdateScheduler.recordUpdated(this.context, System.currentTimeMillis());
        }
        Set<Long> feeds = new HashSet<>();
        for (long id : refreshedFeedIds) {
            feeds.add(id);
        }
        return executor.submit(() -> run(feeds, scheduled, allowMobile));
    }

    @Override
    public void performAutoCleanup(Context context) {
        try {
            executor.submit(this::cleanup).get();
        } catch (Exception e) {
            Log.e(TAG, "Cleanup failed", e);
        }
    }

    @Override
    public boolean mayDelete(FeedItem item) {
        return isSafeToDelete(item) && isDeletionAllowed(item.getFeedId());
    }

    @Override
    public boolean isDeletionAllowed(long feedId) {
        return settings.isDeletionAllowed(feedId);
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onFeedItemEvent(FeedItemEvent event) {
        Map<Long, List<Long>> downloadedByFeed = new HashMap<>();
        for (FeedItem item : event.items) {
            if (item.getMedia() != null && item.getMedia().isDownloaded()) {
                List<Long> ids = downloadedByFeed.get(item.getFeedId());
                if (ids == null) {
                    ids = new ArrayList<>();
                    downloadedByFeed.put(item.getFeedId(), ids);
                }
                ids.add(item.getId());
            }
        }
        if (!downloadedByFeed.isEmpty()) {
            executor.execute(() -> {
                for (Map.Entry<Long, List<Long>> entry : downloadedByFeed.entrySet()) {
                    store.addDownloadHistory(entry.getKey(), entry.getValue());
                }
            });
        }
    }

    private void cleanup() {
        planAll(null);
    }

    private void run(Set<Long> downloadFeeds, boolean scheduled, boolean allowMobile) {
        try {
            List<FeedItem> planned = planAll(downloadFeeds);
            boolean manual = !scheduled && downloadFeeds != null;
            boolean wifi = onWifi();
            Log.d(TAG, "Run: scheduled " + scheduled + ", manual " + manual + ", shows " + (downloadFeeds != null
                    ? downloadFeeds.size() : 0) + ", planned " + planned.size() + ", wifi " + wifi
                    + ", charging " + AutomaticDownloadAlgorithm.deviceCharging(context));
            if (scheduled) {
                if (!NetworkUtils.networkAvailable() || (!allowMobile && NetworkUtils.isNetworkRestricted())) {
                    Log.d(TAG, "Scheduled run: no allowed network, nothing downloaded");
                    return;
                }
            } else if (manual) {
                if (!NetworkUtils.networkAvailable() || (!wifi && !NetworkUtils.isAutoDownloadAllowed())) {
                    Log.d(TAG, "Update now: no Wi-Fi and mobile downloads not allowed, nothing downloaded");
                    return;
                }
            } else {
                boolean networkAllowed = NetworkUtils.isAutoDownloadAllowed();
                boolean powerAllowed = AutomaticDownloadAlgorithm.deviceCharging(context)
                        || UserPreferences.isEnableAutodownloadOnBattery();
                if (!networkAllowed || !powerAllowed) {
                    return;
                }
            }
            List<FeedItem> toDownload = new ArrayList<>();
            if (UserPreferences.isEnableAutodownloadQueue()) {
                for (FeedItem item : DBReader.getQueue()) {
                    if (isDownloadable(item)) {
                        toDownload.add(item);
                    }
                }
            }
            planned.sort((a, b) -> Long.compare(time(b.getPubDate()), time(a.getPubDate())));
            for (FeedItem item : planned) {
                if (!containsId(toDownload, item.getId())) {
                    toDownload.add(item);
                }
            }
            Log.d(TAG, "Downloading " + toDownload.size() + " episodes");
            if (toDownload.isEmpty()) {
                return;
            }
            for (FeedItem item : toDownload) {
                if ((scheduled && allowMobile) || (manual && wifi)) {
                    DownloadServiceInterface.get().downloadNow(context, item, true);
                } else {
                    DownloadServiceInterface.get().download(context, item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Automatic download failed", e);
        }
    }

    private List<FeedItem> planAll(Set<Long> downloadFeeds) {
        List<FeedItem> toDownload = new ArrayList<>();
        List<Feed> feeds = new ArrayList<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                feeds.add(feed);
            }
        }
        Map<Long, ShowDownloadSettings> all = settings.getForFeeds(feeds);
        long now = System.currentTimeMillis();
        for (Feed feed : feeds) {
            ShowDownloadSettings show = all.get(feed.getId());
            if (show == null || (feed.isLocalFeed() && !show.isAllowDeletions())) {
                continue;
            }
            List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
            Set<Long> history = store.getDownloadHistory(feed.getId());
            List<Long> newlyDownloaded = new ArrayList<>();
            Map<Long, FeedItem> byId = new HashMap<>();
            List<DownloadEpisode> episodes = new ArrayList<>();
            for (FeedItem item : items) {
                FeedMedia media = item.getMedia();
                if (media == null) {
                    continue;
                }
                byId.put(item.getId(), item);
                if (media.isDownloaded() && !history.contains(item.getId())) {
                    newlyDownloaded.add(item.getId());
                }
                episodes.add(DownloadEpisode.builder(item.getId(), time(item.getPubDate()))
                        .downloaded(media.isDownloaded())
                        .played(item.isPlayed())
                        .halfPlayed(media.getPosition() > 0)
                        .favourite(item.isTagged(FeedItem.TAG_FAVORITE))
                        .inSmartPlay(item.isTagged(FeedItem.TAG_QUEUE))
                        .inHistory(history.contains(item.getId()) || newlyDownloaded.contains(item.getId()))
                        .downloadable(isDownloadable(item)
                                && feed.getPreferences().getFilter().shouldAutoDownload(item))
                        .build());
            }
            store.addDownloadHistory(feed.getId(), newlyDownloaded);
            DownloadPlan plan = DownloadPlanner.plan(show, episodes, now);
            if (downloadFeeds != null && downloadFeeds.contains(feed.getId())) {
                Log.d(TAG, "Show " + feed.getId() + ": mode " + show.getMode() + ", count " + show.getEpisodeCount()
                        + ", keep " + show.getKeepAtMost() + ", episodes " + episodes.size() + ", downloaded "
                        + countDownloaded(episodes) + ", to download " + plan.getToDownload().size()
                        + ", to delete " + plan.getToDelete().size());
            }
            for (DownloadEpisode episode : plan.getToDelete()) {
                FeedItem item = byId.get(episode.getId());
                if (item != null && mayDelete(item)) {
                    try {
                        DBWriter.deleteFeedMediaOfItem(context, item.getMedia()).get();
                    } catch (Exception e) {
                        Log.e(TAG, "Could not delete episode", e);
                    }
                }
            }
            boolean wantDownloads = downloadFeeds != null && downloadFeeds.contains(feed.getId())
                    && !feed.isLocalFeed();
            if (wantDownloads && show.getMode() != DownloadMode.IGNORE) {
                for (DownloadEpisode episode : plan.getToDownload()) {
                    FeedItem item = byId.get(episode.getId());
                    if (item != null) {
                        toDownload.add(item);
                    }
                }
            }
        }
        EpisodeCleanupAlgorithmFactory.build().performCleanup(context);
        return toDownload;
    }

    private boolean isDownloadable(FeedItem item) {
        FeedMedia media = item.getMedia();
        return media != null && !media.isDownloaded() && item.isAutoDownloadEnabled()
                && item.getFeed() != null && !item.getFeed().isLocalFeed()
                && !DownloadServiceInterface.get().isDownloadingEpisode(media.getDownloadUrl());
    }

    private boolean onWifi() {
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private static int countDownloaded(List<DownloadEpisode> episodes) {
        int count = 0;
        for (DownloadEpisode episode : episodes) {
            if (episode.isDownloaded()) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsId(List<FeedItem> items, long id) {
        for (FeedItem item : items) {
            if (item.getId() == id) {
                return true;
            }
        }
        return false;
    }

    private static long time(Date date) {
        return date != null ? date.getTime() : 0;
    }
}
