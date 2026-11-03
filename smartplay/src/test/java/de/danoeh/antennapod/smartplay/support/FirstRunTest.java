package de.danoeh.antennapod.smartplay.support;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FirstRunTest {

    @Test
    public void welcomeShowsOnceOnAFreshInstall() {
        assertTrue(FirstRun.showWelcome(false, false));
        assertFalse(FirstRun.showWelcome(true, false));
    }

    @Test
    public void welcomeSkipsPeopleWhoAlreadyHaveShows() {
        assertFalse(FirstRun.showWelcome(false, true));
    }

    @Test
    public void crashAskedAboutOnce() {
        assertTrue(FirstRun.askAboutCrash(2000, 1000, false));
        assertFalse(FirstRun.askAboutCrash(2000, 2000, false));
        assertFalse(FirstRun.askAboutCrash(0, 0, false));
    }

    @Test
    public void neverAskMeansNever() {
        assertFalse(FirstRun.askAboutCrash(5000, 0, true));
    }
}
