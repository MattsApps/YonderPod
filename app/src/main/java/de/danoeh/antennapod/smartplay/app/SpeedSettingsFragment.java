package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.PlaybackSpeedFeedSettingDialogBinding;
import de.danoeh.antennapod.event.settings.SoundDefaultsChangedEvent;
import de.danoeh.antennapod.event.settings.UsualSpeedChangedEvent;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

import org.greenrobot.eventbus.EventBus;

/**
 * Tune › controls › Speed and silences (coding thread 18): the usual playback speed and the usual skip silence,
 * for shows left on usual, and "Show the speed button" for the player (moved here from Buttons and rewind).
 * Each is set once and the page holds nothing else, so all three sit up front.
 */
public class SpeedSettingsFragment extends Fragment {
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.speed_settings_fragment, container, false);
        View row = root.findViewById(R.id.speed_usual_row);
        ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(R.string.speed_usual);
        row.setOnClickListener(v -> chooseSpeed());
        showSpeed();
        MaterialSwitch skipSilence = root.findViewById(R.id.speed_skip_silence);
        skipSilence.setChecked(UserPreferences.isSkipSilence());
        skipSilence.setOnCheckedChangeListener((button, checked) -> {
            UserPreferences.setSkipSilence(checked);
            EventBus.getDefault().post(new SoundDefaultsChangedEvent());
        });
        MaterialSwitch showButton = root.findViewById(R.id.speed_show_button);
        showButton.setChecked(ControlsPreferences.showSpeedButton());
        showButton.setOnCheckedChangeListener((button, checked) -> ControlsPreferences.setShowSpeedButton(checked));
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof AppCompatActivity
                && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.tune_speed_silences);
        }
        showSpeed();
    }

    private void showSpeed() {
        if (root == null) {
            return;
        }
        ((TextView) root.findViewById(R.id.speed_usual_row).findViewById(R.id.tuneRowValue)).setText(
                String.format(Locale.getDefault(), "%.2fx", UserPreferences.getPlaybackSpeed()));
    }

    /** The usual speed: heard now if the playing show is left on usual (a show with its own speed keeps it). */
    private void chooseSpeed() {
        PlaybackSpeedFeedSettingDialogBinding binding = PlaybackSpeedFeedSettingDialogBinding.inflate(
                getLayoutInflater());
        binding.useGlobalCheckbox.setVisibility(View.GONE);
        float speed = UserPreferences.getPlaybackSpeed();
        binding.seekBar.updateSpeed(speed);
        binding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", speed));
        binding.seekBar.setProgressChangedListener(chosen ->
                binding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", chosen)));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.speed_usual)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.yonder_button_ok, (dialog, which) -> {
                    UserPreferences.setPlaybackSpeed(binding.seekBar.getCurrentSpeed());
                    EventBus.getDefault().post(new UsualSpeedChangedEvent());
                    showSpeed();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }
}
