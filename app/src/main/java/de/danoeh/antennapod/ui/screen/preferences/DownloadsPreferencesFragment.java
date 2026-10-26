package de.danoeh.antennapod.ui.screen.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.smartplay.app.DownloadSettingsDialog;
import de.danoeh.antennapod.smartplay.app.ScheduleText;
import de.danoeh.antennapod.smartplay.app.UpdateScheduleFragment;
import de.danoeh.antennapod.smartplay.app.UpdateScheduler;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.preferences.screen.AnimatedPreferenceFragment;
import de.danoeh.antennapod.ui.preferences.screen.ProxyDialog;
import de.danoeh.antennapod.ui.preferences.screen.downloads.ChooseDataFolderDialog;

import java.io.File;


public class DownloadsPreferencesFragment extends AnimatedPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String PREF_SCREEN_AUTODL = "prefAutoDownloadSettings";
    private static final String PREF_SCREEN_AUTO_DELETE = "prefAutoDeleteScreen";
    private static final String PREF_PROXY = "prefProxy";
    private static final String PREF_CHOOSE_DATA_DIR = "prefChooseDataDir";
    private static final String PREF_YONDER_DEFAULTS = "prefYonderDownloadDefaults";
    private static final String PREF_YONDER_SCHEDULE = "prefYonderSchedule";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_downloads);
        setupNetworkScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((PreferenceActivity) getActivity()).getSupportActionBar().setTitle(R.string.downloads_pref);
        PreferenceManager.getDefaultSharedPreferences(getContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(getContext()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        setDataFolderText();
        Context context = getContext().getApplicationContext();
        Single.fromCallable(() -> ScheduleText.summary(context, UpdateScheduler.getDefault(context).getSchedule()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(summary -> {
                    if (findPreference(PREF_YONDER_SCHEDULE) != null) {
                        findPreference(PREF_YONDER_SCHEDULE).setSummary(summary);
                    }
                }, error -> { });
    }

    private void setupNetworkScreen() {
        findPreference(PREF_SCREEN_AUTODL).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_autodownload);
            return true;
        });
        findPreference(UserPreferences.PREF_UPDATE_INTERVAL_MINUTES).setVisible(false);
        findPreference(PREF_YONDER_SCHEDULE).setOnPreferenceClickListener(preference -> {
            getParentFragmentManager().beginTransaction()
                    .replace(getId(), new UpdateScheduleFragment())
                    .addToBackStack(getString(R.string.schedule_title))
                    .commit();
            return true;
        });
        Runnable updateDefaultsSummary = () -> DownloadSettingsDialog.loadSummary(getContext(),
                DownloadSettingsDialog.DEFAULTS, summary -> findPreference(PREF_YONDER_DEFAULTS).setSummary(summary));
        updateDefaultsSummary.run();
        findPreference(PREF_YONDER_DEFAULTS).setOnPreferenceClickListener(preference -> {
            DownloadSettingsDialog.show(getContext(), DownloadSettingsDialog.DEFAULTS, updateDefaultsSummary);
            return true;
        });
        findPreference(PREF_SCREEN_AUTO_DELETE).setOnPreferenceClickListener(preference -> {
            ((PreferenceActivity) getActivity()).openScreen(R.xml.preferences_auto_deletion);
            return true;
        });
        // validate and set correct value: number of downloads between 1 and 50 (inclusive)
        findPreference(PREF_PROXY).setOnPreferenceClickListener(preference -> {
            ProxyDialog dialog = new ProxyDialog(getActivity());
            dialog.show();
            return true;
        });
        findPreference(PREF_CHOOSE_DATA_DIR).setOnPreferenceClickListener(preference -> {
            ChooseDataFolderDialog.showDialog(getContext(), path -> {
                UserPreferences.setDataFolder(path);
                setDataFolderText();
            });
            return true;
        });
        setDataFolderText();
    }

    private void setDataFolderText() {
        File f = UserPreferences.getDataFolder(null);
        if (f != null) {
            findPreference(PREF_CHOOSE_DATA_DIR).setSummary(f.getAbsolutePath());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (UserPreferences.PREF_UPDATE_INTERVAL_MINUTES.equals(key)
                || UserPreferences.PREF_MOBILE_UPDATE.equals(key)) {
            FeedUpdateManager.getInstance().restartUpdateAlarm(getContext(), true);
        }
    }
}
