package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * A short record of how each episode ended (or didn't), so a stuck episode can be explained afterwards.
 * Lines go to logcat under the tag EpisodeEnd and to no_backup/episode-end-log.txt, which keeps only the
 * newest few kilobytes.
 */
public final class EpisodeEndLog {
    private static final String TAG = "EpisodeEnd";
    private static final String FILE_NAME = "episode-end-log.txt";
    private static final long MAX_BYTES = 32 * 1024;
    private static final long KEEP_BYTES = 16 * 1024;

    private EpisodeEndLog() {
    }

    public static void note(Context context, String line) {
        Log.d(TAG, line);
        File file = new File(context.getNoBackupFilesDir(), FILE_NAME);
        String stamped = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + " " + line + "\n";
        Schedulers.io().scheduleDirect(() -> append(file, stamped));
    }

    private static synchronized void append(File file, String line) {
        try {
            if (file.length() > MAX_BYTES) {
                byte[] tail = new byte[(int) KEEP_BYTES];
                try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
                    in.seek(file.length() - KEEP_BYTES);
                    in.readFully(tail);
                }
                try (FileOutputStream out = new FileOutputStream(file, false)) {
                    out.write(tail);
                }
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.w(TAG, "Couldn't write the episode end log", e);
        }
    }

    public static String describe(long positionMs, long durationMs) {
        return clock(positionMs) + " of " + clock(durationMs)
                + (durationMs > 0 ? " (" + ((durationMs - positionMs) / 1000) + " s left)" : "");
    }

    private static String clock(long ms) {
        if (ms < 0) {
            return "?";
        }
        long seconds = ms / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
}
