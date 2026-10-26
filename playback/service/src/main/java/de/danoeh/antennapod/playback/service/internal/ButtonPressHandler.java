package de.danoeh.antennapod.playback.service.internal;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.playback.ButtonLogEvent;
import de.danoeh.antennapod.storage.preferences.ButtonPress;
import de.danoeh.antennapod.storage.preferences.ControlsPreferences;

public class ButtonPressHandler {
    private static final long MULTI_PRESS_WINDOW_MS = 400;
    private static final long LONG_PRESS_MS = 700;

    public interface Listener {
        void onPress(ButtonPress press);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private int count = 0;
    private boolean down = false;
    private boolean longFired = false;

    private final Runnable finish = () -> {
        int presses = count;
        count = 0;
        deliver(presses >= 3 ? ButtonPress.TRIPLE : presses == 2 ? ButtonPress.DOUBLE : ButtonPress.SINGLE);
    };

    private final Runnable longPress = () -> {
        if (down) {
            longFired = true;
            count = 0;
            handler.removeCallbacks(finish);
            deliver(ButtonPress.LONG);
        }
    };

    public ButtonPressHandler(Listener listener) {
        this.listener = listener;
    }

    public boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getRepeatCount() == 0) {
            log(KeyEvent.keyCodeToString(code).replace("KEYCODE_", "")
                    + (event.getAction() == KeyEvent.ACTION_DOWN ? " pressed" : " released"));
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
                deliver(code == KeyEvent.KEYCODE_MEDIA_NEXT ? ButtonPress.NEXT : ButtonPress.PREVIOUS);
            }
            return true;
        }
        return false;
    }

    public void onCommand(ButtonPress press, String source) {
        log(press.name() + " command from " + source);
        deliver(press);
    }

    private void deliver(ButtonPress press) {
        log("→ " + press.name() + ": " + ControlsPreferences.getButtonAction(press).name());
        if (!ControlsPreferences.isTesting()) {
            listener.onPress(press);
        }
    }

    public static void log(String line) {
        ControlsPreferences.log(line);
        EventBus.getDefault().post(new ButtonLogEvent(line));
    }
}
