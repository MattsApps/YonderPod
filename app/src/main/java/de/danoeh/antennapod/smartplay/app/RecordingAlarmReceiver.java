package de.danoeh.antennapod.smartplay.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RecordingAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "RecordingAlarm";
    /** The recording notification's stop button: the same as "stop today's recording". */
    static final String ACTION_STOP_TODAY = "de.danoeh.antennapod.smartplay.STOP_TODAY";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        long showId = intent.getLongExtra(Recordings.KEY_SHOW, -1);
        boolean stopToday = ACTION_STOP_TODAY.equals(intent.getAction());
        long start = intent.getLongExtra(Recordings.KEY_START, 0);
        long end = intent.getLongExtra(Recordings.KEY_END, 0);
        new Thread(() -> {
            try {
                RecordingStore.Show show = RecordingStore.getInstance(app).getShow(showId);
                if (stopToday) {
                    Recordings.stopToday(app, showId);
                } else if (show == null) {
                    Log.d(TAG, "Ignoring an alarm for a recording that no longer exists");
                } else if (show.stopped) {
                    Log.d(TAG, "Ignoring an alarm for " + show.name + ": it's stopped");
                } else if (Recordings.isLeftOver(show)) {
                    Recordings.forget(app, show);
                } else {
                    RecordingService.start(app, showId, start, end);
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not start recording", e);
            } finally {
                result.finish();
            }
        }).start();
    }
}
