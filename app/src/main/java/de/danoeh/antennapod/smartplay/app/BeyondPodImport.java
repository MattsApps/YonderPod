package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.download.DownloadMode;
import de.danoeh.antennapod.smartplay.download.ShowDownloadSettings;
import de.danoeh.antennapod.smartplay.moving.BeyondPodMapping;
import de.danoeh.antennapod.smartplay.schedule.CategoryScheduleMode;
import de.danoeh.antennapod.smartplay.schedule.UpdateSchedule;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class BeyondPodImport {

    public static final class Show {
        public String beyondPodId = "";
        public String name = "";
        String url = "";
        public List<String> categories = new ArrayList<>();
        public ShowDownloadSettings settings;
        public boolean isPrivate;
        public boolean alreadyFollowed;
    }

    public static final class Rule {
        String feedId;
        String category;
        SmartPlayRule.Order order;
        int count;
    }

    public static final class SmartPlay {
        public String name = "";
        public final List<Rule> rules = new ArrayList<>();
    }

    static final class Track {
        String feedId;
        String url;
        String title;
        boolean played;
        long positionSeconds;
    }

    public static final class Summary {
        public long created;
        public final List<Show> shows = new ArrayList<>();
        public final List<SmartPlay> smartPlays = new ArrayList<>();
        final List<Track> tracks = new ArrayList<>();
        public ShowDownloadSettings defaults;
        public int forwardSeconds;
        public int backSeconds;
        public int updateMinute = -1;
        public boolean updateDaily;
        @Nullable
        public Boolean wifiOnly;
        @Nullable
        public Boolean chargingOnly;
    }

    private BeyondPodImport() {
    }

    public static Summary read(Context context, Uri uri) throws IOException {
        File folder = new File(context.getCacheDir(), "beyondpod");
        FileUtils.deleteQuietly(folder);
        folder.mkdirs();
        File db = null;
        File settings = null;
        File manifest = null;
        try (ZipInputStream zip = new ZipInputStream(context.getContentResolver().openInputStream(uri))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = new File(entry.getName()).getName().toLowerCase(Locale.ROOT);
                File target;
                if (name.startsWith("beyondpod.db")) {
                    target = db = new File(folder, "beyondpod.db");
                } else if (name.startsWith("settings.xml")) {
                    target = settings = new File(folder, "settings.xml");
                } else if (name.startsWith("backupmanifest")) {
                    target = manifest = new File(folder, "manifest.txt");
                } else {
                    continue;
                }
                try (OutputStream out = new FileOutputStream(target)) {
                    IOUtils.copy(zip, out);
                }
            }
        }
        if (db == null) {
            FileUtils.deleteQuietly(folder);
            throw new IOException("No BeyondPod database in this file");
        }
        Summary summary = new Summary();
        try {
            Map<String, String> values = settings != null ? readSettings(settings) : new HashMap<>();
            summary.defaults = BeyondPodMapping.defaults(values.get("DefaultNumberPodcastsToDownload"),
                    values.get("DefaultKeepAtMostPodcasts"), values.get("DefaultMaximumPodcastAge"),
                    DownloadSettingsRepository.getInstance(context).getDefaults());
            summary.forwardSeconds = BeyondPodMapping.number(values.get("ForwardSkipInterval"), 0);
            summary.backSeconds = BeyondPodMapping.number(values.get("BackwardSkipInterval"), 0);
            for (Map.Entry<String, String> setting : values.entrySet()) {
                String key = setting.getKey().toLowerCase(Locale.ROOT);
                boolean on = "true".equals(setting.getValue());
                if (key.contains("wifi") && key.contains("only")) {
                    summary.wifiOnly = on;
                } else if (key.contains("charg") && (key.contains("update") || key.contains("download"))) {
                    summary.chargingOnly = on;
                }
            }
            if (manifest != null) {
                summary.created = manifestTime(manifest);
            }
            readDatabase(db, summary);
        } finally {
            FileUtils.deleteQuietly(folder);
        }
        markFollowed(summary);
        return summary;
    }

    private static Map<String, String> readSettings(File file) throws IOException {
        Map<String, String> values = new HashMap<>();
        try (InputStream in = new FileInputStream(file)) {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(in, null);
            String pendingKey = null;
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) {
                    String key = parser.getAttributeValue(null, "name");
                    String value = parser.getAttributeValue(null, "value");
                    if (key != null && value != null) {
                        values.put(key, value);
                    } else if (key != null && "string".equals(parser.getName())) {
                        pendingKey = key;
                    }
                } else if (event == XmlPullParser.TEXT && pendingKey != null) {
                    values.put(pendingKey, parser.getText());
                    pendingKey = null;
                } else if (event == XmlPullParser.END_TAG) {
                    pendingKey = null;
                }
            }
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        return values;
    }

    private static long manifestTime(File manifest) throws IOException {
        List<String> lines = FileUtils.readLines(manifest, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).trim().equalsIgnoreCase("[timeStamp]")) {
                try {
                    SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                    return format.parse(lines.get(i + 1).trim()).getTime();
                } catch (ParseException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void readDatabase(File file, Summary summary) throws IOException {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor cursor = db.rawQuery("SELECT * FROM feeds ORDER BY _id", null)) {
                while (cursor.moveToNext()) {
                    Show show = new Show();
                    show.beyondPodId = text(cursor, "feedId");
                    show.name = text(cursor, "name");
                    show.url = text(cursor, "url");
                    if (show.url.isEmpty()) {
                        continue;
                    }
                    show.categories = BeyondPodMapping.categories(text(cursor, "category"));
                    show.settings = BeyondPodMapping.settings(number(cursor, "custDownload"),
                            number(cursor, "maxDownload"), number(cursor, "maxTracks"),
                            number(cursor, "maxTrackAge"), summary.defaults);
                    show.isPrivate = BeyondPodMapping.isPrivate(show.url, text(cursor, "username"));
                    summary.shows.add(show);
                }
            }
            Map<Integer, SmartPlay> lists = new LinkedHashMap<>();
            if (hasTable(db, "smartplaylist")) {
                try (Cursor cursor = db.rawQuery("SELECT * FROM smartplaylist ORDER BY playlistId, sortOrder", null)) {
                    while (cursor.moveToNext()) {
                        Integer id = number(cursor, "playlistId");
                        SmartPlay list = lists.get(id);
                        if (list == null) {
                            list = new SmartPlay();
                            lists.put(id, list);
                        }
                        String name = text(cursor, "playlistName");
                        if (list.name.isEmpty() && !name.isEmpty()) {
                            list.name = name;
                        }
                        Rule rule = new Rule();
                        rule.feedId = text(cursor, "feedId");
                        rule.category = text(cursor, "categoryId");
                        rule.order = BeyondPodMapping.order(number(cursor, "episodeFilter"));
                        rule.count = BeyondPodMapping.count(number(cursor, "numEpisodes"));
                        list.rules.add(rule);
                    }
                }
            }
            summary.smartPlays.addAll(lists.values());
            if (hasTable(db, "scheduled_tasks")) {
                try (Cursor cursor = db.rawQuery("SELECT * FROM scheduled_tasks WHERE active = 1 ORDER BY _id", null)) {
                    if (cursor.moveToFirst()) {
                        Long start = longValue(cursor, "startTime");
                        Long period = longValue(cursor, "recPeriod");
                        if (start != null && start > 0) {
                            summary.updateMinute = BeyondPodMapping.minuteOfDay(start, TimeZone.getDefault());
                            summary.updateDaily = period != null && period == 86400000L;
                        }
                    }
                }
            }
            if (hasTable(db, "tracks")) {
                try (Cursor cursor = db.rawQuery("SELECT * FROM tracks", null)) {
                    while (cursor.moveToNext()) {
                        Track track = new Track();
                        track.feedId = text(cursor, "parentFeedID");
                        track.url = text(cursor, "url");
                        track.title = text(cursor, "name");
                        Integer played = number(cursor, "played");
                        Long position = longValue(cursor, "playedTime");
                        track.played = played != null && played == 1;
                        track.positionSeconds = position != null ? position : 0;
                        if (track.played || track.positionSeconds > 0) {
                            summary.tracks.add(track);
                        }
                    }
                }
            }
        } catch (SQLiteException e) {
            throw new IOException(e);
        }
    }

    private static boolean hasTable(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[] {table})) {
            return cursor.moveToFirst();
        }
    }

    private static String text(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            return "";
        }
        return cursor.getString(index).trim();
    }

    @Nullable
    private static Integer number(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getInt(index);
    }

    @Nullable
    private static Long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static void markFollowed(Summary summary) {
        Map<String, Feed> existing = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                existing.put(feed.getDownloadUrl(), feed);
            }
        }
        for (Show show : summary.shows) {
            show.alreadyFollowed = existing.containsKey(show.url);
        }
    }

    public static int privateCount(Summary summary) {
        int count = 0;
        for (Show show : summary.shows) {
            count += show.isPrivate ? 1 : 0;
        }
        return count;
    }

    public static Map<DownloadMode, Integer> modeCounts(Summary summary) {
        Map<DownloadMode, Integer> counts = new LinkedHashMap<>();
        for (Show show : summary.shows) {
            Integer count = counts.get(show.settings.getMode());
            counts.put(show.settings.getMode(), count == null ? 1 : count + 1);
        }
        return counts;
    }

    public static void apply(Context context, Summary summary)
            throws InterruptedException, ExecutionException {
        DownloadSettingsRepository downloads = DownloadSettingsRepository.getInstance(context);
        downloads.setDefaults(summary.defaults);
        for (Show show : summary.shows) {
            if (show.alreadyFollowed) {
                continue;
            }
            Feed feed = new Feed(show.url, null, show.name);
            feed.setItems(Collections.emptyList());
            FeedDatabaseWriter.updateFeed(context, feed, false);
        }
        Map<String, Feed> byUrl = new HashMap<>();
        for (Feed feed : DBReader.getFeedList()) {
            byUrl.put(feed.getDownloadUrl(), feed);
        }
        Map<String, Long> ids = new HashMap<>();
        Map<String, String> urls = new HashMap<>();
        for (Show show : summary.shows) {
            Feed feed = byUrl.get(show.url);
            if (feed == null) {
                continue;
            }
            ids.put(show.beyondPodId, feed.getId());
            urls.put(show.beyondPodId, show.url);
            if (show.alreadyFollowed) {
                continue;
            }
            downloads.set(feed.getId(), show.settings);
            FeedPreferences preferences = feed.getPreferences();
            if (preferences != null && !show.categories.isEmpty()) {
                preferences.getTags().addAll(show.categories);
                DBWriter.setFeedPreferences(preferences).get();
            }
        }
        SmartPlayStore store = SmartPlayStore.getInstance(context);
        List<String> names = new ArrayList<>();
        for (SmartPlayStore.StoredSmartPlay existing : store.getAll()) {
            names.add(existing.getDefinition().getName().toLowerCase(Locale.ROOT));
        }
        for (SmartPlay list : summary.smartPlays) {
            if (list.rules.isEmpty()) {
                continue;
            }
            String name = list.name.isEmpty() ? context.getString(R.string.beyondpod_name) : list.name;
            if (names.contains(name.toLowerCase(Locale.ROOT))) {
                name = context.getString(R.string.beyondpod_copy_name, name);
            }
            SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(name);
            for (Rule rule : list.rules) {
                EpisodeSource source = EpisodeSource.allFeeds();
                Long feedId = ids.get(rule.feedId);
                List<String> categories = BeyondPodMapping.categories(rule.category);
                if (feedId != null) {
                    source = EpisodeSource.feed(feedId);
                } else if (!categories.isEmpty()) {
                    source = EpisodeSource.category(categories.get(0));
                }
                builder.rule(SmartPlayRule.of(rule.order, rule.count, source));
            }
            store.insert(builder.build());
        }
        if (summary.forwardSeconds > 0) {
            UserPreferences.setFastForwardSecs(summary.forwardSeconds);
        }
        if (summary.backSeconds > 0) {
            UserPreferences.setRewindSecs(summary.backSeconds);
        }
        if (summary.updateMinute >= 0 || summary.wifiOnly != null || summary.chargingOnly != null) {
            UpdateSchedule schedule = UpdateScheduler.getDefault(context).getSchedule();
            if (summary.updateMinute >= 0) {
                schedule = schedule.withTime(summary.updateMinute / 60, summary.updateMinute % 60);
            }
            if (summary.updateDaily) {
                schedule = schedule.withWeekdays(UpdateSchedule.ALL_DAYS);
            }
            if (summary.wifiOnly != null) {
                schedule = schedule.withWifiOnly(summary.wifiOnly);
            }
            if (summary.chargingOnly != null) {
                schedule = schedule.withChargingOnly(summary.chargingOnly);
            }
            UpdateScheduler.save(context, UpdateScheduler.DEFAULT_KEY, CategoryScheduleMode.OWN, schedule);
        }
        for (Track track : summary.tracks) {
            String feedUrl = urls.get(track.feedId);
            if (feedUrl != null) {
                ImportedPlayState.add(context, feedUrl, track.url, track.title, track.played,
                        track.positionSeconds);
            }
        }
        FeedUpdateManager.getInstance().runOnce(context);
    }
}
