package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ScheduledRunWorker extends Worker {
    private static final String TAG = "ScheduledRunWorker";

    public ScheduledRunWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String key = getInputData().getString(UpdateScheduler.KEY_SCHEDULE_KEY);
        if (key != null) {
            try {
                UpdateScheduler.runIfDue(getApplicationContext(), key);
            } catch (Exception e) {
                Log.e(TAG, "Scheduled run failed", e);
            }
        }
        return Result.success();
    }
}
