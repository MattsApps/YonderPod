package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

public class DuckingFocus implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "DuckingFocus";

    public interface Listener {
        void onDuck(boolean ducked);

        void onInterrupted();

        void onInterruptionOver();

        void onLost();
    }

    private final AudioManager audioManager;
    private final Listener listener;
    private final AudioFocusRequestCompat request;
    private boolean held = false;
    private boolean ducked = false;
    private boolean interrupted = false;
    private boolean pausing = false;

    public DuckingFocus(Context context, Listener listener) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
        request = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributesCompat.Builder()
                        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                        .build())
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this, new Handler(Looper.getMainLooper()))
                .build();
    }

    public void request() {
        interrupted = false;
        pausing = false;
        if (held || audioManager == null) {
            return;
        }
        held = AudioManagerCompat.requestAudioFocus(audioManager, request)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        Log.d(TAG, "Audio focus: own request, granted " + held);
    }

    public boolean keepsFocusWhilePaused() {
        if (pausing) {
            pausing = false;
            return true;
        }
        return false;
    }

    public void abandon() {
        interrupted = false;
        pausing = false;
        setDucked(false);
        if (!held || audioManager == null) {
            return;
        }
        held = false;
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request);
        Log.d(TAG, "Audio focus: own focus released");
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        boolean callOrRecording = isCallOrRecording();
        Log.d(TAG, "Audio focus: own change " + focusChange + ", call or recording " + callOrRecording);
        if (!held) {
            return;
        }
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                setDucked(false);
                if (interrupted) {
                    interrupted = false;
                    listener.onInterruptionOver();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (AudioFocusPolicy.ducksForInterruption(
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, callOrRecording)) {
                    setDucked(true);
                } else if (!interrupted) {
                    setDucked(false);
                    interrupted = true;
                    pausing = true;
                    listener.onInterrupted();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                held = false;
                interrupted = false;
                pausing = false;
                setDucked(false);
                AudioManagerCompat.abandonAudioFocusRequest(audioManager, request);
                listener.onLost();
                break;
            default:
                break;
        }
    }

    private void setDucked(boolean ducked) {
        if (this.ducked == ducked) {
            return;
        }
        this.ducked = ducked;
        listener.onDuck(ducked);
    }

    private boolean isCallOrRecording() {
        if (audioManager == null) {
            return false;
        }
        try {
            return audioManager.getMode() != AudioManager.MODE_NORMAL
                    || !audioManager.getActiveRecordingConfigurations().isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
