package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher;
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class FindPodcastsFragment extends FindFragment {
    private static final String TAG = "FindPodcasts";
    private static final int TRENDING = 25;

    @Override
    protected int world() {
        return R.string.world_podcasts;
    }

    @Override
    protected int hint() {
        return R.string.tend_find_podcast;
    }

    @Override
    protected int[] tabLabels() {
        return new int[] {R.string.find_results, R.string.find_trending, R.string.find_categories};
    }

    @Override
    protected int actionLabel() {
        return R.string.find_follow;
    }

    @Override
    protected int doneLabel() {
        return R.string.find_following;
    }

    @Override
    protected int footer() {
        return R.string.find_paste_feed;
    }

    @Override
    protected boolean footerIsLink() {
        return true;
    }

    @Override
    protected List<Result> fetch(int tab, String query) throws Exception {
        Context app = requireContext().getApplicationContext();
        List<Result> results = new ArrayList<>();
        if (tab == 2) {
            for (String category : app.getResources().getStringArray(R.array.find_podcast_categories)) {
                Result result = new Result();
                result.title = category;
                result.choice = true;
                results.add(result);
            }
            return results;
        }
        List<PodcastSearchResult> found;
        if (tab == 1) {
            SharedPreferences prefs = app.getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE);
            String country = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE,
                    Locale.getDefault().getCountry());
            found = new ItunesTopListLoader(app).loadToplist(country, TRENDING, Collections.emptyList());
        } else {
            found = new ItunesPodcastSearcher().search(query).blockingGet();
        }
        Set<String> urls = Follow.subscribedUrls();
        Set<String> titles = Follow.subscribedTitles();
        for (PodcastSearchResult podcast : found) {
            Result result = new Result();
            result.title = podcast.title;
            result.meta = podcast.author != null ? podcast.author : "";
            result.artUrl = podcast.imageUrl;
            result.payload = podcast;
            result.done = (podcast.feedUrl != null && urls.contains(podcast.feedUrl))
                    || (podcast.title != null && titles.contains(podcast.title.trim()));
            results.add(result);
        }
        return results;
    }

    @Override
    protected void act(Result result, Runnable onDone) {
        PodcastSearchResult podcast = (PodcastSearchResult) result.payload;
        if (podcast.feedUrl == null) {
            return;
        }
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            ItunesPodcastSearcher searcher = new ItunesPodcastSearcher();
            String url = searcher.urlNeedsLookup(podcast.feedUrl)
                    ? searcher.lookupUrl(podcast.feedUrl).blockingGet() : podcast.feedUrl;
            return Follow.subscribe(app, url, podcast.title, podcast.imageUrl);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feed -> {
                    onDone.run();
                    Toast.makeText(app, app.getString(R.string.find_following_toast, podcast.title),
                            Toast.LENGTH_SHORT).show();
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.find_error, Toast.LENGTH_LONG).show();
                }));
    }

    @Override
    protected void open(Result result) {
        PodcastSearchResult podcast = (PodcastSearchResult) result.payload;
        if (podcast.feedUrl != null) {
            startActivity(new OnlineFeedviewActivityStarter(requireContext(), podcast.feedUrl).getIntent());
        }
    }

    @Override
    protected void onFooter() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.radio_add_link_dialog, null);
        EditText url = view.findViewById(R.id.link_url);
        url.setHint(R.string.find_feed_link_hint);
        view.findViewById(R.id.link_name).setVisibility(View.GONE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.find_paste_feed)
                .setView(view)
                .setPositiveButton(R.string.find_open, (dialog, which) -> {
                    String link = url.getText().toString().trim();
                    if (!link.isEmpty()) {
                        startActivity(new OnlineFeedviewActivityStarter(requireContext(), link).getIntent());
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }
}
