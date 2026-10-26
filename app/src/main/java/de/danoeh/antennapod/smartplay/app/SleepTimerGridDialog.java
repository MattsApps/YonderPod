package de.danoeh.antennapod.smartplay.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.playback.SleepTimerUpdatedEvent;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.playback.service.internal.MediaLibrarySessionCallback;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;
import de.danoeh.antennapod.storage.preferences.SleepTimerType;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;

public class SleepTimerGridDialog extends BottomSheetDialogFragment {
    private static final int[] MINUTES = {15, 30, 45, 60, 75};
    private static final int[] MINUTE_BUTTONS = {R.id.sleep_15, R.id.sleep_30, R.id.sleep_45, R.id.sleep_60,
        R.id.sleep_75};

    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.sleep_grid_dialog, container, false);
        root.findViewById(R.id.sleep_episode).setOnClickListener(v -> start(SleepTimerType.EPISODES, 1));
        for (int i = 0; i < MINUTES.length; i++) {
            int minutes = MINUTES[i];
            Button button = root.findViewById(MINUTE_BUTTONS[i]);
            button.setText(getString(R.string.sleep_grid_minutes, minutes));
            button.setOnClickListener(v -> start(SleepTimerType.CLOCK, minutes));
        }
        root.findViewById(R.id.sleep_off).setOnClickListener(v -> {
            PlaybackController.bindToMedia3Service(requireActivity(), controller -> controller.sendCustomCommand(
                    MediaLibrarySessionCallback.SESSION_COMMAND_DISABLE_SLEEP_TIMER, Bundle.EMPTY));
            dismiss();
        });
        root.findViewById(R.id.sleep_custom_start).setOnClickListener(v -> {
            EditText custom = root.findViewById(R.id.sleep_custom);
            try {
                int minutes = Integer.parseInt(custom.getText().toString().trim());
                if (minutes > 0) {
                    start(SleepTimerType.CLOCK, minutes);
                }
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), R.string.time_dialog_invalid_input, Toast.LENGTH_SHORT).show();
            }
        });
        root.findViewById(R.id.sleep_settings).setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PreferenceActivity.class);
            intent.putExtra(PreferenceActivity.OPEN_SLEEP_SETTINGS, true);
            startActivity(intent);
            dismiss();
        });
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        SleepTimerUpdatedEvent sticky = EventBus.getDefault().getStickyEvent(SleepTimerUpdatedEvent.class);
        show(sticky);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSleepTimerUpdated(SleepTimerUpdatedEvent event) {
        show(event);
    }

    private void start(SleepTimerType type, int value) {
        if (!PlaybackService.isRunning) {
            Toast.makeText(getContext(), R.string.no_media_playing_label, Toast.LENGTH_SHORT).show();
            return;
        }
        SleepTimerPreferences.setSleepTimerType(type);
        SleepTimerPreferences.setLastTimer(String.valueOf(value));
        PlaybackController.bindToMedia3Service(requireActivity(), controller -> controller.sendCustomCommand(
                MediaLibrarySessionCallback.SESSION_COMMAND_SET_SLEEP_TIMER, Bundle.EMPTY));
        dismiss();
    }

    private void show(@Nullable SleepTimerUpdatedEvent event) {
        if (root == null) {
            return;
        }
        boolean active = PlaybackService.isRunning && event != null && !event.isCancelled() && !event.isOver();
        int choice = SleepTimerPreferences.gridChoice();
        TextView status = root.findViewById(R.id.sleep_status);
        if (!active) {
            status.setText(R.string.sleep_status_off);
        } else if (choice == SleepTimerPreferences.GRID_EPISODE_ENDS) {
            status.setText(R.string.sleep_status_episode);
        } else if (choice == SleepTimerPreferences.GRID_EPISODES) {
            int episodes = (int) event.getDisplayTimeLeft();
            status.setText(getResources().getQuantityString(R.plurals.sleep_status_episodes, episodes, episodes));
        } else {
            status.setText(getString(R.string.sleep_status_minutes,
                    Converter.getDurationStringLong((int) event.getMillisTimeLeft())));
        }
        ((MaterialButton) root.findViewById(R.id.sleep_episode))
                .setChecked(active && choice == SleepTimerPreferences.GRID_EPISODE_ENDS);
        for (int i = 0; i < MINUTES.length; i++) {
            ((MaterialButton) root.findViewById(MINUTE_BUTTONS[i])).setChecked(active && choice == MINUTES[i]);
        }
        ((MaterialButton) root.findViewById(R.id.sleep_off)).setChecked(!active);
    }
}
