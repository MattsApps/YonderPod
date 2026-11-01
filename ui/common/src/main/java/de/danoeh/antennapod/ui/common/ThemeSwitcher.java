package de.danoeh.antennapod.ui.common;

import android.content.Context;
import androidx.annotation.StyleRes;

public abstract class ThemeSwitcher {
    @StyleRes
    public static int getNoTitleTheme(Context context) {
        return WildInkPalette.current().noTitleTheme;
    }

    @StyleRes
    public static int getTranslucentTheme(Context context) {
        return WildInkPalette.current().translucentTheme;
    }
}
