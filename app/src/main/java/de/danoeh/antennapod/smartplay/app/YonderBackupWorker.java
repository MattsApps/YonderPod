package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.smartplay.backup.BackupFiles;

public class YonderBackupWorker extends Worker {
    private static final String TAG = "YonderBackupWorker";
    private static final String WORK = "YonderAutomaticBackup";

    public YonderBackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context, boolean replace) {
        String frequency = BackupSettings.frequency(context);
        if (BackupSettings.OFF.equals(frequency) || BackupSettings.folder(context) == null) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(YonderBackupWorker.class,
                BackupFiles.intervalDays(frequency), TimeUnit.DAYS)
                .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK,
                replace ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE : ExistingPeriodicWorkPolicy.KEEP,
                request);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            backUpToFolder(getApplicationContext());
            return Result.success();
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Automatic backup failed: " + e.getMessage());
            BackupSettings.setLast(getApplicationContext(), System.currentTimeMillis(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return Result.failure();
        }
    }

    public static void backUpToFolder(Context context) throws IOException {
        String folderUri = BackupSettings.folder(context);
        if (folderUri == null) {
            throw new IOException("No backup folder");
        }
        DocumentFile folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri));
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            throw new IOException("Can't open the backup folder");
        }
        long now = System.currentTimeMillis();
        DocumentFile file = folder.createFile("application/octet-stream",
                BackupFiles.automaticName(now, TimeZone.getDefault()));
        if (file == null) {
            throw new IOException("Can't create a file in the backup folder");
        }
        try (OutputStream out = context.getContentResolver().openOutputStream(file.getUri(), "wt")) {
            if (out == null) {
                throw new IOException("Can't write to the backup folder");
            }
            YonderBackup.write(context, out, BackupSettings.password(context));
        } catch (IOException e) {
            file.delete();
            throw e;
        }
        List<BackupFiles.Found> found = new ArrayList<>();
        for (DocumentFile existing : folder.listFiles()) {
            if (existing.getName() != null) {
                found.add(new BackupFiles.Found(existing.getName(), existing.lastModified()));
            }
        }
        List<String> old = BackupFiles.toDelete(found, BackupSettings.keep(context));
        for (DocumentFile existing : folder.listFiles()) {
            if (old.contains(existing.getName())) {
                existing.delete();
            }
        }
        BackupSettings.setLast(context, now, "");
    }
}
