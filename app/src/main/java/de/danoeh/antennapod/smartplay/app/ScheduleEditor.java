package de.danoeh.antennapod.smartplay.app;

import android.app.TimePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.TimeZone;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.ScheduleTimes;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;

public class ScheduleEditor {
    public interface Listener {
        void onChanged(UpdateSchedule schedule);
    }

    private final View root;
    private final Button time;
    private final TextView window;
    private final TextView daysLabel;
    private final ChipGroup days;
    private final MaterialSwitch wifiOnly;
    private final MaterialSwitch charging;
    private final View advanced;
    private final MaterialSwitch everyN;
    private final TextView everyNLabel;
    private final EditText everyNDays;
    private final Chip[] dayChips = new Chip[7];
    private UpdateSchedule schedule;
    private Listener listener;
    private boolean binding;

    public ScheduleEditor(View root, UpdateSchedule initial) {
        this.root = root;
        this.schedule = initial;
        time = root.findViewById(R.id.editor_time);
        window = root.findViewById(R.id.editor_window);
        daysLabel = root.findViewById(R.id.editor_days_label);
        days = root.findViewById(R.id.editor_days);
        wifiOnly = root.findViewById(R.id.editor_wifi_only);
        charging = root.findViewById(R.id.editor_charging);
        advanced = root.findViewById(R.id.editor_advanced);
        everyN = root.findViewById(R.id.editor_every_n);
        everyNLabel = root.findViewById(R.id.editor_every_n_label);
        everyNDays = root.findViewById(R.id.editor_every_n_days);

        for (int i = 0; i < 7; i++) {
            Chip chip = new Chip(root.getContext());
            chip.setCheckable(true);
            chip.setText(ScheduleText.shortDay(ScheduleText.DAYS_FROM_MONDAY[i]));
            chip.setOnCheckedChangeListener((button, checked) -> onEdited());
            dayChips[i] = chip;
            days.addView(chip);
        }
        time.setOnClickListener(v -> new TimePickerDialog(root.getContext(), (picker, hour, minute) -> {
            schedule = schedule.withTime(hour, minute);
            bind();
            notifyChanged();
        }, schedule.getHour(), schedule.getMinute(), DateFormat.is24HourFormat(root.getContext())).show());
        wifiOnly.setOnCheckedChangeListener((button, checked) -> onEdited());
        charging.setOnCheckedChangeListener((button, checked) -> onEdited());
        everyN.setOnCheckedChangeListener((button, checked) -> onEdited());
        everyNDays.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onEdited();
            }
        });
        root.findViewById(R.id.editor_advanced_toggle).setOnClickListener(v ->
                advanced.setVisibility(advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        if (schedule.getEveryNDays() >= 2) {
            advanced.setVisibility(View.VISIBLE);
        }
        bind();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public UpdateSchedule getSchedule() {
        return schedule;
    }

    public void setVisible(boolean visible) {
        root.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void bind() {
        binding = true;
        time.setText(ScheduleText.time(root.getContext(), schedule.getHour(), schedule.getMinute()));
        window.setText(ScheduleText.window(root.getContext(), schedule));
        for (int i = 0; i < 7; i++) {
            dayChips[i].setChecked(schedule.runsOn(ScheduleText.DAYS_FROM_MONDAY[i]));
        }
        wifiOnly.setChecked(schedule.isWifiOnly());
        charging.setChecked(schedule.isChargingOnly());
        boolean useEveryN = schedule.getEveryNDays() >= 2;
        everyN.setChecked(useEveryN);
        if (useEveryN && !String.valueOf(schedule.getEveryNDays()).equals(everyNDays.getText().toString())) {
            everyNDays.setText(String.valueOf(schedule.getEveryNDays()));
        }
        updateDayVisibility(useEveryN);
        binding = false;
    }

    private void updateDayVisibility(boolean useEveryN) {
        daysLabel.setVisibility(useEveryN ? View.GONE : View.VISIBLE);
        days.setVisibility(useEveryN ? View.GONE : View.VISIBLE);
        everyNLabel.setVisibility(useEveryN ? View.VISIBLE : View.GONE);
        everyNDays.setVisibility(useEveryN ? View.VISIBLE : View.GONE);
    }

    private void onEdited() {
        if (binding) {
            return;
        }
        int weekdays = 0;
        for (int i = 0; i < 7; i++) {
            if (dayChips[i].isChecked()) {
                weekdays |= UpdateSchedule.dayBit(ScheduleText.DAYS_FROM_MONDAY[i]);
            }
        }
        int n = 0;
        if (everyN.isChecked()) {
            try {
                n = Math.max(2, Integer.parseInt(everyNDays.getText().toString().trim()));
            } catch (NumberFormatException e) {
                n = 2;
            }
        }
        long anchor = n == schedule.getEveryNDays() ? schedule.getAnchorDay()
                : ScheduleTimes.epochDay(System.currentTimeMillis(), TimeZone.getDefault());
        schedule = schedule.withWeekdays(weekdays)
                .withEveryNDays(n, anchor)
                .withWifiOnly(wifiOnly.isChecked())
                .withChargingOnly(charging.isChecked());
        updateDayVisibility(n >= 2);
        if (n >= 2 && everyNDays.getText().toString().trim().isEmpty()) {
            binding = true;
            everyNDays.setText(String.valueOf(n));
            binding = false;
        }
        notifyChanged();
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onChanged(schedule);
        }
    }
}
