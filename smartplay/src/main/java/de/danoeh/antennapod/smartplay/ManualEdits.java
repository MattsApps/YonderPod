package de.danoeh.antennapod.smartplay;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ManualEdits {
    private final Set<Long> handAddedIds;
    private final Set<Long> removedIds;

    public ManualEdits(Collection<Long> handAddedIds, Collection<Long> removedIds) {
        this.handAddedIds = Collections.unmodifiableSet(new HashSet<>(handAddedIds));
        this.removedIds = Collections.unmodifiableSet(new HashSet<>(removedIds));
    }

    public static ManualEdits none() {
        return new ManualEdits(Collections.emptySet(), Collections.emptySet());
    }

    public Set<Long> getHandAddedIds() {
        return handAddedIds;
    }

    public Set<Long> getRemovedIds() {
        return removedIds;
    }

    public boolean isHandAdded(long episodeId) {
        return handAddedIds.contains(episodeId);
    }

    public boolean isRemoved(long episodeId) {
        return removedIds.contains(episodeId);
    }
}
