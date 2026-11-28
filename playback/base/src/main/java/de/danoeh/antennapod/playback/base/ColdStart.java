package de.danoeh.antennapod.playback.base;

import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

/**
 * 1.0.8: cold-start timing. One log line per stage of a cold start (a widget tap, say, after Android killed
 * Yonder), all under the tag "ColdStart", each with the milliseconds since the process started. Only the first
 * play of each process is logged, so a warm play costs one boolean check. Changes nothing about startup.
 * Read with: adb logcat -d -v time -s ColdStart
 */
public final class ColdStart {
    public static final String TAG = "ColdStart";
    private static volatile boolean playLogged = false;

    private ColdStart() {
    }

    /** Milliseconds since this process started. */
    public static long sinceStart() {
        return SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime();
    }

    /** A stage of starting the process (setup, service created). Always logged: each happens once a process. */
    public static void stage(String what) {
        Log.i(TAG, sinceStart() + " ms: " + what);
    }

    /** A stage that took a while: logs how long it took, with the time it finished. */
    public static void timed(String what, long startedAt) {
        Log.i(TAG, sinceStart() + " ms: " + what + " (" + (SystemClock.elapsedRealtime() - startedAt) + " ms)");
    }

    /** The time to pass to timed(). */
    public static long now() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * A stage of the first play in this process (what plays next, prepare, ready, first audio). Ignored once
     * the first audio has been logged, so later plays log nothing.
     */
    public static void play(String what) {
        if (!playLogged) {
            stage(what);
        }
    }

    /** The first audio of this process: logged once, then play() goes quiet. */
    public static void firstAudio() {
        if (!playLogged) {
            playLogged = true;
            stage("first audio");
        }
    }
}
