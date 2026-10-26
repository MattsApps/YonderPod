package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.util.Set;

import de.danoeh.antennapod.storage.preferences.ControlsPreferences;

public class CarModeAutoOpener {
    private static boolean openedForConnection = false;

    private final Activity activity;
    private AudioDeviceCallback callback;

    public CarModeAutoOpener(Activity activity) {
        this.activity = activity;
    }

    public void start() {
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null || !ControlsPreferences.carModeAuto()) {
            return;
        }
        if (!isChosenDeviceConnected(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))) {
            openedForConnection = false;
        }
        callback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                if (!openedForConnection && isChosenDeviceConnected(addedDevices)) {
                    openedForConnection = true;
                    activity.startActivity(new Intent(activity, CarModeActivity.class));
                }
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                if (isChosenDeviceConnected(removedDevices)) {
                    openedForConnection = false;
                }
            }
        };
        audioManager.registerAudioDeviceCallback(callback, new Handler(Looper.getMainLooper()));
    }

    public void stop() {
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null && callback != null) {
            audioManager.unregisterAudioDeviceCallback(callback);
        }
        callback = null;
    }

    private static boolean isChosenDeviceConnected(AudioDeviceInfo[] devices) {
        Set<String> chosen = ControlsPreferences.getCarModeDevices();
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && device.getProductName() != null
                    && chosen.contains(device.getProductName().toString())) {
                return true;
            }
        }
        return false;
    }
}
