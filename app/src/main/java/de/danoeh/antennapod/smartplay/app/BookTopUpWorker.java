package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.database.DBReader;

public class BookTopUpWorker extends Worker {
    private static final String TAG = "BookTopUp";
    private static final String WORK_PREFIX = "bookTopUp-";
    private static final String KEY_FEED_ID = "feedId";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static Listener listener;

    public BookTopUpWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static synchronized void init(Context context) {
        if (listener == null) {
            listener = new Listener(context.getApplicationContext());
            EventBus.getDefault().register(listener);
        }
    }

    public static void request(Context context, long feedId) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Feed feed = DBReader.getFeed(feedId, false, 0, 0);
                if (feed == null || feed.isLocalFeed() || !Books.isBook(app, feedId)) {
                    return;
                }
                boolean mobile = Books.mayUseMobileData(app, feed);
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(mobile ? NetworkType.CONNECTED : NetworkType.UNMETERED)
                        .build();
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BookTopUpWorker.class)
                        .setInputData(new Data.Builder().putLong(KEY_FEED_ID, feedId).build())
                        .setConstraints(constraints)
                        .build();
                WorkManager.getInstance(app).enqueueUniqueWork(WORK_PREFIX + feedId,
                        ExistingWorkPolicy.REPLACE, request);
            } catch (Exception e) {
                Log.e(TAG, "Could not request top-up", e);
            }
        });
    }

    @NonNull
    @Override
    public Result doWork() {
        long feedId = getInputData().getLong(KEY_FEED_ID, -1);
        if (feedId >= 0) {
            try {
                Books.topUp(getApplicationContext(), feedId);
            } catch (Exception e) {
                Log.e(TAG, "Top-up failed", e);
            }
        }
        return Result.success();
    }

    public static final class Listener {
        private final Context context;

        Listener(Context context) {
            this.context = context;
        }

        @Subscribe(threadMode = ThreadMode.POSTING)
        public void onFeedItemEvent(FeedItemEvent event) {
            Set<Long> feeds = new HashSet<>();
            for (FeedItem item : event.items) {
                if (item != null && item.isPlayed()) {
                    feeds.add(item.getFeedId());
                }
            }
            if (feeds.isEmpty()) {
                return;
            }
            EXECUTOR.execute(() -> {
                Set<Long> books = Books.bookIds(context);
                for (long feedId : feeds) {
                    if (books.contains(feedId)) {
                        request(context, feedId);
                    }
                }
            });
        }
    }
}
