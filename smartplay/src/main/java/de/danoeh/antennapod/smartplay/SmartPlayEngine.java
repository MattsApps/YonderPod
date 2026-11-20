package de.danoeh.antennapod.smartplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SmartPlayEngine {
    private static final Comparator<SmartPlayEpisode> LATEST_FIRST =
            Comparator.comparingLong(SmartPlayEpisode::getPubDate).reversed()
                    .thenComparingLong(SmartPlayEpisode::getId);
    private static final Comparator<SmartPlayEpisode> OLDEST_FIRST =
            Comparator.comparingLong(SmartPlayEpisode::getPubDate)
                    .thenComparingLong(SmartPlayEpisode::getId);
    private static final Comparator<SmartPlayEpisode> BOOK_ORDER =
            Comparator.comparingLong(SmartPlayEpisode::getShowLastPlayed).reversed()
                    .thenComparingLong(SmartPlayEpisode::getFeedId)
                    .thenComparing(OLDEST_FIRST);
    private static final Comparator<SmartPlayEpisode> MOST_RECENTLY_PLAYED_FIRST =
            Comparator.comparingLong(SmartPlayEpisode::getLastPlayedTime).reversed()
                    .thenComparing(LATEST_FIRST);

    private final Random random;

    public SmartPlayEngine() {
        this(new Random());
    }

    public SmartPlayEngine(Random random) {
        this.random = random;
    }

    public List<SmartPlayEpisode> rebuild(SmartPlayDefinition definition, List<SmartPlayEpisode> candidates) {
        return rebuild(definition, candidates, Collections.emptyList(), ManualEdits.none());
    }

    public List<SmartPlayEpisode> rebuild(SmartPlayDefinition definition, List<SmartPlayEpisode> candidates,
                                          List<SmartPlayEpisode> currentList, ManualEdits edits) {
        return rebuild(definition, candidates, currentList, edits, null);
    }

    /**
     * halfPlayedHere: the half-played episodes that go on top of this Smart Play (its own, and the one carried
     * into it, 0.9.36); null for every half-played episode, as before. Others keep their normal rule slot.
     * Audiobook chapters are unchanged: they jump only in a book list that covers them.
     */
    public List<SmartPlayEpisode> rebuild(SmartPlayDefinition definition, List<SmartPlayEpisode> candidates,
                                          List<SmartPlayEpisode> currentList, ManualEdits edits,
                                          Set<Long> halfPlayedHere) {
        Map<Long, Integer> slots = new java.util.LinkedHashMap<>();
        for (int i = 0; i < currentList.size(); i++) {
            long id = currentList.get(i).getId();
            if (edits.isHandAdded(id) && !slots.containsKey(id)) {
                slots.put(id, i);
            }
        }
        List<SmartPlayEpisode> handAdded = new ArrayList<>();
        Map<Long, SmartPlayEpisode> latestState = byId(candidates);
        for (SmartPlayEpisode episode : currentList) {
            if (slots.containsKey(episode.getId())) {
                handAdded.add(latest(latestState, episode));
            }
        }
        return rebuildWithSlots(definition, candidates, handAdded, slots, edits, halfPlayedHere);
    }

    /**
     * Rebuilds with hand-added episodes put back at their slots (handAdded in slot order, slots by episode id).
     * Used when switching to a Smart Play whose list isn't the one showing.
     */
    public List<SmartPlayEpisode> rebuildWithSlots(SmartPlayDefinition definition, List<SmartPlayEpisode> candidates,
                                                   List<SmartPlayEpisode> handAddedInOrder, Map<Long, Integer> slots,
                                                   ManualEdits edits, Set<Long> halfPlayedHere) {
        Map<Long, SmartPlayEpisode> latestState = byId(candidates);
        List<Integer> handAddedPositions = new ArrayList<>();
        List<SmartPlayEpisode> handAdded = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (SmartPlayEpisode episode : handAddedInOrder) {
            SmartPlayEpisode current = latest(latestState, episode);
            Integer slot = slots.get(current.getId());
            if (slot != null && edits.isHandAdded(current.getId()) && !edits.isRemoved(current.getId())
                    && !current.isPlayed() && used.add(current.getId())) {
                handAddedPositions.add(slot);
                handAdded.add(current);
            }
        }

        List<SmartPlayEpisode> pool = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (!edits.isRemoved(episode.getId()) && !definition.isBlocked(episode)) {
                pool.add(episode);
            }
        }

        List<SmartPlayEpisode> result = new ArrayList<>();
        List<SmartPlayEpisode> halfPlayed = new ArrayList<>();
        for (SmartPlayEpisode episode : pool) {
            boolean bookish = episode.isBook() || definition.isBookList();
            boolean onTop = bookish ? definition.covers(episode)
                    : halfPlayedHere == null || halfPlayedHere.contains(episode.getId());
            if (episode.isHalfPlayed() && onTop) {
                halfPlayed.add(episode);
            }
        }
        halfPlayed.sort(MOST_RECENTLY_PLAYED_FIRST);
        addAll(result, used, halfPlayed);
        addAll(result, used, priorityEpisodes(definition, pool, used));
        addAll(result, used, ruleEpisodes(definition, pool, used));

        for (int i = 0; i < handAdded.size(); i++) {
            result.add(Math.min(handAddedPositions.get(i), result.size()), handAdded.get(i));
        }
        return result;
    }

    public List<SmartPlayEpisode> onNewDownloads(SmartPlayDefinition definition,
                                                 List<SmartPlayEpisode> candidates,
                                                 List<SmartPlayEpisode> currentList) {
        return onNewDownloads(definition, candidates, currentList, ManualEdits.none());
    }

    public List<SmartPlayEpisode> onNewDownloads(SmartPlayDefinition definition,
                                                 List<SmartPlayEpisode> candidates,
                                                 List<SmartPlayEpisode> currentList, ManualEdits edits) {
        return onNewDownloads(definition, candidates, currentList, edits, null);
    }

    public List<SmartPlayEpisode> onNewDownloads(SmartPlayDefinition definition,
                                                 List<SmartPlayEpisode> candidates,
                                                 List<SmartPlayEpisode> currentList, ManualEdits edits,
                                                 Set<Long> halfPlayedHere) {
        Map<Long, SmartPlayEpisode> latestState = byId(candidates);
        List<SmartPlayEpisode> kept = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (SmartPlayEpisode episode : currentList) {
            SmartPlayEpisode current = latest(latestState, episode);
            if (!current.isPlayed() && used.add(current.getId())) {
                kept.add(current);
            }
        }
        if (kept.isEmpty()) {
            return rebuild(definition, candidates, Collections.emptyList(), edits, halfPlayedHere);
        }

        List<SmartPlayEpisode> pool = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (!edits.isRemoved(episode.getId()) && !definition.isBlocked(episode)) {
                pool.add(episode);
            }
        }
        int insertAt = 0;
        while (insertAt < kept.size() && kept.get(insertAt).isHalfPlayed()) {
            insertAt++;
        }
        kept.addAll(insertAt, priorityEpisodes(definition, pool, used));
        return kept;
    }

    private static Map<Long, SmartPlayEpisode> byId(List<SmartPlayEpisode> episodes) {
        Map<Long, SmartPlayEpisode> result = new HashMap<>();
        for (SmartPlayEpisode episode : episodes) {
            result.put(episode.getId(), episode);
        }
        return result;
    }

    private static SmartPlayEpisode latest(Map<Long, SmartPlayEpisode> latestState, SmartPlayEpisode episode) {
        SmartPlayEpisode current = latestState.get(episode.getId());
        return current != null ? current : episode;
    }

    private List<SmartPlayEpisode> priorityEpisodes(SmartPlayDefinition definition,
                                                    List<SmartPlayEpisode> candidates, Set<Long> used) {
        List<SmartPlayEpisode> result = new ArrayList<>();
        Set<Long> seenFeeds = new HashSet<>();
        for (SmartPlayDefinition.PriorityFeed priorityFeed : definition.getPriorityFeeds()) {
            if (!seenFeeds.add(priorityFeed.getFeedId())) {
                continue;
            }
            List<SmartPlayEpisode> fromFeed = new ArrayList<>();
            for (SmartPlayEpisode episode : candidates) {
                if (episode.getFeedId() == priorityFeed.getFeedId() && episode.isAvailable()
                        && !used.contains(episode.getId())) {
                    fromFeed.add(episode);
                }
            }
            boolean oldestFirst = priorityFeed.getOrder() == SmartPlayDefinition.PriorityOrder.OLDEST_FIRST;
            fromFeed.sort(oldestFirst ? OLDEST_FIRST : LATEST_FIRST);
            result.addAll(fromFeed);
        }
        return result;
    }

    private List<SmartPlayEpisode> ruleEpisodes(SmartPlayDefinition definition,
                                                List<SmartPlayEpisode> candidates, Set<Long> alreadyUsed) {
        Set<Long> used = new HashSet<>(alreadyUsed);
        List<SmartPlayEpisode> picked = new ArrayList<>();
        int target = definition.getTargetLength();
        List<SmartPlayRule> rules = definition.getRules();
        List<Map<Long, Integer>> perShowCounts = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            perShowCounts.add(new HashMap<>());
        }
        boolean progress = true;
        while (picked.size() < target && progress) {
            progress = false;
            for (int i = 0; i < rules.size(); i++) {
                SmartPlayRule rule = rules.get(i);
                int wanted = Math.min(rule.getCount(), target - picked.size());
                if (wanted <= 0) {
                    break;
                }
                List<SmartPlayEpisode> chosen = pick(rule, candidates, used, wanted, perShowCounts.get(i));
                for (SmartPlayEpisode episode : chosen) {
                    used.add(episode.getId());
                    picked.add(episode);
                }
                if (!chosen.isEmpty()) {
                    progress = true;
                }
            }
        }
        return picked;
    }

    private List<SmartPlayEpisode> pick(SmartPlayRule rule, List<SmartPlayEpisode> candidates,
                                        Set<Long> used, int wanted, Map<Long, Integer> perShowCount) {
        List<SmartPlayEpisode> eligible = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (episode.isAvailable() && !used.contains(episode.getId()) && rule.covers(episode)) {
                eligible.add(episode);
            }
        }
        switch (rule.getOrder()) {
            case OLDEST:
                eligible.sort(OLDEST_FIRST);
                break;
            case RANDOM:
                eligible.sort(Comparator.comparingLong(SmartPlayEpisode::getId));
                Collections.shuffle(eligible, random);
                break;
            case NEXT_CHAPTER:
                eligible.sort(BOOK_ORDER);
                break;
            case LATEST:
            default:
                eligible.sort(LATEST_FIRST);
                break;
        }
        List<SmartPlayEpisode> chosen = new ArrayList<>();
        for (SmartPlayEpisode episode : eligible) {
            if (chosen.size() >= wanted) {
                break;
            }
            int fromShow = perShowCount.containsKey(episode.getFeedId()) ? perShowCount.get(episode.getFeedId()) : 0;
            if (rule.getMaxPerShow() > 0 && fromShow >= rule.getMaxPerShow()) {
                continue;
            }
            perShowCount.put(episode.getFeedId(), fromShow + 1);
            chosen.add(episode);
        }
        return chosen;
    }

    private static void addAll(List<SmartPlayEpisode> result, Set<Long> used, List<SmartPlayEpisode> episodes) {
        for (SmartPlayEpisode episode : episodes) {
            if (used.add(episode.getId())) {
                result.add(episode);
            }
        }
    }
}
