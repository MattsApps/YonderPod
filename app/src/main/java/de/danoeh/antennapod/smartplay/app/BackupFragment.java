package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.backup.BackupFiles;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BackupFragment extends Fragment {
    private static final String TAG = "BackupFragment";
    private static final int[] KEEP_CHOICES = {1, 3, 5, 10, 20};

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;
    private boolean backingUp = false;

    private final ActivityResultLauncher<String> createBackup = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"), this::backUpTo);
    private final ActivityResultLauncher<String[]> pickRestore = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    ImportFlow.open(this, uri);
                }
            });
    private final ActivityResultLauncher<Uri> pickFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::folderChosen);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.backup_fragment, container, false);
        ((TextView) root.findViewById(R.id.pageTitle)).setText(R.string.backup_title);
        ((TextView) root.findViewById(R.id.pageIntro)).setText(R.string.backup_intro);
        TextView action = root.findViewById(R.id.pageAction);
        action.setText(R.string.backup_now);
        action.setOnClickListener(v -> {
            if (!backingUp) {
                createBackup.launch(BackupFiles.manualName(System.currentTimeMillis(), TimeZone.getDefault()));
            }
        });
        show();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void show() {
        if (root == null || getContext() == null) {
            return;
        }
        Context context = requireContext();
        long last = BackupSettings.last(context);
        String note = last > 0
                ? getString(R.string.backup_last, DateUtils.getRelativeDateTimeString(context, last,
                        DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0))
                : getString(R.string.backup_never);
        String error = BackupSettings.lastError(context);
        if (!error.isEmpty()) {
            note += "\n" + getString(R.string.backup_auto_failed, error);
        }
        ((TextView) root.findViewById(R.id.pageActionNote)).setText(note);

        LinearLayout sections = root.findViewById(R.id.pageSections);
        sections.removeAllViews();
        row(sections, R.string.backup_restore, getString(R.string.backup_restore_value),
                () -> pickRestore.launch(new String[] {"*/*"}));
        LinearLayout recent = new LinearLayout(context);
        recent.setOrientation(LinearLayout.VERTICAL);
        sections.addView(recent);
        listRecent(recent);

        heading(sections, R.string.backup_automatic_heading);
        String frequency = BackupSettings.frequency(context);
        row(sections, R.string.backup_automatic, frequencyName(frequency), this::chooseFrequency);
        if (!BackupSettings.OFF.equals(frequency)) {
            row(sections, R.string.backup_folder, folderName(), () -> pickFolder.launch(null));
            row(sections, R.string.backup_keep, getString(R.string.backup_keep_value, BackupSettings.keep(context)),
                    this::chooseKeep);
        }

        heading(sections, R.string.backup_password_heading);
        note(sections, R.string.backup_private_warning);
        row(sections, R.string.backup_password, getString(BackupSettings.password(context) != null
                ? R.string.look_on : R.string.tune_off), this::choosePassword);

        heading(sections, R.string.backup_moving_heading);
        row(sections, R.string.backup_beyondpod, getString(R.string.backup_beyondpod_value),
                () -> pickRestore.launch(new String[] {"*/*"}));
        row(sections, R.string.moving_title, "", () -> TendRows.open(this, new MovingFragment()));
        row(sections, R.string.backup_opml, "", () -> {
            Intent intent = new Intent(requireContext(), PreferenceActivity.class);
            intent.putExtra(PreferenceActivity.OPEN_IMPORT_EXPORT, true);
            startActivity(intent);
        });
    }

    private void listRecent(LinearLayout recent) {
        String folder = BackupSettings.folder(requireContext());
        if (folder == null) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            List<DocumentFile> found = new ArrayList<>();
            DocumentFile tree = DocumentFile.fromTreeUri(app, Uri.parse(folder));
            if (tree != null && tree.canRead()) {
                for (DocumentFile file : tree.listFiles()) {
                    if (file.getName() != null && file.getName().endsWith(BackupFiles.EXTENSION)) {
                        found.add(file);
                    }
                }
            }
            Collections.sort(found, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            return found.subList(0, Math.min(5, found.size()));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(files -> {
                    if (getContext() == null || files.isEmpty()) {
                        return;
                    }
                    note(recent, R.string.backup_recent);
                    for (DocumentFile file : files) {
                        View row = LayoutInflater.from(getContext()).inflate(R.layout.tune_row, recent, false);
                        ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(DateUtils.formatDateTime(
                                getContext(), file.lastModified(), DateUtils.FORMAT_SHOW_DATE
                                        | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH));
                        ((TextView) row.findViewById(R.id.tuneRowValue)).setText(
                                file.getName().startsWith("Yonder-auto") ? getString(R.string.backup_recent_auto)
                                        : getString(R.string.backup_recent_manual));
                        row.setOnClickListener(v -> ImportFlow.open(this, file.getUri()));
                        recent.addView(row);
                    }
                }, error -> Log.d(TAG, "Can't list backups: " + error.getMessage())));
    }

    private String folderName() {
        String folder = BackupSettings.folder(requireContext());
        if (folder == null) {
            return getString(R.string.backup_folder_none);
        }
        DocumentFile document = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folder));
        return document != null && document.getName() != null ? document.getName() : "";
    }

    private String frequencyName(String frequency) {
        if (BackupSettings.DAILY.equals(frequency)) {
            return getString(R.string.backup_daily);
        } else if (BackupSettings.WEEKLY.equals(frequency)) {
            return getString(R.string.backup_weekly);
        }
        return getString(R.string.tune_off);
    }

    private void chooseFrequency() {
        String[] values = {BackupSettings.OFF, BackupSettings.DAILY, BackupSettings.WEEKLY};
        String[] names = {frequencyName(values[0]), frequencyName(values[1]), frequencyName(values[2])};
        int checked = Arrays.asList(values).indexOf(BackupSettings.frequency(requireContext()));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_automatic)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    BackupSettings.setFrequency(requireContext(), values[which]);
                    if (!BackupSettings.OFF.equals(values[which]) && BackupSettings.folder(requireContext()) == null) {
                        pickFolder.launch(null);
                    }
                    YonderBackupWorker.schedule(requireContext(), true);
                    show();
                })
                .show();
    }

    private void folderChosen(@Nullable Uri uri) {
        if (uri == null) {
            if (BackupSettings.folder(requireContext()) == null) {
                BackupSettings.setFrequency(requireContext(), BackupSettings.OFF);
                show();
            }
            return;
        }
        requireContext().getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        BackupSettings.setFolder(requireContext(), uri.toString());
        YonderBackupWorker.schedule(requireContext(), true);
        show();
    }

    private void chooseKeep() {
        String[] names = new String[KEEP_CHOICES.length];
        int checked = -1;
        for (int i = 0; i < KEEP_CHOICES.length; i++) {
            names[i] = getString(R.string.backup_keep_value, KEEP_CHOICES[i]);
            if (KEEP_CHOICES[i] == BackupSettings.keep(requireContext())) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_keep)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    BackupSettings.setKeep(requireContext(), KEEP_CHOICES[which]);
                    show();
                })
                .show();
    }

    private void choosePassword() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.backup_password_dialog, null);
        EditText first = content.findViewById(R.id.backupPassword);
        EditText second = content.findViewById(R.id.backupPasswordAgain);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.backup_password)
                .setMessage(R.string.backup_password_message)
                .setView(content)
                .setPositiveButton(R.string.backup_password_save, (dialog, which) -> {
                    String password = first.getText().toString();
                    if (password.isEmpty() || !password.equals(second.getText().toString())) {
                        Toast.makeText(requireContext(), R.string.backup_password_mismatch, Toast.LENGTH_LONG)
                                .show();
                        return;
                    }
                    BackupSettings.setPassword(requireContext(), password);
                    show();
                })
                .setNegativeButton(R.string.cancel_label, null);
        if (BackupSettings.password(requireContext()) != null) {
            builder.setNeutralButton(R.string.backup_password_remove, (dialog, which) -> {
                BackupSettings.setPassword(requireContext(), null);
                show();
            });
        }
        builder.show();
    }

    private void backUpTo(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        backingUp = true;
        ((TextView) root.findViewById(R.id.pageAction)).setText(R.string.backup_working);
        disposables.add(Completable.fromAction(() -> {
            try (OutputStream out = app.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) {
                    throw new IOException("Can't write the file");
                }
                YonderBackup.write(app, out, BackupSettings.password(app));
            }
            BackupSettings.setLast(app, System.currentTimeMillis(), "");
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> finishBackup(getString(R.string.backup_done)), error -> {
                    Log.e(TAG, "Backup failed: " + error.getMessage());
                    finishBackup(getString(R.string.backup_failed, String.valueOf(error.getMessage())));
                }));
    }

    private void finishBackup(String message) {
        backingUp = false;
        if (root != null) {
            ((TextView) root.findViewById(R.id.pageAction)).setText(R.string.backup_now);
            show();
        }
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void heading(LinearLayout parent, int text) {
        TextView heading = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tune_heading, parent, false);
        heading.setText(text);
        parent.addView(heading);
    }

    private void note(LinearLayout parent, int text) {
        TextView note = new TextView(parent.getContext());
        TextViewCompat.setTextAppearance(note, R.style.TextAppearance_WildInk_Detail);
        note.setText(text);
        note.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        parent.addView(note);
    }

    private void row(LinearLayout parent, int label, String value, Runnable onClick) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.tune_row, parent, false);
        ((TextView) row.findViewById(R.id.tuneRowLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tuneRowValue)).setText(value);
        row.setOnClickListener(v -> onClick.run());
        parent.addView(row);
    }
}
