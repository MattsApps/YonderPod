package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class Palette {
    public static final String CUSTOM = "custom";
    public static final int BOOKS = 0;
    public static final int PODCASTS = 1;
    public static final int RADIO = 2;

    public final String key;
    public final int background;
    public final int text;
    public final int secondary;
    public final int accent;
    public final int onAccent;
    public final int raised;
    @StyleRes
    public final int noTitleTheme;
    @StyleRes
    public final int translucentTheme;
    @StringRes
    public final int name;

    private Palette(String key, int[] colours, int noTitleTheme, int translucentTheme, int name) {
        this.key = key;
        this.background = colours[CustomColours.BACKGROUND];
        this.text = colours[CustomColours.TEXT];
        this.secondary = colours[CustomColours.SECONDARY];
        this.accent = colours[CustomColours.ACCENT];
        this.onAccent = colours[CustomColours.ON_ACCENT];
        this.raised = colours[CustomColours.RAISED];
        this.noTitleTheme = noTitleTheme;
        this.translucentTheme = translucentTheme;
        this.name = name;
    }

    public static Palette of(String key) {
        if (CUSTOM.equals(key) && UserPreferences.hasCustomPalette() && ColourOverride.isSupported()) {
            return custom(UserPreferences.getCustomBackground(), UserPreferences.getCustomAccent());
        }
        WildInkPalette palette = WildInkPalette.fromKey(key);
        return new Palette(palette.key, new int[] {palette.background, palette.text, palette.secondary,
            palette.accent, palette.onAccent, palette.raised}, palette.noTitleTheme, palette.translucentTheme,
                palette.name);
    }

    public static Palette custom(int background, int accent) {
        int[] colours = CustomColours.derive(background, accent);
        boolean light = CustomColours.isLight(colours[CustomColours.BACKGROUND]);
        return new Palette(CUSTOM, colours,
                light ? R.style.Theme_WildInk_CustomLight_NoTitle : R.style.Theme_WildInk_CustomDark_NoTitle,
                light ? R.style.Theme_WildInk_CustomLight_Translucent : R.style.Theme_WildInk_CustomDark_Translucent,
                R.string.palette_custom);
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (WildInkPalette palette : WildInkPalette.values()) {
            keys.add(palette.key);
        }
        if (UserPreferences.hasCustomPalette() && ColourOverride.isSupported()) {
            keys.add(CUSTOM);
        }
        return keys;
    }

    public static Palette forWorld(Context context, int world) {
        boolean dayNight = UserPreferences.isPaletteDayNight();
        if (dayNight && isNight(context)) {
            return of(UserPreferences.getNightPalette());
        } else if (UserPreferences.isPalettePerWorld()) {
            return of(UserPreferences.getWorldPalette(world));
        } else if (dayNight) {
            return of(UserPreferences.getDayPalette());
        }
        return of(UserPreferences.getPalette());
    }

    public static Palette app(Context context) {
        return forWorld(context, PODCASTS);
    }

    public static void applyCustomColours(Context context) {
        if (!UserPreferences.hasCustomPalette() || !ColourOverride.isSupported()) {
            return;
        }
        Palette custom = custom(UserPreferences.getCustomBackground(), UserPreferences.getCustomAccent());
        Map<Integer, Integer> colours = new HashMap<>();
        colours.put(R.color.wildink_custom_bg, custom.background);
        colours.put(R.color.wildink_custom_text, custom.text);
        colours.put(R.color.wildink_custom_secondary, custom.secondary);
        colours.put(R.color.wildink_custom_accent, custom.accent);
        colours.put(R.color.wildink_custom_on_accent, custom.onAccent);
        colours.put(R.color.wildink_custom_raised, custom.raised);
        ColourOverride.apply(context, colours);
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
