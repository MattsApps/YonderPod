package de.danoeh.antennapod.smartplay.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class UpdateScheduleFragment extends Fragment {
    private static final String TAG = "UpdateScheduleFragment";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;

    private static final class CategoryRow {
        final String name;
        final SmartPlayStore.StoredSchedule stored;
        final int shows;

        CategoryRow(String name, SmartPlayStore.StoredSchedule stored, int shows) {
            this.name = name;
            this.stored = stored;
            this.shows = shows;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.update_schedule_fragment, container, false);
        root.findViewById(R.id.schedule_battery_fix).setOnClickListener(v -> fixBatteryOptimisation(getContext()));
        Context context = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> UpdateScheduler.getDefault(context).getSchedule())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(schedule -> {
                    ScheduleEditor editor = new ScheduleEditor(root.findViewById(R.id.schedule_default_editor),
                            schedule);
                    editor.setListener(updated -> save(UpdateScheduler.DEFAULT_KEY,
                            CategoryScheduleMode.OWN, updated));
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof AppCompatActivity
                && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.schedule_title);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        root.findViewById(R.id.schedule_battery_card).setVisibility(
                UpdateScheduler.isBatteryOptimised(requireContext()) ? View.VISIBLE : View.GONE);
        loadCategories();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void save(String key, CategoryScheduleMode mode, UpdateSchedule schedule) {
        Context context = requireContext().getApplicationContext();
        disposables.add(Completable.fromAction(() -> UpdateScheduler.save(context, key, mode, schedule))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    if (UpdateScheduler.DEFAULT_KEY.equals(key)) {
                        updateStatus();
                    } else {
                        loadCategories();
                    }
                    if (UpdateScheduler.isBatteryOptimised(context) && UpdateScheduler.takeBatteryPrompt(context)) {
                        showBatteryPrompt();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void updateStatus() {
        Context context = requireContext();
        disposables.add(Single.fromCallable(() -> ScheduleText.updated(context) + " · " + ScheduleText.next(context))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(text -> ((TextView) root.findViewById(R.id.schedule_status)).setText(text),
                        error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void loadCategories() {
        Context context = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            List<CategoryRow> rows = new ArrayList<>();
            for (String category : UpdateScheduler.getCategories()) {
                rows.add(new CategoryRow(category, UpdateScheduler.getCategory(context, category),
                        UpdateScheduler.countShows(category)));
            }
            return rows;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showCategories, error -> Log.e(TAG, Log.getStackTraceString(error))));
        updateStatus();
    }

    private void showCategories(List<CategoryRow> rows) {
        LinearLayout list = root.findViewById(R.id.schedule_categories);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (CategoryRow row : rows) {
            View item = inflater.inflate(R.layout.schedule_category_item, list, false);
            ((TextView) item.findViewById(R.id.schedule_category_name)).setText(row.name);
            ((TextView) item.findViewById(R.id.schedule_category_summary)).setText(categorySummary(row.stored));
            item.setOnClickListener(v -> showCategoryDialog(row));
            list.addView(item);
        }
    }

    private String categorySummary(SmartPlayStore.StoredSchedule stored) {
        switch (stored.getMode()) {
            case OWN:
                return ScheduleText.summary(requireContext(), stored.getSchedule());
            case MANUAL:
                return getString(R.string.schedule_mode_manual);
            case DEFAULT:
            default:
                return getString(R.string.schedule_mode_default);
        }
    }

    private void showCategoryDialog(CategoryRow row) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.schedule_category_dialog, null);
        RadioGroup mode = view.findViewById(R.id.category_mode);
        ScheduleEditor editor = new ScheduleEditor(view.findViewById(R.id.category_editor),
                row.stored.getSchedule());
        int checked = row.stored.getMode() == CategoryScheduleMode.OWN ? R.id.category_mode_own
                : row.stored.getMode() == CategoryScheduleMode.MANUAL ? R.id.category_mode_manual
                : R.id.category_mode_default;
        mode.check(checked);
        editor.setVisible(checked == R.id.category_mode_own);
        mode.setOnCheckedChangeListener((group, id) -> editor.setVisible(id == R.id.category_mode_own));
        TextView downloads = view.findViewById(R.id.category_download_settings);
        downloads.setText(getResources().getQuantityString(R.plurals.schedule_category_download_button,
                row.shows, row.shows));
        downloads.setVisibility(row.shows > 0 ? View.VISIBLE : View.GONE);
        downloads.setOnClickListener(v -> {
            Context context = requireContext();
            disposables.add(Single.fromCallable(() -> UpdateScheduler.showsIn(row.name))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ids -> DownloadSettingsDialog.showBulk(context, ids),
                            error -> Log.e(TAG, Log.getStackTraceString(error))));
        });
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(row.name)
                .setView(view)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    int id = mode.getCheckedRadioButtonId();
                    CategoryScheduleMode chosen = id == R.id.category_mode_own ? CategoryScheduleMode.OWN
                            : id == R.id.category_mode_manual ? CategoryScheduleMode.MANUAL
                            : CategoryScheduleMode.DEFAULT;
                    save(row.name, chosen, editor.getSchedule());
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void showBatteryPrompt() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.schedule_battery_dialog_title)
                .setMessage(R.string.schedule_battery_dialog_message)
                .setPositiveButton(R.string.schedule_battery_fix, (dialog, which) ->
                        fixBatteryOptimisation(getContext()))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void fixBatteryOptimisation(Context context) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Log.e(TAG, "No battery optimisation settings screen");
            }
        }
    }
}
