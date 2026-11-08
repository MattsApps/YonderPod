package de.danoeh.antennapod.smartplay.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.HashSet;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class RecordingService extends Service {
    private static final String TAG = "RecordingService";
    private static final int NOTIFICATION_ID = 1991;

    private final Set<Long> running = new HashSet<>();
    private volatile boolean destroyed = false;

    public static void start(Context context, long showId, long start, long end) {
        if (showId < 0) {
            return;
        }
        Intent intent = new Intent(context, RecordingService.class);
        intent.putExtra(Recordings.KEY_SHOW, showId);
        intent.putExtra(Recordings.KEY_START, start);
        intent.putExtra(Recordings.KEY_END, end);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (Exception e) {
            Log.e(TAG, "Could not start recording", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long showId = intent != null ? intent.getLongExtra(Recordings.KEY_SHOW, -1) : -1;
        RecordingStore.Show show = showId >= 0 ? RecordingStore.getInstance(this).getShow(showId) : null;
        startInForeground(show != null ? show.name : "");
        if (show == null) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        ShowTimes.Airing airing = new ShowTimes.Airing(intent.getLongExtra(Recordings.KEY_START, 0),
                intent.getLongExtra(Recordings.KEY_END, 0));
        synchronized (running) {
            if (!running.add(showId)) {
                return START_NOT_STICKY;
            }
        }
        new Thread(() -> record(show, airing), "Recording-" + showId).start();
        return START_NOT_STICKY;
    }

    private void startInForeground(String name) {
        Notification notification = new NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setContentTitle(getString(R.string.recording_live_notification, name))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .setSilent(true)
                .build();
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
    }

    private void record(RecordingStore.Show show, ShowTimes.Airing airing) {
        Context context = getApplicationContext();
        try {
            FeedItem station = DBReader.getFeedItem(show.stationItemId);
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (station == null || station.getMedia() == null) {
                return;
            }
            if (!show.allowMobile && connectivity != null && connectivity.isActiveNetworkMetered()) {
                Recordings.missed(context, show, airing, R.string.recording_missed_wifi, false);
                return;
            }
            long until = airing.getEnd() + RecordingRules.LIVE_PADDING_MILLIS;
            Recorder.Saved saved = Recorder.recordLive(station.getMedia().getDownloadUrl(),
                    station.getMedia().getMimeType(), until, Recordings.folder(context, show),
                    Recordings.fileName(show, airing), () -> destroyed);
            Recordings.saved(context, show, saved, airing);
        } catch (Recorder.UnsupportedException e) {
            Recordings.missed(context, show, airing, R.string.recording_missed_unsupported, false);
        } catch (Exception e) {
            Log.e(TAG, "Live recording failed", e);
            Recordings.missed(context, show, airing, R.string.recording_missed_connection, false);
        } finally {
            RecordingStore.Show latest = RecordingStore.getInstance(context).getShow(show.id);
            if (latest != null) {
                latest.preferLiveNext = false;
                RecordingStore.getInstance(context).update(latest);
                Recordings.scheduleAfter(context, latest, airing.getEnd());
            }
            synchronized (running) {
                running.remove(show.id);
            }
            stopIfIdle();
        }
    }

    private void stopIfIdle() {
        synchronized (running) {
            if (running.isEmpty()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
