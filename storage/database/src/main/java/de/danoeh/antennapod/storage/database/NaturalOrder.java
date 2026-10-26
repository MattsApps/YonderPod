package de.danoeh.antennapod.storage.database;

import java.util.Comparator;
import java.util.Locale;

public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    private NaturalOrder() {
    }

    @Override
    public int compare(String a, String b) {
        String left = a != null ? a.toLowerCase(Locale.ROOT) : "";
        String right = b != null ? b.toLowerCase(Locale.ROOT) : "";
        int i = 0;
        int j = 0;
        while (i < left.length() && j < right.length()) {
            char c = left.charAt(i);
            char d = right.charAt(j);
            if (Character.isDigit(c) && Character.isDigit(d)) {
                int startI = i;
                int startJ = j;
                while (i < left.length() && Character.isDigit(left.charAt(i))) {
                    i++;
                }
                while (j < right.length() && Character.isDigit(right.charAt(j))) {
                    j++;
                }
                String numberLeft = stripZeros(left.substring(startI, i));
                String numberRight = stripZeros(right.substring(startJ, j));
                if (numberLeft.length() != numberRight.length()) {
                    return numberLeft.length() - numberRight.length();
                }
                int result = numberLeft.compareTo(numberRight);
                if (result != 0) {
                    return result;
                }
            } else {
                if (c != d) {
                    return c - d;
                }
                i++;
                j++;
            }
        }
        return (left.length() - i) - (right.length() - j);
    }

    private static String stripZeros(String number) {
        int start = 0;
        while (start < number.length() - 1 && number.charAt(start) == '0') {
            start++;
        }
        return number.substring(start);
    }
}
