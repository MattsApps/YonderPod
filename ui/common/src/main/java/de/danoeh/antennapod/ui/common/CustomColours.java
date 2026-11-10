package de.danoeh.antennapod.ui.common;

public final class CustomColours {
    public static final int BACKGROUND = 0;
    public static final int TEXT = 1;
    public static final int SECONDARY = 2;
    public static final int ACCENT = 3;
    public static final int ON_ACCENT = 4;
    public static final int RAISED = 5;

    static final double TEXT_CONTRAST = 4.5;
    static final double ACCENT_CONTRAST = 3.0;
    private static final int DARK_INK = 0xFF111111;
    private static final int LIGHT_INK = 0xFFF3EFE6;

    private CustomColours() {
    }

    public static int[] derive(int background, int accent) {
        background = opaque(background);
        accent = opaque(accent);
        int text = contrast(background, DARK_INK) >= contrast(background, LIGHT_INK) ? DARK_INK : LIGHT_INK;
        background = nudgeAway(background, text, TEXT_CONTRAST);
        int raised = towards(background, text, 0.08);
        int secondary = readable(towards(text, background, 0.35), text, background, raised);
        accent = nudgeEitherWay(accent, background, ACCENT_CONTRAST);
        int onAccent = contrast(accent, DARK_INK) >= contrast(accent, LIGHT_INK) ? DARK_INK : LIGHT_INK;
        return new int[] {background, text, secondary, accent, onAccent, raised};
    }

    public static boolean isLight(int colour) {
        return luminance(colour) > 0.4;
    }

    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static double luminance(int colour) {
        return 0.2126 * channel((colour >> 16) & 0xFF) + 0.7152 * channel((colour >> 8) & 0xFF)
                + 0.0722 * channel(colour & 0xFF);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int readable(int colour, int text, int background, int raised) {
        int result = colour;
        for (int step = 0; step <= 20; step++) {
            result = towards(colour, text, step * 0.05);
            if (contrast(result, background) >= TEXT_CONTRAST && contrast(result, raised) >= TEXT_CONTRAST) {
                return result;
            }
        }
        return result;
    }

    private static int nudgeAway(int colour, int other, double target) {
        int towards = luminance(other) > 0.18 ? 0xFF000000 : 0xFFFFFFFF;
        int result = colour;
        for (int step = 1; step <= 25 && contrast(result, other) < target; step++) {
            result = towards(colour, towards, step * 0.04);
        }
        return result;
    }

    private static int nudgeEitherWay(int colour, int other, double target) {
        for (int step = 0; step <= 25; step++) {
            int lighter = towards(colour, 0xFFFFFFFF, step * 0.04);
            int darker = towards(colour, 0xFF000000, step * 0.04);
            boolean lighterOk = contrast(lighter, other) >= target;
            boolean darkerOk = contrast(darker, other) >= target;
            if (lighterOk && darkerOk) {
                return contrast(lighter, other) >= contrast(darker, other) ? lighter : darker;
            } else if (lighterOk) {
                return lighter;
            } else if (darkerOk) {
                return darker;
            }
        }
        return nudgeAway(colour, other, target);
    }

    static int towards(int from, int to, double amount) {
        int r = mix((from >> 16) & 0xFF, (to >> 16) & 0xFF, amount);
        int g = mix((from >> 8) & 0xFF, (to >> 8) & 0xFF, amount);
        int b = mix(from & 0xFF, to & 0xFF, amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, double amount) {
        return (int) Math.round(a + (b - a) * Math.min(1, Math.max(0, amount)));
    }

    private static int opaque(int colour) {
        return colour | 0xFF000000;
    }
}
