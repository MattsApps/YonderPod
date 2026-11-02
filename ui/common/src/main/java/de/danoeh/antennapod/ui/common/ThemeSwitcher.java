package de.danoeh.antennapod.ui.common;

import android.content.Context;
import androidx.annotation.StyleRes;

public abstract class ThemeSwitcher {
    @StyleRes
    public static int getNoTitleTheme(Context context) {
        Palette.applyCustomColours(context);
        return Palette.app(context).noTitleTheme;
    }

    @StyleRes
    public static int getTranslucentTheme(Context context) {
        Palette.applyCustomColours(context);
        return Palette.app(context).translucentTheme;
    }

    @StyleRes
    public static int getWorldTheme(Context context, int world) {
        return Palette.forWorld(context, world).noTitleTheme;
    }
}
