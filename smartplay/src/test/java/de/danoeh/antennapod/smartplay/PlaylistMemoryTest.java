package de.danoeh.antennapod.smartplay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/** 0.9.36: hand-added episodes per Smart Play, "Playing from", detours. */
public class PlaylistMemoryTest {
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NORMAL = 1;
    private static final long GAMING = 2;
    private static final long WALKING = 3;
    private static final long COMEDY = 10;
    private static final long XBOX = 20;

    private final SmartPlayEngine engine = new SmartPlayEngine(new Random(42));

    private static SmartPlayEpisode episode(long id, long feedId, int day, String category) {
        return SmartPlayEpisode.builder(id, feedId).pubDate(day * DAY).downloaded(true).category(category).build();
    }

    private static SmartPlayEpisode halfPlayed(long id, long feedId, int day, String category, long lastPlayed) {
        return SmartPlayEpisode.builder(id, feedId).pubDate(day * DAY).downloaded(true).category(category)
                .position(60000).lastPlayedTime(lastPlayed).build();
    }

    private static List<Long> ids(List<SmartPlayEpisode> episodes) {
        List<Long> result = new ArrayList<>();
        for (SmartPlayEpisode episode : episodes) {
            result.add(episode.getId());
        }
        return result;
    }

    private static SmartPlayDefinition latest(String name, int count) {
        return SmartPlayDefinition.builder(name)
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, count, EpisodeSource.allFeeds())).build();
    }

    private static SmartPlayDefinition gaming() {
        return SmartPlayDefinition.builder("Gaming")
                .rule(SmartPlayRule.of(SmartPlayRule.Order.LATEST, 5, EpisodeSource.allFeeds()))
                .neverInclude(new Exclusions(Collections.singleton("comedy"), Collections.<Long>emptySet())).build();
    }

    private List<SmartPlayEpisode> build(PlaylistMemory memory, long smartPlay, SmartPlayDefinition definition,
                                         List<SmartPlayEpisode> candidates) {
        List<SmartPlayEpisode> handAdded = new ArrayList<>();
        for (long id : memory.handAddedIds(smartPlay)) {
            for (SmartPlayEpisode candidate : candidates) {
                if (candidate.getId() == id) {
                    handAdded.add(candidate);
                }
            }
        }
        ManualEdits edits = new ManualEdits(memory.handAddedIds(smartPlay), Collections.emptySet());
        return engine.rebuildWithSlots(definition, candidates, handAdded, memory.handAddedSlots(smartPlay), edits,
                memory.onTopIn(smartPlay));
    }

    @Test
    public void handAddedEpisodesStayInTheirOwnSmartPlayAcrossSwitches() {
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, XBOX, 9, "gaming"), episode(2, XBOX, 8, "gaming"),
                episode(50, COMEDY, 1, "comedy"), episode(51, COMEDY, 2, "comedy"));
        PlaylistMemory memory = new PlaylistMemory();
        memory.handAdd(NORMAL, 50, 0);
        memory.addToPlaylist(NORMAL, 51);

        memory.onHighlightMoved(GAMING);
        List<SmartPlayEpisode> gamingList = build(memory, GAMING, gaming(), candidates);
        assertFalse(ids(gamingList).contains(50L));
        assertFalse(ids(gamingList).contains(51L));
        assertTrue(memory.handAddedIds(GAMING).isEmpty());

        memory.onHighlightMoved(NORMAL);
        List<SmartPlayEpisode> normalList = build(memory, NORMAL, latest("Normal", 2), candidates);
        assertEquals(Arrays.asList(50L, 51L, 1L, 2L), ids(normalList));
    }

    @Test
    public void threeAddToPlaylistCallsPlayInTheOrderAdded() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.addToPlaylist(WALKING, 7);
        memory.addToPlaylist(WALKING, 3);
        memory.addToPlaylist(WALKING, 5);
        assertEquals(Arrays.asList(7L, 3L, 5L), memory.handAddedIds(WALKING));

        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, XBOX, 9, "gaming"), episode(3, XBOX, 1, "gaming"), episode(5, XBOX, 2, "gaming"),
                episode(7, XBOX, 3, "gaming"));
        List<SmartPlayEpisode> list = build(memory, WALKING, latest("Walking", 1), candidates);
        assertEquals(Arrays.asList(7L, 3L, 5L, 1L), ids(list));
    }

    @Test
    public void addToPlaylistGoesAfterTheHandAddedEpisodesAtTheTopNotAtTheEnd() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.handAdd(NORMAL, 4, 0);
        memory.handAdd(NORMAL, 9, 6);
        memory.addToPlaylist(NORMAL, 8);
        assertEquals(Arrays.asList(4L, 8L, 9L), memory.handAddedIds(NORMAL));
        assertEquals(Integer.valueOf(1), memory.handAddedSlots(NORMAL).get(8L));
        // In the highlighted list, the top starts after the playing episode.
        assertEquals(2, memory.addToPlaylistSlot(NORMAL, 1));
    }

    @Test
    public void aDetourEntersNoList() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(1, true, NORMAL);
        memory.onLoaded(99, false, NORMAL);
        assertEquals(99, memory.getDetour());
        assertFalse(memory.allHandAddedIds().contains(99L));
        assertFalse(memory.onTopIn(NORMAL).contains(99L));

        memory.onHighlightMoved(GAMING);
        assertFalse(memory.onTopIn(GAMING).contains(99L));
        assertEquals(-1, memory.getCarriedEpisode());

        // Something else started: the detour is forgotten by the playlists.
        memory.onLoaded(2, true, GAMING);
        assertEquals(-1, memory.getDetour());
        assertFalse(memory.onTopIn(NORMAL).contains(99L));
        assertFalse(memory.onTopIn(GAMING).contains(99L));
    }

    @Test
    public void theInterruptedEpisodeKeepsItsSlots() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(1, true, NORMAL);
        memory.onHighlightMoved(WALKING);
        assertTrue(memory.onTopIn(WALKING).contains(1L));

        // Play pressed on episode 5 in Walking's list: episode 1 is interrupted.
        memory.onLoaded(5, true, WALKING);
        assertTrue(memory.onTopIn(NORMAL).contains(1L));
        assertFalse(memory.onTopIn(WALKING).contains(1L));

        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(4, XBOX, 9, "gaming"), episode(6, XBOX, 8, "gaming"),
                halfPlayed(1, XBOX, 7, "gaming", 100), episode(5, XBOX, 1, "gaming"));
        // Walking (latest first) takes it at its normal rule slot, not the top.
        assertEquals(Arrays.asList(4L, 6L, 1L, 5L), ids(build(memory, WALKING, latest("Walking", 4), candidates)));
        // Its own Smart Play still has it on top.
        assertEquals(1L, ids(build(memory, NORMAL, latest("Normal", 4), candidates)).get(0).longValue());
    }

    @Test
    public void halfPlayedEpisodeIsOnlyOnTopOfItsOwnSmartPlay() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(1, true, NORMAL);
        memory.onLoaded(2, true, NORMAL);
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(3, XBOX, 9, "gaming"), halfPlayed(1, XBOX, 1, "gaming", 100));
        assertEquals(Arrays.asList(1L, 3L), ids(build(memory, NORMAL, latest("Normal", 5), candidates)));
        assertEquals(Arrays.asList(3L, 1L), ids(build(memory, GAMING, latest("Gaming", 5), candidates)));
    }

    @Test
    public void tappingThroughSmartPlaysLeavesNoTrace() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(50, true, NORMAL);
        memory.onHighlightMoved(GAMING);
        memory.onHighlightMoved(WALKING);
        memory.onHighlightMoved(3000);
        assertTrue(memory.onTopIn(GAMING).isEmpty());
        assertTrue(memory.onTopIn(WALKING).isEmpty());
        assertTrue(memory.handAddedIds(GAMING).isEmpty());
        assertTrue(memory.handAddedIds(WALKING).isEmpty());
        assertTrue(memory.onTopIn(3000).contains(50L));
        assertTrue(memory.onTopIn(NORMAL).contains(50L));

        // Matt's Gaming case: a comedy episode half-played in Normal isn't on top of Gaming, and Gaming's
        // never include keeps it out.
        List<SmartPlayEpisode> candidates = Arrays.asList(
                episode(1, XBOX, 9, "gaming"), halfPlayed(50, COMEDY, 1, "comedy", 100));
        memory.onLoaded(1, true, 3000);
        assertEquals(Collections.singletonList(1L), ids(build(memory, GAMING, gaming(), candidates)));
    }

    @Test
    public void movingBackToItsOwnSmartPlayIsNotACarry() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(1, true, NORMAL);
        memory.onHighlightMoved(GAMING);
        memory.onHighlightMoved(NORMAL);
        assertEquals(-1, memory.getCarriedEpisode());
        assertTrue(memory.onTopIn(NORMAL).contains(1L));
        assertFalse(memory.onTopIn(GAMING).contains(1L));
    }

    @Test
    public void snapshotKeepsSlotsWithoutTheCarriedEpisode() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.onLoaded(1, true, NORMAL);
        memory.onHighlightMoved(GAMING);
        memory.handAdd(GAMING, 8, 1);
        memory.snapshot(GAMING, Arrays.asList(1L, 8L, 4L, 5L));
        assertEquals(Integer.valueOf(0), memory.handAddedSlots(GAMING).get(8L));
        memory.snapshot(GAMING, Arrays.asList(1L, 4L, 5L));
        assertTrue(memory.handAddedIds(GAMING).isEmpty());
    }

    @Test
    public void upgradeMovesTheSharedListIntoTheHighlightedSmartPlay() {
        PlaylistMemory memory = PlaylistMemory.upgradeFrom0935(NORMAL, Arrays.asList(5L, 6L, 7L, 8L),
                new HashSet<>(Arrays.asList(7L, 5L, 99L)), Collections.singleton(6L), 6);
        assertEquals(Arrays.asList(5L, 7L, 99L), memory.handAddedIds(NORMAL));
        assertEquals(Integer.valueOf(0), memory.handAddedSlots(NORMAL).get(5L));
        assertEquals(Integer.valueOf(2), memory.handAddedSlots(NORMAL).get(7L));
        assertTrue(memory.handAddedIds(GAMING).isEmpty());
        assertTrue(memory.onTopIn(NORMAL).contains(6L));
        assertEquals(6, memory.getLoaded());

        PlaylistMemory again = PlaylistMemory.parse(memory.serialize());
        assertEquals(memory.serialize(), again.serialize());
        assertEquals(Arrays.asList(5L, 7L, 99L), again.handAddedIds(NORMAL));
    }

    @Test
    public void forgettingAnEpisodeTakesItOutOfEverySmartPlay() {
        PlaylistMemory memory = new PlaylistMemory();
        memory.handAdd(NORMAL, 4, 0);
        memory.handAdd(GAMING, 4, 2);
        memory.forget(Collections.singleton(4L));
        assertTrue(memory.allHandAddedIds().isEmpty());
    }
}
