package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.storage.database.DBReader;

/**
 * 1.0.5: the Smart Plays Yonder made by itself for a recorded show, a book or a folder stay underneath (play
 * order, resuming, extra parts) but aren't shown. One someone renamed or edited is theirs and stays visible.
 */
final class AutoSmartPlays {
    private AutoSmartPlays() {
    }

    static List<SmartPlayStore.StoredSmartPlay> visible(Context context, List<SmartPlayStore.StoredSmartPlay> all) {
        List<SmartPlayStore.StoredSmartPlay> result = new ArrayList<>();
        for (SmartPlayStore.StoredSmartPlay stored : all) {
            if (!isHidden(context, stored)) {
                result.add(stored);
            }
        }
        return result;
    }

    static boolean isHidden(Context context, SmartPlayStore.StoredSmartPlay stored) {
        SmartPlayDefinition definition = stored.getDefinition();
        long feedId = onlyFeed(definition);
        if (feedId < 0) {
            return false;
        }
        String json = SmartPlayJson.toJson(definition);
        if (definition.isBookList()) {
            Feed feed = DBReader.getFeed(feedId, false, 0, 0);
            return feed != null && definition.getName().equals(feed.getTitle())
                    && json.equals(SmartPlayJson.toJson(Books.bookSmartPlay(feed)));
        }
        RecordingStore.Show show = RecordingStore.getInstance(context).showForFeed(feedId);
        return show != null && definition.getName().equals(show.name)
                && json.equals(SmartPlayJson.toJson(SmartPlayDefinition.builder(show.name)
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 1, EpisodeSource.feed(feedId)))
                .build()));
    }

    /** The one feed every rule takes from, or -1. Cheap: no database. */
    private static long onlyFeed(SmartPlayDefinition definition) {
        if (definition.getRules().isEmpty() || !definition.getPriorityFeeds().isEmpty()) {
            return -1;
        }
        long feedId = -1;
        for (SmartPlayRule rule : definition.getRules()) {
            EpisodeSource source = rule.getSource();
            if (source.getType() != EpisodeSource.Type.FEED || (feedId >= 0 && source.getFeedId() != feedId)) {
                return -1;
            }
            feedId = source.getFeedId();
        }
        return feedId;
    }
}
