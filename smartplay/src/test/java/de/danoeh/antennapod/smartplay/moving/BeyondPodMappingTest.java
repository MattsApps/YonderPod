package de.danoeh.antennapod.smartplay.moving;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BeyondPodMappingTest {
    private static final ShowDownloadSettings DEFAULTS = ShowDownloadSettings.beyondPodDefaults();

    @Test
    public void modes() {
        assertEquals(DownloadMode.MANUAL, BeyondPodMapping.mode(0));
        assertEquals(DownloadMode.LATEST, BeyondPodMapping.mode(1));
        assertEquals(DownloadMode.STREAM_LATEST, BeyondPodMapping.mode(2));
        assertEquals(DownloadMode.STREAM_IN_ORDER, BeyondPodMapping.mode(3));
        assertEquals(DownloadMode.IN_ORDER, BeyondPodMapping.mode(4));
        assertEquals(DownloadMode.IGNORE, BeyondPodMapping.mode(5));
        assertEquals(DownloadMode.LATEST, BeyondPodMapping.mode(null));
        assertEquals(DownloadMode.LATEST, BeyondPodMapping.mode(42));
    }

    @Test
    public void feedSettingsFallBackToDefaults() {
        ShowDownloadSettings settings = BeyondPodMapping.settings(1, 1, null, 99999, DEFAULTS);
        assertEquals(DownloadMode.LATEST, settings.getMode());
        assertEquals(1, settings.getEpisodeCount());
        assertEquals(5, settings.getKeepAtMost());
        assertEquals(ShowDownloadSettings.NO_LIMIT, settings.getDeleteOlderThanDays());
        ShowDownloadSettings custom = BeyondPodMapping.settings(4, 3, 8, 30, DEFAULTS);
        assertEquals(DownloadMode.IN_ORDER, custom.getMode());
        assertEquals(3, custom.getEpisodeCount());
        assertEquals(8, custom.getKeepAtMost());
        assertEquals(30, custom.getDeleteOlderThanDays());
    }

    @Test
    public void globalDefaults() {
        ShowDownloadSettings settings = BeyondPodMapping.defaults("2", "7", "99999", DEFAULTS);
        assertEquals(2, settings.getEpisodeCount());
        assertEquals(7, settings.getKeepAtMost());
        assertEquals(ShowDownloadSettings.NO_LIMIT, settings.getDeleteOlderThanDays());
    }

    @Test
    public void categoriesDropUncategorized() {
        assertEquals(Collections.emptyList(), BeyondPodMapping.categories("Uncategorized|Uncategorized"));
        assertEquals(Arrays.asList("Comedy", "News"), BeyondPodMapping.categories("Comedy|News"));
        assertEquals(Arrays.asList("Comedy"), BeyondPodMapping.categories("Comedy|comedy"));
        assertEquals(Collections.emptyList(), BeyondPodMapping.categories(null));
    }

    @Test
    public void rules() {
        assertEquals(SmartPlayRule.Order.LATEST, BeyondPodMapping.order(-2));
        assertEquals(SmartPlayRule.Order.OLDEST, BeyondPodMapping.order(-1));
        assertEquals(1, BeyondPodMapping.count(null));
        assertEquals(3, BeyondPodMapping.count(3));
        assertTrue(BeyondPodMapping.isAllFeeds(null, ""));
        assertTrue(BeyondPodMapping.isAllFeeds(null, "Uncategorized"));
        assertFalse(BeyondPodMapping.isAllFeeds("63f59467", ""));
        assertFalse(BeyondPodMapping.isAllFeeds(null, "Comedy"));
    }

    @Test
    public void updateTime() {
        assertEquals(2 * 60 + 25, BeyondPodMapping.minuteOfDay(1672280700000L, TimeZone.getTimeZone("UTC")));
        assertEquals(15 * 60 + 25,
                BeyondPodMapping.minuteOfDay(1672280700000L, TimeZone.getTimeZone("Pacific/Auckland")));
    }

    @Test
    public void privateFeeds() {
        assertTrue(BeyondPodMapping.isPrivate("https://www.patreon.com/rss/x?auth=abc", ""));
        assertTrue(BeyondPodMapping.isPrivate("https://feeds.example.com/x", "me"));
        assertTrue(BeyondPodMapping.isPrivate("https://user:pw@feeds.example.com/x", ""));
        assertFalse(BeyondPodMapping.isPrivate("https://feeds.acast.com/public/shows/x", ""));
    }
}
