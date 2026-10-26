package de.danoeh.antennapod.smartplay.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class DownloadPlannerTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 100 * DAY;

    private static DownloadEpisode.Builder episode(long id, int day) {
        return DownloadEpisode.builder(id, day * DAY);
    }

    private static DownloadEpisode fresh(long id, int day) {
        return episode(id, day).build();
    }

    private static DownloadEpisode downloaded(long id, int day) {
        return episode(id, day).downloaded(true).build();
    }

    private static ShowDownloadSettings settings(DownloadMode mode, int count, int keep) {
        return new ShowDownloadSettings(mode, count, keep, 0, true);
    }

    private static List<Long> ids(List<DownloadEpisode> episodes) {
        List<Long> result = new ArrayList<>();
        for (DownloadEpisode episode : episodes) {
            result.add(episode.getId());
        }
        return result;
    }

    private static List<DownloadEpisode> serial() {
        return new ArrayList<>(Arrays.asList(fresh(1, 91), fresh(2, 92), fresh(3, 93), fresh(4, 94), fresh(5, 95)));
    }

    @Test
    public void latestDownloadsOnlyTheNewest() {
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.LATEST, 1, 5), serial(), NOW);
        assertEquals(Arrays.asList(5L), ids(plan.getToDownload()));
    }

    @Test
    public void latestDoesNotDigBackWhenNewestIsAlreadyDownloaded() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(4, downloaded(5, 95));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.LATEST, 1, 5), episodes, NOW);
        assertTrue(plan.getToDownload().isEmpty());
    }

    @Test
    public void inOrderStartsWithOldestUnheard() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(0, episode(1, 91).played(true).build());
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.IN_ORDER, 2, 0), episodes, NOW);
        assertEquals(Arrays.asList(2L, 3L), ids(plan.getToDownload()));
    }

    @Test
    public void inOrderSkipsDownloadHistory() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(0, episode(1, 91).inHistory(true).build());
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.IN_ORDER, 1, 0), episodes, NOW);
        assertEquals(Arrays.asList(2L), ids(plan.getToDownload()));
    }

    @Test
    public void inOrderStopsAtKeepLimitInsteadOfDeleting() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(0, downloaded(1, 91));
        episodes.set(1, downloaded(2, 92));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.IN_ORDER, 2, 2), episodes, NOW);
        assertTrue(plan.getToDownload().isEmpty());
        assertTrue(plan.getToDelete().isEmpty());
    }

    @Test
    public void workBackwardsTakesNewestThenOlder() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(4, episode(5, 95).inHistory(true).build());
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.BACKWARDS, 2, 0), episodes, NOW);
        assertEquals(Arrays.asList(4L, 3L), ids(plan.getToDownload()));
    }

    @Test
    public void manualStreamAndIgnoreNeverDownload() {
        for (DownloadMode mode : Arrays.asList(DownloadMode.MANUAL, DownloadMode.STREAM_LATEST,
                DownloadMode.STREAM_IN_ORDER, DownloadMode.IGNORE)) {
            assertTrue(DownloadPlanner.plan(settings(mode, 3, 0), serial(), NOW).getToDownload().isEmpty());
        }
    }

    @Test
    public void keepAtMostDeletesOldestButKeepsHalfPlayedFavouritesAndSmartPlay() {
        List<DownloadEpisode> episodes = Arrays.asList(
                episode(1, 91).downloaded(true).halfPlayed(true).build(),
                episode(2, 92).downloaded(true).favourite(true).build(),
                downloaded(3, 93),
                episode(4, 94).downloaded(true).inSmartPlay(true).build(),
                downloaded(5, 95),
                downloaded(6, 96));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.MANUAL, 1, 4), episodes, NOW);
        assertEquals(Arrays.asList(3L, 5L), ids(plan.getToDelete()));
    }

    @Test
    public void keepAtMostDeletesPlayedFirst() {
        List<DownloadEpisode> episodes = Arrays.asList(
                downloaded(1, 91), downloaded(2, 92), episode(3, 93).downloaded(true).played(true).build());
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.MANUAL, 1, 2), episodes, NOW);
        assertEquals(Arrays.asList(3L), ids(plan.getToDelete()));
    }

    @Test
    public void latestMakesRoomForTheNewEpisode() {
        List<DownloadEpisode> episodes = Arrays.asList(downloaded(1, 91), downloaded(2, 92), fresh(3, 93));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.LATEST, 1, 2), episodes, NOW);
        assertEquals(Arrays.asList(3L), ids(plan.getToDownload()));
        assertEquals(Arrays.asList(1L), ids(plan.getToDelete()));
    }

    @Test
    public void latestSkipsDownloadWhenEverythingIsProtected() {
        List<DownloadEpisode> episodes = Arrays.asList(
                episode(1, 91).downloaded(true).favourite(true).build(),
                episode(2, 92).downloaded(true).favourite(true).build(), fresh(3, 93));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.LATEST, 1, 2), episodes, NOW);
        assertTrue(plan.getToDownload().isEmpty());
        assertTrue(plan.getToDelete().isEmpty());
    }

    @Test
    public void inOrderDeletesNewestUnplayedWhenLimitIsLowered() {
        List<DownloadEpisode> episodes = Arrays.asList(downloaded(1, 91), downloaded(2, 92), downloaded(3, 93));
        DownloadPlan plan = DownloadPlanner.plan(settings(DownloadMode.IN_ORDER, 1, 2), episodes, NOW);
        assertEquals(Arrays.asList(3L), ids(plan.getToDelete()));
    }

    @Test
    public void deleteOlderThanDays() {
        List<DownloadEpisode> episodes = Arrays.asList(downloaded(1, 80), downloaded(2, 98),
                episode(3, 70).downloaded(true).favourite(true).build(), fresh(4, 85));
        ShowDownloadSettings settings = new ShowDownloadSettings(DownloadMode.BACKWARDS, 5, 0, 10, true);
        DownloadPlan plan = DownloadPlanner.plan(settings, episodes, NOW);
        assertEquals(Arrays.asList(1L), ids(plan.getToDelete()));
        assertTrue(plan.getToDownload().isEmpty());
    }

    @Test
    public void deletionsOffProtectsTheWholeShow() {
        List<DownloadEpisode> episodes = Arrays.asList(downloaded(1, 10), downloaded(2, 92), downloaded(3, 93));
        ShowDownloadSettings settings = new ShowDownloadSettings(DownloadMode.MANUAL, 1, 1, 5, false);
        assertTrue(DownloadPlanner.plan(settings, episodes, NOW).getToDelete().isEmpty());
    }

    @Test
    public void streamLatestOffersNewestUnplayed() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(4, episode(5, 95).played(true).build());
        assertEquals(Arrays.asList(4L, 3L),
                ids(DownloadPlanner.streamable(settings(DownloadMode.STREAM_LATEST, 2, 0), episodes)));
    }

    @Test
    public void streamInOrderOffersOldestUnplayed() {
        List<DownloadEpisode> episodes = serial();
        episodes.set(0, episode(1, 91).played(true).build());
        assertEquals(Arrays.asList(2L),
                ids(DownloadPlanner.streamable(settings(DownloadMode.STREAM_IN_ORDER, 1, 0), episodes)));
    }

    @Test
    public void downloadModesAreNotStreamable() {
        assertTrue(DownloadPlanner.streamable(settings(DownloadMode.LATEST, 3, 0), serial()).isEmpty());
    }
}
