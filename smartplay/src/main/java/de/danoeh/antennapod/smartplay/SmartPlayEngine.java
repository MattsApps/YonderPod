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
        List<SmartPlayEpisode> result = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        List<SmartPlayEpisode> halfPlayed = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (episode.isHalfPlayed()) {
                halfPlayed.add(episode);
            }
        }
        halfPlayed.sort(MOST_RECENTLY_PLAYED_FIRST);
        addAll(result, used, halfPlayed);

        addAll(result, used, priorityEpisodes(definition, candidates, used));
        addAll(result, used, ruleEpisodes(definition, candidates, used));
        return result;
    }

    public List<SmartPlayEpisode> onNewDownloads(SmartPlayDefinition definition,
                                                 List<SmartPlayEpisode> candidates,
                                                 List<SmartPlayEpisode> currentList) {
        Map<Long, SmartPlayEpisode> latestState = new HashMap<>();
        for (SmartPlayEpisode episode : candidates) {
            latestState.put(episode.getId(), episode);
        }

        List<SmartPlayEpisode> kept = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (SmartPlayEpisode episode : currentList) {
            SmartPlayEpisode current = latestState.containsKey(episode.getId())
                    ? latestState.get(episode.getId()) : episode;
            if (!current.isPlayed() && used.add(current.getId())) {
                kept.add(current);
            }
        }
        if (kept.isEmpty()) {
            return rebuild(definition, candidates);
        }

        int insertAt = 0;
        while (insertAt < kept.size() && kept.get(insertAt).isHalfPlayed()) {
            insertAt++;
        }
        kept.addAll(insertAt, priorityEpisodes(definition, candidates, used));
        return kept;
    }

    private List<SmartPlayEpisode> priorityEpisodes(SmartPlayDefinition definition,
                                                    List<SmartPlayEpisode> candidates, Set<Long> used) {
        boolean oldestFirst = definition.getPriorityOrder() == SmartPlayDefinition.PriorityOrder.OLDEST_FIRST;
        Comparator<SmartPlayEpisode> order = oldestFirst ? OLDEST_FIRST : LATEST_FIRST;
        List<SmartPlayEpisode> result = new ArrayList<>();
        Set<Long> seenFeeds = new HashSet<>();
        for (long feedId : definition.getPriorityFeedIds()) {
            if (!seenFeeds.add(feedId)) {
                continue;
            }
            List<SmartPlayEpisode> fromFeed = new ArrayList<>();
            for (SmartPlayEpisode episode : candidates) {
                if (episode.getFeedId() == feedId && episode.isAvailable() && !used.contains(episode.getId())) {
                    fromFeed.add(episode);
                }
            }
            fromFeed.sort(order);
            result.addAll(fromFeed);
        }
        return result;
    }

    private List<SmartPlayEpisode> ruleEpisodes(SmartPlayDefinition definition,
                                                List<SmartPlayEpisode> candidates, Set<Long> alreadyUsed) {
        Set<Long> used = new HashSet<>(alreadyUsed);
        List<SmartPlayEpisode> picked = new ArrayList<>();
        int target = definition.getTargetLength();
        boolean progress = true;
        while (picked.size() < target && progress) {
            progress = false;
            for (SmartPlayRule rule : definition.getRules()) {
                int wanted = Math.min(rule.getCount(), target - picked.size());
                if (wanted <= 0) {
                    break;
                }
                List<SmartPlayEpisode> chosen = pick(rule, candidates, used, wanted);
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
                                        Set<Long> used, int wanted) {
        List<SmartPlayEpisode> eligible = new ArrayList<>();
        for (SmartPlayEpisode episode : candidates) {
            if (episode.isAvailable() && !used.contains(episode.getId()) && rule.getSource().covers(episode)) {
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
            case LATEST:
            default:
                eligible.sort(LATEST_FIRST);
                break;
        }
        return new ArrayList<>(eligible.subList(0, Math.min(wanted, eligible.size())));
    }

    private static void addAll(List<SmartPlayEpisode> result, Set<Long> used, List<SmartPlayEpisode> episodes) {
        for (SmartPlayEpisode episode : episodes) {
            if (used.add(episode.getId())) {
                result.add(episode);
            }
        }
    }
}
