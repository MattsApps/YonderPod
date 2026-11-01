package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioSurface extends WorldSurface {
    private static final String TAG = "RadioSurface";
    private static final int READY = 3;

    private static final class State {
        FeedItem station;
        String country = "";
        final List<FeedItem> ready = new ArrayList<>();
    }

    @Override
    protected int world() {
        return RADIO;
    }

    @Override
    protected int layout() {
        return R.layout.world_radio;
    }

    @Override
    protected void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            State state = new State();
            state.station = Radio.lastStation(app);
            if (state.station != null) {
                state.country = RadioPreferences.get(state.station.getId()).country;
            }
            for (RecordingStore.Show show : RecordingStore.getInstance(app).getShows()) {
                Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
                if (feed != null) {
                    state.ready.addAll(DBReader.getFeedItemList(feed, new FeedItemFilter(FeedItemFilter.UNPLAYED),
                            SortOrder.DATE_NEW_OLD, 0, READY));
                }
            }
            Collections.sort(state.ready, (a, b) -> Long.compare(time(b), time(a)));
            while (state.ready.size() > READY) {
                state.ready.remove(state.ready.size() - 1);
            }
            return state;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private static long time(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
    }

    private void show(State state) {
        if (root == null) {
            return;
        }
        TextView subtitle = root.findViewById(R.id.worldShow);
        TextView title = root.findViewById(R.id.worldTitle);
        TextView hint = root.findViewById(R.id.worldHint);
        View live = root.findViewById(R.id.worldLive);
        FeedItem station = state.station;
        if (station == null) {
            subtitle.setVisibility(View.GONE);
            live.setVisibility(View.INVISIBLE);
            title.setText(R.string.world_radio_empty);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            hint.setText(R.string.world_radio_empty_hint);
            hint.setVisibility(View.VISIBLE);
            showPlaying(false);
            root.findViewById(R.id.worldPlay).setOnClickListener(v -> openTend());
        } else {
            String name = station.getTitle() != null ? station.getTitle() : "";
            subtitle.setText(state.country.isEmpty() ? name : name + " · " + state.country);
            subtitle.setVisibility(View.VISIBLE);
            live.setVisibility(View.VISIBLE);
            title.setText(name.toLowerCase(Locale.getDefault()));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, name.length() > 12 ? 40 : 56);
            hint.setVisibility(View.GONE);
            FeedMedia media = station.getMedia();
            showPlaying(isPlaying(media));
            root.findViewById(R.id.worldPlay).setOnClickListener(v -> {
                if (isPlaying(media)) {
                    pause();
                } else {
                    RadioFragment.play(requireContext(), station);
                }
            });
            View.OnClickListener open = v -> {
                if (isCurrent(media)) {
                    openPlayer();
                }
            };
            title.setOnClickListener(open);
            subtitle.setOnClickListener(open);
        }
        LinearLayout list = root.findViewById(R.id.worldReadyList);
        list.removeAllViews();
        root.findViewById(R.id.worldReady).setVisibility(state.ready.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (FeedItem item : state.ready) {
            View row = inflater.inflate(R.layout.world_ready_row, list, false);
            ((TextView) row.findViewById(R.id.readyTitle))
                    .setText(item.getFeed() != null ? item.getFeed().getTitle() : item.getTitle());
            ((TextView) row.findViewById(R.id.readyWhen)).setText(when(time(item)));
            row.setOnClickListener(v -> {
                if (item.getMedia() != null) {
                    new PlaybackServiceStarter(requireContext(), item.getMedia()).callEvenIfRunning(true).start();
                }
            });
            list.addView(row);
        }
    }

    private String when(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, today)) {
            return getString(R.string.world_today);
        } else if (sameDay(then, yesterday)) {
            return getString(R.string.world_yesterday);
        }
        return ScheduleText.shortDay(then.get(Calendar.DAY_OF_WEEK));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
