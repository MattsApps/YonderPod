package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;

public final class HelpIndex {
    private static final String TAG = "HelpIndex";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String APP_NS = "http://schemas.android.com/apk/res-auto";
    private static final String SEARCH_NS = "http://schemas.android.com/apk/com.bytehamster.lib.preferencesearch";
    private static final int MAX_RESULTS = 40;

    public static final int OPEN_TUNE = 1;
    public static final int OPEN_CONTROLS = 2;
    public static final int OPEN_SLEEP = 3;
    public static final int OPEN_SCHEDULE = 4;
    public static final int OPEN_BACKUP = 5;
    public static final int OPEN_MOVING = 6;
    public static final int OPEN_ABOUT = 7;
    public static final int OPEN_WELCOME = 8;
    public static final int OPEN_ADVANCED = 9;
    public static final int OPEN_FIND_PODCAST = 10;
    public static final int OPEN_FIND_BOOK = 11;
    public static final int OPEN_FIND_STATION = 12;
    public static final int OPEN_TOPIC = 13;

    private static final Set<String> HIDDEN_ADVANCED = new HashSet<>(Arrays.asList("searchPreference",
            "prefDocumentation", "prefViewForum", "prefContribute", "prefSendBugReport", "prefScreenParentalControl"));

    public static final class Item {
        public final String title;
        public final String path;
        public final int open;
        @Nullable
        public final String target;
        public final int screen;
        @Nullable
        public final String about;
        public final boolean topic;
        final List<String> titleWords;
        final List<String> extraWords;
        final List<String> otherWords;

        Item(String title, String path, String words, String other, int open, @Nullable String target, int screen,
             @Nullable String about, boolean topic) {
            this.title = title;
            this.path = path;
            this.open = open;
            this.target = target;
            this.screen = screen;
            this.about = about;
            this.topic = topic;
            this.titleWords = split(title);
            this.extraWords = split(words);
            this.otherWords = split(path + " " + other);
        }
    }

    public static final class Group {
        public final String name;
        public final List<Item> topics = new ArrayList<>();

        Group(String name) {
            this.name = name;
        }
    }

    public final List<Item> items = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    private final Context context;
    private final String separator;

    private HelpIndex(Context context) {
        this.context = context;
        this.separator = " " + context.getString(R.string.symbol_more) + " ";
    }

    public static HelpIndex load(Context context) {
        HelpIndex index = new HelpIndex(context);
        index.addYonderSettings();
        index.addAdvancedSettings();
        index.addTopics();
        return index;
    }

    public List<Item> search(String query) {
        List<String> tokens = split(query);
        List<Item> found = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        if (tokens.isEmpty()) {
            return found;
        }
        for (Item item : items) {
            int score = score(item, tokens);
            if (score > 0) {
                int at = 0;
                while (at < found.size() && (scores.get(at) > score
                        || (scores.get(at) == score && (!found.get(at).topic || item.topic)))) {
                    at++;
                }
                found.add(at, item);
                scores.add(at, score);
            }
        }
        return found.size() > MAX_RESULTS ? found.subList(0, MAX_RESULTS) : found;
    }

    private static int score(Item item, List<String> tokens) {
        int total = 0;
        for (String token : tokens) {
            int best = 0;
            if (matches(item.titleWords, token)) {
                best = 3;
            } else if (matches(item.extraWords, token)) {
                best = 2;
            } else if (matches(item.otherWords, token)) {
                best = 1;
            }
            if (best == 0) {
                return 0;
            }
            total += best;
        }
        return total;
    }

    private static boolean matches(List<String> words, String token) {
        for (String word : words) {
            if (word.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    static List<String> split(@Nullable String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        String clean = text.toLowerCase(Locale.getDefault())
                .replaceAll("[\\-'’]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        if (clean.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(clean.split(" "));
    }

    private String path(int... parts) {
        StringBuilder path = new StringBuilder();
        for (int part : parts) {
            if (path.length() > 0) {
                path.append(separator);
            }
            path.append(context.getString(part).toLowerCase(Locale.getDefault()));
        }
        return path.toString();
    }

    private void setting(int title, int words, int open, int... path) {
        String name = context.getString(title);
        items.add(new Item(name, path(path), context.getString(words), "", open, name, 0, null, false));
    }

    private void addYonderSettings() {
        setting(R.string.tune_daily_update, R.string.help_words_daily_update, OPEN_TUNE,
                R.string.tune_title, R.string.tune_updates);
        setting(R.string.tune_category_schedules, R.string.help_words_category_schedules, OPEN_TUNE,
                R.string.tune_title, R.string.tune_updates);
        setting(R.string.schedule_wifi_only, R.string.help_words_wifi, OPEN_SCHEDULE,
                R.string.tune_title, R.string.tune_updates, R.string.tune_daily_update);
        setting(R.string.schedule_charging_only, R.string.help_words_charging, OPEN_SCHEDULE,
                R.string.tune_title, R.string.tune_updates, R.string.tune_daily_update);
        setting(R.string.tune_download_defaults, R.string.help_words_download_defaults, OPEN_TUNE,
                R.string.tune_title, R.string.tune_downloads);
        setting(R.string.tune_auto_download, R.string.help_words_auto_download, OPEN_TUNE,
                R.string.tune_title, R.string.tune_downloads);
        setting(R.string.tune_storage, R.string.help_words_storage, OPEN_TUNE,
                R.string.tune_title, R.string.tune_downloads);
        setting(R.string.sleep_shake, R.string.help_words_shake, OPEN_SLEEP,
                R.string.tune_title, R.string.tune_sleep);
        setting(R.string.sleep_vibrate, R.string.help_words_vibrate, OPEN_SLEEP,
                R.string.tune_title, R.string.tune_sleep);
        setting(R.string.sleep_stop_at_title, R.string.help_words_stop_at, OPEN_SLEEP,
                R.string.tune_title, R.string.tune_sleep);
        setting(R.string.sleep_bedtime_title, R.string.help_words_bedtime, OPEN_SLEEP,
                R.string.tune_title, R.string.tune_sleep);
        setting(R.string.controls_buttons_title, R.string.help_words_buttons, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls);
        setting(R.string.controls_test_buttons, R.string.help_words_button_tester, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_buttons_title);
        setting(R.string.controls_notifications_label, R.string.help_words_notification_sounds, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_interruptions_title);
        setting(R.string.controls_reconnect_label, R.string.help_words_reconnect, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_interruptions_title);
        setting(R.string.car_mode_auto, R.string.help_words_car_mode, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.car_mode_title);
        setting(R.string.controls_fade_in, R.string.help_words_fade_in, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_player_title);
        setting(R.string.controls_keep_screen_on, R.string.help_words_screen_on, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_player_title);
        setting(R.string.controls_show_speed, R.string.help_words_speed_button, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_player_title);
        setting(R.string.controls_rewind, R.string.help_words_rewind, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_advanced);
        setting(R.string.controls_interruption_back, R.string.help_words_interruption_back, OPEN_CONTROLS,
                R.string.tune_title, R.string.tune_controls, R.string.controls_advanced);
        setting(R.string.world_books, R.string.help_words_world_books, OPEN_TUNE,
                R.string.tune_title, R.string.tune_worlds);
        setting(R.string.world_radio, R.string.help_words_world_radio, OPEN_TUNE,
                R.string.tune_title, R.string.tune_worlds);
        setting(R.string.worlds_left_edge, R.string.help_words_edges, OPEN_TUNE,
                R.string.tune_title, R.string.tune_worlds);
        setting(R.string.worlds_right_edge, R.string.help_words_edges, OPEN_TUNE,
                R.string.tune_title, R.string.tune_worlds);
        setting(R.string.look_everywhere, R.string.help_words_palette, OPEN_TUNE,
                R.string.tune_title, R.string.tune_look);
        setting(R.string.look_night, R.string.help_words_night, OPEN_TUNE,
                R.string.tune_title, R.string.tune_look, R.string.look_advanced);
        if (Palette.customSupported()) {
            setting(R.string.look_wallpaper, R.string.help_words_wallpaper, OPEN_TUNE,
                    R.string.tune_title, R.string.tune_look, R.string.look_advanced);
        }
        setting(R.string.tune_language, R.string.help_words_language, OPEN_TUNE,
                R.string.tune_title, R.string.tune_look);
        setting(R.string.tune_import_export, R.string.help_words_backup, OPEN_TUNE,
                R.string.tune_title, R.string.tune_backup);
        setting(R.string.tune_sync, R.string.help_words_sync, OPEN_TUNE,
                R.string.tune_title, R.string.tune_backup);
        setting(R.string.tune_welcome, R.string.help_words_welcome, OPEN_WELCOME,
                R.string.tune_title, R.string.tune_help);
        setting(R.string.moving_title, R.string.help_words_moving, OPEN_TUNE,
                R.string.tune_title, R.string.tune_help);
        setting(R.string.tune_about_app, R.string.help_words_about, OPEN_TUNE,
                R.string.tune_title, R.string.tune_about);
    }

    private void addAdvancedSettings() {
        String advanced = path(R.string.tune_title, R.string.tune_advanced);
        String downloads = advanced + separator + page(R.xml.preferences_downloads);
        String userInterface = advanced + separator + page(R.xml.preferences_user_interface);
        parse(R.xml.preferences, advanced);
        parse(R.xml.preferences_user_interface, userInterface);
        parse(R.xml.preferences_swipe, userInterface + separator + page(R.xml.preferences_swipe));
        parse(R.xml.preferences_playback, advanced + separator + page(R.xml.preferences_playback));
        parse(R.xml.preferences_downloads, downloads);
        parse(R.xml.preferences_autodownload, downloads + separator + page(R.xml.preferences_autodownload));
        parse(R.xml.preferences_auto_deletion, downloads + separator + page(R.xml.preferences_auto_deletion));
        parse(R.xml.preferences_synchronization, advanced + separator + page(R.xml.preferences_synchronization));
        parse(R.xml.preferences_import_export, advanced + separator + page(R.xml.preferences_import_export));
    }

    private String page(int screen) {
        return context.getString(PreferenceActivity.getTitleOfPage(screen)).toLowerCase(Locale.getDefault());
    }

    private boolean hidden(String key) {
        if (HIDDEN_ADVANCED.contains(key)) {
            return true;
        }
        if ("prefTintedColors".equals(key)) {
            return Build.VERSION.SDK_INT < 31;
        }
        if ("prefPersistNotify".equals(key)) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        }
        return false;
    }

    private void parse(int screen, String path) {
        try (XmlResourceParser parser = context.getResources().getXml(screen)) {
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    add(parser, screen, path);
                }
                event = parser.next();
            }
        } catch (Exception e) {
            Log.e(TAG, "Couldn't read settings page " + screen, e);
        }
    }

    private void add(XmlResourceParser parser, int screen, String path) {
        String tag = parser.getName();
        if (tag.endsWith("PreferenceCategory") || tag.endsWith("PreferenceScreen")
                || tag.endsWith("SearchPreference")) {
            return;
        }
        String key = parser.getAttributeValue(ANDROID_NS, "key");
        if (key == null || hidden(key) || ignored(parser, APP_NS) || ignored(parser, SEARCH_NS)) {
            return;
        }
        String title = text(parser, ANDROID_NS, "title");
        if (title == null || title.isEmpty()) {
            return;
        }
        String summary = text(parser, ANDROID_NS, "summary");
        String keywords = text(parser, APP_NS, "keywords");
        int extra = advancedWords(key);
        String words = (keywords == null ? "" : keywords) + " " + (extra == 0 ? "" : context.getString(extra));
        String other = summary == null || summary.contains("%") ? "" : summary;
        items.add(new Item(title, path, words, other, OPEN_ADVANCED, key, screen, null, false));
    }

    @Nullable
    private boolean ignored(XmlResourceParser parser, String namespace) {
        return parser.getAttributeBooleanValue(namespace, "ignore", false)
                || "true".equalsIgnoreCase(parser.getAttributeValue(namespace, "ignore"));
    }

    private String text(XmlResourceParser parser, String namespace, String name) {
        int id = parser.getAttributeResourceValue(namespace, name, 0);
        if (id != 0) {
            try {
                return context.getString(id);
            } catch (Exception e) {
                return null;
            }
        }
        return parser.getAttributeValue(namespace, name);
    }

    private static int advancedWords(String key) {
        switch (key) {
            case "prefGlobalDefaultSortedOrder":
                return R.string.help_words_sort_order;
            case "prefMobileUpdateTypes":
                return R.string.help_words_mobile_data;
            case "prefPlaybackSpeedLauncher":
                return R.string.help_words_speed;
            case "prefPlaybackFastForwardDeltaLauncher":
            case "prefPlaybackRewindDeltaLauncher":
                return R.string.help_words_skip;
            case "prefPauseOnHeadsetDisconnect":
                return R.string.help_words_disconnect;
            case "prefAutoDelete":
            case "prefEpisodeCleanup":
                return R.string.help_words_clean_up;
            case "prefChooseDataDir":
                return R.string.help_words_data_folder;
            case "prefEpisodeCacheSize":
                return R.string.help_words_download_limit;
            case "prefSmartMarkAsPlayedSecs":
                return R.string.help_words_mark_played;
            case "prefFollowQueue":
                return R.string.help_words_continuous;
            case "notifications":
                return R.string.help_words_app_notifications;
            default:
                return 0;
        }
    }

    private Group group(int name) {
        Group group = new Group(context.getString(name));
        groups.add(group);
        return group;
    }

    private void topic(Group group, int title, int about, int open, int target) {
        topic(group, title, about, open, target == 0 ? null : context.getString(target), 0);
    }

    private void topic(Group group, int title, int about, int open, @Nullable String target, int screen) {
        String aboutText = context.getString(about);
        Item item = new Item(context.getString(title),
                context.getString(R.string.tend_help).toLowerCase(Locale.getDefault()) + separator
                        + group.name.toLowerCase(Locale.getDefault()),
                "", aboutText, open, target, screen, aboutText, true);
        group.topics.add(item);
        items.add(item);
    }

    private void addTopics() {
        Group start = group(R.string.help_group_start);
        topic(start, R.string.help_topic_welcome, R.string.help_about_welcome, OPEN_WELCOME, 0);
        topic(start, R.string.help_topic_depths, R.string.help_about_depths, OPEN_WELCOME, 0);
        topic(start, R.string.help_topic_getting_around, R.string.help_about_getting_around, OPEN_TOPIC, 0);
        topic(start, R.string.help_topic_help, R.string.help_about_help, OPEN_TOPIC, 0);
        topic(start, R.string.help_topic_moving_beyondpod, R.string.help_about_moving_beyondpod, OPEN_MOVING, 0);
        topic(start, R.string.help_topic_shelves, R.string.help_about_shelves, OPEN_TUNE, R.string.worlds_left_edge);
        topic(start, R.string.help_topic_inbox, R.string.help_about_inbox, OPEN_TOPIC, 0);

        Group finding = group(R.string.help_group_finding);
        topic(finding, R.string.help_topic_find_podcast, R.string.help_about_find_podcast, OPEN_FIND_PODCAST, 0);
        topic(finding, R.string.help_topic_private_feed, R.string.help_about_private_feed, OPEN_FIND_PODCAST, 0);
        topic(finding, R.string.help_topic_find_book, R.string.help_about_find_book, OPEN_FIND_BOOK, 0);
        topic(finding, R.string.help_topic_own_files, R.string.help_about_own_files, OPEN_TOPIC, 0);
        topic(finding, R.string.help_topic_find_station, R.string.help_about_find_station, OPEN_FIND_STATION, 0);

        Group shows = group(R.string.help_group_shows);
        topic(shows, R.string.help_topic_subscriptions, R.string.help_about_subscriptions, OPEN_TOPIC, 0);
        topic(shows, R.string.help_topic_categories, R.string.help_about_categories, OPEN_TOPIC, 0);
        topic(shows, R.string.help_topic_download_modes, R.string.help_about_download_modes, OPEN_TUNE,
                R.string.tune_download_defaults);
        topic(shows, R.string.help_topic_show_downloads, R.string.help_about_show_downloads, OPEN_TUNE,
                R.string.tune_download_defaults);
        topic(shows, R.string.help_topic_schedules, R.string.help_about_schedules, OPEN_SCHEDULE, 0);
        topic(shows, R.string.help_topic_streaming, R.string.help_about_streaming, OPEN_ADVANCED,
                "prefMobileUpdateTypes", R.xml.preferences_downloads);

        Group smartPlay = group(R.string.help_group_smart_play);
        topic(smartPlay, R.string.help_topic_smart_play, R.string.help_about_smart_play, OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_smart_play_build, R.string.help_about_smart_play_build,
                OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_smart_play_priority, R.string.help_about_smart_play_priority,
                OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_playlist, R.string.help_about_playlist, OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_coverage, R.string.help_about_coverage, OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_smart_play_hours, R.string.help_about_smart_play_hours,
                OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_smart_play_books, R.string.help_about_smart_play_books,
                OPEN_TOPIC, 0);
        topic(smartPlay, R.string.help_topic_smart_play_menu, R.string.help_about_smart_play_menu,
                OPEN_TOPIC, 0);

        Group radio = group(R.string.help_group_radio);
        topic(radio, R.string.help_topic_radio_live, R.string.help_about_radio_live, OPEN_TOPIC, 0);
        topic(radio, R.string.help_topic_radio_record, R.string.help_about_radio_record, OPEN_TOPIC, 0);
        topic(radio, R.string.help_topic_radio_saving, R.string.help_about_radio_saving, OPEN_TOPIC, 0);
        topic(radio, R.string.help_topic_radio_waiting, R.string.help_about_radio_waiting, OPEN_TOPIC, 0);
        topic(radio, R.string.help_topic_radio_short_names, R.string.help_about_radio_short_names, OPEN_TOPIC, 0);

        Group books = group(R.string.help_group_books);
        topic(books, R.string.help_topic_book_pages, R.string.help_about_book_pages, OPEN_TOPIC, 0);
        topic(books, R.string.help_topic_book_extras, R.string.help_about_book_extras, OPEN_TOPIC, 0);
        topic(books, R.string.help_topic_book_delete, R.string.help_about_book_delete, OPEN_TOPIC, 0);

        Group playback = group(R.string.help_group_playback);
        topic(playback, R.string.help_topic_sleep, R.string.help_about_sleep, OPEN_SLEEP, 0);
        topic(playback, R.string.help_topic_buttons, R.string.help_about_buttons, OPEN_CONTROLS,
                R.string.controls_buttons_title);
        topic(playback, R.string.help_topic_interruptions, R.string.help_about_interruptions, OPEN_CONTROLS,
                R.string.controls_notifications_label);
        topic(playback, R.string.help_topic_car_mode, R.string.help_about_car_mode, OPEN_CONTROLS,
                R.string.car_mode_auto);
        topic(playback, R.string.help_topic_speed, R.string.help_about_speed, OPEN_ADVANCED,
                "prefPlaybackSpeedLauncher", R.xml.preferences_playback);
        topic(playback, R.string.help_topic_player, R.string.help_about_player, OPEN_TOPIC, 0);
        topic(playback, R.string.help_topic_widget, R.string.help_about_widget, OPEN_TOPIC, 0);

        Group look = group(R.string.help_group_look);
        topic(look, R.string.help_topic_colours, R.string.help_about_colours, OPEN_TUNE, R.string.look_everywhere);
        topic(look, R.string.help_topic_hide_world, R.string.help_about_hide_world, OPEN_TUNE, R.string.world_books);
        topic(look, R.string.help_topic_top_line, R.string.help_about_top_line, OPEN_TOPIC, 0);
        topic(look, R.string.help_topic_back_from_app, R.string.help_about_back_from_app, OPEN_TOPIC, 0);
        topic(look, R.string.help_topic_history, R.string.help_about_history, OPEN_TOPIC, 0);

        Group safe = group(R.string.help_group_safe);
        topic(safe, R.string.help_topic_backup, R.string.help_about_backup, OPEN_BACKUP, 0);
        topic(safe, R.string.help_topic_moving_other, R.string.help_about_moving_other, OPEN_MOVING, 0);
        topic(safe, R.string.help_topic_apple_spotify, R.string.help_about_apple_spotify, OPEN_MOVING, 0);
        topic(safe, R.string.help_topic_subscribe_links, R.string.help_about_subscribe_links, OPEN_TOPIC, 0);
        topic(safe, R.string.help_topic_open_source, R.string.help_about_open_source, OPEN_ABOUT, 0);

        Group trouble = group(R.string.help_group_trouble);
        topic(trouble, R.string.help_topic_updates_missed, R.string.help_about_updates_missed, OPEN_SCHEDULE, 0);
        topic(trouble, R.string.help_topic_recording_missed, R.string.help_about_recording_missed, OPEN_TOPIC, 0);
        topic(trouble, R.string.help_topic_wont_play, R.string.help_about_wont_play, OPEN_TOPIC, 0);
        topic(trouble, R.string.help_topic_storage, R.string.help_about_storage, OPEN_TUNE, R.string.tune_storage);
    }
}
