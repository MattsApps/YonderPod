package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.smartplay.moving.ShowLinks;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class BulkAddFlow {
    private BulkAddFlow() {
    }

    public static void find(Fragment fragment, String text, Runnable onSubscribed) {
        List<String> lines = ShowLinks.lines(text);
        if (lines.isEmpty()) {
            ImportFlow.toast(fragment, fragment.getString(R.string.moving_bulk_empty));
            return;
        }
        AlertDialog working = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setMessage(fragment.getResources().getQuantityString(R.plurals.moving_bulk_finding, lines.size(),
                        lines.size()))
                .setCancelable(false)
                .show();
        Single.fromCallable(() -> {
            List<ShowResolver.Match> matches = new ArrayList<>();
            for (String line : lines) {
                matches.add(ShowResolver.resolve(line));
            }
            return matches;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(matches -> {
                    working.dismiss();
                    if (fragment.isAdded()) {
                        confirm(fragment, matches, onSubscribed);
                    }
                }, error -> {
                    working.dismiss();
                    ImportFlow.toast(fragment, fragment.getString(R.string.import_failed, error.getMessage()));
                });
    }

    private static void confirm(Fragment fragment, List<ShowResolver.Match> matches, Runnable onSubscribed) {
        Context context = fragment.requireContext();
        List<ShowResolver.Match> found = new ArrayList<>();
        List<String> spotifyOnly = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (ShowResolver.Match match : matches) {
            if (match.found()) {
                found.add(match);
            } else if (match.spotifyOnly) {
                spotifyOnly.add(match.title);
            } else {
                missing.add(match.input);
            }
        }
        String notes = notes(context, spotifyOnly, missing);
        if (found.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.moving_bulk_none)
                    .setMessage(notes)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        String[] labels = new String[found.size()];
        boolean[] checked = new boolean[found.size()];
        for (int i = 0; i < found.size(); i++) {
            ShowResolver.Match match = found.get(i);
            String label = match.author.isEmpty() ? match.title
                    : context.getString(R.string.moving_bulk_by, match.title, match.author);
            labels[i] = match.closest ? context.getString(R.string.moving_bulk_closest, label, match.input) : label;
            checked[i] = true;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.moving_bulk_confirm)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.moving_bulk_subscribe, (dialog, which) -> {
                    List<ShowResolver.Match> chosen = new ArrayList<>();
                    for (int i = 0; i < found.size(); i++) {
                        if (checked[i]) {
                            chosen.add(found.get(i));
                        }
                    }
                    subscribe(fragment, chosen, notes, onSubscribed);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static String notes(Context context, List<String> spotifyOnly, List<String> missing) {
        List<String> parts = new ArrayList<>();
        if (!spotifyOnly.isEmpty()) {
            parts.add(context.getString(R.string.moving_bulk_spotify_only,
                    TextUtils.join(context.getString(R.string.list_separator), spotifyOnly)));
        }
        if (!missing.isEmpty()) {
            parts.add(context.getString(R.string.moving_bulk_missing,
                    TextUtils.join(context.getString(R.string.list_separator), missing)));
        }
        return TextUtils.join("\n\n", parts);
    }

    private static void subscribe(Fragment fragment, List<ShowResolver.Match> chosen, String notes,
                                  Runnable onSubscribed) {
        Context app = fragment.requireContext().getApplicationContext();
        Completable.fromAction(() -> {
            for (ShowResolver.Match match : chosen) {
                Feed feed = new Feed(match.feedUrl, null, match.title);
                feed.setItems(Collections.emptyList());
                FeedDatabaseWriter.updateFeed(app, feed, false);
            }
            FeedUpdateManager.getInstance().runOnce(app);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    onSubscribed.run();
                    if (!fragment.isAdded()) {
                        return;
                    }
                    String done = fragment.getResources().getQuantityString(R.plurals.moving_bulk_done,
                            chosen.size(), chosen.size());
                    new MaterialAlertDialogBuilder(fragment.requireContext())
                            .setTitle(done)
                            .setMessage(notes.isEmpty() ? null : notes)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }, error -> ImportFlow.toast(fragment,
                        fragment.getString(R.string.import_failed, error.getMessage())));
    }
}
