package de.danoeh.antennapod.smartplay;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Exclusions {
    private static final Exclusions NONE = new Exclusions(Collections.emptySet(), Collections.emptySet());

    private final Set<String> categories;
    private final Set<Long> feedIds;

    public Exclusions(Collection<String> categories, Collection<Long> feedIds) {
        this.categories = Collections.unmodifiableSet(new HashSet<>(categories));
        this.feedIds = Collections.unmodifiableSet(new HashSet<>(feedIds));
    }

    public static Exclusions none() {
        return NONE;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public Set<Long> getFeedIds() {
        return feedIds;
    }

    public boolean isEmpty() {
        return categories.isEmpty() && feedIds.isEmpty();
    }

    public boolean matches(SmartPlayEpisode episode) {
        return matchesFeed(episode.getFeedId(), episode.getCategories());
    }

    public boolean matchesFeed(long feedId, Set<String> feedCategories) {
        if (feedIds.contains(feedId)) {
            return true;
        }
        for (String category : feedCategories) {
            if (categories.contains(category)) {
                return true;
            }
        }
        return false;
    }
}
