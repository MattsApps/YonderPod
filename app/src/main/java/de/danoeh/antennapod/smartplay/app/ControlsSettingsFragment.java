package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.ButtonAction;
import de.danoeh.antennapod.storage.preferences.ButtonPress;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;

public class ControlsSettingsFragment extends Fragment {
    private static final int[] THRESHOLD_MINUTES = {1, 2, 5, 15, 30, 60, 180, 360, 720, 1440, 2880, 10080};
    private static final int[] REWIND_SECONDS = {0, 2, 3, 5, 10, 15, 20, 30, 45, 60};
    private static final int[] INTERRUPTION_SECONDS = {0, 1, 2, 3, 5, 10};
    private static final String[] RESUME_MODES = {ControlsPreferences.RESUME_OFF, ControlsPreferences.RESUME_ANY,
        ControlsPreferences.RESUME_CAR};

    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.controls_settings_fragment, container, false);
        setupButtons(inflater);
        root.findViewById(R.id.controls_test_buttons).setOnClickListener(v ->
                getParentFragmentManager().beginTransaction()
                        .replace(getId(), new ButtonTesterFragment())
                        .addToBackStack(getString(R.string.button_tester_title))
                        .commit());
        setupInterruptions();
        setupSwitch(R.id.controls_fade_in, ControlsPreferences.fadeIn(), ControlsPreferences::setFadeIn);
        setupSwitch(R.id.controls_keep_screen_on, ControlsPreferences.keepScreenOn(),
                ControlsPreferences::setKeepScreenOn);
        setupSwitch(R.id.controls_show_speed, ControlsPreferences.showSpeedButton(),
                ControlsPreferences::setShowSpeedButton);
        View advanced = root.findViewById(R.id.controls_advanced);
        root.findViewById(R.id.controls_advanced_toggle).setOnClickListener(v ->
                advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        setupRewind(inflater);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof AppCompatActivity
                && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.controls_title);
        }
        showCarDevices();
    }

    private void setupButtons(LayoutInflater inflater) {
        LinearLayout list = root.findViewById(R.id.controls_buttons);
        List<String> actions = new ArrayList<>();
        for (ButtonAction action : ButtonAction.values()) {
            actions.add(actionLabel(action));
        }
        for (ButtonPress press : ButtonPress.values()) {
            Spinner spinner = addRow(list, pressLabel(press));
            spinner.setAdapter(adapter(actions));
            spinner.setSelection(ControlsPreferences.getButtonAction(press).ordinal());
            spinner.setOnItemSelectedListener(listener(position ->
                    ControlsPreferences.setButtonAction(press, ButtonAction.values()[position])));
        }
    }

    private void setupInterruptions() {
        List<String> notificationOptions = new ArrayList<>();
        notificationOptions.add(getString(R.string.controls_notifications_pause));
        notificationOptions.add(getString(R.string.controls_notifications_duck));
        Spinner notifications = root.findViewById(R.id.controls_notifications);
        notifications.setAdapter(adapter(notificationOptions));
        notifications.setSelection(ControlsPreferences.duckNotifications() ? 1 : 0);
        notifications.setOnItemSelectedListener(listener(position ->
                ControlsPreferences.setDuckNotifications(position == 1)));

        List<String> resumeOptions = new ArrayList<>();
        resumeOptions.add(getString(R.string.controls_reconnect_off));
        resumeOptions.add(getString(R.string.controls_reconnect_any));
        resumeOptions.add(getString(R.string.controls_reconnect_car));
        Spinner reconnect = root.findViewById(R.id.controls_reconnect);
        reconnect.setAdapter(adapter(resumeOptions));
        int selected = 0;
        for (int i = 0; i < RESUME_MODES.length; i++) {
            if (RESUME_MODES[i].equals(ControlsPreferences.getResumeOnReconnect())) {
                selected = i;
            }
        }
        reconnect.setSelection(selected);
        reconnect.setOnItemSelectedListener(listener(position -> {
            ControlsPreferences.setResumeOnReconnect(RESUME_MODES[position]);
            showCarDevices();
        }));
    }

    private void showCarDevices() {
        View car = root.findViewById(R.id.controls_car);
        boolean carOnly = ControlsPreferences.RESUME_CAR.equals(ControlsPreferences.getResumeOnReconnect());
        car.setVisibility(carOnly ? View.VISIBLE : View.GONE);
        LinearLayout list = root.findViewById(R.id.controls_car_list);
        list.removeAllViews();
        Set<String> known = new TreeSet<>(ControlsPreferences.getKnownDevices());
        Set<String> chosen = ControlsPreferences.getCarDevices();
        if (known.isEmpty()) {
            TextView none = new TextView(requireContext());
            none.setText(R.string.controls_car_none);
            list.addView(none);
            return;
        }
        for (String name : known) {
            CheckBox box = new CheckBox(requireContext());
            box.setText(name);
            box.setChecked(chosen.contains(name));
            box.setOnCheckedChangeListener((button, checked) -> {
                Set<String> devices = ControlsPreferences.getCarDevices();
                if (checked) {
                    devices.add(name);
                } else {
                    devices.remove(name);
                }
                ControlsPreferences.setCarDevices(devices);
            });
            list.addView(box);
        }
    }

    private void setupRewind(LayoutInflater inflater) {
        setupSwitch(R.id.controls_rewind, ControlsPreferences.isRewindEnabled(), ControlsPreferences::setRewindEnabled);
        LinearLayout tiers = root.findViewById(R.id.controls_rewind_tiers);
        List<String> thresholds = new ArrayList<>();
        for (int minutes : THRESHOLD_MINUTES) {
            thresholds.add(durationLabel(minutes));
        }
        List<String> amounts = new ArrayList<>();
        for (int seconds : REWIND_SECONDS) {
            amounts.add(getString(R.string.controls_seconds, seconds));
        }
        for (int tier = 0; tier < ControlsPreferences.TIERS; tier++) {
            int tierIndex = tier;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView after = new TextView(requireContext());
            after.setText(R.string.controls_rewind_after);
            row.addView(after);
            Spinner threshold = new Spinner(requireContext());
            threshold.setAdapter(adapter(thresholds));
            threshold.setSelection(indexOf(THRESHOLD_MINUTES, ControlsPreferences.getRewindThresholdMinutes(tier)));
            threshold.setOnItemSelectedListener(listener(position ->
                    ControlsPreferences.setRewindThresholdMinutes(tierIndex, THRESHOLD_MINUTES[position])));
            row.addView(threshold, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView back = new TextView(requireContext());
            back.setText(R.string.controls_rewind_back);
            row.addView(back);
            Spinner amount = new Spinner(requireContext());
            amount.setAdapter(adapter(amounts));
            amount.setSelection(indexOf(REWIND_SECONDS, ControlsPreferences.getRewindSeconds(tier)));
            amount.setOnItemSelectedListener(listener(position ->
                    ControlsPreferences.setRewindSeconds(tierIndex, REWIND_SECONDS[position])));
            row.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            tiers.addView(row);
        }
        List<String> interruption = new ArrayList<>();
        for (int seconds : INTERRUPTION_SECONDS) {
            interruption.add(getString(R.string.controls_seconds, seconds));
        }
        Spinner interruptionBack = root.findViewById(R.id.controls_interruption_back);
        interruptionBack.setAdapter(adapter(interruption));
        interruptionBack.setSelection(indexOf(INTERRUPTION_SECONDS, ControlsPreferences.getInterruptionBackSeconds()));
        interruptionBack.setOnItemSelectedListener(listener(position ->
                ControlsPreferences.setInterruptionBackSeconds(INTERRUPTION_SECONDS[position])));
    }

    private Spinner addRow(LinearLayout list, String label) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = new TextView(requireContext());
        text.setText(label);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Spinner spinner = new Spinner(requireContext());
        row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        list.addView(row);
        return spinner;
    }

    private String durationLabel(int minutes) {
        if (minutes >= 1440 && minutes % 1440 == 0) {
            return getResources().getQuantityString(R.plurals.controls_days, minutes / 1440, minutes / 1440);
        } else if (minutes >= 60 && minutes % 60 == 0) {
            return getResources().getQuantityString(R.plurals.controls_hours, minutes / 60, minutes / 60);
        }
        return getString(R.string.controls_minutes, minutes);
    }

    private String pressLabel(ButtonPress press) {
        switch (press) {
            case DOUBLE:
                return getString(R.string.controls_press_double);
            case TRIPLE:
                return getString(R.string.controls_press_triple);
            case LONG:
                return getString(R.string.controls_press_long);
            case NEXT:
                return getString(R.string.controls_press_next);
            case PREVIOUS:
                return getString(R.string.controls_press_previous);
            case SINGLE:
            default:
                return getString(R.string.controls_press_single);
        }
    }

    private String actionLabel(ButtonAction action) {
        switch (action) {
            case SKIP_FORWARD:
                return getString(R.string.controls_action_skip_forward);
            case SKIP_BACK:
                return getString(R.string.controls_action_skip_back);
            case NEXT_EPISODE:
                return getString(R.string.controls_action_next);
            case PREVIOUS_EPISODE:
                return getString(R.string.controls_action_previous);
            case REBUILD_SMART_PLAY:
                return getString(R.string.controls_action_rebuild);
            case PLAY_PAUSE:
            default:
                return getString(R.string.controls_action_play_pause);
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private void setupSwitch(int id, boolean value, BooleanSetter setter) {
        MaterialSwitch view = root.findViewById(id);
        view.setChecked(value);
        view.setOnCheckedChangeListener((button, checked) -> setter.set(checked));
    }

    private ArrayAdapter<String> adapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private interface PositionListener {
        void onSelected(int position);
    }

    private static AdapterView.OnItemSelectedListener listener(PositionListener positionListener) {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                positionListener.onSelected(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
    }
}
