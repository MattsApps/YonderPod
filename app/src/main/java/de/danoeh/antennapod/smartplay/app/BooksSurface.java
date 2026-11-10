package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.database.DBReader;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class BooksSurface extends WorldSurface {
    private static final String TAG = "BooksSurface";

    private static final class State {
        Feed book;
        Books.Progress progress;
        long totalMillis;
        FeedItem after;
    }

    @Override
    protected int world() {
        return BOOKS;
    }

    @Override
    protected int layout() {
        return R.layout.world_books;
    }

    @Override
    protected void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            State state = new State();
            Set<Long> books = Books.bookIds(app);
            Map<Long, Long> lastPlayed = Books.lastPlayedPerBook(app, books);
            long best = -1;
            for (Feed feed : DBReader.getFeedList()) {
                if (!books.contains(feed.getId()) || Books.isFinished(app, feed.getId())) {
                    continue;
                }
                Long played = lastPlayed.get(feed.getId());
                long value = played != null ? played : 0;
                if (state.book == null || value > best) {
                    state.book = feed;
                    best = value;
                }
            }
            if (state.book != null) {
                state.progress = Books.progress(app, state.book);
                List<FeedItem> chapters = Books.chaptersInOrder(app, state.book);
                for (int i = 0; i < chapters.size(); i++) {
                    FeedMedia media = chapters.get(i).getMedia();
                    if (media != null) {
                        state.totalMillis += media.getDuration();
                    }
                    if (state.progress.next != null && chapters.get(i).getId() == state.progress.next.getId()
                            && i + 1 < chapters.size()) {
                        state.after = chapters.get(i + 1);
                    }
                }
            }
            return state;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void show(State state) {
        if (root == null) {
            return;
        }
        TextView author = root.findViewById(R.id.worldShow);
        TextView title = root.findViewById(R.id.worldTitle);
        TextView hint = root.findViewById(R.id.worldHint);
        TextView next = root.findViewById(R.id.worldNext);
        View chapterBox = root.findViewById(R.id.worldChapterBox);
        View progressRow = root.findViewById(R.id.worldProgressRow);
        if (state.book == null) {
            author.setVisibility(View.GONE);
            title.setText(R.string.world_books_empty);
            hint.setText(R.string.world_books_empty_hint);
            hint.setVisibility(View.VISIBLE);
            chapterBox.setVisibility(View.INVISIBLE);
            progressRow.setVisibility(View.INVISIBLE);
            next.setVisibility(View.INVISIBLE);
            showPlaying(false);
            root.findViewById(R.id.worldPlay).setOnClickListener(v -> openTend());
            return;
        }
        Feed book = state.book;
        Books.Progress progress = state.progress;
        String bookAuthor = book.getAuthor();
        author.setText(bookAuthor);
        author.setVisibility(bookAuthor == null || bookAuthor.isEmpty() ? View.GONE : View.VISIBLE);
        title.setText(book.getTitle() != null ? book.getDisplayTitle().toLowerCase(Locale.getDefault()) : "");
        hint.setVisibility(View.GONE);
        chapterBox.setVisibility(View.VISIBLE);
        progressRow.setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.worldChapter)).setText(String.valueOf(Math.max(1, progress.chapter)));
        ((TextView) root.findViewById(R.id.worldChapters))
                .setText(getString(R.string.world_chapter_of, progress.chapters));
        float done = state.totalMillis > 0
                ? (float) (state.totalMillis - progress.millisLeft) / state.totalMillis : 0;
        WildInkBar bar = root.findViewById(R.id.worldProgress);
        bar.setTickStyle(false);
        bar.setProgress(done);
        ((TextView) root.findViewById(R.id.worldPercent))
                .setText(getString(R.string.world_book_percent, Math.round(done * 100)));
        ((TextView) root.findViewById(R.id.worldTime))
                .setText(getString(R.string.world_left, duration(progress.millisLeft)));
        if (state.after != null && state.after.getMedia() != null) {
            next.setText(getString(R.string.world_then_chapter, progress.chapter + 1,
                    duration(state.after.getMedia().getDuration())));
            next.setVisibility(View.VISIBLE);
        } else {
            next.setVisibility(View.INVISIBLE);
        }
        FeedMedia media = progress.next != null ? progress.next.getMedia() : null;
        showPlaying(isPlaying(media));
        Context app = requireContext().getApplicationContext();
        root.findViewById(R.id.worldPlay).setOnClickListener(v -> {
            if (isPlaying(media)) {
                pause();
            } else {
                disposables.add(Completable.fromAction(() -> Books.listen(app, book))
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> { }, error -> Log.e(TAG, Log.getStackTraceString(error))));
            }
        });
        View.OnClickListener open = v -> {
            if (isCurrent(media)) {
                openPlayer();
            }
        };
        title.setOnClickListener(open);
        author.setOnClickListener(open);
    }
}
