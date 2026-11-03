package de.danoeh.antennapod.ui.screen.playback.video;

import android.app.Activity;
import android.content.pm.PackageManager;

public class PictureInPictureUtil {
    private PictureInPictureUtil() {
    }

    public static boolean supportsPictureInPicture(Activity activity) {
        PackageManager packageManager = activity.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    public static boolean isInPictureInPictureMode(Activity activity) {
        if (supportsPictureInPicture(activity)) {
            return activity.isInPictureInPictureMode();
        } else {
            return false;
        }
    }
}
