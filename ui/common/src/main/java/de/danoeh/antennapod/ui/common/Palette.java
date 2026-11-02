package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class Palette {
    public static final int BOOKS = 0;
    public static final int PODCASTS = 1;
    public static final int RADIO = 2;
    private static final String[] WORLD_DEFAULTS = {"plum", "charcoal", "night"};

    private static final int[][] SLOT_COLOURS = {
        {R.color.wildink_custom0_bg, R.color.wildink_custom0_text, R.color.wildink_custom0_secondary,
            R.color.wildink_custom0_accent, R.color.wildink_custom0_on_accent, R.color.wildink_custom0_raised},
        {R.color.wildink_custom1_bg, R.color.wildink_custom1_text, R.color.wildink_custom1_secondary,
            R.color.wildink_custom1_accent, R.color.wildink_custom1_on_accent, R.color.wildink_custom1_raised},
        {R.color.wildink_custom2_bg, R.color.wildink_custom2_text, R.color.wildink_custom2_secondary,
            R.color.wildink_custom2_accent, R.color.wildink_custom2_on_accent, R.color.wildink_custom2_raised},
        {R.color.wildink_custom3_bg, R.color.wildink_custom3_text, R.color.wildink_custom3_secondary,
            R.color.wildink_custom3_accent, R.color.wildink_custom3_on_accent, R.color.wildink_custom3_raised},
        {R.color.wildink_custom4_bg, R.color.wildink_custom4_text, R.color.wildink_custom4_secondary,
            R.color.wildink_custom4_accent, R.color.wildink_custom4_on_accent, R.color.wildink_custom4_raised},
        {R.color.wildink_custom5_bg, R.color.wildink_custom5_text, R.color.wildink_custom5_secondary,
            R.color.wildink_custom5_accent, R.color.wildink_custom5_on_accent, R.color.wildink_custom5_raised},
    };
    private static final int[][] SLOT_THEMES = {
        {R.style.Theme_WildInk_Custom0Dark_NoTitle, R.style.Theme_WildInk_Custom0Dark_Translucent,
            R.style.Theme_WildInk_Custom0Light_NoTitle, R.style.Theme_WildInk_Custom0Light_Translucent},
        {R.style.Theme_WildInk_Custom1Dark_NoTitle, R.style.Theme_WildInk_Custom1Dark_Translucent,
            R.style.Theme_WildInk_Custom1Light_NoTitle, R.style.Theme_WildInk_Custom1Light_Translucent},
        {R.style.Theme_WildInk_Custom2Dark_NoTitle, R.style.Theme_WildInk_Custom2Dark_Translucent,
            R.style.Theme_WildInk_Custom2Light_NoTitle, R.style.Theme_WildInk_Custom2Light_Translucent},
        {R.style.Theme_WildInk_Custom3Dark_NoTitle, R.style.Theme_WildInk_Custom3Dark_Translucent,
            R.style.Theme_WildInk_Custom3Light_NoTitle, R.style.Theme_WildInk_Custom3Light_Translucent},
        {R.style.Theme_WildInk_Custom4Dark_NoTitle, R.style.Theme_WildInk_Custom4Dark_Translucent,
            R.style.Theme_WildInk_Custom4Light_NoTitle, R.style.Theme_WildInk_Custom4Light_Translucent},
        {R.style.Theme_WildInk_Custom5Dark_NoTitle, R.style.Theme_WildInk_Custom5Dark_Translucent,
            R.style.Theme_WildInk_Custom5Light_NoTitle, R.style.Theme_WildInk_Custom5Light_Translucent},
    };

    public final String key;
    @Nullable
    public final String label;
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

    private Palette(String key, @Nullable String label, int[] colours, int noTitleTheme, int translucentTheme,
                    int name) {
        this.key = key;
        this.label = label;
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

    public static boolean customSupported() {
        return ColourOverride.isSupported();
    }

    public static Palette of(String key) {
        return of(key, 0, "charcoal");
    }

    private static Palette of(String key, int slot, String fallback) {
        if (CustomPalettes.isCustom(key)) {
            CustomPalettes.Entry entry = customSupported() ? CustomPalettes.get(key) : null;
            if (entry != null) {
                return custom(entry.key(), entry.name, entry.background, entry.accent, slot);
            }
            key = fallback;
        }
        WildInkPalette palette = WildInkPalette.fromKey(key);
        return new Palette(palette.key, null, new int[] {palette.background, palette.text, palette.secondary,
            palette.accent, palette.onAccent, palette.raised}, palette.noTitleTheme, palette.translucentTheme,
                palette.name);
    }

    public static Palette custom(String key, @Nullable String label, int background, int accent, int slot) {
        int[] colours = CustomColours.derive(background, accent);
        boolean light = CustomColours.isLight(colours[CustomColours.BACKGROUND]);
        int[] themes = SLOT_THEMES[slot];
        return new Palette(key, label, colours, light ? themes[2] : themes[0], light ? themes[3] : themes[1],
                R.string.palette_custom);
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (WildInkPalette palette : WildInkPalette.values()) {
            keys.add(palette.key);
        }
        if (customSupported()) {
            for (CustomPalettes.Entry entry : CustomPalettes.all()) {
                keys.add(entry.key());
            }
        }
        return keys;
    }

    public static String chosenKey(int world) {
        return UserPreferences.isPaletteEverywhere() ? UserPreferences.getPalette()
                : UserPreferences.getWorldPalette(world);
    }

    public static String nightKey(int world) {
        return UserPreferences.getWorldNightPalette(UserPreferences.isPaletteEverywhere() ? PODCASTS : world);
    }

    public static Palette forWorld(Context context, int world) {
        String fallback = UserPreferences.isPaletteEverywhere() ? "charcoal" : WORLD_DEFAULTS[world];
        String night = nightKey(world);
        if (!night.isEmpty() && isNight(context)) {
            return of(night, world + 3, fallback);
        }
        return of(chosenKey(world), world, fallback);
    }

    public static Palette app(Context context) {
        return forWorld(context, PODCASTS);
    }

    public static void applyCustomColours(Context context) {
        if (!customSupported() || CustomPalettes.all().isEmpty()) {
            return;
        }
        Map<Integer, Integer> colours = new HashMap<>();
        for (int world = BOOKS; world <= RADIO; world++) {
            addSlot(colours, of(chosenKey(world), world, "charcoal"), world);
            String night = nightKey(world);
            if (!night.isEmpty()) {
                addSlot(colours, of(night, world + 3, "charcoal"), world + 3);
            }
        }
        ColourOverride.apply(context, colours);
    }

    private static void addSlot(Map<Integer, Integer> colours, Palette palette, int slot) {
        if (palette.label == null) {
            return;
        }
        int[] ids = SLOT_COLOURS[slot];
        int[] values = {palette.background, palette.text, palette.secondary, palette.accent, palette.onAccent,
            palette.raised};
        for (int i = 0; i < ids.length; i++) {
            colours.put(ids[i], values[i]);
        }
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
