package de.danoeh.antennapod.smartplay.app;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class RecordingBurstWorker extends Worker {
    private static final String TAG = "RecordingBurst";
    private static final long GIVE_UP_AFTER_END = 30 * 60_000L;
    private static final int MAX_ATTEMPTS = 6;

    public RecordingBurstWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        long showId = getInputData().getLong(Recordings.KEY_SHOW, -1);
        RecordingStore.Show show = RecordingStore.getInstance(context).getShow(showId);
        if (show == null) {
            return Result.success();
        }
        ShowTimes.Airing airing = new ShowTimes.Airing(getInputData().getLong(Recordings.KEY_START, 0),
                getInputData().getLong(Recordings.KEY_END, 0));
        try {
            setForegroundAsync(foregroundInfo(context, show));
        } catch (Exception e) {
            Log.d(TAG, "Could not run in the foreground: " + e.getMessage());
        }
        try {
            Recorder.Saved saved = Recorder.burst(show.sourceUrl, Recordings.maxBitrate(show), airing,
                    Recordings.folder(context, show), Recordings.fileName(show, airing));
            Recordings.saved(context, show, saved, airing);
        } catch (Recorder.NotYetException e) {
            if (System.currentTimeMillis() < airing.getEnd() + GIVE_UP_AFTER_END) {
                return Result.retry();
            }
            Recordings.missed(context, show, airing, R.string.recording_missed_window, true);
        } catch (Recorder.GoneException e) {
            Recordings.missed(context, show, airing, R.string.recording_missed_window, true);
        } catch (Recorder.UnsupportedException e) {
            Log.d(TAG, "Quick saving isn't possible: " + e.getMessage());
            show.method = RecordingRules.Method.LIVE.name();
            Recordings.missed(context, show, airing, R.string.recording_missed_unsupported, false);
        } catch (Exception e) {
            Log.e(TAG, "Quick save failed", e);
            if (getRunAttemptCount() < MAX_ATTEMPTS
                    && RecordingRules.stillInWindow(airing, show.rewindSeconds, System.currentTimeMillis())) {
                return Result.retry();
            }
            Recordings.missed(context, show, airing, R.string.recording_missed_connection, true);
        }
        RecordingStore.Show latest = RecordingStore.getInstance(context).getShow(showId);
        if (latest != null) {
            Recordings.scheduleAfter(context, latest, airing.getEnd());
        }
        return Result.success();
    }

    static ForegroundInfo foregroundInfo(Context context, RecordingStore.Show show) {
        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setContentTitle(context.getString(R.string.recording_saving, show.name))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .setSilent(true)
                .build();
        int id = (int) (2000 + show.id);
        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(id, notification);
    }
}
