package de.danoeh.antennapod.smartplay.app;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class RecordShowDialog {
    private static final String TAG = "RecordShowDialog";
    private static final int MAX_KEEP = 10;

    private RecordShowDialog() {
    }

    public static void create(Context context, FeedItem station) {
        RecordingStore.Show show = new RecordingStore.Show();
        List<String> zones = zonesFor(RadioPreferences.get(station.getId()).countryCode);
        show.zoneId = zones.contains(TimeZone.getDefault().getID()) || zones.isEmpty()
                ? TimeZone.getDefault().getID() : zones.get(0);
        for (int day = Calendar.MONDAY; day <= Calendar.FRIDAY; day++) {
            show.weekdays |= ShowTimes.dayBit(day);
        }
        show.startMinute = 18 * 60;
        show.endMinute = 21 * 60;
        show.name = Recordings.defaultName(context, station.getTitle(), show);
        show(context, station, show, zones, true);
    }

    public static void edit(Context context, RecordingStore.Show show) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> {
            FeedItem station = DBReader.getFeedItem(show.stationItemId);
            if (station == null) {
                throw new IllegalStateException("Station missing");
            }
            return station;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(station -> {
                    List<String> zones = zonesFor(RadioPreferences.get(station.getId()).countryCode);
                    if (!zones.contains(show.zoneId)) {
                        zones.add(0, show.zoneId);
                    }
                    show(context, station, show, zones, false);
                }, error -> Toast.makeText(app, R.string.recording_station_missing, Toast.LENGTH_LONG).show());
    }

    private static void show(Context context, FeedItem station, RecordingStore.Show show, List<String> zones,
                             boolean isNew) {
        View view = LayoutInflater.from(context).inflate(R.layout.record_show_dialog, null);
        EditText name = view.findViewById(R.id.record_name);
        Spinner zone = view.findViewById(R.id.record_zone);
        ChipGroup days = view.findViewById(R.id.record_days);
        Button start = view.findViewById(R.id.record_start);
        Button end = view.findViewById(R.id.record_end);
        RadioGroup available = view.findViewById(R.id.record_available);
        MaterialSwitch mobile = view.findViewById(R.id.record_mobile);
        Spinner keep = view.findViewById(R.id.record_keep);

        name.setText(show.name);
        String[] autoName = {Recordings.defaultName(context, station.getTitle(), show)};
        Runnable updateName = () -> {
            String fresh = Recordings.defaultName(context, station.getTitle(), show);
            if (name.getText().toString().trim().equals(autoName[0])) {
                name.setText(fresh);
            }
            autoName[0] = fresh;
        };
        List<String> zoneLabels = new ArrayList<>();
        for (String id : zones) {
            zoneLabels.add(zoneLabel(id));
        }
        ArrayAdapter<String> zoneAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                zoneLabels);
        zoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        zone.setAdapter(zoneAdapter);
        zone.setSelection(Math.max(0, zones.indexOf(show.zoneId)));

        for (int day : ScheduleText.DAYS_FROM_MONDAY) {
            Chip chip = new Chip(context);
            chip.setCheckable(true);
            chip.setText(ScheduleText.shortDay(day));
            int bit = ShowTimes.dayBit(day);
            chip.setChecked((show.weekdays & bit) != 0);
            chip.setOnCheckedChangeListener((button, checked) -> {
                show.weekdays = checked ? show.weekdays | bit : show.weekdays & ~bit;
                updateName.run();
            });
            days.addView(chip);
        }

        Runnable updateTimes = () -> {
            start.setText(context.getString(R.string.record_starts,
                    ScheduleText.time(context, show.startMinute / 60, show.startMinute % 60)));
            end.setText(context.getString(R.string.record_ends,
                    ScheduleText.time(context, show.endMinute / 60, show.endMinute % 60)));
            updateName.run();
        };
        updateTimes.run();
        start.setOnClickListener(v -> new TimePickerDialog(context, (picker, hour, minute) -> {
            show.startMinute = hour * 60 + minute;
            updateTimes.run();
        }, show.startMinute / 60, show.startMinute % 60, DateFormat.is24HourFormat(context)).show());
        end.setOnClickListener(v -> new TimePickerDialog(context, (picker, hour, minute) -> {
            show.endMinute = hour * 60 + minute;
            updateTimes.run();
        }, show.endMinute / 60, show.endMinute % 60, DateFormat.is24HourFormat(context)).show());

        Runnable updateAvailability = () -> {
            String selected = zones.get(Math.max(0, zone.getSelectedItemPosition()));
            boolean sameClock = ShowTimes.sameClock(TimeZone.getDefault(), TimeZone.getTimeZone(selected));
            available.setVisibility(sameClock ? View.GONE : View.VISIBLE);
            ((RadioButton) view.findViewById(R.id.record_available_same_time))
                    .setText(context.getString(R.string.record_available_same_time,
                            ScheduleText.time(context, show.startMinute / 60, show.startMinute % 60)));
        };
        available.check(show.sameLocalTime ? R.id.record_available_same_time : R.id.record_available_saved);
        updateAvailability.run();
        zone.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updateAvailability.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mobile.setChecked(show.allowMobile);
        List<String> keepLabels = new ArrayList<>();
        for (int i = 1; i <= MAX_KEEP; i++) {
            keepLabels.add(context.getResources().getQuantityString(R.plurals.record_keep, i, i));
        }
        ArrayAdapter<String> keepAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                keepLabels);
        keepAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keep.setAdapter(keepAdapter);
        keep.setSelection(Math.max(0, Math.min(MAX_KEEP, show.keep) - 1));

        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(isNew ? context.getString(R.string.record_title, station.getTitle())
                        : context.getString(R.string.record_edit))
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String newName = name.getText().toString().trim();
                    show.name = newName.isEmpty() ? Recordings.defaultName(context, station.getTitle(), show)
                            : newName;
                    show.zoneId = zones.get(Math.max(0, zone.getSelectedItemPosition()));
                    show.sameLocalTime = available.getCheckedRadioButtonId() == R.id.record_available_same_time;
                    show.allowMobile = mobile.isChecked();
                    show.keep = keep.getSelectedItemPosition() + 1;
                    if (show.weekdays == 0 || show.startMinute == show.endMinute) {
                        Toast.makeText(app, R.string.record_needs_times, Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(app, R.string.radio_checking, Toast.LENGTH_SHORT).show();
                    Single.fromCallable(() -> {
                        if (isNew) {
                            Recordings.create(app, station, show);
                        } else {
                            Recordings.update(app, show);
                            Recordings.applyKeep(app, show);
                        }
                        return show;
                    })
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(saved -> afterSave(context, saved, isNew),
                                    error -> Log.e(TAG, Log.getStackTraceString(error)));
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void afterSave(Context context, RecordingStore.Show show, boolean isNew) {
        if (isNew) {
            PlayInDialog.show(context, show.feedId, show.name);
        }
        if (Recordings.usesLive(show) && !Recordings.canScheduleExact(context) && Build.VERSION.SDK_INT >= 31) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.record_alarm_title)
                    .setMessage(R.string.record_alarm_message)
                    .setPositiveButton(R.string.record_alarm_allow, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + context.getPackageName()));
                        try {
                            context.startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Could not open alarm settings", e);
                        }
                    })
                    .setNegativeButton(R.string.record_alarm_later, null)
                    .show();
        }
    }

    static List<String> zonesFor(String countryCode) {
        List<String> result = new ArrayList<>();
        if (countryCode != null && !countryCode.isEmpty()) {
            result.addAll(android.icu.util.TimeZone.getAvailableIDs(
                    android.icu.util.TimeZone.SystemTimeZoneType.CANONICAL_LOCATION,
                    countryCode.toUpperCase(Locale.ROOT), null));
        }
        if (result.isEmpty()) {
            for (String id : TimeZone.getAvailableIDs()) {
                if (id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")) {
                    result.add(id);
                }
            }
            Collections.sort(result);
        }
        return result;
    }

    static String zoneLabel(String id) {
        String city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        return city + " (" + TimeZone.getTimeZone(id).getDisplayName(false, TimeZone.SHORT) + ")";
    }
}
