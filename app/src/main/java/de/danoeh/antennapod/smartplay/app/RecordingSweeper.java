package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.event.FeedItemEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;

public final class RecordingSweeper {
    private static final String TAG = "RecordingSweeper";
    private static final long DELAY_MILLIS = 5_000L;
    private static RecordingSweeper instance;
    private final Context context;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> waiting;

    private RecordingSweeper(Context context) {
        this.context = context;
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new RecordingSweeper(context.getApplicationContext());
            EventBus.getDefault().register(instance);
        }
    }

    @Subscribe
    public void onFeedItemEvent(FeedItemEvent event) {
        later();
    }

    @Subscribe
    public void onPlayerStatus(PlayerStatusEvent event) {
        later();
    }

    private synchronized void later() {
        if (waiting != null && !waiting.isDone()) {
            waiting.cancel(false);
        }
        waiting = executor.schedule(() -> {
            try {
                if (Recordings.worthSweeping(context)) {
                    Recordings.sweep(context);
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not tidy recordings", e);
            }
        }, DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }
}
