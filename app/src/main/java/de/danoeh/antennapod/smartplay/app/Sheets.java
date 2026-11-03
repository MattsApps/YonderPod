package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.os.Build;

public final class Sheets {
    private Sheets() {
    }

    public static void skipWindowAnimation(Activity activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            legacySkip(activity);
        }
    }

    public static void finishQuietly(Activity activity) {
        activity.finish();
        if (Build.VERSION.SDK_INT < 34) {
            legacySkip(activity);
        }
    }

    @SuppressWarnings("deprecation")
    private static void legacySkip(Activity activity) {
        activity.overridePendingTransition(0, 0);
    }
}
