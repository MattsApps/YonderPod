package de.danoeh.antennapod.smartplay.app;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.SleepTimerPreferences;

public class SleepSettingsFragment extends Fragment {
    private static final int[] BEDTIME_MINUTES = {SleepTimerPreferences.EPISODE_ENDS, 15, 30, 45, 60, 75};

    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.sleep_settings_fragment, container, false);

        MaterialSwitch shake = root.findViewById(R.id.sleep_shake);
        shake.setChecked(SleepTimerPreferences.shakeToReset());
        shake.setOnCheckedChangeListener((button, checked) -> SleepTimerPreferences.setShakeToReset(checked));
        MaterialSwitch vibrate = root.findViewById(R.id.sleep_vibrate);
        vibrate.setChecked(SleepTimerPreferences.vibrate());
        vibrate.setOnCheckedChangeListener((button, checked) -> SleepTimerPreferences.setVibrate(checked));

        setupStopAt();
        setupBedtime();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof AppCompatActivity
                && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.sleep_settings_title);
        }
    }

    private void setupStopAt() {
        MaterialSwitch enabled = root.findViewById(R.id.sleep_stop_at);
        View details = root.findViewById(R.id.sleep_stop_at_details);
        Button time = root.findViewById(R.id.sleep_stop_at_time);
        enabled.setChecked(SleepTimerPreferences.stopAtEnabled());
        details.setVisibility(enabled.isChecked() ? View.VISIBLE : View.GONE);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            SleepTimerPreferences.setStopAtEnabled(checked);
            details.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        updateStopAtTime(time);
        time.setOnClickListener(v -> {
            int minuteOfDay = SleepTimerPreferences.stopAtMinuteOfDay();
            new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
                SleepTimerPreferences.setStopAtMinuteOfDay(hour * 60 + minute);
                SleepTimerPreferences.setStopAtEnabled(true);
                enabled.setChecked(true);
                updateStopAtTime(time);
            }, minuteOfDay / 60, minuteOfDay % 60, DateFormat.is24HourFormat(requireContext())).show();
        });

        ChipGroup days = root.findViewById(R.id.sleep_stop_days);
        for (int day : ScheduleText.DAYS_FROM_MONDAY) {
            Chip chip = new Chip(requireContext());
            chip.setCheckable(true);
            chip.setText(ScheduleText.shortDay(day));
            int bit = 1 << (day - 1);
            chip.setChecked((SleepTimerPreferences.stopAtDays() & bit) != 0);
            chip.setOnCheckedChangeListener((button, checked) -> {
                int current = SleepTimerPreferences.stopAtDays();
                SleepTimerPreferences.setStopAtDays(checked ? current | bit : current & ~bit);
            });
            days.addView(chip);
        }

        RadioGroup mode = root.findViewById(R.id.sleep_stop_mode);
        mode.check(SleepTimerPreferences.stopAtFinishEpisode() ? R.id.sleep_stop_finish : R.id.sleep_stop_on_dot);
        mode.setOnCheckedChangeListener((group, id) ->
                SleepTimerPreferences.setStopAtFinishEpisode(id == R.id.sleep_stop_finish));
    }

    private void updateStopAtTime(Button time) {
        int minuteOfDay = SleepTimerPreferences.stopAtMinuteOfDay();
        time.setText(ScheduleText.time(requireContext(), minuteOfDay / 60, minuteOfDay % 60));
    }

    private void setupBedtime() {
        MaterialSwitch enabled = root.findViewById(R.id.sleep_bedtime);
        View details = root.findViewById(R.id.sleep_bedtime_details);
        enabled.setChecked(SleepTimerPreferences.autoEnable());
        details.setVisibility(enabled.isChecked() ? View.VISIBLE : View.GONE);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            SleepTimerPreferences.setAutoEnable(checked);
            details.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        List<String> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hours.add(ScheduleText.time(requireContext(), hour, 0));
        }
        Spinner from = root.findViewById(R.id.sleep_bedtime_from);
        Spinner to = root.findViewById(R.id.sleep_bedtime_to);
        from.setAdapter(adapter(hours));
        to.setAdapter(adapter(hours));
        from.setSelection(SleepTimerPreferences.autoEnableFrom());
        to.setSelection(SleepTimerPreferences.autoEnableTo());
        from.setOnItemSelectedListener(listener(SleepTimerPreferences::setAutoEnableFrom));
        to.setOnItemSelectedListener(listener(SleepTimerPreferences::setAutoEnableTo));

        List<String> timers = new ArrayList<>();
        int selected = 0;
        for (int i = 0; i < BEDTIME_MINUTES.length; i++) {
            timers.add(BEDTIME_MINUTES[i] == SleepTimerPreferences.EPISODE_ENDS
                    ? getString(R.string.sleep_grid_episode)
                    : getString(R.string.sleep_grid_minutes, BEDTIME_MINUTES[i]));
            if (BEDTIME_MINUTES[i] == SleepTimerPreferences.bedtimeMinutes()) {
                selected = i;
            }
        }
        Spinner timer = root.findViewById(R.id.sleep_bedtime_timer);
        timer.setAdapter(adapter(timers));
        timer.setSelection(selected);
        timer.setOnItemSelectedListener(listener(position ->
                SleepTimerPreferences.setBedtimeMinutes(BEDTIME_MINUTES[position])));
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
