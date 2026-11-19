package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Deleting a show or radio station (0.9.34) never waits for what's playing. If the loaded episode, recording or
 * station belongs to what's being deleted, the player stops at once and unloads it with nothing next (paused and
 * responsive, as after Android kills Yonder), and only then does the delete run.
 */
final class StopNow {
    private static final String TAG = "StopNow";

    interface Belongs {
        /** Runs off the main thread, so it may read the database. */
        boolean test(FeedMedia media);
    }

    private StopNow() {
    }

    /** Stops and unloads what's loaded if it belongs, then runs {@code then} on the main thread (always, once). */
    static void ifLoadedThen(Context context, Belongs belongs, Runnable then) {
        Context app = context.getApplicationContext();
        Disposable ignored = Single.fromCallable(() -> {
            long mediaId = PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
            if (mediaId == PlaybackPreferences.NO_MEDIA_PLAYING) {
                return false;
            }
            FeedMedia media = DBReader.getFeedMedia(mediaId);
            return media != null && belongs.test(media);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> {
                    if (loaded) {
                        Log.d(TAG, "What's loaded is being deleted: stopping it first");
                        PlaybackController.unloadThen(app, then);
                    } else {
                        then.run();
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    then.run();
                });
    }
}
