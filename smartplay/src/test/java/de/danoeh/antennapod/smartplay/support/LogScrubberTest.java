package de.danoeh.antennapod.smartplay.support;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogScrubberTest {

    @Test
    public void privateFeedLinksLoseEverythingButTheHost() {
        String scrubbed = LogScrubber.scrub("D/Feed: refreshing https://www.patreon.com/rss/threebeansalad"
                + "?auth=Zq8xV3secretToken&show=123 done");
        assertFalse(scrubbed.contains("Zq8xV3secretToken"));
        assertFalse(scrubbed.contains("threebeansalad"));
        assertTrue(scrubbed.contains("https://www.patreon.com/…"));
    }

    @Test
    public void passwordsInLinksAreRemoved() {
        String scrubbed = LogScrubber.scrub("GET https://matt:hunter2@feeds.example.com/private.xml 401");
        assertFalse(scrubbed.contains("hunter2"));
        assertFalse(scrubbed.contains("matt"));
        assertEquals("GET https://feeds.example.com/… 401", scrubbed);
    }

    @Test
    public void namedSecretsAreHidden() {
        String scrubbed = LogScrubber.scrub("login password=hunter2 token: abc.def username=matt");
        assertFalse(scrubbed.contains("hunter2"));
        assertFalse(scrubbed.contains("abc.def"));
        assertFalse(scrubbed.contains("matt"));
        assertTrue(scrubbed.contains("password=<hidden>"));
    }

    @Test
    public void filePathsAreHidden() {
        String scrubbed = LogScrubber.scrub("open /storage/emulated/0/Music/Matt's Holiday Mix/track 1.mp3 failed\n"
                + "at /data/user/0/com.mattsapps.yonderpod/files/media/Rest Is Politics/ep.mp3");
        assertFalse(scrubbed.contains("Holiday"));
        assertFalse(scrubbed.contains("Rest Is Politics"));
        assertTrue(scrubbed.contains("<file>"));
    }

    @Test
    public void otherLinksAndEmailsAreHidden() {
        String scrubbed = LogScrubber.scrub("uri content://com.android.externalstorage.documents/tree/primary%3AMusic "
                + "from me@example.com");
        assertFalse(scrubbed.contains("primary"));
        assertFalse(scrubbed.contains("me@example.com"));
    }

    @Test
    public void ordinaryLinesStayReadable() {
        assertEquals("E/Player: buffering 3 s at 1.5x", LogScrubber.scrub("E/Player: buffering 3 s at 1.5x"));
        assertEquals("", LogScrubber.scrub(null));
    }
}
