package de.danoeh.antennapod.smartplay.download;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookTopUpTest {

    private static DownloadEpisode.Builder chapter(long id) {
        return DownloadEpisode.builder(id, id * 1000);
    }

    private static List<Long> ids(List<DownloadEpisode> episodes) {
        List<Long> result = new ArrayList<>();
        for (DownloadEpisode episode : episodes) {
            result.add(episode.getId());
        }
        return result;
    }

    @Test
    public void finishingChapterOneFetchesChapterFour() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).played(true).downloaded(true).build(),
                chapter(2).downloaded(true).build(),
                chapter(3).downloaded(true).build(),
                chapter(4).build(),
                chapter(5).build());
        assertEquals(Collections.singletonList(4L), ids(BookTopUp.chaptersToDownload(chapters, 3)));
    }

    @Test
    public void newBookFetchesTheFirstThreeInOrder() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(5).build(), chapter(3).build(), chapter(1).build(), chapter(4).build(), chapter(2).build());
        assertEquals(Arrays.asList(1L, 2L, 3L), ids(BookTopUp.chaptersToDownload(chapters, 3)));
    }

    @Test
    public void catchesUpAfterSeveralChaptersFinishedOffline() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).played(true).build(),
                chapter(2).played(true).build(),
                chapter(3).downloaded(true).build(),
                chapter(4).build(),
                chapter(5).build(),
                chapter(6).build());
        assertEquals(Arrays.asList(4L, 5L), ids(BookTopUp.chaptersToDownload(chapters, 3)));
    }

    @Test
    public void nothingToDoWhenAlreadyThreeAhead() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).downloaded(true).halfPlayed(true).build(),
                chapter(2).downloaded(true).build(),
                chapter(3).downloaded(true).build(),
                chapter(4).build());
        assertTrue(BookTopUp.chaptersToDownload(chapters, 3).isEmpty());
    }

    @Test
    public void keepAheadIsPerBook() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).downloaded(true).build(), chapter(2).build(), chapter(3).build(),
                chapter(4).build(), chapter(5).build(), chapter(6).build());
        assertEquals(Collections.singletonList(2L), ids(BookTopUp.chaptersToDownload(chapters, 2)));
        assertEquals(Arrays.asList(2L, 3L, 4L, 5L), ids(BookTopUp.chaptersToDownload(chapters, 5)));
    }

    @Test
    public void chapterThatCannotBeDownloadedStillCountsAsAhead() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).downloadable(false).build(), chapter(2).build(), chapter(3).build(), chapter(4).build());
        assertEquals(Arrays.asList(2L, 3L), ids(BookTopUp.chaptersToDownload(chapters, 3)));
    }

    @Test
    public void deletedButUnplayedChapterIsFetchedAgain() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).played(true).build(), chapter(2).inHistory(true).build(), chapter(3).build());
        assertEquals(Arrays.asList(2L, 3L), ids(BookTopUp.chaptersToDownload(chapters, 3)));
    }

    @Test
    public void finishedBookFetchesNothing() {
        List<DownloadEpisode> chapters = Arrays.asList(
                chapter(1).played(true).build(), chapter(2).played(true).build());
        assertTrue(BookTopUp.chaptersToDownload(chapters, 3).isEmpty());
    }

    @Test
    public void mobileDataFollowsTheBooksSchedule() {
        assertFalse(BookTopUp.mayUseMobileData(true, Collections.emptyList()));
        assertTrue(BookTopUp.mayUseMobileData(false, Collections.emptyList()));
        assertFalse(BookTopUp.mayUseMobileData(true, Collections.singletonList(
                new BookTopUp.CategorySchedule(CategoryScheduleMode.DEFAULT, true))));
        assertTrue(BookTopUp.mayUseMobileData(false, Collections.singletonList(
                new BookTopUp.CategorySchedule(CategoryScheduleMode.DEFAULT, true))));
        assertTrue(BookTopUp.mayUseMobileData(true, Collections.singletonList(
                new BookTopUp.CategorySchedule(CategoryScheduleMode.OWN, false))));
        assertFalse(BookTopUp.mayUseMobileData(false, Collections.singletonList(
                new BookTopUp.CategorySchedule(CategoryScheduleMode.MANUAL, false))));
        assertTrue(BookTopUp.mayUseMobileData(true, Arrays.asList(
                new BookTopUp.CategorySchedule(CategoryScheduleMode.MANUAL, true),
                new BookTopUp.CategorySchedule(CategoryScheduleMode.OWN, false))));
    }

    @Test
    public void keepAheadStaysInRange() {
        assertEquals(1, BookTopUp.clampKeepAhead(0));
        assertEquals(3, BookTopUp.clampKeepAhead(3));
        assertEquals(BookTopUp.MAX_KEEP_AHEAD, BookTopUp.clampKeepAhead(99));
    }
}
