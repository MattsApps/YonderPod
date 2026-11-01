package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.model.feed.SortOrder;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioStationFragment extends Fragment {
    private static final String TAG = "RadioStationFragment";
    private static final String ARG_STATION = "station";
    private static final int LATEST = 3;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private long stationId;
    private FeedItem station;
    private MaterialToolbar toolbar;
    private TextView info;
    private LinearLayout showsView;

    private static final class ShowBlock {
        RecordingStore.Show show;
        String times;
        String status;
        List<FeedItem> latest;
    }

    private static final class Loaded {
        FeedItem station;
        List<ShowBlock> shows = new ArrayList<>();
    }

    public static RadioStationFragment newInstance(long stationItemId) {
        RadioStationFragment fragment = new RadioStationFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_STATION, stationItemId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        stationId = getArguments() != null ? getArguments().getLong(ARG_STATION, -1) : -1;
        View root = inflater.inflate(R.layout.radio_station_fragment, container, false);
        toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        info = root.findViewById(R.id.station_info);
        showsView = root.findViewById(R.id.station_shows);
        root.findViewById(R.id.station_listen).setOnClickListener(v -> {
            if (station != null) {
                RadioFragment.play(requireContext(), station);
            }
        });
        root.findViewById(R.id.station_record).setOnClickListener(v -> {
            if (station != null) {
                RecordShowDialog.create(requireContext(), station);
            }
        });
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStationsChanged(Radio.ChangedEvent event) {
        load();
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            Loaded loaded = new Loaded();
            loaded.station = DBReader.getFeedItem(stationId);
            for (RecordingStore.Show show : Recordings.showsFor(app, stationId)) {
                ShowBlock block = new ShowBlock();
                block.show = show;
                block.times = Recordings.timesLine(app, show);
                block.status = Recordings.summary(app, show);
                Feed feed = DBReader.getFeed(show.feedId, false, 0, 0);
                block.latest = feed == null ? new ArrayList<>() : DBReader.getFeedItemList(feed,
                        FeedItemFilter.unfiltered(), SortOrder.DATE_NEW_OLD, 0, LATEST);
                loaded.shows.add(block);
            }
            return loaded;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void show(Loaded loaded) {
        station = loaded.station;
        if (station == null) {
            getParentFragmentManager().popBackStack();
            return;
        }
        toolbar.setTitle(station.getTitle());
        String details = RadioFragment.details(requireContext(), RadioPreferences.get(station.getId()));
        info.setText(details);
        info.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);
        showsView.removeAllViews();
        if (loaded.shows.isEmpty()) {
            showsView.addView(line(getString(R.string.station_no_shows), false));
        }
        for (ShowBlock block : loaded.shows) {
            TextView name = line(block.show.name, true);
            name.setTextSize(16);
            name.setPadding(0, dp(16), 0, 0);
            name.setOnClickListener(v -> ((MainActivity) getActivity())
                    .loadChildFragment(FeedItemlistFragment.newInstance(block.show.feedId)));
            showsView.addView(name);
            showsView.addView(line(block.times, false));
            showsView.addView(line(block.status, false));
            if (block.latest.isEmpty()) {
                showsView.addView(line(getString(R.string.station_no_recordings), false));
            }
            for (FeedItem item : block.latest) {
                String text = "▶  " + item.getTitle();
                if (item.getMedia() != null && item.getMedia().getDuration() > 0) {
                    text += " · " + Converter.getDurationStringLocalized(getResources(),
                            item.getMedia().getDuration(), false);
                }
                TextView recording = line(text, true);
                recording.setPadding(0, dp(10), 0, dp(10));
                recording.setOnClickListener(v -> {
                    if (item.getMedia() != null) {
                        new PlaybackServiceStarter(requireContext(), item.getMedia()).callEvenIfRunning(true).start();
                    }
                });
                showsView.addView(recording);
            }
        }
    }

    private TextView line(String text, boolean clickable) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        if (clickable) {
            TypedValue value = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            view.setBackgroundResource(value.resourceId);
        } else {
            view.setTextColor(view.getTextColors().withAlpha(180));
        }
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
