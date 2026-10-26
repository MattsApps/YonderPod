package de.danoeh.antennapod.smartplay.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScheduleRunPlanner {
    private ScheduleRunPlanner() {
    }

    public static List<Long> feedsForDefault(Map<Long, Set<String>> feedCategories,
                                             Map<String, CategoryScheduleMode> modes) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : feedCategories.entrySet()) {
            if (entry.getValue().isEmpty()) {
                result.add(entry.getKey());
                continue;
            }
            for (String category : entry.getValue()) {
                CategoryScheduleMode mode = modes.get(category);
                if (mode == null || mode == CategoryScheduleMode.DEFAULT) {
                    result.add(entry.getKey());
                    break;
                }
            }
        }
        return result;
    }

    public static List<Long> feedsForCategory(String category, Map<Long, Set<String>> feedCategories) {
        List<Long> result = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : feedCategories.entrySet()) {
            if (entry.getValue().contains(category)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
}
