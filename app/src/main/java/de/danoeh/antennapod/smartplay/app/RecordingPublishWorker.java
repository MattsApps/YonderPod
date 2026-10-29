package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class RecordingPublishWorker extends Worker {
    private static final String TAG = "RecordingPublish";

    public RecordingPublishWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Recordings.publishPending(getApplicationContext(), getInputData().getLong(Recordings.KEY_PENDING, -1));
        } catch (Exception e) {
            Log.e(TAG, "Could not publish recording", e);
        }
        return Result.success();
    }
}
