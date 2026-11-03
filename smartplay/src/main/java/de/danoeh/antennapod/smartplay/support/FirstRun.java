package de.danoeh.antennapod.smartplay.support;

public final class FirstRun {
    private FirstRun() {
    }

    public static boolean showWelcome(boolean alreadyShown, boolean hasShows) {
        return !alreadyShown && !hasShows;
    }

    public static boolean askAboutCrash(long crashTime, long lastAnswered, boolean neverAsk) {
        return !neverAsk && crashTime > 0 && crashTime > lastAnswered;
    }
}
