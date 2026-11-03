package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.Converter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class LibriVoxSearchFragment extends FindFragment {
    private static final String TAG = "LibriVoxSearch";

    public static LibriVoxSearchFragment newInstance(@Nullable String query) {
        LibriVoxSearchFragment fragment = new LibriVoxSearchFragment();
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int world() {
        return R.string.world_books;
    }

    @Override
    protected int hint() {
        return R.string.librivox_hint;
    }

    @Override
    protected int[] tabLabels() {
        return new int[] {R.string.find_results, R.string.find_genres, R.string.find_new};
    }

    @Override
    protected int actionLabel() {
        return R.string.find_listen;
    }

    @Override
    protected int doneLabel() {
        return R.string.find_on_shelf;
    }

    @Override
    protected int footer() {
        return R.string.find_librivox_about;
    }

    @Override
    protected List<Result> fetch(int tab, String query) throws Exception {
        Context app = requireContext().getApplicationContext();
        List<Result> results = new ArrayList<>();
        if (tab == 1) {
            for (String genre : app.getResources().getStringArray(R.array.find_book_genres)) {
                Result result = new Result();
                result.title = genre;
                result.choice = true;
                results.add(result);
            }
            return results;
        }
        List<LibriVox.Book> books = tab == 2 ? LibriVox.latest() : LibriVox.search(query);
        Set<String> urls = Follow.subscribedUrls();
        for (LibriVox.Book book : books) {
            Result result = new Result();
            result.title = book.title;
            List<String> meta = new ArrayList<>();
            if (!book.authors.isEmpty()) {
                meta.add(book.authors);
            }
            if (book.seconds > 0) {
                meta.add(Converter.getDurationStringLocalized(app.getResources(), book.seconds * 1000, false));
            }
            if (!book.language.isEmpty() && !"English".equals(book.language)) {
                meta.add(book.language);
            }
            result.meta = TextUtils.join(getString(R.string.detail_separator), meta);
            result.artUrl = book.coverUrl;
            result.payload = book;
            result.done = urls.contains(book.rssUrl);
            results.add(result);
        }
        return results;
    }

    @Override
    protected void act(Result result, Runnable onDone) {
        LibriVox.Book book = (LibriVox.Book) result.payload;
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            Feed feed = Follow.subscribe(app, book.rssUrl, book.title, book.coverUrl);
            Books.setBook(app, feed, true);
            return feed;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feed -> {
                    onDone.run();
                    Toast.makeText(app, app.getString(R.string.find_on_shelf_toast, book.title),
                            Toast.LENGTH_SHORT).show();
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.find_error, Toast.LENGTH_LONG).show();
                }));
    }

    @Override
    protected void open(Result result) {
        LibriVox.Book book = (LibriVox.Book) result.payload;
        startActivity(new OnlineFeedviewActivityStarter(requireContext(), book.rssUrl).getIntent());
    }
}
