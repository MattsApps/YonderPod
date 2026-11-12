package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private TextView name;
    private TextView info;
    private LinearLayout showsView;

    private static final class ShowBlock {
        RecordingStore.Show show;
        String times;
        String status;
        List<FeedItem> latest;
        List<RecordingStore.Pending> waiting;
    }

    private static final class Loaded {
        FeedItem station;
        String shortName = "";
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
        name = root.findViewById(R.id.station_name);
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
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            load();
        }
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
            if (loaded.station != null) {
                loaded.shortName = Radio.shortName(app, loaded.station);
            }
            for (RecordingStore.Show show : Recordings.showsFor(app, stationId)) {
                ShowBlock block = new ShowBlock();
                block.show = show;
                block.times = Recordings.timesLine(app, show);
                block.status = Recordings.summary(app, show);
                block.waiting = Recordings.pendingFor(app, show.id);
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
        name.setText(loaded.shortName.toLowerCase(Locale.getDefault()));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, loaded.shortName.length() > 12 ? 40 : 56);
        String details = RadioFragment.details(requireContext(), RadioPreferences.get(station.getId()));
        info.setText(details.isEmpty() ? station.getTitle() : getString(R.string.detail_joined,
                station.getTitle(), details));
        showsView.removeAllViews();
        if (loaded.shows.isEmpty()) {
            showsView.addView(line(getString(R.string.station_no_shows), false));
        }
        for (ShowBlock block : loaded.shows) {
            TextView showName = line(block.show.name, true);
            TextViewCompat.setTextAppearance(showName, R.style.TextAppearance_WildInk_SectionHeading);
            showName.setPadding(0, dp(18), 0, dp(2));
            showName.setOnClickListener(v -> ((MainActivity) getActivity())
                    .loadChildFragment(FeedItemlistFragment.newInstance(block.show.feedId)));
            showName.setOnLongClickListener(v -> {
                showActions(v, block.show);
                return true;
            });
            showsView.addView(showName);
            if (block.show.stopped) {
                showsView.addView(line(getString(R.string.record_not_recording), false));
            }
            showsView.addView(line(block.times, false));
            showsView.addView(line(block.status, false));
            Context app = requireContext().getApplicationContext();
            for (RecordingStore.Pending pending : block.waiting) {
                TextView listen = line(getString(R.string.recording_listen_now), true);
                TextViewCompat.setTextAppearance(listen, R.style.Widget_WildInk_Link);
                listen.setTextColor(MaterialColors.getColor(listen, R.attr.colorPrimary));
                listen.setMinHeight(dp(48));
                listen.setGravity(Gravity.CENTER_VERTICAL);
                listen.setOnClickListener(v -> RadioSurface.listenNow(app, pending.id));
                showsView.addView(listen);
            }
            if (block.latest.isEmpty()) {
                showsView.addView(line(getString(R.string.station_no_recordings), false));
            }
            for (FeedItem item : block.latest) {
                String text = item.getTitle();
                if (item.getMedia() != null && item.getMedia().getDuration() > 0) {
                    text = getString(R.string.detail_joined, text, Converter.getDurationStringLocalized(
                            getResources(), item.getMedia().getDuration(), false));
                }
                text = getString(R.string.play_symbol_label, text);
                TextView recording = line(text, true);
                TextViewCompat.setTextAppearance(recording, R.style.Widget_WildInk_Link);
                recording.setMinHeight(dp(48));
                recording.setGravity(Gravity.CENTER_VERTICAL);
                recording.setOnClickListener(v -> {
                    if (item.getMedia() != null) {
                        new PlaybackServiceStarter(requireContext(), item.getMedia()).callEvenIfRunning(true).start();
                    }
                });
                showsView.addView(recording);
            }
        }
    }

    /**
     * Long press on a show's name: change times; stop today's recording (only while it's recording live);
     * stop recording this show, or start it again; delete this show. The show's ⋮ menu and the recording
     * notification use the same calls.
     */
    private void showActions(View anchor, RecordingStore.Show show) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.record_change_times);
        if (Recordings.isRecordingNow(show.id)) {
            menu.getMenu().add(0, 2, 1, R.string.record_stop_today);
        }
        menu.getMenu().add(0, 3, 2, show.stopped ? R.string.record_start_show : R.string.record_stop_show);
        menu.getMenu().add(0, 4, 3, R.string.record_delete_show);
        menu.setOnMenuItemClickListener(item -> {
            if (!isAdded()) {
                return true;
            }
            if (item.getItemId() == 1) {
                RecordShowDialog.edit(requireContext(), show);
            } else if (item.getItemId() == 2) {
                RecordShowDialog.stopToday(requireContext(), show);
            } else if (item.getItemId() == 3) {
                RecordShowDialog.stopOrStartShow(requireContext(), show, null);
            } else {
                RecordShowDialog.confirmRemove(requireContext(), show, null);
            }
            return true;
        });
        menu.show();
    }

    private TextView line(String text, boolean clickable) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        if (clickable) {
            TypedValue value = new TypedValue();
            requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
            view.setBackgroundResource(value.resourceId);
        } else {
            TextViewCompat.setTextAppearance(view, R.style.TextAppearance_WildInk_Detail);
        }
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
