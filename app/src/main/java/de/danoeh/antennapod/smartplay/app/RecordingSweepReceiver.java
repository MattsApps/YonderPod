package de.danoeh.antennapod.smartplay.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RecordingSweepReceiver extends BroadcastReceiver {
    private static final String TAG = "RecordingSweep";

    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                Recordings.sweep(app);
            } catch (Exception e) {
                Log.e(TAG, "Could not tidy recordings", e);
            } finally {
                result.finish();
            }
        }).start();
    }
}
