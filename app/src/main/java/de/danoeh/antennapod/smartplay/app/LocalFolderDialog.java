package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class LocalFolderDialog {
    private static final String TAG = "LocalFolderDialog";
    private static final LocalFolders.PlayOrder[] ORDERS = LocalFolders.PlayOrder.values();

    private static final class State {
        final LocalFolders.PlayOrder order;
        final ShowDownloadSettings settings;

        State(LocalFolders.PlayOrder order, ShowDownloadSettings settings) {
            this.order = order;
            this.settings = settings;
        }
    }

    public interface SummaryCallback {
        void onSummary(String summary);
    }

    private LocalFolderDialog() {
    }

    public static void loadSummary(Context context, long feedId, SummaryCallback callback) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> load(app, feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> callback.onSummary(summary(context, state)),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public static void show(Context context, long feedId, Runnable onSaved) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> load(app, feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> showEditor(context, feedId, state, onSaved),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static State load(Context context, long feedId) {
        return new State(LocalFolders.getPlayOrder(context, feedId),
                DownloadSettingsRepository.getInstance(context).get(feedId));
    }

    private static String summary(Context context, State state) {
        int orderText;
        switch (state.order) {
            case TRACK:
                orderText = R.string.local_summary_track;
                break;
            case OLDEST:
                orderText = R.string.local_summary_oldest;
                break;
            case NEWEST:
                orderText = R.string.local_summary_newest;
                break;
            case FILE_NAME:
            default:
                orderText = R.string.local_summary_file_name;
                break;
        }
        return context.getString(state.settings.isAllowDeletions()
                ? R.string.local_summary_deletable : R.string.local_summary_protected, context.getString(orderText));
    }

    private static String orderLabel(Context context, LocalFolders.PlayOrder order) {
        switch (order) {
            case TRACK:
                return context.getString(R.string.local_order_track);
            case OLDEST:
                return context.getString(R.string.local_order_oldest);
            case NEWEST:
                return context.getString(R.string.local_order_newest);
            case FILE_NAME:
            default:
                return context.getString(R.string.local_order_file_name);
        }
    }

    private static void showEditor(Context context, long feedId, State state, Runnable onSaved) {
        View view = LayoutInflater.from(context).inflate(R.layout.local_folder_dialog, null);
        Spinner order = view.findViewById(R.id.local_play_order);
        MaterialSwitch allowDelete = view.findViewById(R.id.local_allow_delete);
        View offNote = view.findViewById(R.id.local_allow_delete_off);
        View limits = view.findViewById(R.id.local_limits);
        EditText keep = view.findViewById(R.id.local_keep);
        EditText age = view.findViewById(R.id.local_age);

        List<String> labels = new ArrayList<>();
        for (LocalFolders.PlayOrder playOrder : ORDERS) {
            labels.add(orderLabel(context, playOrder));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        order.setAdapter(adapter);
        order.setSelection(state.order.ordinal());

        ShowDownloadSettings settings = state.settings;
        keep.setText(settings.getKeepAtMost() > 0 ? String.valueOf(settings.getKeepAtMost()) : "");
        age.setText(settings.getDeleteOlderThanDays() > 0 ? String.valueOf(settings.getDeleteOlderThanDays()) : "");
        allowDelete.setChecked(settings.isAllowDeletions());
        offNote.setVisibility(settings.isAllowDeletions() ? View.GONE : View.VISIBLE);
        limits.setVisibility(settings.isAllowDeletions() ? View.VISIBLE : View.GONE);
        allowDelete.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                offNote.setVisibility(View.VISIBLE);
                limits.setVisibility(View.GONE);
                return;
            }
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.local_allow_delete_warning_title)
                    .setMessage(R.string.local_allow_delete_warning)
                    .setPositiveButton(R.string.local_allow_delete_confirm, (d, which) -> {
                        offNote.setVisibility(View.GONE);
                        limits.setVisibility(View.VISIBLE);
                    })
                    .setNegativeButton(R.string.cancel_label, (d, which) -> allowDelete.setChecked(false))
                    .setOnCancelListener(d -> allowDelete.setChecked(false))
                    .show();
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.local_files_title)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (d, which) -> {
                    LocalFolders.PlayOrder chosen = ORDERS[order.getSelectedItemPosition()];
                    boolean allowed = allowDelete.isChecked();
                    ShowDownloadSettings updated = new ShowDownloadSettings(DownloadMode.MANUAL, 1,
                            allowed ? parse(keep) : ShowDownloadSettings.NO_LIMIT,
                            allowed ? parse(age) : ShowDownloadSettings.NO_LIMIT, allowed);
                    Context app = context.getApplicationContext();
                    Completable.fromAction(() -> {
                        LocalFolders.setPlayOrder(app, feedId, chosen);
                        DownloadSettingsRepository.getInstance(app).set(feedId, updated);
                    })
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> {
                                if (onSaved != null) {
                                    onSaved.run();
                                }
                            }, error -> Log.e(TAG, Log.getStackTraceString(error)));
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static int parse(EditText field) {
        try {
            return Integer.parseInt(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return ShowDownloadSettings.NO_LIMIT;
        }
    }
}
