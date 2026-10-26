package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.Locale;

import de.danoeh.antennapod.event.playback.ButtonLogEvent;
import de.danoeh.antennapod.playback.service.R;
import de.danoeh.antennapod.storage.preferences.ButtonAction;
import de.danoeh.antennapod.storage.preferences.ButtonPress;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;

public class ButtonPressHandler {
    private static final long MULTI_PRESS_WINDOW_MS = 400;
    private static final long LONG_PRESS_MS = 700;

    public interface Listener {
        void onPress(ButtonPress press);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final Listener listener;
    private int count = 0;
    private boolean down = false;
    private boolean longFired = false;

    private final Runnable finish = () -> {
        int presses = count;
        count = 0;
        deliver(presses >= 3 ? ButtonPress.TRIPLE : presses == 2 ? ButtonPress.DOUBLE : ButtonPress.SINGLE, null);
    };

    private final Runnable longPress = () -> {
        if (down) {
            longFired = true;
            count = 0;
            handler.removeCallbacks(finish);
            deliver(ButtonPress.LONG, null);
        }
    };

    public ButtonPressHandler(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getRepeatCount() == 0) {
            boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
            String friendly = context.getString(pressed ? R.string.tester_pressed : R.string.tester_released,
                    keyName(code));
            log(friendly, KeyEvent.keyCodeToString(code) + (pressed ? " ACTION_DOWN" : " ACTION_UP"));
        }
        if (code == KeyEvent.KEYCODE_HEADSETHOOK || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    down = true;
                    longFired = false;
                    handler.removeCallbacks(finish);
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                down = false;
                handler.removeCallbacks(longPress);
                if (!longFired) {
                    count++;
                    handler.removeCallbacks(finish);
                    if (count >= 3) {
                        finish.run();
                    } else {
                        handler.postDelayed(finish, MULTI_PRESS_WINDOW_MS);
                    }
                }
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MEDIA_NEXT || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                deliver(code == KeyEvent.KEYCODE_MEDIA_NEXT ? ButtonPress.NEXT : ButtonPress.PREVIOUS, null);
            }
            return true;
        }
        return false;
    }

    public void onCommand(ButtonPress press, String source) {
        deliver(press, source);
    }

    public void onDeviceConnected(boolean bluetooth, String name) {
        log(context.getString(bluetooth ? R.string.tester_bluetooth_connected
                : R.string.tester_headphones_connected, name), bluetooth ? "TYPE_BLUETOOTH_A2DP" : "TYPE_WIRED");
    }

    private void deliver(ButtonPress press, String source) {
        ButtonAction action = ControlsPreferences.getButtonAction(press);
        String actionText = actionName(action);
        String friendly = context.getString(R.string.tester_result, pressName(press),
                actionText.substring(0, 1).toLowerCase(Locale.getDefault()) + actionText.substring(1));
        if (source != null) {
            friendly = context.getString(R.string.tester_from, friendly, source);
        }
        log(friendly, press.name() + " → " + action.name());
        if (!ControlsPreferences.isTesting()) {
            listener.onPress(press);
        }
    }

    private void log(String friendly, String raw) {
        ControlsPreferences.log(friendly, raw);
        EventBus.getDefault().post(new ButtonLogEvent(friendly));
    }

    private String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return context.getString(R.string.tester_key_play_pause);
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return context.getString(R.string.tester_key_play);
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return context.getString(R.string.tester_key_pause);
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return context.getString(R.string.tester_key_next);
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return context.getString(R.string.tester_key_previous);
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                return context.getString(R.string.tester_key_fast_forward);
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                return context.getString(R.string.tester_key_rewind);
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return context.getString(R.string.tester_key_stop);
            default:
                return context.getString(R.string.tester_key_other);
        }
    }

    private String pressName(ButtonPress press) {
        switch (press) {
            case DOUBLE:
                return context.getString(R.string.controls_press_double);
            case TRIPLE:
                return context.getString(R.string.controls_press_triple);
            case LONG:
                return context.getString(R.string.controls_press_long);
            case NEXT:
                return context.getString(R.string.controls_press_next);
            case PREVIOUS:
                return context.getString(R.string.controls_press_previous);
            case SINGLE:
            default:
                return context.getString(R.string.controls_press_single);
        }
    }

    private String actionName(ButtonAction action) {
        switch (action) {
            case SKIP_FORWARD:
                return context.getString(R.string.controls_action_skip_forward);
            case SKIP_BACK:
                return context.getString(R.string.controls_action_skip_back);
            case NEXT_EPISODE:
                return context.getString(R.string.controls_action_next);
            case PREVIOUS_EPISODE:
                return context.getString(R.string.controls_action_previous);
            case REBUILD_SMART_PLAY:
                return context.getString(R.string.controls_action_rebuild);
            case PLAY_PAUSE:
            default:
                return context.getString(R.string.controls_action_play_pause);
        }
    }
}
