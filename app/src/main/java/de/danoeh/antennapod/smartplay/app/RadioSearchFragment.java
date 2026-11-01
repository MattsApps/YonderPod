package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioSearchFragment extends FindFragment {
    private static final String TAG = "RadioSearch";
    private static final String COUNTRY = "country:";
    private static final String TAG_PREFIX = "tag:";

    @Override
    protected int world() {
        return R.string.world_radio;
    }

    @Override
    protected int hint() {
        return R.string.radio_search_hint;
    }

    @Override
    protected int[] tabLabels() {
        return new int[] {R.string.find_results, R.string.find_near_me, R.string.find_countries,
            R.string.find_genres};
    }

    @Override
    protected int actionLabel() {
        return R.string.find_add;
    }

    @Override
    protected int doneLabel() {
        return R.string.find_added;
    }

    @Override
    protected int footer() {
        return R.string.find_paste_stream;
    }

    @Override
    protected boolean footerIsLink() {
        return true;
    }

    @Override
    protected void onFooter() {
        RadioFragment.showAddLink(requireContext(), () -> { });
    }

    @Override
    protected List<Result> fetch(int tab, String query) throws Exception {
        Context app = requireContext().getApplicationContext();
        List<Result> results = new ArrayList<>();
        if (tab == 2) {
            for (RadioBrowser.Country country : RadioBrowser.countries()) {
                Result result = new Result();
                result.title = country.name;
                result.meta = String.valueOf(country.stations);
                result.choice = true;
                result.choiceQuery = COUNTRY + country.code;
                results.add(result);
            }
            return results;
        }
        if (tab == 3) {
            for (String genre : app.getResources().getStringArray(R.array.find_radio_genres)) {
                Result result = new Result();
                result.title = genre;
                result.choice = true;
                result.choiceQuery = TAG_PREFIX + genre;
                results.add(result);
            }
            return results;
        }
        List<RadioBrowser.Station> stations;
        if (tab == 1) {
            stations = RadioBrowser.byCountry(Locale.getDefault().getCountry());
        } else if (query.startsWith(COUNTRY)) {
            stations = RadioBrowser.byCountry(query.substring(COUNTRY.length()));
        } else if (query.startsWith(TAG_PREFIX)) {
            stations = RadioBrowser.byTag(query.substring(TAG_PREFIX.length()));
        } else {
            stations = RadioBrowser.search(query);
        }
        Set<String> added = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (FeedItem station : Radio.stations(app)) {
            if (station.getMedia() != null) {
                added.add(station.getMedia().getDownloadUrl());
            }
            names.add(station.getTitle());
        }
        for (RadioBrowser.Station station : stations) {
            Result result = new Result();
            result.title = station.name;
            List<String> meta = new ArrayList<>();
            if (!station.country.isEmpty()) {
                meta.add(station.country);
            }
            String quality = RadioFragment.quality(app, station.codec, station.bitrate);
            if (!quality.isEmpty()) {
                meta.add(quality);
            }
            result.meta = TextUtils.join(" · ", meta);
            result.artUrl = station.logoUrl;
            result.payload = station;
            result.done = added.contains(station.streamUrl) || names.contains(station.name);
            results.add(result);
        }
        return results;
    }

    @Override
    protected void act(Result result, Runnable onDone) {
        add((RadioBrowser.Station) result.payload, onDone, false);
    }

    @Override
    protected void open(Result result) {
        RadioBrowser.Station station = (RadioBrowser.Station) result.payload;
        add(station, () -> {
            result.done = true;
        }, true);
    }

    private void add(RadioBrowser.Station found, Runnable onDone, boolean play) {
        Context app = requireContext().getApplicationContext();
        Toast.makeText(app, getString(R.string.radio_adding, found.name), Toast.LENGTH_SHORT).show();
        disposables.add(Single.fromCallable(() -> {
            String mimeType = found.hls ? MediaItemAdapter.MIME_HLS : null;
            String streamUrl = found.streamUrl;
            try {
                StreamCheck.Result check = StreamCheck.check(found.streamUrl);
                streamUrl = check.streamUrl;
                mimeType = check.mimeType;
            } catch (Exception e) {
                Log.d(TAG, "Could not check " + found.streamUrl);
            }
            RadioBrowser.countClick(found.id);
            return Radio.add(app, found.name, streamUrl, mimeType, found.homepage, found.logoUrl,
                    new RadioPreferences.Station(found.country, found.countryCode, found.codec, found.bitrate,
                            found.id, RadioPreferences.Quality.STANDARD));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(station -> {
                    onDone.run();
                    if (play) {
                        RadioFragment.play(app, station);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.radio_link_failed, Toast.LENGTH_LONG).show();
                }));
    }
}
