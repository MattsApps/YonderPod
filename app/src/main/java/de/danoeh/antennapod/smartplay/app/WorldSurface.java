package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class WorldSurface extends Fragment {
    public static final int BOOKS = 0;
    public static final int PODCASTS = 1;
    public static final int RADIO = 2;

    protected final CompositeDisposable disposables = new CompositeDisposable();
    protected View root;

    protected abstract int world();

    @LayoutRes
    protected abstract int layout();

    protected abstract void load();

    protected void onPosition(PlaybackPositionEvent event) {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = TendRows.worldInflater(this, inflater, world()).inflate(layout(), container, false);
        int[] fullNames = {R.string.world_books, R.string.world_podcasts, R.string.world_radio};
        ((TextView) root.findViewById(R.id.worldName)).setText(fullNames[world()]);
        neighbours(this, root, AppShelves.pages(requireContext()), world());
        TipMark.bind(this, root);
        PlayCircleFit.bind(root);
        LivePauseFit.bind(root);
        OtherStopFit.bind(root);
        root.findViewById(R.id.worldOtherStop).setOnClickListener(v -> stopOther());
        root.findViewById(R.id.worldHandle).setOnClickListener(v -> openTend());
        for (int id : new int[] {R.id.worldFrom, R.id.worldNext}) {
            View link = root.findViewById(id);
            if (link != null) {
                link.setOnClickListener(v -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).openPlaylist();
                    }
                });
            }
        }
        return root;
    }

    static void neighbours(Fragment fragment, View root, WorldPages pages, int page) {
        neighbour(fragment, root.findViewById(R.id.worldLeft), pages.leftOf(page), R.string.world_neighbour_left);
        neighbour(fragment, root.findViewById(R.id.worldRight), pages.rightOf(page),
                R.string.world_neighbour_right);
    }

    private static void neighbour(Fragment fragment, TextView label, int target, int format) {
        if (target == WorldPages.NONE) {
            label.setText("");
            label.setClickable(false);
            label.setBackground(null);
            return;
        }
        int[] shortNames = {R.string.world_short_books, R.string.world_short_podcasts, R.string.world_short_radio};
        String name = fragment.getString(WorldPages.isWorld(target) ? shortNames[target] : R.string.world_short_apps);
        label.setText(fragment.getString(format, name));
        label.setContentDescription(name);
        label.setOnClickListener(v -> {
            if (fragment.getParentFragment() instanceof WorldsFragment) {
                ((WorldsFragment) fragment.getParentFragment()).showPage(target);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        load();
        checkOtherPlaying();
        TipMark.bind(this, root);
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
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        load();
        checkOtherPlaying();
    }

    /**
     * The small stop circle (1.0.2) shows only while something from another world plays: never in the world
     * that is playing, so never beside live pause's stack, and never while paused (live pause included).
     * Every start, pause or stop, from the app, notification, widget, Bluetooth, Tend or a call, posts a
     * PlayerStatusEvent, so it follows at once.
     */
    private void checkOtherPlaying() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> WorldOf.playing(app))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playing -> {
                    if (root != null) {
                        boolean other = playing != WorldOf.NONE && playing != world();
                        root.findViewById(R.id.worldOtherStop).setVisibility(other ? View.VISIBLE : View.GONE);
                    }
                }, error -> Log.e("WorldSurface", Log.getStackTraceString(error))));
    }

    /**
     * Stops what another world is playing. An episode, book, file or recording pauses where it is (its place
     * is kept); live radio, or radio playing behind live, just stops, with no live pause and no buffer. The
     * big circle keeps offering this world's own thing.
     */
    private void stopOther() {
        root.findViewById(R.id.worldOtherStop).setVisibility(View.GONE);
        if (!BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            pause();
            return;
        }
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> WorldOf.playing(app))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(playing -> PlaybackController.bindToMedia3Service(app, controller -> {
                    if (playing == RADIO) {
                        controller.stop();
                    } else {
                        controller.pause();
                    }
                }), error -> Log.e("WorldSurface", Log.getStackTraceString(error))));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueChanged(QueueEvent event) {
        load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedUpdateRunning(FeedUpdateRunningEvent event) {
        if (!event.isFeedUpdateRunning) {
            load();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onTipChanged(Tip.ChangedEvent event) {
        TipMark.bind(this, root);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPositionChanged(PlaybackPositionEvent event) {
        onPosition(event);
    }

    protected void openTend() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openWorld(world());
        }
    }

    protected void openPlayer() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openPlayer();
        }
    }

    static boolean isCurrent(@Nullable FeedMedia media) {
        return media != null && media.getId() == PlaybackPreferences.getCurrentlyPlayingFeedMediaId();
    }

    static boolean isPlaying(@Nullable FeedMedia media) {
        return isCurrent(media) && PlaybackService.isRunning
                && PlaybackPreferences.getCurrentPlayerStatus() == PlaybackPreferences.PLAYER_STATUS_PLAYING;
    }

    protected void pause() {
        if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
            PlaybackController.bindToMedia3Service(requireContext(), controller -> controller.pause());
        } else {
            requireContext().sendBroadcast(
                    MediaButtonStarter.createIntent(requireContext(), KeyEvent.KEYCODE_MEDIA_PAUSE));
        }
    }

    protected void showPlaying(boolean playing) {
        ImageView icon = root.findViewById(R.id.worldPlayIcon);
        icon.setImageResource(playing ? R.drawable.wildink_pause : R.drawable.wildink_play_centred);
        root.findViewById(R.id.worldPlay).setContentDescription(
                getString(playing ? R.string.pause_label : R.string.play_label));
    }

    protected String duration(long millis) {
        long minutes = Math.max(0, Math.round(millis / 60000.0));
        if (minutes >= 60) {
            return getString(R.string.world_hours_minutes, (int) (minutes / 60), (int) (minutes % 60));
        }
        return getResources().getQuantityString(R.plurals.world_minutes, (int) minutes, (int) minutes);
    }
}
