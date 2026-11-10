package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class BeyondPodImportFlow {
    private static final String TAG = "BeyondPodImportFlow";

    private BeyondPodImportFlow() {
    }

    public static void open(Fragment fragment, Uri uri) {
        Context app = fragment.requireContext().getApplicationContext();
        Single.fromCallable(() -> BeyondPodImport.read(app, uri))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(summary -> {
                    if (fragment.isAdded()) {
                        confirm(fragment, summary);
                    }
                }, error -> {
                    Log.e(TAG, "Can't read BeyondPod backup: " + error.getClass().getSimpleName());
                    ImportFlow.toast(fragment, fragment.getString(R.string.import_failed, error.getMessage()));
                });
    }

    static String describe(Context context, BeyondPodImport.Summary summary) {
        List<String> lines = new ArrayList<>();
        if (summary.created > 0) {
            lines.add(context.getString(R.string.beyondpod_from, DateUtils.formatDateTime(context, summary.created,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR)));
        }
        int followed = 0;
        for (BeyondPodImport.Show show : summary.shows) {
            followed += show.alreadyFollowed ? 1 : 0;
        }
        String shows = context.getResources().getQuantityString(R.plurals.restore_shows, summary.shows.size(),
                summary.shows.size());
        int privateFeeds = BeyondPodImport.privateCount(summary);
        if (privateFeeds > 0) {
            shows = context.getString(R.string.beyondpod_private, shows, privateFeeds);
        }
        lines.add(shows);
        List<String> modes = new ArrayList<>();
        for (Map.Entry<DownloadMode, Integer> entry : BeyondPodImport.modeCounts(summary).entrySet()) {
            modes.add(context.getString(R.string.beyondpod_mode_count, entry.getValue(),
                    DownloadSettingsDialog.modeLabel(context, entry.getKey())));
        }
        if (!modes.isEmpty()) {
            lines.add(context.getString(R.string.beyondpod_modes,
                    TextUtils.join(context.getString(R.string.list_separator), modes)));
        }
        for (BeyondPodImport.SmartPlay list : summary.smartPlays) {
            lines.add(context.getResources().getQuantityString(R.plurals.beyondpod_smart_play, list.rules.size(),
                    list.name.isEmpty() ? "BeyondPod" : list.name, list.rules.size()));
        }
        List<String> settings = new ArrayList<>();
        if (summary.forwardSeconds > 0 && summary.backSeconds > 0) {
            settings.add(context.getString(R.string.beyondpod_skips, summary.forwardSeconds, summary.backSeconds));
        }
        if (summary.updateMinute >= 0) {
            settings.add(context.getString(R.string.beyondpod_update_time,
                    ScheduleText.time(context, summary.updateMinute / 60, summary.updateMinute % 60)));
        }
        if (Boolean.TRUE.equals(summary.wifiOnly)) {
            settings.add(context.getString(R.string.beyondpod_wifi_only));
        }
        if (Boolean.TRUE.equals(summary.chargingOnly)) {
            settings.add(context.getString(R.string.beyondpod_charging_only));
        }
        settings.add(context.getString(R.string.beyondpod_defaults, summary.defaults.getEpisodeCount(),
                summary.defaults.getKeepAtMost()));
        lines.add(context.getString(R.string.beyondpod_settings,
                TextUtils.join(context.getString(R.string.list_separator), settings)));
        if (followed > 0) {
            lines.add(context.getResources().getQuantityString(R.plurals.beyondpod_already, followed, followed));
        }
        return TextUtils.join("\n\n", lines);
    }

    private static void confirm(Fragment fragment, BeyondPodImport.Summary summary) {
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.beyondpod_title)
                .setMessage(describe(fragment.requireContext(), summary))
                .setPositiveButton(R.string.beyondpod_import, (dialog, which) -> apply(fragment, summary))
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    private static void apply(Fragment fragment, BeyondPodImport.Summary summary) {
        Context app = fragment.requireContext().getApplicationContext();
        AlertDialog working = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setMessage(R.string.beyondpod_working)
                .setCancelable(false)
                .show();
        Completable.fromAction(() -> BeyondPodImport.apply(app, summary))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    working.dismiss();
                    if (fragment.isAdded()) {
                        new MaterialAlertDialogBuilder(fragment.requireContext())
                                .setTitle(R.string.beyondpod_done_title)
                                .setMessage(R.string.beyondpod_done)
                                .setPositiveButton(R.string.yonder_button_ok, null)
                                .show();
                    }
                }, error -> {
                    working.dismiss();
                    Log.e(TAG, "BeyondPod import failed: " + error.getClass().getSimpleName());
                    ImportFlow.toast(fragment, fragment.getString(R.string.import_failed, error.getMessage()));
                });
    }
}
