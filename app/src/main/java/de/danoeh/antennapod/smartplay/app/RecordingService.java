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
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.smartplay.radio.RecordingRules;
import de.danoeh.antennapod.smartplay.radio.ShowTimes;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

public class RecordingService extends Service {
    private static final String TAG = "RecordingService";
    private static final int NOTIFICATION_ID = 1991;
    private static final String KEY_EXTRA_START = "extraStart";
    private static final String KEY_EXTRA_END = "extraEnd";

    private static final Map<Long, Control> CONTROLS = new ConcurrentHashMap<>();

    /** Shows recording now, by id, with the name the notification shows. */
    private final Map<Long, String> recording = new LinkedHashMap<>();
    private volatile boolean destroyed = false;
    private volatile boolean cutShort = false;

    /** What a running live recording should do: when to stop, and whether to throw away what it has. */
    private static final class Control {
        final long start;
        /** The airing's end (it moves if the show's times are changed while it records). */
        volatile long end;
        volatile long stopAt;
        volatile boolean discard;
        /** The listener stopped it ("stop today's recording"), so it isn't called cut short. */
        volatile boolean stoppedByListener;

        Control(long start, long end, long stopAt) {
            this.start = start;
            this.end = end;
            this.stopAt = stopAt;
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
        if (newEnd <= now) {
            control.stoppedByListener = true;
        }
        Log.d(TAG, "Recording " + showId + " now ends at " + newEnd);
    }

    /**
     * Stops the running recording now and keeps what it has, saved as a normal recording. The airing's end
     * is left alone, so the next recording is scheduled from the next airing, not this one.
     */
    static void stopNow(long showId) {
        Control control = CONTROLS.get(showId);
        if (control != null) {
            control.stoppedByListener = true;
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
        startExtra(context, showId, start, end, 0, 0);
    }

    /**
     * Records extraStart–extraEnd as the extra part of the airing start–end (a show lengthened after its
     * recording finished). extraStart 0 records the airing itself.
     */
    static void startExtra(Context context, long showId, long start, long end, long extraStart, long extraEnd) {
        if (showId < 0) {
            return;
        }
        Intent intent = new Intent(context, RecordingService.class);
        intent.putExtra(Recordings.KEY_SHOW, showId);
        intent.putExtra(Recordings.KEY_START, start);
        intent.putExtra(Recordings.KEY_END, end);
        intent.putExtra(KEY_EXTRA_START, extraStart);
        intent.putExtra(KEY_EXTRA_END, extraEnd);
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
        boolean starting = show != null && !show.stopped;
        boolean already;
        synchronized (recording) {
            already = starting && recording.containsKey(showId);
            if (starting && !already) {
                recording.put(showId, show.name);
            }
        }
        // Android needs the service in the foreground after every start, even one that does nothing.
        boolean foreground = startInForeground();
        if (!starting) {
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (already) {
            return START_NOT_STICKY;
        }
        ShowTimes.Airing airing = new ShowTimes.Airing(intent.getLongExtra(Recordings.KEY_START, 0),
                intent.getLongExtra(Recordings.KEY_END, 0));
        long extraStart = intent.getLongExtra(KEY_EXTRA_START, 0);
        long extraEnd = intent.getLongExtra(KEY_EXTRA_END, 0);
        if (!foreground) {
            if (extraStart <= 0) {
                Recordings.missed(this, show, airing, R.string.recording_missed_android, false);
            }
            Recordings.scheduleAfter(this, show, airing.getEnd());
            finished(showId);
            return START_NOT_STICKY;
        }
        new Thread(() -> record(show, airing, extraStart, extraEnd), "Recording-" + showId).start();
        return START_NOT_STICKY;
    }

    /**
     * One notification while anything records, naming every show recording now. It has no stop button:
     * recordings are set on purpose and stopped from the show's page. Tapping it opens the radio section.
     */
    private boolean startInForeground() {
        List<String> names;
        synchronized (recording) {
            names = new ArrayList<>(recording.values());
        }
        String title = names.size() <= 1
                ? getString(R.string.recording_live_notification, names.isEmpty() ? "" : names.get(0))
                : getString(R.string.recording_live_notification_many, names.size());
        Intent open = new Intent(this, MainActivity.class);
        open.putExtra(MainActivity.EXTRA_OPEN_WORLD, WorldSurface.RADIO);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                NotificationUtils.CHANNEL_ID_DOWNLOADING)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setContentIntent(PendingIntent.getActivity(this, NOTIFICATION_ID, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true);
        if (names.size() > 1) {
            builder.setContentText(TextUtils.join(", ", names));
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (String name : names) {
                style.addLine(name);
            }
            builder.setStyle(style);
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

    private void record(RecordingStore.Show show, ShowTimes.Airing airing, long extraStart, long extraEnd) {
        Context context = getApplicationContext();
        boolean extra = extraStart > 0;
        long began = System.currentTimeMillis();
        long stopAt = extra ? extraEnd + (extraEnd >= airing.getEnd() ? RecordingRules.LIVE_PADDING_MILLIS : 0)
                : airing.getEnd() + RecordingRules.LIVE_PADDING_MILLIS;
        Control control = new Control(airing.getStart(), airing.getEnd(), stopAt);
        CONTROLS.put(show.id, control);
        // So pages showing "recording the rest" under a cut-short part catch up as soon as it starts.
        EventBus.getDefault().post(new Radio.ChangedEvent());
        File folder = null;
        File part = null;
        try {
            FeedItem station = DBReader.getFeedItem(show.stationItemId);
            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (station == null || station.getMedia() == null) {
                return;
            }
            if (!show.allowMobile && connectivity != null && connectivity.isActiveNetworkMetered()) {
                if (!extra) {
                    Recordings.missed(context, show, airing, R.string.recording_missed_wifi, false);
                }
                return;
            }
            String late = extra ? null : RecordingRecovery.lateText(context, show, airing, began);
            folder = Recordings.folder(context, show);
            part = Recorder.partFile(folder, extra ? Recordings.tailFileName(show, airing, extraStart)
                    : Recordings.fileName(show, airing));
            // Noted before the file exists, so if Yonder is closed part-way it's found and kept next time.
            RecordingRecovery.begin(context, part, show.id, airing, extraStart, extraEnd, true);
            // The stop time lives in control, so changing the show's times can move it while recording.
            Recorder.Saved saved;
            RecordingHttp.begin(Recordings.userAgent(context, show));
            try {
                saved = Recorder.recordLive(station.getMedia().getDownloadUrl(),
                        station.getMedia().getMimeType(), part, () -> destroyed || control.discard
                                || System.currentTimeMillis() >= control.stopAt);
            } finally {
                RecordingHttp.end();
            }
            long finished = System.currentTimeMillis();
            if (control.discard) {
                FileUtils.deleteQuietly(saved.file);
                return;
            }
            // Use the show as it is now: its name or times may have been changed while it recorded.
            RecordingStore.Show current = RecordingStore.getInstance(context).getShow(show.id);
            if (current == null) {
                FileUtils.deleteQuietly(saved.file);
                return;
            }
            if (extra) {
                // Its title shows the times it covers: to its planned end, or the show's new end if the show
                // was lengthened again while it recorded, or when it stopped if that was earlier.
                long due = control.end == airing.getEnd() ? extraEnd : control.end;
                Recordings.savedTail(context, current, saved, airing, extraStart, Math.min(finished, due), finished);
            } else {
                ShowTimes.Airing recorded = control.end == airing.getEnd() ? airing
                        : new ShowTimes.Airing(airing.getStart(), control.end);
                Recordings.saved(context, current, saved, recorded, finished, finished);
            }
            if (!control.stoppedByListener && finished < control.stopAt - RecordingRecovery.SHORT_BY_MILLIS) {
                String time = ScheduleText.time(context, finished);
                Recordings.note(context, show.id, airing.getStart(), getString(cutShort
                        ? R.string.recording_cut_short_android : R.string.recording_cut_short_unknown, time));
            } else if (late != null) {
                Recordings.note(context, show.id, airing.getStart(), late);
            }
        } catch (Recorder.UnsupportedException e) {
            if (!control.discard && !extra) {
                missedLatest(context, show.id, airing, R.string.recording_missed_unsupported);
            }
        } catch (Exception e) {
            if (!control.discard) {
                Log.e(TAG, "Live recording failed", e);
                if (!extra) {
                    missedLatest(context, show.id, airing, R.string.recording_missed_connection);
                }
            }
        } finally {
            CONTROLS.remove(show.id, control);
            if (part != null) {
                RecordingRecovery.end(context, part);
            }
            if (control.discard && folder != null) {
                FileUtils.deleteQuietly(folder);
            }
            RecordingStore.Show latest = RecordingStore.getInstance(context).getShow(show.id);
            if (latest != null) {
                latest.preferLiveNext = false;
                RecordingStore.getInstance(context).update(latest);
                Recordings.scheduleAfter(context, latest, control.end);
            }
            finished(show.id);
        }
    }

    /** A show stopped recording: the notification drops it, and goes when nothing's left. */
    private void finished(long showId) {
        boolean idle;
        synchronized (recording) {
            recording.remove(showId);
            idle = recording.isEmpty();
        }
        if (idle) {
            stopIfIdle();
        } else if (!destroyed) {
            startInForeground();
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
        synchronized (recording) {
            if (recording.isEmpty()) {
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
