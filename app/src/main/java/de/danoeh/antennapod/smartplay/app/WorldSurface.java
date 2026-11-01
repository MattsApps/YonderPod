package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.QueueEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

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
        root = inflater.inflate(layout(), container, false);
        int[] names = {R.id.worldBooks, R.id.worldPodcasts, R.id.worldRadio};
        for (int i = 0; i < names.length; i++) {
            TextView name = root.findViewById(names[i]);
            int target = i;
            TextViewCompat.setTextAppearance(name, i == world()
                    ? R.style.TextAppearance_WildInk_World : R.style.TextAppearance_WildInk_WorldOther);
            name.setOnClickListener(v -> {
                if (getParentFragment() instanceof WorldsFragment) {
                    ((WorldsFragment) getParentFragment()).showWorld(target);
                }
            });
        }
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
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onQueueChanged(QueueEvent event) {
        load();
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
        icon.setImageResource(playing ? R.drawable.wildink_pause : R.drawable.wildink_play);
        root.findViewById(R.id.worldPlay).setContentDescription(
                getString(playing ? R.string.pause_label : R.string.play_label));
    }

    protected String duration(long millis) {
        long minutes = Math.max(0, Math.round(millis / 60000.0));
        if (minutes >= 60) {
            return getString(R.string.world_hours_minutes, (int) (minutes / 60), (int) (minutes % 60));
        }
        return getString(R.string.world_minutes, (int) minutes);
    }
}
