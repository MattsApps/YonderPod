package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Add to playlist (0.9.36): asks which Smart Play, then puts the episodes next to play there, after any
 * already hand-added at its top. With only one Smart Play it doesn't ask.
 */
public final class AddToSmartPlay {
    private static final String TAG = "AddToSmartPlay";

    private AddToSmartPlay() {
    }

    public static void ask(Activity activity, List<FeedItem> items, @Nullable Runnable done) {
        List<FeedItem> episodes = new ArrayList<>();
        for (FeedItem item : items) {
            if (item.getMedia() != null && !item.getMedia().isLiveRadio()) {
                episodes.add(item);
            }
        }
        SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
        if (episodes.isEmpty() || manager == null) {
            finish(done);
            return;
        }
        Single.fromCallable(() -> AutoSmartPlays.visible(activity, SmartPlayStore.getInstance(activity).getAll()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(smartPlays -> {
                    if (activity.isFinishing() || smartPlays.isEmpty()) {
                        finish(done);
                        return;
                    }
                    if (smartPlays.size() == 1) {
                        add(activity, manager, smartPlays.get(0), episodes, done);
                        return;
                    }
                    long highlighted = manager.getActiveId();
                    String[] names = new String[smartPlays.size()];
                    for (int i = 0; i < smartPlays.size(); i++) {
                        String name = SmartPlayHours.nameWithHours(activity, smartPlays.get(i));
                        names[i] = smartPlays.get(i).getId() == highlighted
                                ? activity.getString(R.string.add_to_smart_play_playing_from, name) : name;
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.add_to_smart_play_title)
                            .setItems(names, (dialog, which) ->
                                    add(activity, manager, smartPlays.get(which), episodes, done))
                            .setOnCancelListener(dialog -> finish(done))
                            .show();
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    finish(done);
                });
    }

    private static void add(Activity activity, SmartPlayQueueManager manager, SmartPlayStore.StoredSmartPlay smartPlay,
                            List<FeedItem> episodes, @Nullable Runnable done) {
        manager.addToPlaylist(smartPlay.getId(), episodes);
        Toast.makeText(activity.getApplicationContext(), activity.getResources().getQuantityString(
                R.plurals.add_to_smart_play_done, episodes.size(), episodes.size(),
                smartPlay.getDefinition().getName()), Toast.LENGTH_SHORT).show();
        finish(done);
    }

    private static void finish(@Nullable Runnable done) {
        if (done != null) {
            done.run();
        }
    }
}
