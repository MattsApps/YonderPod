package de.danoeh.antennapod.smartplay.app;

import android.app.Notification;
import android.app.PendingIntent;
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

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class RecordingService extends Service {
    private static final String TAG = "RecordingService";
    private static final int NOTIFICATION_ID = 1991;

    private static final Map<Long, Control> CONTROLS = new ConcurrentHashMap<>();

    private final Set<Long> running = new HashSet<>();
    private volatile boolean destroyed = false;
    private volatile boolean cutShort = false;

    /** What a running live recording should do: when to stop, and whether to throw away what it has. */
    private static final class Control {
        final long start;
        volatile long end;
        volatile long stopAt;
        volatile boolean discard;

        Control(long start, long end) {
            this.start = start;
            this.end = end;
            this.stopAt = end + RecordingRules.LIVE_PADDING_MILLIS;
        }
    }

    /** Start time of the airing this show is recording now, or -1 if it isn't recording. */
    static long recordingStart(long showId) {
        Control control = CONTROLS.get(showId);
        return control != null ? control.start : -1;
    }

    /**
     * The running recording takes a new end. If that's still ahead it stops then (plus the usual padding);
     * if it has passed, it stops now and keeps what it has.
     */
    static void changeEnd(long showId, long newEnd) {
        Control control = CONTROLS.get(showId);
        if (control == null) {
            return;
        }
        long now = System.currentTimeMillis();
        control.end = newEnd;
        control.stopAt = newEnd <= now ? now : newEnd + RecordingRules.LIVE_PADDING_MILLIS;
        Log.d(TAG, "Recording " + showId + " now ends at " + newEnd);
    }

    /**
     * Stops the running recording now and keeps what it has, saved as a normal recording. The airing's end
     * is left alone, so the next recording is scheduled from the next airing, not this one.
     */
    static void stopNow(long showId) {
        Control control = CONTROLS.get(showId);
        if (control != null) {
            control.stopAt = System.currentTimeMillis();
            Log.d(TAG, "Recording " + showId + " stopped; keeping what it has");
        }
    }

    /** Stops the running recording at once and deletes what it recorded. */
    static void discard(long showId) {
        Control control = CONTROLS.get(showId);
        if (control != null) {
            control.discard = true;
            Log.d(TAG, "Recording " + showId + " stopped and discarded");
        }
    }

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
        boolean foreground = startInForeground(show);
        if (show == null || show.stopped) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        ShowTimes.Airing airing = new ShowTimes.Airing(intent.getLongExtra(Recordings.KEY_START, 0),
                intent.getLongExtra(Recordings.KEY_END, 0));
        if (!foreground) {
            Recordings.missed(this, show, airing, R.string.recording_missed_android, false);
            Recordings.scheduleAfter(this, show, airing.getEnd());
            stopIfIdle();
            return START_NOT_STICKY;
        }
        synchronized (running) {
            if (!running.add(showId)) {
                return START_NOT_STICKY;
            }
        }
        new Thread(() -> record(show, airing), "Recording-" + showId).start();
        return START_NOT_STICKY;
    }

    private boolean startInForeground(@Nullable RecordingStore.Show show) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setContentTitle(getString(R.string.recording_live_notification, show != null ? show.name : ""))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
                .setSilent(true);
        if (show != null) {
            // The same as "stop today's recording" on the station page (Recordings.stopToday).
            Intent stop = new Intent(this, RecordingAlarmReceiver.class);
            stop.setAction(RecordingAlarmReceiver.ACTION_STOP_TODAY);
            stop.setData(android.net.Uri.parse("yonderpod-recording-stop://" + show.id));
            stop.putExtra(Recordings.KEY_SHOW, show.id);
            builder.addAction(0, getString(R.string.record_stop_today), PendingIntent.getBroadcast(this,
                    (int) show.id, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Notification notification = builder.build();
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Android didn't let the recording run in the background", e);
            return false;
        }
    }

    private void record(RecordingStore.Show show, ShowTimes.Airing airing) {
        Context context = getApplicationContext();
        Control control = new Control(airing.getStart(), airing.getEnd());
        CONTROLS.put(show.id, control);
        File folder = null;
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
            // The stop time lives in control, so changing the show's times can move it while recording.
            Recorder.Saved saved;
            folder = Recordings.folder(context, show);
            RecordingHttp.begin(Recordings.userAgent(context, show));
            try {
                saved = Recorder.recordLive(station.getMedia().getDownloadUrl(),
                        station.getMedia().getMimeType(), Long.MAX_VALUE, folder,
                        Recordings.fileName(show, airing), () -> destroyed || control.discard
                                || System.currentTimeMillis() >= control.stopAt);
            } finally {
                RecordingHttp.end();
            }
            long finished = System.currentTimeMillis();
            if (control.discard) {
                FileUtils.deleteQuietly(saved.file);
                return;
            }
            ShowTimes.Airing recorded = control.end == airing.getEnd() ? airing
                    : new ShowTimes.Airing(airing.getStart(), control.end);
            // Use the show as it is now: its name or times may have been changed while it recorded.
            RecordingStore.Show current = RecordingStore.getInstance(context).getShow(show.id);
            if (current == null) {
                FileUtils.deleteQuietly(saved.file);
                return;
            }
            Recordings.saved(context, current, saved, recorded, finished);
            if (cutShort) {
                Recordings.missed(context, current, recorded, R.string.recording_cut_short, false);
            }
        } catch (Recorder.UnsupportedException e) {
            if (!control.discard) {
                missedLatest(context, show.id, airing, R.string.recording_missed_unsupported);
            }
        } catch (Exception e) {
            if (!control.discard) {
                Log.e(TAG, "Live recording failed", e);
                missedLatest(context, show.id, airing, R.string.recording_missed_connection);
            }
        } finally {
            CONTROLS.remove(show.id, control);
            if (control.discard && folder != null) {
                FileUtils.deleteQuietly(folder);
            }
            RecordingStore.Show latest = RecordingStore.getInstance(context).getShow(show.id);
            if (latest != null) {
                latest.preferLiveNext = false;
                RecordingStore.getInstance(context).update(latest);
                Recordings.scheduleAfter(context, latest, control.end);
            }
            synchronized (running) {
                running.remove(show.id);
            }
            stopIfIdle();
        }
    }

    /** Notes a failed recording on the show as it is now, so a change made while recording isn't undone. */
    private static void missedLatest(Context context, long showId, ShowTimes.Airing airing, int reason) {
        RecordingStore.Show current = RecordingStore.getInstance(context).getShow(showId);
        if (current != null) {
            Recordings.missed(context, current, airing, reason, false);
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
    public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "Android stopped the recording early");
        cutShort = true;
        destroyed = true;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }
}
