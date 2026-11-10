package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.OpmlImportActivity;
import de.danoeh.antennapod.smartplay.backup.BackupCrypto;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class ImportFlow {
    private static final String TAG = "ImportFlow";

    private ImportFlow() {
    }

    public static void open(Fragment fragment, Uri uri) {
        Context app = fragment.requireContext().getApplicationContext();
        Single.fromCallable(() -> YonderBackup.sniff(app, uri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(kind -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    switch (kind) {
                        case YONDER:
                        case ANTENNAPOD:
                            inspect(fragment, uri, null);
                            break;
                        case BEYONDPOD:
                            BeyondPodImportFlow.open(fragment, uri);
                            break;
                        case OPML:
                            Intent intent = new Intent(fragment.requireContext(), OpmlImportActivity.class);
                            intent.setData(uri);
                            fragment.startActivity(intent);
                            break;
                        default:
                            toast(fragment, fragment.getString(R.string.import_unknown_file));
                            break;
                    }
                }, error -> toast(fragment, fragment.getString(R.string.import_failed, error.getMessage())));
    }

    private static void inspect(Fragment fragment, Uri uri, @Nullable String password) {
        Context app = fragment.requireContext().getApplicationContext();
        Single.fromCallable(() -> YonderBackup.inspect(app, uri, password))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(summary -> {
                    if (fragment.isAdded()) {
                        confirm(fragment, summary);
                    }
                }, error -> {
                    if (!fragment.isAdded()) {
                        return;
                    }
                    if (error instanceof YonderBackup.NeedsPasswordException) {
                        askPassword(fragment, uri, false);
                    } else if (error instanceof BackupCrypto.WrongPasswordException) {
                        askPassword(fragment, uri, true);
                    } else if (error instanceof YonderBackup.TooNewException) {
                        toast(fragment, fragment.getString(R.string.restore_too_new));
                    } else {
                        Log.e(TAG, "Can't read backup: " + error.getMessage());
                        toast(fragment, fragment.getString(R.string.import_failed, error.getMessage()));
                    }
                });
    }

    private static void askPassword(Fragment fragment, Uri uri, boolean wrong) {
        View content = LayoutInflater.from(fragment.requireContext()).inflate(R.layout.backup_password_dialog, null);
        EditText field = content.findViewById(R.id.backupPassword);
        content.findViewById(R.id.backupPasswordAgain).setVisibility(View.GONE);
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.restore_password_title)
                .setMessage(wrong ? R.string.restore_password_wrong : R.string.restore_password_message)
                .setView(content)
                .setPositiveButton(R.string.restore_password_open, (dialog, which) ->
                        inspect(fragment, uri, field.getText().toString()))
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    static String counts(Context context, int shows, int smartPlays, int stations, int recordings) {
        List<String> parts = new ArrayList<>();
        parts.add(context.getResources().getQuantityString(R.plurals.restore_shows, shows, shows));
        if (smartPlays > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.restore_smart_plays, smartPlays,
                    smartPlays));
        }
        if (stations > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.restore_stations, stations, stations));
        }
        if (recordings > 0) {
            parts.add(context.getResources().getQuantityString(R.plurals.restore_recordings, recordings,
                    recordings));
        }
        return TextUtils.join(context.getString(R.string.detail_separator), parts);
    }

    private static void confirm(Fragment fragment, YonderBackup.Summary summary) {
        Context context = fragment.requireContext();
        String date = summary.created > 0 ? DateUtils.formatDateTime(context, summary.created,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR) : "";
        String countLine = counts(context, summary.shows, summary.smartPlays, summary.stations, summary.recordings);
        boolean antennaPod = summary.kind == YonderBackup.Kind.ANTENNAPOD;
        String message = antennaPod
                ? context.getString(R.string.restore_antennapod_message, countLine)
                : context.getString(R.string.restore_message, date, summary.appVersion, countLine);
        new MaterialAlertDialogBuilder(context)
                .setTitle(antennaPod ? R.string.restore_antennapod_title : R.string.restore_title)
                .setMessage(message)
                .setPositiveButton(antennaPod ? R.string.restore_antennapod_button : R.string.restore_button,
                        (dialog, which) -> restore(fragment, summary))
                .setNegativeButton(R.string.yonder_button_cancel, (dialog, which) -> YonderBackup.discard(summary))
                .setOnCancelListener(dialog -> YonderBackup.discard(summary))
                .show();
    }

    private static void restore(Fragment fragment, YonderBackup.Summary summary) {
        Context app = fragment.requireContext().getApplicationContext();
        AlertDialog working = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setMessage(R.string.restore_working)
                .setCancelable(false)
                .show();
        Completable.fromAction(() -> YonderBackup.restore(app, summary))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> YonderBackup.restartApp(app), error -> {
                    working.dismiss();
                    YonderBackup.discard(summary);
                    if (error instanceof YonderBackup.TooNewException) {
                        toast(fragment, fragment.getString(R.string.restore_too_new));
                    } else {
                        Log.e(TAG, "Restore failed: " + error.getMessage());
                        toast(fragment, fragment.getString(R.string.import_failed, error.getMessage()));
                    }
                });
    }

    static void toast(Fragment fragment, String message) {
        if (fragment.getContext() != null) {
            Toast.makeText(fragment.getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}
