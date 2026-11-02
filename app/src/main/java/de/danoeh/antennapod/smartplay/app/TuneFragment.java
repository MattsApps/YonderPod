package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment;
import de.danoeh.antennapod.ui.screen.FavoritesFragment;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.danoeh.antennapod.ui.statistics.StatisticsFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class TuneFragment extends Fragment {
    private static final String TAG = "TuneFragment";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;

    private static final class Values {
        String daily = "";
        int ownCategories;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.tune_fragment, container, false);
        root.findViewById(R.id.tuneEpisodes).setOnClickListener(v -> TendRows.open(this, new AllEpisodesFragment()));
        root.findViewById(R.id.tuneFavourites).setOnClickListener(v -> TendRows.open(this, new FavoritesFragment()));
        root.findViewById(R.id.tuneStatistics).setOnClickListener(v -> TendRows.open(this, new StatisticsFragment()));
        root.findViewById(R.id.tuneAdvanced).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), PreferenceActivity.class)));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            load();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            Values values = new Values();
            values.daily = ScheduleText.summary(app, UpdateScheduler.getDefault(app).getSchedule());
            for (String category : UpdateScheduler.getCategories()) {
                if (UpdateScheduler.getCategory(app, category).getMode() == CategoryScheduleMode.OWN) {
                    values.ownCategories++;
                }
            }
            return values;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void show(Values values) {
        if (root == null || getContext() == null) {
            return;
        }
        LinearLayout sections = root.findViewById(R.id.tuneSections);
        sections.removeAllViews();

        heading(sections, R.string.tune_updates);
        row(sections, R.string.tune_daily_update, values.daily, this::openSchedule);
        row(sections, R.string.tune_category_schedules, values.ownCategories > 0
                ? getString(R.string.tune_category_own, values.ownCategories) : "", this::openSchedule);

        heading(sections, R.string.tune_downloads);
        TextView defaults = row(sections, R.string.tune_download_defaults, "", null);
        Runnable loadDefaults = () -> DownloadSettingsDialog.loadSummary(getContext(),
                DownloadSettingsDialog.DEFAULTS, defaults::setText);
        loadDefaults.run();
        ((View) defaults.getParent()).setOnClickListener(v ->
                DownloadSettingsDialog.show(requireContext(), DownloadSettingsDialog.DEFAULTS, loadDefaults));
        row(sections, R.string.tune_auto_download, "",
                () -> openSettings(PreferenceActivity.OPEN_AUTO_DOWNLOAD_SCREEN));
        row(sections, R.string.tune_storage, "", () -> openSettings(PreferenceActivity.OPEN_DOWNLOADS_SCREEN));

        heading(sections, R.string.tune_sleep);
        Context context = requireContext();
        String bedtime = SleepTimerPreferences.autoEnable()
                ? getString(R.string.tune_range, ScheduleText.time(context, SleepTimerPreferences.autoEnableFrom(), 0),
                        ScheduleText.time(context, SleepTimerPreferences.autoEnableTo(), 0))
                : getString(R.string.tune_off);
        int stopAt = SleepTimerPreferences.stopAtMinuteOfDay();
        String stop = SleepTimerPreferences.stopAtEnabled()
                ? ScheduleText.time(requireContext(), stopAt / 60, stopAt % 60) : getString(R.string.tune_off);
        row(sections, R.string.tune_bedtime, bedtime, () -> TendRows.open(this, new SleepSettingsFragment()));
        row(sections, R.string.tune_stop_at, stop, () -> TendRows.open(this, new SleepSettingsFragment()));

        heading(sections, R.string.tune_controls);
        row(sections, R.string.tune_buttons, getString(R.string.tune_skip_value,
                UserPreferences.getRewindSecs(), UserPreferences.getFastForwardSecs()),
                () -> TendRows.open(this, new ControlsSettingsFragment()));
        row(sections, R.string.tune_playback, "", () -> openSettings(PreferenceActivity.OPEN_PLAYBACK_SCREEN));

        heading(sections, R.string.tune_look);
        look(sections);

        heading(sections, R.string.tune_backup);
        row(sections, R.string.tune_import_export, "", () -> openSettings(PreferenceActivity.OPEN_IMPORT_EXPORT));
        row(sections, R.string.tune_sync, "", () -> openSettings(PreferenceActivity.OPEN_SYNC));

        heading(sections, R.string.tune_about);
        row(sections, R.string.tune_about_app, BuildConfig.VERSION_NAME,
                () -> openSettings(PreferenceActivity.OPEN_ABOUT));
    }

    private void openSchedule() {
        TendRows.open(this, new UpdateScheduleFragment());
    }

    private void openSettings(String extra) {
        Intent intent = new Intent(requireContext(), PreferenceActivity.class);
        intent.putExtra(extra, true);
        startActivity(intent);
    }

    private void heading(LinearLayout parent, int text) {
        TextView heading = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tune_heading, parent, false);
        heading.setText(text);
        parent.addView(heading);
    }

    private TextView row(LinearLayout parent, int label, String value, @Nullable Runnable onClick) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.tune_row, parent, false);
        ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(label);
        TextView valueView = row.findViewById(R.id.tuneRowValue);
        valueView.setText(value);
        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        }
        parent.addView(row);
        return valueView;
    }

    private void look(LinearLayout sections) {
        boolean everywhere = UserPreferences.isPaletteEverywhere();
        row(sections, R.string.look_everywhere, getString(everywhere ? R.string.look_on : R.string.tune_off), () -> {
            UserPreferences.setPaletteEverywhere(!everywhere);
            LookDialogs.changed(requireActivity());
        });
        if (everywhere) {
            sections.addView(LookDialogs.strip(requireActivity(), null, UserPreferences.getPalette(),
                    LookDialogs.EVERYWHERE, false));
        } else {
            int[] labels = {R.string.world_short_books, R.string.world_short_podcasts, R.string.world_short_radio};
            for (int world = Palette.BOOKS; world <= Palette.RADIO; world++) {
                sections.addView(LookDialogs.strip(requireActivity(), getString(labels[world]),
                        UserPreferences.getWorldPalette(world), world, false));
            }
        }
        TextView advanced = new TextView(sections.getContext());
        TextViewCompat.setTextAppearance(advanced, R.style.TextAppearance_WildInk_Label);
        advanced.setText(R.string.look_advanced);
        advanced.setPadding(0, (int) (18 * getResources().getDisplayMetrics().density), 0, 0);
        sections.addView(advanced);
        boolean anyNight = false;
        for (int world = Palette.BOOKS; world <= Palette.RADIO; world++) {
            anyNight |= !Palette.nightKey(world).isEmpty();
        }
        row(sections, R.string.look_night, getString(anyNight ? R.string.look_on : R.string.tune_off),
                () -> LookDialogs.nightPalettes(requireActivity()));
        if (Palette.customSupported()) {
            row(sections, R.string.look_wallpaper, "", () -> LookDialogs.wallpaper(requireActivity(),
                    everywhere ? LookDialogs.EVERYWHERE : Palette.PODCASTS));
        }
    }
}
