package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.playback.service.NextUpRule;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PodcastsSurface extends WorldSurface {
    private static final String TAG = "PodcastsSurface";

    private FeedMedia shown;

    private static final class State {
        FeedMedia media;
        FeedItem next;
        String smartPlay = "";
        String nothingNew;
    }

    @Override
    protected int world() {
        return PODCASTS;
    }

    @Override
    protected int layout() {
        return R.layout.world_podcasts;
    }

    @Override
    protected void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            State state = new State();
            Set<Long> books = WorldOf.filesBooks(app);
            FeedMedia current = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId());
            SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
            // 1.0.5: while another world's Smart Play is highlighted, the playlist isn't Podcasts'.
            boolean podcastsActive = manager == null || manager.getActiveId() == manager.podcastsId();
            List<FeedItem> queue = podcastsActive ? DBReader.getQueue() : new ArrayList<>();
            FeedMedia last = isPodcast(current, books) ? current : WorldOf.lastPodcast(books);
            if (last != null) {
                // 1.0.2: the last podcast played, resumed, even while another world plays.
                state.media = last;
            } else {
                // Nothing loaded: show what play would play, by the same rule every route uses.
                List<FeedItem> podcasts = new ArrayList<>();
                for (FeedItem item : queue) {
                    if (isPodcast(item.getMedia(), books)) {
                        podcasts.add(item);
                    }
                }
                FeedItem first = NextUpRule.firstInPlaylist(podcasts);
                state.media = first != null ? first.getMedia() : null;
                state.nothingNew = first == null ? NextUpPreferences.getNothingNew() : null;
            }
            long shownItem = state.media != null ? state.media.getItemId() : -1;
            boolean passed = shownItem < 0;
            for (FeedItem item : queue) {
                if (passed && item.getId() != shownItem && item.getMedia() != null) {
                    state.next = item;
                    break;
                }
                if (item.getId() == shownItem) {
                    passed = true;
                }
            }
            if (state.next == null) {
                for (FeedItem item : queue) {
                    if (item.getId() != shownItem && item.getMedia() != null) {
                        state.next = item;
                        break;
                    }
                }
            }
            state.smartPlay = manager != null ? manager.podcastsName() : "";
            return state;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private static boolean isPodcast(FeedMedia media, Set<Long> books) {
        return media != null && media.getItem() != null && WorldOf.of(media, books) == PODCASTS;
    }

    private void show(State state) {
        if (root == null) {
            return;
        }
        shown = state.media;
        TextView from = root.findViewById(R.id.worldFrom);
        from.setText(state.smartPlay.isEmpty() ? "" : getString(R.string.world_from, state.smartPlay));
        TextView show = root.findViewById(R.id.worldShow);
        TextView title = root.findViewById(R.id.worldTitle);
        TextView hint = root.findViewById(R.id.worldHint);
        TextView next = root.findViewById(R.id.worldNext);
        View progressRow = root.findViewById(R.id.worldProgressRow);
        if (shown == null) {
            show.setVisibility(View.GONE);
            title.setText(R.string.world_podcasts_empty);
            hint.setText(R.string.world_podcasts_empty_hint);
            hint.setVisibility(View.VISIBLE);
            progressRow.setVisibility(View.INVISIBLE);
        } else {
            show.setVisibility(View.VISIBLE);
            show.setText(shown.getFeedTitle());
            String episode = shown.getEpisodeTitle();
            title.setText(episode);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, episode != null && episode.length() > 40 ? 34 : 44);
            hint.setVisibility(View.GONE);
            progressRow.setVisibility(View.VISIBLE);
            onPosition(new PlaybackPositionEvent(shown.getPosition(), shown.getDuration()));
        }
        next.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openPlaylist();
            }
        });
        if (state.next != null && state.next.getFeed() != null) {
            next.setText(getString(R.string.world_then, state.next.getFeed().getDisplayTitle()));
            next.setVisibility(View.VISIBLE);
        } else if (shown == null) {
            next.setText(R.string.moving_link);
            next.setVisibility(View.VISIBLE);
            next.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).hideWorlds();
                    ((MainActivity) getActivity()).loadChildFragment(new MovingFragment());
                }
            });
        } else {
            next.setVisibility(View.INVISIBLE);
        }
        showPlaying(isPlaying(shown));
        FeedMedia media = shown;
        root.findViewById(R.id.worldPlay).setOnClickListener(v -> {
            if (media == null && state.nothingNew != null) {
                Toast.makeText(requireContext(), getString(R.string.next_up_nothing_new, state.nothingNew),
                        Toast.LENGTH_SHORT).show();
            }
            SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
            if (media != null && isPlaying(media)) {
                pause();
            } else if (manager != null) {
                manager.playPodcasts(media);
            } else if (media != null) {
                new PlaybackServiceStarter(requireContext(), media).callEvenIfRunning(true).start();
            }
        });
        root.findViewById(R.id.worldDetails).setOnClickListener(v -> {
            if (isCurrent(media)) {
                openPlayer();
            }
        });
    }

    @Override
    protected void onPosition(PlaybackPositionEvent event) {
        if (root == null || shown == null) {
            return;
        }
        boolean current = isCurrent(shown);
        int position = current ? event.getPosition() : shown.getPosition();
        int duration = current ? event.getDuration() : shown.getDuration();
        if (position == Playable.INVALID_TIME || duration <= 0) {
            return;
        }
        ((WildInkBar) root.findViewById(R.id.worldProgress)).setProgress((float) position / duration);
        ((TextView) root.findViewById(R.id.worldTime)).setText(duration(duration - position));
    }
}
