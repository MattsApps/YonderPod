package de.danoeh.antennapod.smartplay.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduleAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                UpdateScheduler.reschedule(app);
            } finally {
                result.finish();
            }
        }).start();
    }
}
