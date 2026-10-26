package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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

public final class DownloadSettingsDialog {
    private static final String TAG = "DownloadSettingsDialog";
    public static final long DEFAULTS = -1;
    private static final DownloadMode[] MODES = {
        DownloadMode.MANUAL, DownloadMode.LATEST, DownloadMode.STREAM_LATEST, DownloadMode.IN_ORDER,
        DownloadMode.STREAM_IN_ORDER, DownloadMode.BACKWARDS, DownloadMode.IGNORE
    };

    private DownloadSettingsDialog() {
    }

    public static void show(Context context, long feedId, Runnable onSaved) {
        DownloadSettingsRepository repository = DownloadSettingsRepository.getInstance(context);
        Single.fromCallable(() -> feedId == DEFAULTS ? repository.getDefaults() : repository.get(feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(settings -> showEditor(context, feedId, settings, onSaved),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public static void loadSummary(Context context, long feedId, SummaryCallback callback) {
        DownloadSettingsRepository repository = DownloadSettingsRepository.getInstance(context);
        Single.fromCallable(() -> feedId == DEFAULTS ? repository.getDefaults() : repository.get(feedId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(settings -> callback.onSummary(summary(context, settings)),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public interface SummaryCallback {
        void onSummary(String summary);
    }

    public static String summary(Context context, ShowDownloadSettings settings) {
        String episodes = context.getResources().getQuantityString(R.plurals.download_summary_episodes,
                settings.getEpisodeCount(), settings.getEpisodeCount());
        String text;
        switch (settings.getMode()) {
            case LATEST:
                text = context.getString(R.string.download_summary_latest, episodes);
                break;
            case IN_ORDER:
                text = context.getString(R.string.download_summary_in_order, episodes);
                break;
            case BACKWARDS:
                text = context.getString(R.string.download_summary_backwards, episodes);
                break;
            case STREAM_LATEST:
                text = context.getString(R.string.download_summary_stream_latest, episodes);
                break;
            case STREAM_IN_ORDER:
                text = context.getString(R.string.download_summary_stream_in_order, episodes);
                break;
            case IGNORE:
                text = context.getString(R.string.download_mode_ignore);
                break;
            case MANUAL:
            default:
                text = context.getString(R.string.download_mode_manual);
                break;
        }
        if (settings.getKeepAtMost() > 0) {
            text = context.getString(R.string.download_summary_keep, text, settings.getKeepAtMost());
        }
        if (settings.getDeleteOlderThanDays() > 0) {
            text = context.getString(R.string.download_summary_age, text, settings.getDeleteOlderThanDays());
        }
        if (!settings.isAllowDeletions()) {
            text = context.getString(R.string.download_summary_no_deletions, text);
        }
        return text;
    }

    private static void showEditor(Context context, long feedId, ShowDownloadSettings settings, Runnable onSaved) {
        View view = LayoutInflater.from(context).inflate(R.layout.download_settings_dialog, null);
        Spinner mode = view.findViewById(R.id.download_mode);
        TextView description = view.findViewById(R.id.download_mode_description);
        TextView countLabel = view.findViewById(R.id.download_count_label);
        EditText count = view.findViewById(R.id.download_count);
        EditText keep = view.findViewById(R.id.download_keep);
        EditText age = view.findViewById(R.id.download_age);
        MaterialSwitch allowDeletions = view.findViewById(R.id.download_allow_deletions);

        List<String> labels = new ArrayList<>();
        for (DownloadMode downloadMode : MODES) {
            labels.add(modeLabel(context, downloadMode));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mode.setAdapter(adapter);
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i] == settings.getMode()) {
                mode.setSelection(i);
            }
        }
        mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                DownloadMode chosen = MODES[position];
                String text = modeDescription(context, chosen);
                description.setText(text);
                description.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                boolean usesCount = chosen.downloads() || chosen.streams();
                countLabel.setVisibility(usesCount ? View.VISIBLE : View.GONE);
                count.setVisibility(usesCount ? View.VISIBLE : View.GONE);
                countLabel.setText(chosen.streams() ? R.string.download_stream_count_label
                        : R.string.download_count_label);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        count.setText(String.valueOf(settings.getEpisodeCount()));
        keep.setText(settings.getKeepAtMost() > 0 ? String.valueOf(settings.getKeepAtMost()) : "");
        age.setText(settings.getDeleteOlderThanDays() > 0 ? String.valueOf(settings.getDeleteOlderThanDays()) : "");
        allowDeletions.setChecked(settings.isAllowDeletions());

        DownloadSettingsRepository repository = DownloadSettingsRepository.getInstance(context);
        View showActions = view.findViewById(R.id.download_show_actions);
        showActions.setVisibility(feedId == DEFAULTS ? View.GONE : View.VISIBLE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(feedId == DEFAULTS ? R.string.download_defaults_title : R.string.download_settings_title)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (d, which) -> {
                    ShowDownloadSettings updated = new ShowDownloadSettings(MODES[mode.getSelectedItemPosition()],
                            parse(count, 1), parse(keep, ShowDownloadSettings.NO_LIMIT),
                            parse(age, ShowDownloadSettings.NO_LIMIT), allowDeletions.isChecked());
                    Completable.fromAction(() -> {
                        if (feedId == DEFAULTS) {
                            repository.setDefaults(updated);
                        } else {
                            repository.set(feedId, updated);
                        }
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
                .create();

        view.findViewById(R.id.download_mark_all).setOnClickListener(v ->
                runAndToast(context, () -> repository.markAllAsDownloaded(feedId), R.string.download_mark_all_done));
        view.findViewById(R.id.download_clear_history).setOnClickListener(v ->
                runAndToast(context, () -> repository.clearDownloadHistory(feedId),
                        R.string.download_clear_history_done));
        view.findViewById(R.id.download_defaults_link).setOnClickListener(v ->
                show(context, DEFAULTS, null));
        dialog.show();
    }

    private static void runAndToast(Context context, Runnable action, int message) {
        Completable.fromAction(action::run)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show(),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static int parse(EditText field, int fallback) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String modeLabel(Context context, DownloadMode mode) {
        switch (mode) {
            case LATEST:
                return context.getString(R.string.download_mode_latest);
            case STREAM_LATEST:
                return context.getString(R.string.download_mode_stream_latest);
            case IN_ORDER:
                return context.getString(R.string.download_mode_in_order);
            case STREAM_IN_ORDER:
                return context.getString(R.string.download_mode_stream_in_order);
            case BACKWARDS:
                return context.getString(R.string.download_mode_backwards);
            case IGNORE:
                return context.getString(R.string.download_mode_ignore);
            case MANUAL:
            default:
                return context.getString(R.string.download_mode_manual);
        }
    }

    private static String modeDescription(Context context, DownloadMode mode) {
        switch (mode) {
            case IN_ORDER:
                return context.getString(R.string.download_mode_in_order_description);
            case BACKWARDS:
                return context.getString(R.string.download_mode_backwards_description);
            case STREAM_LATEST:
            case STREAM_IN_ORDER:
                return context.getString(R.string.download_mode_stream_description);
            default:
                return "";
        }
    }
}
