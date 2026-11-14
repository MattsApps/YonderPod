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
import de.danoeh.antennapod.playback.base.LiveBehind;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioSurface extends WorldSurface {
    private static final String TAG = "RadioSurface";
    private static final int READY = 3;

    private static final class State {
        FeedItem station;
        String country = "";
        String shortName = "";
        final List<FeedItem> ready = new ArrayList<>();
        final List<Waiting> waiting = new ArrayList<>();
    }

    private static final class Waiting {
        long pendingId;
        String name;
        String note;
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
                RadioPreferences.Station details = RadioPreferences.get(state.station.getId());
                state.country = RadioBrowser.countryName(details.country, details.countryCode);
                state.shortName = Radio.shortName(app, state.station);
            }
            for (RecordingStore.Show show : RecordingStore.getInstance(app).getShows()) {
                for (RecordingStore.Pending pending : Recordings.pendingFor(app, show.id)) {
                    Waiting waiting = new Waiting();
                    waiting.pendingId = pending.id;
                    waiting.name = show.name;
                    waiting.note = Recordings.heldLine(app, show, pending);
                    state.waiting.add(waiting);
                }
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
            showLivePause(false);
            title.setText(R.string.world_radio_empty);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            hint.setText(R.string.world_radio_empty_hint);
            hint.setVisibility(View.VISIBLE);
            showPlaying(false);
            root.findViewById(R.id.worldPlay).setOnClickListener(v -> openTend());
        } else {
            String name = station.getTitle() != null ? station.getTitle() : "";
            subtitle.setText(state.country.isEmpty() ? name : getString(R.string.detail_joined, name,
                    state.country));
            subtitle.setVisibility(View.VISIBLE);
            title.setText(state.shortName.toLowerCase(Locale.getDefault()));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, state.shortName.length() > 12 ? 40 : 56);
            hint.setVisibility(View.GONE);
            FeedMedia media = station.getMedia();
            boolean behind = LiveBehind.isActive() && isCurrent(media);
            live.setVisibility(behind ? View.GONE : View.VISIBLE);
            showLivePause(behind);
            showPlaying(behind ? !LiveBehind.isPaused() : isPlaying(media));
            root.findViewById(R.id.worldPlay).setOnClickListener(v -> {
                if (LiveBehind.isActive() && isCurrent(media)) {
                    // Paused or behind live: play carries on from the buffer, pause pauses it.
                    if (LiveBehind.isPaused()) {
                        PlaybackController.bindToMedia3Service(requireContext(), controller -> controller.play());
                    } else {
                        pause();
                    }
                } else if (isPlaying(media)) {
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
        root.findViewById(R.id.worldReady).setVisibility(state.ready.isEmpty() && state.waiting.isEmpty()
                ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(root.getContext());
        Context app = requireContext().getApplicationContext();
        for (Waiting waiting : state.waiting) {
            View row = inflater.inflate(R.layout.world_ready_row, list, false);
            ((TextView) row.findViewById(R.id.readyTitle)).setText(waiting.name);
            TextView note = row.findViewById(R.id.readyNote);
            note.setText(waiting.note);
            note.setVisibility(View.VISIBLE);
            TextView when = row.findViewById(R.id.readyWhen);
            when.setText(R.string.recording_listen_now);
            row.setOnClickListener(v -> listenNow(app, waiting.pendingId));
            list.addView(row);
        }
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

    /** Live pause's stack beside the play circle (placed by LivePauseFit): live, how far behind, stop. */
    private void showLivePause(boolean behind) {
        View stack = root.findViewById(R.id.worldLivePause);
        stack.setVisibility(behind ? View.VISIBLE : View.GONE);
        if (!behind) {
            return;
        }
        root.findViewById(R.id.worldLivePaused).setVisibility(LiveBehind.isPaused() ? View.VISIBLE : View.GONE);
        ((TextView) root.findViewById(R.id.worldLiveBehind)).setText(
                getString(R.string.live_pause_minutes_behind, LiveBehind.minutes()));
        root.findViewById(R.id.worldLiveCircle).setOnClickListener(v ->
                PlaybackController.bindToMedia3Service(requireContext(), controller -> controller.seekToNext()));
        root.findViewById(R.id.worldLiveStop).setOnClickListener(v ->
                PlaybackController.bindToMedia3Service(requireContext(), controller -> controller.stop()));
        stack.requestLayout();
    }

    public static void listenNow(Context app, long pendingId) {
        Disposable ignored = Single.fromCallable(() -> {
            FeedItem item = Recordings.releaseNow(app, pendingId);
            if (item == null || item.getMedia() == null) {
                throw new IllegalStateException("Recording missing");
            }
            return item;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(item -> new PlaybackServiceStarter(app, item.getMedia()).callEvenIfRunning(true).start(),
                        error -> Log.e(TAG, Log.getStackTraceString(error)));
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
