package de.danoeh.antennapod.smartplay.support;

import java.util.List;

/**
 * "I'll choose" mode, when the Smart Play has nothing next: what plays from the show just finished.
 * First the next newer episode, otherwise the show's newest unplayed one. Either way, if it's a later part
 * and an earlier part is unplayed, the earliest unplayed part plays instead.
 */
public final class NextInShow {
    private static final int MAX_PARTS_BACK = 20;

    private NextInShow() {
    }

    public static final class Episode {
        public final long id;
        public final String title;
        public final long date;
        public final boolean played;
        /** Left out when Yonder picks (a half-played episode with half-played episodes turned off). */
        public final boolean leftOut;

        public Episode(long id, String title, long date, boolean played, boolean leftOut) {
            this.id = id;
            this.title = title;
            this.date = date;
            this.played = played;
            this.leftOut = leftOut;
        }
    }

    /** The episode to play, or null for nothing new in the show. */
    public static Episode choose(List<Episode> show, long finishedId, long finishedDate) {
        Episode newer = null;
        Episode newest = null;
        for (Episode episode : show) {
            if (episode.id == finishedId || episode.played || episode.leftOut) {
                continue;
            }
            if (episode.date > finishedDate && (newer == null || episode.date < newer.date)) {
                newer = episode;
            }
            if (newest == null || episode.date > newest.date) {
                newest = episode;
            }
        }
        Episode pick = newer != null ? newer : newest;
        return pick == null ? null : earliestUnplayedPart(show, pick, finishedId);
    }

    /** Walks back from a later part to the earliest part that's still unplayed. */
    public static Episode earliestUnplayedPart(List<Episode> show, Episode pick, long finishedId) {
        for (int step = 0; step < MAX_PARTS_BACK; step++) {
            EpisodeParts.Part part = EpisodeParts.of(pick.title);
            if (part == null || part.number <= 1) {
                return pick;
            }
            Episode earlier = null;
            for (Episode episode : show) {
                if (episode.id == pick.id || episode.id == finishedId || episode.played) {
                    continue;
                }
                EpisodeParts.Part other = EpisodeParts.of(episode.title);
                if (other == null || other.number != part.number - 1 || !sameStory(part, other)
                        || episode.date > pick.date) {
                    continue;
                }
                if (earlier == null || episode.date > earlier.date) {
                    earlier = episode;
                }
            }
            if (earlier == null) {
                return pick;
            }
            pick = earlier;
        }
        return pick;
    }

    private static boolean sameStory(EpisodeParts.Part part, EpisodeParts.Part other) {
        return part.stem.isEmpty() || other.stem.isEmpty() || part.stem.equals(other.stem);
    }
}
