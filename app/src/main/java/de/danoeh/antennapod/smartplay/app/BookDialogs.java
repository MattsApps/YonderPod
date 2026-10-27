package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.PlaybackSpeedFeedSettingDialogBinding;
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class BookDialogs {
    private static final String TAG = "BookDialogs";

    private BookDialogs() {
    }

    public static String speedLabel(Context context, Feed feed) {
        float speed = feed.getPreferences() != null ? feed.getPreferences().getFeedPlaybackSpeed()
                : FeedPreferences.SPEED_USE_GLOBAL;
        if (speed == FeedPreferences.SPEED_USE_GLOBAL) {
            return context.getString(R.string.book_speed_usual);
        }
        return context.getString(R.string.book_speed, String.format(Locale.getDefault(), "%.2fx", speed));
    }

    public static void showSpeed(Context context, Feed feed, Runnable onSaved) {
        FeedPreferences preferences = feed.getPreferences();
        if (preferences == null) {
            return;
        }
        PlaybackSpeedFeedSettingDialogBinding binding =
                PlaybackSpeedFeedSettingDialogBinding.inflate(LayoutInflater.from(context));
        binding.seekBar.setProgressChangedListener(speed ->
                binding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", speed)));
        binding.useGlobalCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            binding.seekBar.setEnabled(!isChecked);
            binding.seekBar.setAlpha(isChecked ? 0.4f : 1f);
            binding.currentSpeedLabel.setAlpha(isChecked ? 0.4f : 1f);
            binding.skipSilenceFeed.setEnabled(!isChecked);
            binding.skipSilenceFeed.setAlpha(isChecked ? 0.4f : 1f);
        });
        float speed = preferences.getFeedPlaybackSpeed();
        boolean isGlobal = speed == FeedPreferences.SPEED_USE_GLOBAL;
        binding.useGlobalCheckbox.setChecked(isGlobal);
        binding.seekBar.updateSpeed(isGlobal ? 1 : speed);
        binding.currentSpeedLabel.setText(String.format(Locale.getDefault(), "%.2fx", isGlobal ? 1 : speed));
        binding.skipSilenceFeed.setChecked(!isGlobal
                && preferences.getFeedSkipSilence() == FeedPreferences.SkipSilence.AGGRESSIVE);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.playback_speed)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    boolean global = binding.useGlobalCheckbox.isChecked();
                    preferences.setFeedPlaybackSpeed(global
                            ? FeedPreferences.SPEED_USE_GLOBAL : binding.seekBar.getCurrentSpeed());
                    FeedPreferences.SkipSilence skipSilence;
                    if (global) {
                        skipSilence = FeedPreferences.SkipSilence.GLOBAL;
                    } else if (binding.skipSilenceFeed.isChecked()) {
                        skipSilence = FeedPreferences.SkipSilence.AGGRESSIVE;
                    } else {
                        skipSilence = FeedPreferences.SkipSilence.OFF;
                    }
                    preferences.setFeedSkipSilence(skipSilence);
                    DBWriter.setFeedPreferences(preferences);
                    EventBus.getDefault().post(new SpeedPresetChangedEvent(preferences.getFeedPlaybackSpeed(),
                            feed.getId(), preferences.getFeedSkipSilence()));
                    onSaved.run();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void addBookmark(Context context, FeedMedia media, int position) {
        FeedItem item = media.getItem();
        if (item == null) {
            return;
        }
        EditText note = new EditText(context);
        note.setHint(R.string.bookmark_note_hint);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        note.setPadding(padding, padding / 2, padding, padding / 2);
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.bookmark_entry, item.getTitle(),
                        Converter.getDurationStringLong(position)))
                .setView(note)
                .setPositiveButton(R.string.bookmark_add, (dialog, which) -> {
                    String text = note.getText().toString().trim();
                    Completable.fromAction(() -> SmartPlayStore.getInstance(app)
                                    .addBookmark(item.getFeedId(), item.getId(), position, text))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(() -> Toast.makeText(app, R.string.bookmark_added, Toast.LENGTH_SHORT).show(),
                                    error -> Log.e(TAG, Log.getStackTraceString(error)));
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static final class BookmarkRow {
        final SmartPlayStore.Bookmark bookmark;
        final FeedItem item;

        BookmarkRow(SmartPlayStore.Bookmark bookmark, FeedItem item) {
            this.bookmark = bookmark;
            this.item = item;
        }
    }

    public static void showBookmarks(Context context, Feed feed) {
        Context app = context.getApplicationContext();
        Single.fromCallable(() -> {
            List<BookmarkRow> rows = new ArrayList<>();
            Map<Long, FeedItem> items = new HashMap<>();
            for (SmartPlayStore.Bookmark bookmark : SmartPlayStore.getInstance(app).getBookmarks(feed.getId())) {
                if (!items.containsKey(bookmark.itemId)) {
                    items.put(bookmark.itemId, DBReader.getFeedItem(bookmark.itemId));
                }
                FeedItem item = items.get(bookmark.itemId);
                if (item != null && item.getMedia() != null) {
                    rows.add(new BookmarkRow(bookmark, item));
                }
            }
            return rows;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(rows -> showBookmarkList(context, feed, rows),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static void showBookmarkList(Context context, Feed feed, List<BookmarkRow> rows) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.book_bookmarks)
                .setNegativeButton(R.string.close_label, null);
        if (rows.isEmpty()) {
            builder.setMessage(R.string.bookmarks_empty).show();
            return;
        }
        String[] labels = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            BookmarkRow row = rows.get(i);
            String label = context.getString(R.string.bookmark_entry, row.item.getTitle(),
                    Converter.getDurationStringLong(row.bookmark.position));
            if (!TextUtils.isEmpty(row.bookmark.note)) {
                label = label + "\n" + row.bookmark.note;
            }
            labels[i] = label;
        }
        AlertDialog dialog = builder.setItems(labels, (d, which) ->
                jumpTo(context, rows.get(which).item.getMedia(), rows.get(which).bookmark.position)).create();
        dialog.setOnShowListener(shown -> dialog.getListView().setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    dialog.dismiss();
                    confirmDelete(context, feed, rows.get(position).bookmark);
                    return true;
                }));
        dialog.show();
    }

    private static void confirmDelete(Context context, Feed feed, SmartPlayStore.Bookmark bookmark) {
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.bookmark_delete_question)
                .setPositiveButton(R.string.book_delete, (dialog, which) ->
                        Completable.fromAction(() -> SmartPlayStore.getInstance(app).deleteBookmark(bookmark.id))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> showBookmarks(context, feed),
                                        error -> Log.e(TAG, Log.getStackTraceString(error))))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void jumpTo(Context context, FeedMedia media, int position) {
        Context app = context.getApplicationContext();
        if (PlaybackPreferences.getCurrentlyPlayingFeedMediaId() == media.getId()) {
            PlaybackController.bindToMedia3Service(context, controller -> {
                if (controller.getMediaItemCount() > 0) {
                    controller.seekTo(position);
                    controller.play();
                } else {
                    startAt(app, media, position);
                }
            });
            return;
        }
        startAt(app, media, position);
    }

    private static void startAt(Context app, FeedMedia media, int position) {
        Completable.fromAction(() -> {
            media.setPosition(position);
            media.setLastPlayedTimeStatistics(System.currentTimeMillis());
            DBWriter.setFeedMediaPlaybackInformation(media).get();
        })
                .subscribeOn(Schedulers.io())
                .subscribe(() -> new Handler(Looper.getMainLooper()).post(() ->
                                new PlaybackServiceStarter(app, media).callEvenIfRunning(true).start()),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    public static void confirmFinished(Context context, Feed feed, Runnable onDone) {
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.book_finished_question, feed.getTitle()))
                .setMessage(R.string.book_finished_message)
                .setPositiveButton(R.string.book_finished_confirm, (dialog, which) ->
                        Single.fromCallable(() -> {
                            Books.markFinished(app, feed);
                            return Books.findBookSmartPlay(app, feed);
                        })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(smartPlayId -> {
                                    onDone.run();
                                    if (smartPlayId >= 0) {
                                        offerDeleteSmartPlay(context, feed, smartPlayId);
                                    }
                                }, error -> Log.e(TAG, Log.getStackTraceString(error))))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void offerDeleteSmartPlay(Context context, Feed feed, long smartPlayId) {
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.book_delete_smartplay_question, feed.getTitle()))
                .setMessage(R.string.book_delete_smartplay_message)
                .setPositiveButton(R.string.book_delete, (dialog, which) ->
                        Completable.fromAction(() -> Books.deleteBookSmartPlay(app, smartPlayId))
                                .subscribeOn(Schedulers.io())
                                .subscribe(() -> { }, error -> Log.e(TAG, Log.getStackTraceString(error))))
                .setNegativeButton(R.string.book_keep, null)
                .show();
    }
}
