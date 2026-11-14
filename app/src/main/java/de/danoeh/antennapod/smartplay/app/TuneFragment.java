package de.danoeh.antennapod.smartplay.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.Locale;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
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
        row(sections, R.string.tune_when_episode_ends, getString(!NextUpPreferences.isContinuousAsked()
                ? R.string.tune_next_not_chosen : NextUpPreferences.keepPlaying()
                ? R.string.tune_next_keep : R.string.tune_next_choose), this::chooseWhenEpisodeEnds);
        row(sections, R.string.tune_half_played, getString(NextUpPreferences.includeHalfPlayed()
                ? R.string.tune_half_included : R.string.tune_half_left_out), this::chooseHalfPlayed);
        row(sections, R.string.tune_playback, "", () -> openSettings(PreferenceActivity.OPEN_PLAYBACK_SCREEN));

        heading(sections, R.string.tune_worlds);
        worlds(sections);

        heading(sections, R.string.tune_look);
        look(sections);
        row(sections, R.string.tune_language, language(), this::openLanguage);

        heading(sections, R.string.tune_backup);
        row(sections, R.string.tune_import_export, "", () -> TendRows.open(this, new BackupFragment()));
        row(sections, R.string.tune_sync, "", () -> openSettings(PreferenceActivity.OPEN_SYNC));

        heading(sections, R.string.tune_help);
        row(sections, R.string.tune_welcome, "", () -> WelcomeActivity.show(requireActivity()));
        row(sections, R.string.moving_title, "", () -> TendRows.open(this, new MovingFragment()));

        heading(sections, R.string.tune_about);
        row(sections, R.string.tune_about_app, BuildConfig.VERSION_NAME,
                () -> TendRows.open(this, new AboutFragment()));
        tip(sections);
    }

    /** "tip", or "supporter until …" after a tip, so the tip sheet can always be reached. */
    private void tip(LinearLayout sections) {
        if (!TipShop.canTip(requireContext())) {
            return;
        }
        Tip.checkOnce(requireContext());
        String until = Tip.isSupporter(requireContext()) ? Tip.supporterUntilText(requireContext()) : null;
        TextView value = row(sections, Tip.isSupporter(requireContext()) ? R.string.tune_supporter : R.string.tune_tip,
                until != null ? getString(R.string.tune_supporter_until, until) : "", () -> TipShop.open(this));
        ((View) value.getParent()).setOnLongClickListener(v -> {
            tipTesting();
            return true;
        });
    }

    /** Hidden switches for testing tip me. They only change what the wordmark shows, never unlock anything. */
    private void tipTesting() {
        Context context = requireContext();
        CharSequence[] choices = BuildConfig.DEBUG
                ? new CharSequence[] {getString(R.string.tip_test_jump), getString(R.string.tip_test_forget),
                    getString(R.string.tip_test_normal), getString(R.string.tip_test_year_out)}
                : new CharSequence[] {getString(R.string.tip_test_jump), getString(R.string.tip_test_forget),
                    getString(R.string.tip_test_normal)};
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tip_test_title)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        Tip.testJumpMonth(context);
                    } else if (which == 1) {
                        Tip.testForgetTip(context);
                    } else if (which == 2) {
                        Tip.testBackToNormal(context);
                    } else {
                        Tip.testYearRanOut(context);
                    }
                    load();
                })
                .show();
    }

    private String language() {
        Locale locale = ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0);
        return locale == null ? "" : locale.getDisplayName(locale);
    }

    private void openLanguage() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                startActivity(new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                        Uri.fromParts("package", requireContext().getPackageName(), null)));
                return;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "No language setting for apps");
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tune_language)
                .setMessage(R.string.tune_language_older)
                .setPositiveButton(R.string.yonder_button_ok, null)
                .show();
    }

    private void openSchedule() {
        TendRows.open(this, new UpdateScheduleFragment());
    }

    private void openSettings(String extra) {
        Intent intent = new Intent(requireContext(), PreferenceActivity.class);
        intent.putExtra(extra, true);
        startActivity(intent);
    }

    /** Keep playing or I'll choose: AntennaPod's continuous playback setting, reused. */
    private void chooseWhenEpisodeEnds() {
        String[] options = {getString(R.string.tune_next_keep), getString(R.string.tune_next_choose)};
        int checked = !NextUpPreferences.isContinuousAsked() ? -1 : NextUpPreferences.keepPlaying() ? 0 : 1;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tune_when_episode_ends)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    NextUpPreferences.setKeepPlaying(which == 0, false);
                    dialog.dismiss();
                    load();
                })
                .show();
    }

    private void chooseHalfPlayed() {
        String[] options = {getString(R.string.next_up_half_include), getString(R.string.next_up_half_leave_out)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.next_up_half_title)
                .setMessage(R.string.next_up_half_message)
                .setPositiveButton(R.string.next_up_half_include, (dialog, which) -> {
                    NextUpPreferences.setIncludeHalfPlayed(true);
                    load();
                })
                .setNegativeButton(R.string.next_up_half_leave_out, (dialog, which) -> {
                    NextUpPreferences.setIncludeHalfPlayed(false);
                    load();
                })
                .show();
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

    private void worlds(LinearLayout sections) {
        for (int world : new int[] {WorldPages.BOOKS, WorldPages.RADIO}) {
            boolean shown = UserPreferences.isWorldShown(world);
            row(sections, world == WorldPages.BOOKS ? R.string.world_books : R.string.world_radio,
                    getString(shown ? R.string.look_on : R.string.tune_off), () -> {
                        UserPreferences.setWorldShown(world, !shown);
                        load();
                    });
        }
        for (boolean right : new boolean[] {false, true}) {
            List<AppShelves.App> apps = AppShelves.installed(requireContext(), right);
            String value = apps.isEmpty() ? getString(R.string.worlds_edge_none)
                    : apps.size() == 1 ? apps.get(0).label.toString()
                    : getResources().getQuantityString(R.plurals.shelf_count, apps.size(), apps.size());
            row(sections, right ? R.string.worlds_right_edge : R.string.worlds_left_edge, value,
                    () -> TendRows.open(this, AppShelfFragment.newInstance(right)));
        }
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
                if (!UserPreferences.isWorldShown(world)) {
                    continue;
                }
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
