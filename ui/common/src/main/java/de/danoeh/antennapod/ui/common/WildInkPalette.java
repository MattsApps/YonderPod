package de.danoeh.antennapod.ui.common;

import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import de.danoeh.antennapod.storage.preferences.UserPreferences;

public enum WildInkPalette {
    CHARCOAL("charcoal", 0xFF1C1B19, 0xFFF1ECE2, 0xFFB5AEA2, 0xFFFF5A2B, 0xFF1C1B19, 0xFF2C2A27,
            R.style.Theme_WildInk_Charcoal_NoTitle, R.style.Theme_WildInk_Charcoal_Translucent,
            R.string.palette_charcoal),
    PAPER("paper", 0xFFF3EFE6, 0xFF111111, 0xFF524D47, 0xFFFF4A1C, 0xFF111111, 0xFFE4DED2,
            R.style.Theme_WildInk_Paper_NoTitle, R.style.Theme_WildInk_Paper_Translucent,
            R.string.palette_paper),
    FOREST("forest", 0xFF1E2A23, 0xFFEFE9DA, 0xFFAEB8A8, 0xFFE8B33A, 0xFF1E2A23, 0xFF2A3A30,
            R.style.Theme_WildInk_Forest_NoTitle, R.style.Theme_WildInk_Forest_Translucent,
            R.string.palette_forest),
    NIGHT("night", 0xFF151E36, 0xFFF3EEE3, 0xFFAEB5C8, 0xFFFF7A59, 0xFF151E36, 0xFF202B48,
            R.style.Theme_WildInk_Night_NoTitle, R.style.Theme_WildInk_Night_Translucent,
            R.string.palette_night),
    KRAFT("kraft", 0xFFD6C7AE, 0xFF1A1712, 0xFF3F372C, 0xFFC2341B, 0xFFF3EBDD, 0xFFC9B897,
            R.style.Theme_WildInk_Kraft_NoTitle, R.style.Theme_WildInk_Kraft_Translucent,
            R.string.palette_kraft),
    PLUM("plum", 0xFF2A1C2D, 0xFFF3E9E3, 0xFFC2B1BD, 0xFFFFB21F, 0xFF2A1C2D, 0xFF3A283E,
            R.style.Theme_WildInk_Plum_NoTitle, R.style.Theme_WildInk_Plum_Translucent,
            R.string.palette_plum);

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

    WildInkPalette(String key, int background, int text, int secondary, int accent, int onAccent, int raised,
                   int noTitleTheme, int translucentTheme, int name) {
        this.key = key;
        this.background = background;
        this.text = text;
        this.secondary = secondary;
        this.accent = accent;
        this.onAccent = onAccent;
        this.raised = raised;
        this.noTitleTheme = noTitleTheme;
        this.translucentTheme = translucentTheme;
        this.name = name;
    }

    public static WildInkPalette current() {
        return fromKey(UserPreferences.getPalette());
    }

    public static WildInkPalette fromKey(String key) {
        for (WildInkPalette palette : values()) {
            if (palette.key.equals(key)) {
                return palette;
            }
        }
        return CHARCOAL;
    }
}
