package de.danoeh.antennapod.smartplay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the Smart Plays remember between switches (0.9.36). Plain Java, so it can be unit tested.
 *
 * <ul>
 *   <li>Hand-added episodes belong to one Smart Play each, in order, with the slot they had in its list.
 *       Switching never carries them into another Smart Play.</li>
 *   <li>A half-played episode is on top only in the Smart Play it was played from (its origin), or in the
 *       one the highlight moved to while it was still the loaded episode (carried).</li>
 *   <li>A detour (an episode played from outside the playlists) never enters any list and is never on top of
 *       one. Once something else is started, it's forgotten.</li>
 * </ul>
 *
 * <p>Slots are counted without the carried episode, which always sits in front of them.
 */
public final class PlaylistMemory {
    /** Hand-added episodes per Smart Play: episode id → slot, in the order they play. */
    private final Map<Long, LinkedHashMap<Long, Integer>> handAdded = new LinkedHashMap<>();
    /** Half-played episodes → the Smart Play they were played from. */
    private final Map<Long, Long> origin = new LinkedHashMap<>();
    private long carriedEpisode = -1;
    private long carriedTo = -1;
    private long loaded = -1;
    private long detour = -1;

    // ---- Hand-added episodes ----

    /** Hand-added episodes of a Smart Play, in order of their slots. */
    public List<Long> handAddedIds(long smartPlayId) {
        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(slots(smartPlayId).entrySet());
        entries.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : entries) {
            ids.add(entry.getKey());
        }
        return ids;
    }

    /** The slot each hand-added episode had in its Smart Play's list. */
    public Map<Long, Integer> handAddedSlots(long smartPlayId) {
        return new LinkedHashMap<>(slots(smartPlayId));
    }

    public boolean isHandAdded(long smartPlayId, long episodeId) {
        return slots(smartPlayId).containsKey(episodeId);
    }

    /** Every hand-added episode of every Smart Play. */
    public Set<Long> allHandAddedIds() {
        Set<Long> ids = new HashSet<>();
        for (LinkedHashMap<Long, Integer> slots : handAdded.values()) {
            ids.addAll(slots.keySet());
        }
        return ids;
    }

    /** Marks an episode hand-added at a slot of this Smart Play (Play next, or anything added to its list). */
    public void handAdd(long smartPlayId, long episodeId, int slot) {
        LinkedHashMap<Long, Integer> slots = editableSlots(smartPlayId);
        slots.remove(episodeId);
        for (Map.Entry<Long, Integer> entry : slots.entrySet()) {
            if (entry.getValue() >= slot) {
                entry.setValue(entry.getValue() + 1);
            }
        }
        slots.put(episodeId, Math.max(0, slot));
    }

    /**
     * The slot "Add to playlist" uses: next to play, after the hand-added episodes already waiting at the top,
     * so several added one after another play in the order added. firstSlot is where the top starts (after the
     * playing episode, if it's at the top).
     */
    public int addToPlaylistSlot(long smartPlayId, int firstSlot) {
        int slot = firstSlot;
        Set<Integer> taken = new HashSet<>(slots(smartPlayId).values());
        while (taken.contains(slot)) {
            slot++;
        }
        return slot;
    }

    /** Add to playlist: next to play in that Smart Play, after the hand-added episodes already at the top. */
    public void addToPlaylist(long smartPlayId, long episodeId) {
        LinkedHashMap<Long, Integer> slots = editableSlots(smartPlayId);
        slots.remove(episodeId);
        int slot = addToPlaylistSlot(smartPlayId, 0);
        handAdd(smartPlayId, episodeId, slot);
    }

    /** Taken out of this Smart Play by hand: no longer hand-added there. */
    public void removeHandAdded(long smartPlayId, long episodeId) {
        slots(smartPlayId).remove(episodeId);
    }

    public void clearHandAdded(long smartPlayId) {
        handAdded.remove(smartPlayId);
    }

    /**
     * The Smart Play's list as it now stands: hand-added episodes still in it take their new slots, those no
     * longer in it (played, or taken out) are dropped. The carried episode isn't counted.
     */
    public void snapshot(long smartPlayId, List<Long> list) {
        LinkedHashMap<Long, Integer> slots = slots(smartPlayId);
        if (slots.isEmpty()) {
            return;
        }
        List<Long> counted = new ArrayList<>(list);
        if (carriedTo == smartPlayId) {
            counted.remove(Long.valueOf(carriedEpisode));
        }
        LinkedHashMap<Long, Integer> updated = new LinkedHashMap<>();
        for (int i = 0; i < counted.size(); i++) {
            if (slots.containsKey(counted.get(i))) {
                updated.put(counted.get(i), i);
            }
        }
        handAdded.put(smartPlayId, updated);
    }

    /** Episodes that are gone (deleted, played, no longer playable) leave every Smart Play. */
    public void forget(Collection<Long> episodeIds) {
        for (LinkedHashMap<Long, Integer> slots : handAdded.values()) {
            slots.keySet().removeAll(episodeIds);
        }
        origin.keySet().removeAll(episodeIds);
    }

    public void forgetSmartPlay(long smartPlayId) {
        handAdded.remove(smartPlayId);
        origin.values().removeIf(value -> value == smartPlayId);
        if (carriedTo == smartPlayId) {
            carriedTo = -1;
            carriedEpisode = -1;
        }
    }

    // ---- What's loaded, and which Smart Play a half-played episode is on top of ----

    public long getLoaded() {
        return loaded;
    }

    public long getDetour() {
        return detour;
    }

    public long getCarriedEpisode() {
        return carriedTo >= 0 ? carriedEpisode : -1;
    }

    public long getCarriedTo() {
        return carriedTo;
    }

    public long originOf(long episodeId) {
        Long smartPlay = origin.get(episodeId);
        return smartPlay != null ? smartPlay : -1;
    }

    /**
     * An episode was loaded. fromPlaylist: it was in the highlighted Smart Play's list. Otherwise it's a detour,
     * which no Smart Play keeps. Starting a playlist episode ends any carry and forgets the detour.
     */
    public void onLoaded(long episodeId, boolean fromPlaylist, long highlighted) {
        if (episodeId == loaded) {
            return;
        }
        loaded = episodeId;
        if (!fromPlaylist) {
            detour = episodeId;
            return;
        }
        detour = -1;
        if (episodeId == carriedEpisode) {
            return;
        }
        carriedEpisode = -1;
        carriedTo = -1;
        if (highlighted >= 0) {
            origin.put(episodeId, highlighted);
        }
    }

    /** Nothing loaded any more (stopped with nothing next). */
    public void onUnloaded() {
        loaded = -1;
        detour = -1;
    }

    /**
     * The highlight moved. The loaded episode (not a detour) shows at the top of the new Smart Play and is
     * written into no other: the Smart Plays tapped through on the way keep nothing.
     */
    public void onHighlightMoved(long newHighlight) {
        if (loaded < 0 || loaded == detour) {
            return;
        }
        if (originOf(loaded) == newHighlight) {
            carriedEpisode = -1;
            carriedTo = -1;
            return;
        }
        carriedEpisode = loaded;
        carriedTo = newHighlight;
    }

    /** Half-played episodes that go on top of this Smart Play: its own, and the one carried into it. */
    public Set<Long> onTopIn(long smartPlayId) {
        Set<Long> ids = new HashSet<>();
        for (Map.Entry<Long, Long> entry : origin.entrySet()) {
            if (entry.getValue() == smartPlayId) {
                ids.add(entry.getKey());
            }
        }
        if (carriedTo == smartPlayId && carriedEpisode >= 0) {
            ids.add(carriedEpisode);
        }
        return ids;
    }

    /** Keeps origins only for these episodes (still half-played) and the loaded one. */
    public void retainOrigins(Collection<Long> halfPlayed) {
        origin.keySet().removeIf(id -> id != loaded && !halfPlayed.contains(id));
    }

    // ---- Upgrading from 0.9.35 ----

    /**
     * 0.9.35 kept one hand-added list shared by every Smart Play. It moves into the highlighted Smart Play,
     * keeping its order (inQueueOrder: hand-added episodes in the order of the playlist, slot = index there;
     * notInQueue: hand-added ones not in it, after them). Half-played episodes stay on top of the highlighted
     * Smart Play, as they were.
     */
    public static PlaylistMemory upgradeFrom0935(long highlighted, List<Long> queue, Collection<Long> sharedHandAdded,
                                                 Collection<Long> halfPlayed, long loaded) {
        PlaylistMemory memory = new PlaylistMemory();
        if (highlighted < 0) {
            return memory;
        }
        LinkedHashMap<Long, Integer> slots = new LinkedHashMap<>();
        for (int i = 0; i < queue.size(); i++) {
            if (sharedHandAdded.contains(queue.get(i))) {
                slots.put(queue.get(i), i);
            }
        }
        int next = queue.size();
        for (long id : sharedHandAdded) {
            if (!slots.containsKey(id)) {
                slots.put(id, next++);
            }
        }
        if (!slots.isEmpty()) {
            memory.handAdded.put(highlighted, slots);
        }
        for (long id : halfPlayed) {
            memory.origin.put(id, highlighted);
        }
        if (loaded >= 0) {
            memory.loaded = loaded;
            if (queue.contains(loaded)) {
                memory.origin.put(loaded, highlighted);
            } else {
                memory.detour = loaded;
            }
        }
        return memory;
    }

    // ---- Saving ----

    /** h&lt;sp&gt;=id:slot,id:slot;o=id:sp,…;c=episode:sp;l=loaded;d=detour */
    public String serialize() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Long, LinkedHashMap<Long, Integer>> entry : handAdded.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            out.append('h').append(entry.getKey()).append('=');
            appendPairs(out, entry.getValue());
            out.append(';');
        }
        out.append("o=");
        appendPairs(out, origin);
        out.append(";c=").append(carriedEpisode).append(':').append(carriedTo);
        out.append(";l=").append(loaded).append(";d=").append(detour);
        return out.toString();
    }

    public static PlaylistMemory parse(String saved) {
        PlaylistMemory memory = new PlaylistMemory();
        if (saved == null || saved.isEmpty()) {
            return memory;
        }
        for (String part : saved.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 1) {
                continue;
            }
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            try {
                if (key.startsWith("h")) {
                    LinkedHashMap<Long, Integer> slots = new LinkedHashMap<>();
                    for (long[] pair : pairs(value)) {
                        slots.put(pair[0], (int) pair[1]);
                    }
                    memory.handAdded.put(Long.parseLong(key.substring(1)), slots);
                } else if (key.equals("o")) {
                    for (long[] pair : pairs(value)) {
                        memory.origin.put(pair[0], pair[1]);
                    }
                } else if (key.equals("c")) {
                    String[] pair = value.split(":");
                    memory.carriedEpisode = Long.parseLong(pair[0]);
                    memory.carriedTo = Long.parseLong(pair[1]);
                } else if (key.equals("l")) {
                    memory.loaded = Long.parseLong(value);
                } else if (key.equals("d")) {
                    memory.detour = Long.parseLong(value);
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                // A damaged entry is skipped.
            }
        }
        return memory;
    }

    private static void appendPairs(StringBuilder out, Map<Long, ? extends Number> pairs) {
        boolean first = true;
        for (Map.Entry<Long, ? extends Number> pair : pairs.entrySet()) {
            out.append(first ? "" : ",").append(pair.getKey()).append(':').append(pair.getValue().longValue());
            first = false;
        }
    }

    private static List<long[]> pairs(String value) {
        List<long[]> result = new ArrayList<>();
        for (String pair : value.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                result.add(new long[] {Long.parseLong(pair.substring(0, colon)),
                        Long.parseLong(pair.substring(colon + 1))});
            }
        }
        return result;
    }

    private LinkedHashMap<Long, Integer> slots(long smartPlayId) {
        LinkedHashMap<Long, Integer> slots = handAdded.get(smartPlayId);
        return slots != null ? slots : new LinkedHashMap<>();
    }

    private LinkedHashMap<Long, Integer> editableSlots(long smartPlayId) {
        LinkedHashMap<Long, Integer> slots = handAdded.get(smartPlayId);
        if (slots == null) {
            slots = new LinkedHashMap<>();
            handAdded.put(smartPlayId, slots);
        }
        return slots;
    }
}
