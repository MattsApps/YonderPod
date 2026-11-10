package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class PlayInDialog {
    private static final String TAG = "PlayInDialog";

    private PlayInDialog() {
    }

    private static final class Choices {
        RecordingStore.Show show;
        boolean own;
        final List<SmartPlayStore.StoredSmartPlay> others = new ArrayList<>();
        Set<Long> chosen;
    }

    public static void show(Context context, long feedId, String showName) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> load(app, feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(choices -> ask(context, choices, showName),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static Choices load(Context context, long feedId) {
        Choices choices = new Choices();
        choices.show = RecordingStore.getInstance(context).showForFeed(feedId);
        if (choices.show == null) {
            throw new IllegalStateException("Recorded show missing");
        }
        SmartPlayStore.StoredSmartPlay own = Recordings.ownSmartPlay(context, feedId);
        choices.own = own != null;
        choices.chosen = Recordings.playsIn(context, choices.show);
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            if (own == null || stored.getId() != own.getId()) {
                choices.others.add(stored);
            }
        }
        return choices;
    }

    private static void ask(Context context, Choices choices, String showName) {
        String[] labels = new String[choices.others.size() + 1];
        boolean[] chosen = new boolean[choices.others.size() + 1];
        labels[0] = context.getString(R.string.record_plays_in_own);
        chosen[0] = choices.own;
        for (int i = 0; i < choices.others.size(); i++) {
            labels[i + 1] = choices.others.get(i).getDefinition().getName();
            chosen[i + 1] = choices.chosen.contains(choices.others.get(i).getId());
        }
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.play_in_title, showName))
                .setMultiChoiceItems(labels, chosen, (dialog, which, isChecked) -> chosen[which] = isChecked)
                .setPositiveButton(R.string.yonder_button_save, (dialog, which) -> {
                    Set<Long> others = new LinkedHashSet<>();
                    for (int i = 0; i < choices.others.size(); i++) {
                        if (chosen[i + 1]) {
                            others.add(choices.others.get(i).getId());
                        }
                    }
                    if (!chosen[0] && others.isEmpty()) {
                        Toast.makeText(app, R.string.record_plays_in_none, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Completable.fromAction(() -> Recordings.setPlaysIn(app, choices.show, chosen[0], others))
                            .subscribeOn(Schedulers.io())
                            .subscribe(() -> { }, error -> Log.e(TAG, Log.getStackTraceString(error)));
                })
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }
}
