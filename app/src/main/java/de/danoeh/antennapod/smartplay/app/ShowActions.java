package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Collections;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.screen.feed.preferences.TagSettingsDialog;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * A show's list (0.9.34): refresh, download settings, category, delete show. The long press on a show in
 * Podcasts Tend and the ⋮ menu on the show's page both come here, so they can't drift apart.
 */
public final class ShowActions {
    private static final String TAG = "ShowActions";
    private static final int[] IDS = {
        R.id.show_refresh, R.id.show_download_settings, R.id.show_category, R.id.show_delete
    };
    private static final int[] TITLES = {
        R.string.show_menu_refresh, R.string.show_menu_download_settings, R.string.show_menu_category,
        R.string.show_menu_delete
    };

    public interface Listener {
        /** Something about the show changed (download settings or categories): reload. */
        void onChanged();

        /** This show's refresh has just been asked for. */
        void onRefreshing();

        /** The listener confirmed the delete; it's about to happen (the show's page closes here). */
        void onDeleting();
    }

    private ShowActions() {
    }

    /** Followed shows get the list. Recorded radio shows (their own list) and added files don't. */
    public static boolean applies(@Nullable Feed feed) {
        return feed != null && feed.getState() == Feed.STATE_SUBSCRIBED && !Recordings.isRecordingsFeed(feed)
                && !Radio.isRadioFeed(feed) && !LocalFolders.isAddedFiles(feed);
    }

    public static boolean isShowAction(int itemId) {
        for (int id : IDS) {
            if (id == itemId) {
                return true;
            }
        }
        return false;
    }

    /** The long press: the four items in a small list, the same style as the radio station menu. */
    public static void showMenu(Fragment fragment, View anchor, Feed feed, Listener listener) {
        PopupMenu menu = new PopupMenu(fragment.requireContext(), anchor);
        Menu items = menu.getMenu();
        for (int i = 0; i < IDS.length; i++) {
            items.add(0, IDS[i], i, TITLES[i]);
        }
        menu.setOnMenuItemClickListener(item -> handle(fragment, item.getItemId(), feed, listener));
        menu.show();
    }

    /** Does what a list item (or the matching ⋮ item on the show's page) says. */
    public static boolean handle(Fragment fragment, int itemId, Feed feed, Listener listener) {
        Context context = fragment.requireContext();
        if (itemId == R.id.show_refresh) {
            if (refresh(context, feed)) {
                listener.onRefreshing();
            }
        } else if (itemId == R.id.show_download_settings) {
            DownloadSettingsDialog.show(context, feed.getId(), listener::onChanged);
        } else if (itemId == R.id.show_category) {
            // Yonder's single-show category box, the same one as the show's settings and the new-show prompt.
            // It saves through DBWriter, whose FeedListUpdateEvent makes Tend regroup.
            if (feed.getPreferences() != null) {
                TagSettingsDialog.newInstance(Collections.singletonList(feed.getPreferences()))
                        .show(fragment.getChildFragmentManager(), TagSettingsDialog.TAG);
            }
        } else if (itemId == R.id.show_delete) {
            confirmDelete(context, feed, listener);
        } else {
            return false;
        }
        return true;
    }

    /**
     * Checks just this show now, whatever the schedule. The single-show update ends in Yonder's own
     * YonderAutoDownloadManager, which downloads new episodes by this show's download settings (nothing for a
     * show set not to download), with the same Wi-Fi rule as "update now".
     */
    static boolean refresh(Context context, Feed feed) {
        if (!feed.isLocalFeed() && !NetworkUtils.networkAvailable()) {
            Toast.makeText(context, R.string.download_error_no_connection, Toast.LENGTH_SHORT).show();
            return false;
        }
        Toast.makeText(context, context.getString(R.string.show_refreshing, feed.getTitle()),
                Toast.LENGTH_SHORT).show();
        FeedUpdateManager.getInstance().runOnce(context, feed);
        return true;
    }

    private static void confirmDelete(Context context, Feed feed, Listener listener) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.show_delete_question, feed.getTitle()))
                .setPositiveButton(R.string.yonder_button_delete, (dialog, which) -> {
                    listener.onDeleting();
                    delete(context, feed);
                })
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    /**
     * Deletes the show now: anything of it that's playing stops at once (nothing starts after it), then its
     * episodes leave the Smart Play list, its downloads are deleted and the show goes (DBWriter.deleteFeed does
     * those three, and cancels its downloads). Smart Play rules that point at it are left as they are.
     */
    static void delete(Context context, Feed feed) {
        Context app = context.getApplicationContext();
        long feedId = feed.getId();
        String title = feed.getTitle();
        StopNow.ifLoadedThen(app, media -> media.getItem() != null && media.getItem().getFeedId() == feedId, () -> {
            Disposable ignored = Completable.fromAction(() -> {
                DeletedShows.remember(app, feedId, title);
                DBWriter.deleteFeed(app, feedId).get();
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> Log.d(TAG, "Deleted " + title),
                            error -> Log.e(TAG, Log.getStackTraceString(error)));
        });
    }
}
