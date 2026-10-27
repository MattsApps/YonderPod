package de.danoeh.antennapod.smartplay;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class BookGenresTest {

    @Test
    public void crimeFindsLibriVoxCrimeGenres() {
        assertEquals(Arrays.asList("Crime & Mystery Fiction", "Detective Fiction", "crime", "^Crime"),
                BookGenres.genreQueries("crime"));
    }

    @Test
    public void thrillerFindsLibriVoxThrillerGenre() {
        assertEquals(Arrays.asList("Suspense, Espionage, Political & Thrillers", "Thrillers", "^Thrillers"),
                BookGenres.genreQueries("Thrillers"));
    }

    @Test
    public void unknownWordsStillSearchAsTyped() {
        assertEquals(Arrays.asList("science fiction", "^Science fiction"),
                BookGenres.genreQueries(" science fiction "));
    }

    @Test
    public void emptyQuerySearchesNothing() {
        assertEquals(Collections.emptyList(), BookGenres.genreQueries("  "));
    }
}
