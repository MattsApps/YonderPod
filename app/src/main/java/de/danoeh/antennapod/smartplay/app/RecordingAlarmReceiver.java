package de.danoeh.antennapod.smartplay.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RecordingAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RecordingService.start(context, intent.getLongExtra(Recordings.KEY_SHOW, -1),
                intent.getLongExtra(Recordings.KEY_START, 0), intent.getLongExtra(Recordings.KEY_END, 0));
    }
}
