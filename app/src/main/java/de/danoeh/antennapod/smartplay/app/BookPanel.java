package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.View;

import java.util.Collections;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.databinding.FeeditemlistHeaderBinding;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class BookPanel {
    private static final String TAG = "BookPanel";

    public interface Callback {
        void onBookState(boolean book);
    }

    private static final class State {
        final boolean book;
        final Books.Progress progress;

        State(boolean book, Books.Progress progress) {
            this.book = book;
            this.progress = progress;
        }
    }

    private BookPanel() {
    }

    public static Disposable bind(Context context, FeeditemlistHeaderBinding header, Feed feed, Callback callback) {
        Context app = context.getApplicationContext();
        if (feed.getState() != Feed.STATE_SUBSCRIBED) {
            header.bookPanel.setVisibility(View.GONE);
            callback.onBookState(false);
            return null;
        }
        return Single.fromCallable(() -> {
            Books.detectNewBooks(app, Collections.singletonList(feed));
            boolean book = Books.isBook(app, feed.getId());
            return new State(book, book ? Books.progress(app, feed) : null);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    callback.onBookState(state.book);
                    if (!state.book) {
                        header.bookPanel.setVisibility(View.GONE);
                        return;
                    }
                    show(context, header, feed, state.progress);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private static void show(Context context, FeeditemlistHeaderBinding header, Feed feed, Books.Progress progress) {
        header.bookPanel.setVisibility(View.VISIBLE);
        if (progress.isFinished()) {
            header.bookProgress.setText(context.getResources().getQuantityString(R.plurals.book_progress_finished,
                    progress.chapters, progress.chapters));
        } else {
            header.bookProgress.setText(context.getString(R.string.book_progress, progress.chapter,
                    progress.chapters, Converter.getDurationStringLocalized(context.getResources(),
                            progress.millisLeft, false)));
        }
        header.bookContinue.setVisibility(progress.isFinished() ? View.GONE : View.VISIBLE);
        header.bookContinue.setOnClickListener(v -> {
            if (progress.next != null && progress.next.getMedia() != null) {
                new PlaybackServiceStarter(context, progress.next.getMedia()).callEvenIfRunning(true).start();
            }
        });
        header.bookListen.setVisibility(progress.isFinished() ? View.GONE : View.VISIBLE);
        header.bookListen.setOnClickListener(v -> Completable.fromAction(() ->
                        Books.listen(context.getApplicationContext(), feed))
                .subscribeOn(Schedulers.io())
                .subscribe(() -> { }, error -> Log.e(TAG, Log.getStackTraceString(error))));
        header.bookBookmarks.setOnClickListener(v -> BookDialogs.showBookmarks(context, feed));
        header.bookSpeed.setText(BookDialogs.speedLabel(context, feed));
        header.bookSpeed.setOnClickListener(v -> BookDialogs.showSpeed(context, feed,
                () -> header.bookSpeed.setText(BookDialogs.speedLabel(context, feed))));
    }

    public static void setBook(Context context, Feed feed, boolean book, Runnable onDone) {
        Context app = context.getApplicationContext();
        Completable.fromAction(() -> Books.setBook(app, feed, book))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onDone::run, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}
