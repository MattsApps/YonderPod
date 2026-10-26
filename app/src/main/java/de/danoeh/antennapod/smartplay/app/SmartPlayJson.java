package de.danoeh.antennapod.smartplay.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.Exclusions;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;

public final class SmartPlayJson {
    private static final int VERSION = 1;

    private SmartPlayJson() {
    }

    public static String toJson(SmartPlayDefinition definition) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            JSONArray priority = new JSONArray();
            for (SmartPlayDefinition.PriorityFeed feed : definition.getPriorityFeeds()) {
                JSONObject item = new JSONObject();
                item.put("feed", feed.getFeedId());
                item.put("order", feed.getOrder().name());
                priority.put(item);
            }
            root.put("priority", priority);
            root.put("never", exclusionsToJson(definition.getNeverInclude()));
            JSONArray rules = new JSONArray();
            for (SmartPlayRule rule : definition.getRules()) {
                JSONObject item = new JSONObject();
                item.put("order", rule.getOrder().name());
                item.put("count", rule.getCount());
                JSONObject source = new JSONObject();
                source.put("type", rule.getSource().getType().name());
                if (rule.getSource().getType() == EpisodeSource.Type.FEED) {
                    source.put("feed", rule.getSource().getFeedId());
                } else if (rule.getSource().getType() == EpisodeSource.Type.CATEGORY) {
                    source.put("category", rule.getSource().getCategory());
                }
                item.put("source", source);
                item.put("exclude", exclusionsToJson(rule.getExclusions()));
                item.put("maxPerShow", rule.getMaxPerShow());
                rules.put(item);
            }
            root.put("rules", rules);
            return root.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static SmartPlayDefinition fromJson(String name, String json) {
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(name);
        try {
            JSONObject root = new JSONObject(json);
            JSONArray priority = root.optJSONArray("priority");
            if (priority != null) {
                for (int i = 0; i < priority.length(); i++) {
                    JSONObject item = priority.getJSONObject(i);
                    builder.priorityFeed(item.getLong("feed"), SmartPlayDefinition.PriorityOrder.valueOf(
                            item.optString("order", SmartPlayDefinition.PriorityOrder.OLDEST_FIRST.name())));
                }
            }
            builder.neverInclude(exclusionsFromJson(root.optJSONObject("never")));
            JSONArray rules = root.optJSONArray("rules");
            if (rules != null) {
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject item = rules.getJSONObject(i);
                    builder.rule(SmartPlayRule.builder()
                            .order(SmartPlayRule.Order.valueOf(item.optString("order", "LATEST")))
                            .count(Math.max(1, item.optInt("count", 1)))
                            .source(sourceFromJson(item.optJSONObject("source")))
                            .exclusions(exclusionsFromJson(item.optJSONObject("exclude")))
                            .maxPerShow(item.optInt("maxPerShow", 0))
                            .build());
                }
            }
        } catch (JSONException | IllegalArgumentException e) {
            return builder.build();
        }
        return builder.build();
    }

    private static JSONObject exclusionsToJson(Exclusions exclusions) throws JSONException {
        JSONObject result = new JSONObject();
        JSONArray categories = new JSONArray();
        for (String category : exclusions.getCategories()) {
            categories.put(category);
        }
        JSONArray feeds = new JSONArray();
        for (long feedId : exclusions.getFeedIds()) {
            feeds.put(feedId);
        }
        result.put("categories", categories);
        result.put("feeds", feeds);
        return result;
    }

    private static Exclusions exclusionsFromJson(JSONObject json) {
        if (json == null) {
            return Exclusions.none();
        }
        List<String> categories = new ArrayList<>();
        JSONArray categoryArray = json.optJSONArray("categories");
        if (categoryArray != null) {
            for (int i = 0; i < categoryArray.length(); i++) {
                categories.add(categoryArray.optString(i));
            }
        }
        List<Long> feeds = new ArrayList<>();
        JSONArray feedArray = json.optJSONArray("feeds");
        if (feedArray != null) {
            for (int i = 0; i < feedArray.length(); i++) {
                feeds.add(feedArray.optLong(i));
            }
        }
        return new Exclusions(categories, feeds);
    }

    private static EpisodeSource sourceFromJson(JSONObject source) {
        if (source == null) {
            return EpisodeSource.allFeeds();
        }
        String type = source.optString("type", EpisodeSource.Type.ALL_FEEDS.name());
        if (EpisodeSource.Type.FEED.name().equals(type)) {
            return EpisodeSource.feed(source.optLong("feed", -1));
        } else if (EpisodeSource.Type.CATEGORY.name().equals(type)) {
            return EpisodeSource.category(source.optString("category", ""));
        }
        return EpisodeSource.allFeeds();
    }
}
