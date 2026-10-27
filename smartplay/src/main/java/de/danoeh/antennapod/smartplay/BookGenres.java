package de.danoeh.antennapod.smartplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BookGenres {
    private static final String CRIME = "Crime & Mystery Fiction";
    private static final String DETECTIVE = "Detective Fiction";
    private static final String THRILLERS = "Suspense, Espionage, Political & Thrillers";
    private static final String ADVENTURE = "Action & Adventure Fiction";
    private static final String BIOGRAPHY = "Biography & Autobiography";
    private static final String HUMOUR = "Humorous Fiction";

    private static final String[][] WORDS = {
        {"crime", CRIME, DETECTIVE},
        {"mystery", CRIME, DETECTIVE},
        {"mysteries", CRIME, DETECTIVE},
        {"detective", DETECTIVE, CRIME},
        {"murder", CRIME, DETECTIVE},
        {"thriller", THRILLERS},
        {"suspense", THRILLERS},
        {"spy", THRILLERS},
        {"spies", THRILLERS},
        {"espionage", THRILLERS},
        {"adventure", ADVENTURE},
        {"action", ADVENTURE},
        {"biography", BIOGRAPHY},
        {"biographies", BIOGRAPHY},
        {"autobiography", BIOGRAPHY},
        {"humour", HUMOUR},
        {"humor", HUMOUR},
        {"funny", HUMOUR},
    };

    private BookGenres() {
    }

    public static List<String> genreQueries(String query) {
        String trimmed = query.trim();
        Set<String> result = new LinkedHashSet<>();
        if (trimmed.isEmpty()) {
            return new ArrayList<>(result);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String[] entry : WORDS) {
            if (lower.contains(entry[0])) {
                for (int i = 1; i < entry.length; i++) {
                    result.add(entry[i]);
                }
            }
        }
        result.add(trimmed);
        result.add("^" + trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1));
        return new ArrayList<>(result);
    }
}
