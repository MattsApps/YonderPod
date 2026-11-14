package de.danoeh.antennapod.smartplay.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a part number from an episode title, so a later part never plays before an earlier one.
 *
 * <p>Covered: "Part 2", "Part II", "Part Two", "Pt 2", "Pt. 2", "Pt.2", "Part #2", "2 of 3", "(2/3)" and
 * "[2/3]", in any letter case. The words one to ten and Roman numerals I to XX are read after "Part" or "Pt".
 * The stem is what comes before the part marker, lower case with punctuation dropped, so "Rome (Part 2)" and
 * "Rome, Pt 1" share the stem "rome".
 */
public final class EpisodeParts {
    private static final String WORDS = "one|two|three|four|five|six|seven|eight|nine|ten";
    private static final String[] WORD_LIST = WORDS.split("\\|");
    private static final Pattern PART = Pattern.compile(
            "\\b(?:part|pt)\\b\\.?\\s*#?\\s*(\\d{1,2}|" + WORDS + "|[ivx]{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OF = Pattern.compile("\\b(\\d{1,2})\\s+of\\s+(\\d{1,2})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLASH = Pattern.compile("[\\(\\[]\\s*(\\d{1,2})\\s*/\\s*(\\d{1,2})\\s*[\\)\\]]");

    private EpisodeParts() {
    }

    public static final class Part {
        public final int number;
        public final String stem;

        Part(int number, String stem) {
            this.number = number;
            this.stem = stem;
        }
    }

    /** The title's part, or null if it doesn't name one. */
    public static Part of(String title) {
        if (title == null) {
            return null;
        }
        Matcher part = PART.matcher(title);
        if (part.find()) {
            int number = number(part.group(1));
            if (number > 0) {
                return new Part(number, stem(title.substring(0, part.start())));
            }
        }
        Matcher of = OF.matcher(title);
        if (of.find()) {
            Part found = ofTotal(title, of);
            if (found != null) {
                return found;
            }
        }
        Matcher slash = SLASH.matcher(title);
        if (slash.find()) {
            return ofTotal(title, slash);
        }
        return null;
    }

    private static Part ofTotal(String title, Matcher matcher) {
        int number = Integer.parseInt(matcher.group(1));
        int total = Integer.parseInt(matcher.group(2));
        if (number < 1 || total < 2 || number > total) {
            return null;
        }
        return new Part(number, stem(title.substring(0, matcher.start())));
    }

    private static int number(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (Character.isDigit(lower.charAt(0))) {
            return Integer.parseInt(lower);
        }
        for (int i = 0; i < WORD_LIST.length; i++) {
            if (WORD_LIST[i].equals(lower)) {
                return i + 1;
            }
        }
        return roman(lower);
    }

    private static int roman(String text) {
        int total = 0;
        int previous = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            int value = c == 'i' ? 1 : c == 'v' ? 5 : c == 'x' ? 10 : 0;
            if (value == 0) {
                return 0;
            }
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return total <= 20 ? total : 0;
    }

    static String stem(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
