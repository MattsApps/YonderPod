package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class PlayInDialog {
    private static final String TAG = "PlayInDialog";

    private PlayInDialog() {
    }

    public static void show(Context context, long feedId, String showName) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> SmartPlayStore.getInstance(app).getAll())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(smartPlays -> ask(context, smartPlays, feedId, showName),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static void ask(Context context, List<SmartPlayStore.StoredSmartPlay> smartPlays, long feedId,
                            String showName) {
        String[] labels = new String[smartPlays.size() + 1];
        boolean[] ticked = new boolean[smartPlays.size() + 1];
        for (int i = 0; i < smartPlays.size(); i++) {
            labels[i] = smartPlays.get(i).getDefinition().getName();
            ticked[i] = playsShow(smartPlays.get(i).getDefinition(), feedId);
        }
        labels[smartPlays.size()] = context.getString(R.string.play_in_new);
        boolean[] chosen = ticked.clone();
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.play_in_title, showName))
                .setMultiChoiceItems(labels, chosen, (dialog, which, isChecked) -> chosen[which] = isChecked)
                .setPositiveButton(R.string.confirm_label, (dialog, which) ->
                        Completable.fromAction(() -> save(app, smartPlays, ticked, chosen, feedId, showName))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> SmartPlayQueueManager.getInstance().onSmartPlaysChanged(),
                                        error -> Log.e(TAG, Log.getStackTraceString(error))))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void save(Context context, List<SmartPlayStore.StoredSmartPlay> smartPlays, boolean[] before,
                             boolean[] after, long feedId, String showName) {
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        for (int i = 0; i < smartPlays.size(); i++) {
            if (before[i] == after[i]) {
                continue;
            }
            SmartPlayDefinition original = smartPlays.get(i).getDefinition();
            SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(original.getName());
            for (SmartPlayDefinition.PriorityFeed feed : original.getPriorityFeeds()) {
                builder.priorityFeed(feed.getFeedId(), feed.getOrder());
            }
            for (SmartPlayRule rule : original.getRules()) {
                if (after[i] || !isShowRule(rule, feedId)) {
                    builder.rule(rule);
                }
            }
            if (after[i]) {
                builder.rule(showRule(feedId));
            }
            builder.neverInclude(original.getNeverInclude());
            store.update(smartPlays.get(i).getId(), builder.build());
        }
        if (after[smartPlays.size()]) {
            store.insert(SmartPlayDefinition.builder(showName).rule(showRule(feedId)).build());
        }
    }

    private static SmartPlayRule showRule(long feedId) {
        return SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(feedId));
    }

    private static boolean isShowRule(SmartPlayRule rule, long feedId) {
        return rule.getSource().getType() == EpisodeSource.Type.FEED && rule.getSource().getFeedId() == feedId;
    }

    static boolean playsShow(SmartPlayDefinition definition, long feedId) {
        for (SmartPlayRule rule : definition.getRules()) {
            if (isShowRule(rule, feedId)) {
                return true;
            }
        }
        return false;
    }
}
