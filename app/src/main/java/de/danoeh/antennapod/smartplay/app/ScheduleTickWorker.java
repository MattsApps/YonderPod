package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ScheduleTickWorker extends Worker {
    public ScheduleTickWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        UpdateScheduler.checkDue(getApplicationContext());
        return Result.success();
    }
}
