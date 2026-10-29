package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.radio.RewindWindow;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class RadioStationDialog {
    private static final String TAG = "RadioStationDialog";

    private RadioStationDialog() {
    }

    public static void show(Context context, FeedItem station, Runnable onSaved) {
        View view = LayoutInflater.from(context).inflate(R.layout.radio_station_dialog, null);
        EditText name = view.findViewById(R.id.station_name);
        EditText logo = view.findViewById(R.id.station_logo);
        RadioGroup quality = view.findViewById(R.id.station_quality);
        TextView details = view.findViewById(R.id.station_details);
        TextView rewind = view.findViewById(R.id.station_rewind);
        RadioPreferences.Station saved = RadioPreferences.get(station.getId());
        name.setText(station.getTitle());
        logo.setText(station.getImageUrl());
        switch (saved.quality) {
            case LOW:
                quality.check(R.id.station_quality_low);
                break;
            case HIGH:
                quality.check(R.id.station_quality_high);
                break;
            case STANDARD:
            default:
                quality.check(R.id.station_quality_standard);
                break;
        }
        String detailText = RadioFragment.details(context, saved);
        details.setText(detailText);
        details.setVisibility(detailText.isEmpty() ? View.GONE : View.VISIBLE);
        rewind.setText(R.string.radio_rewind_checking);
        String streamUrl = station.getMedia() != null ? station.getMedia().getDownloadUrl() : null;
        Disposable check = streamUrl == null ? null : Single.fromCallable(() -> StreamCheck.check(streamUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> rewind.setText(rewindText(context, result.rewind)),
                        error -> rewind.setText(R.string.radio_rewind_unknown));
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.radio_edit)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String newName = name.getText().toString().trim();
                    String newLogo = logo.getText().toString().trim();
                    RadioPreferences.Quality chosen;
                    int checked = quality.getCheckedRadioButtonId();
                    if (checked == R.id.station_quality_low) {
                        chosen = RadioPreferences.Quality.LOW;
                    } else if (checked == R.id.station_quality_high) {
                        chosen = RadioPreferences.Quality.HIGH;
                    } else {
                        chosen = RadioPreferences.Quality.STANDARD;
                    }
                    Completable.fromAction(() -> Radio.update(station,
                                    newName.isEmpty() ? station.getTitle() : newName, newLogo, chosen))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(onSaved::run, error -> Log.e(TAG, Log.getStackTraceString(error)));
                })
                .setNegativeButton(R.string.cancel_label, null)
                .setOnDismissListener(dialog -> {
                    if (check != null) {
                        check.dispose();
                    }
                })
                .show();
    }

    static String rewindText(Context context, RewindWindow window) {
        switch (window.getKind()) {
            case REWIND:
                return context.getString(R.string.radio_rewind_window, Converter.getDurationStringLocalized(
                        context.getResources(), window.getSeconds() * 1000, false));
            case LIVE_ONLY:
                return context.getString(R.string.radio_rewind_none);
            case NOT_LIVE:
                return context.getString(R.string.radio_rewind_not_live);
            case UNKNOWN:
            default:
                return context.getString(R.string.radio_rewind_unknown);
        }
    }
}
