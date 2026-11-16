package de.danoeh.antennapod.smartplay.app;

import java.util.Locale;

/**
 * F-DROID BUILD ONLY: the Buy Me a Coffee shop items and the tip codes, all in this one file.
 *
 * <p>Honesty system: one shared code, no server, no online checks. Every shop item gives the same code.
 * Entering a valid code brings "yonder" back for a year from that day.
 */
final class TipCodes {

    // The four Buy Me a Coffee shop items, in the same order as the buttons on the tip screen.
    static final String[] SHOP_ITEMS = {
        "https://buymeacoffee.com/mattsapps/e/598788", // NZ$3
        "https://buymeacoffee.com/mattsapps/e/598790", // NZ$6
        "https://buymeacoffee.com/mattsapps/e/598794", // NZ$12
        "https://buymeacoffee.com/mattsapps/e/598796", // NZ$24
    };

    // Every code Yonder has ever given out. All of them stay valid, so never remove one.
    // To add next year's code: add a new line at the end of this list, in quotes, with a comma after it.
    // Spaces and upper/lower case don't matter.
    static final String[] CODES = {
        "3689", // first code, Nov 2026
    };

    private TipCodes() {
    }

    static boolean matches(String typed) {
        String tidy = tidy(typed);
        if (tidy.isEmpty()) {
            return false;
        }
        for (String code : CODES) {
            if (tidy(code).equals(tidy)) {
                return true;
            }
        }
        return false;
    }

    private static String tidy(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
