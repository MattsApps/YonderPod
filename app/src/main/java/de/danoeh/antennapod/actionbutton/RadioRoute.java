package de.danoeh.antennapod.actionbutton;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.smartplay.app.Recordings;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;

/**
 * 1.0.8: every Radio route starts the Radio world. The play and stream buttons on a recording's show page,
 * its episode page, search results and the downloads list are outside any smart play's list (playlist rows have
 * their own play button), so a recording played from them belongs to Radio, even if it's also in a playlist:
 * its extra parts, then the next ready recording, then that station live.
 */
public final class RadioRoute {
    private RadioRoute() {
    }

    /** Starts the Radio world if this is a radio recording; anything else is left alone. */
    public static void beforePlay(FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || media.isLiveRadio() || item.getFeed() == null
                || !Recordings.isRecordingsFeed(item.getFeed())) {
            return;
        }
        NextUpPreferences.startWorld(media.getId(), NextUpPreferences.WORLD_RADIO);
    }
}
