package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;

public final class ImportedPlayState {
    private static final String TAG = "ImportedPlayState";
    private static final String PREFS = "YonderImportedPlays";
    private static final String KEY = "pending";
    private static ImportedPlayState instance;

    private final Context context;

    private ImportedPlayState(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ImportedPlayState(context);
            EventBus.getDefault().register(instance);
        }
    }

    public static int remember(Context context, Uri opml) {
        JSONObject pending = load(context);
        int count = 0;
        try (InputStream in = context.getContentResolver().openInputStream(opml)) {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(in, null);
            String feedUrl = null;
            int feedDepth = -1;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.END_TAG && parser.getDepth() == feedDepth) {
                    feedUrl = null;
                    feedDepth = -1;
                }
                if (event != XmlPullParser.START_TAG || !"outline".equals(parser.getName())) {
                    continue;
                }
                String xmlUrl = parser.getAttributeValue(null, "xmlUrl");
                if (xmlUrl != null && !xmlUrl.isEmpty()) {
                    feedUrl = xmlUrl;
                    feedDepth = parser.getDepth();
                    continue;
                }
                if (feedUrl == null) {
                    continue;
                }
                boolean played = "1".equals(parser.getAttributeValue(null, "played"));
                long progress = parseLong(parser.getAttributeValue(null, "progress"));
                String enclosure = parser.getAttributeValue(null, "enclosureUrl");
                String title = parser.getAttributeValue(null, "title");
                if (!played && progress <= 0) {
                    continue;
                }
                JSONArray episodes = pending.optJSONArray(feedUrl);
                if (episodes == null) {
                    episodes = new JSONArray();
                    pending.put(feedUrl, episodes);
                }
                episodes.put(new JSONObject().put("url", enclosure == null ? "" : enclosure)
                        .put("title", title == null ? "" : title).put("played", played).put("progress", progress));
                count++;
            }
        } catch (Exception e) {
            Log.w(TAG, "No episode states read: " + e.getClass().getSimpleName());
            return 0;
        }
        save(context, pending);
        return count;
    }

    public static void add(Context context, String feedUrl, String url, String title, boolean played,
                           long progressSeconds) {
        JSONObject pending = load(context);
        try {
            JSONArray episodes = pending.optJSONArray(feedUrl);
            if (episodes == null) {
                episodes = new JSONArray();
                pending.put(feedUrl, episodes);
            }
            episodes.put(new JSONObject().put("url", url == null ? "" : url).put("title", title == null ? "" : title)
                    .put("played", played).put("progress", progressSeconds));
        } catch (JSONException e) {
            return;
        }
        save(context, pending);
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static JSONObject load(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY, "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void save(Context context, JSONObject pending) {
        prefs(context).edit().putString(KEY, pending.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onFeedUpdateRunning(FeedUpdateRunningEvent event) {
        if (!event.isFeedUpdateRunning) {
            apply();
        }
    }

    private synchronized void apply() {
        JSONObject pending = load(context);
        if (pending.length() == 0) {
            return;
        }
        List<Feed> feeds = DBReader.getFeedList();
        List<String> done = new ArrayList<>();
        Iterator<String> urls = pending.keys();
        while (urls.hasNext()) {
            String url = urls.next();
            Feed feed = null;
            for (Feed candidate : feeds) {
                if (url.equals(candidate.getDownloadUrl())) {
                    feed = candidate;
                }
            }
            if (feed == null) {
                continue;
            }
            List<FeedItem> items = DBReader.getFeedItemList(feed, FeedItemFilter.unfiltered(),
                    SortOrder.DATE_NEW_OLD, 0, Integer.MAX_VALUE);
            if (items.isEmpty()) {
                continue;
            }
            JSONArray episodes = pending.optJSONArray(url);
            List<FeedItem> played = new ArrayList<>();
            for (int i = 0; episodes != null && i < episodes.length(); i++) {
                JSONObject episode = episodes.optJSONObject(i);
                FeedItem item = find(items, episode.optString("url"), episode.optString("title"));
                if (item == null) {
                    continue;
                }
                if (episode.optBoolean("played")) {
                    played.add(item);
                } else if (item.getMedia() != null) {
                    FeedMedia media = item.getMedia();
                    media.setPosition((int) Math.min(Integer.MAX_VALUE, episode.optLong("progress") * 1000));
                    media.setLastPlayedTimeStatistics(System.currentTimeMillis());
                    DBWriter.setFeedMediaPlaybackInformation(media);
                }
            }
            if (!played.isEmpty()) {
                DBWriter.markItemsPlayed(FeedItem.PLAYED, true, played);
            }
            done.add(url);
        }
        for (String url : done) {
            pending.remove(url);
        }
        save(context, pending);
    }

    private static FeedItem find(List<FeedItem> items, String url, String title) {
        for (FeedItem item : items) {
            if (!url.isEmpty() && item.getMedia() != null && url.equals(item.getMedia().getDownloadUrl())) {
                return item;
            }
        }
        for (FeedItem item : items) {
            if (!title.isEmpty() && title.equals(item.getTitle())) {
                return item;
            }
        }
        return null;
    }
}
