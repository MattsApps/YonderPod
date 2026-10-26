package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.playback.service.PlaybackService;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import de.danoeh.antennapod.ui.episodes.ImageResourceUtils;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CarModeActivity extends AppCompatActivity {
    private static final String TAG = "CarModeActivity";

    private FeedMedia media;
    private Disposable disposable;
    private TextView title;
    private TextView show;
    private TextView time;
    private ProgressBar progress;
    private ImageView artwork;
    private ImageButton play;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeSwitcher.getNoTitleTheme(this));
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.car_mode_activity);
        title = findViewById(R.id.car_title);
        show = findViewById(R.id.car_show);
        time = findViewById(R.id.car_time);
        progress = findViewById(R.id.car_progress);
        artwork = findViewById(R.id.car_artwork);
        play = findViewById(R.id.car_play);

        findViewById(R.id.car_exit).setOnClickListener(v -> finish());
        findViewById(R.id.car_back).setOnClickListener(v ->
                PlaybackController.bindToMedia3Service(this, controller -> controller.seekBack()));
        findViewById(R.id.car_forward).setOnClickListener(v ->
                PlaybackController.bindToMedia3Service(this, controller -> controller.seekForward()));
        findViewById(R.id.car_next).setOnClickListener(v ->
                PlaybackController.bindToMedia3Service(this, controller -> controller.seekToNextMediaItem()));
        play.setOnClickListener(v -> togglePlay());
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        loadMedia();
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void togglePlay() {
        if (PlaybackService.isRunning) {
            PlaybackController.bindToMedia3Service(this, controller -> controller.pause());
            return;
        }
        PlaybackController.bindToMedia3Service(this, controller -> {
            if (controller.getMediaItemCount() > 0) {
                controller.play();
            } else if (media != null) {
                new PlaybackServiceStarter(this, media).callEvenIfRunning(true).start();
            }
        });
    }

    private void loadMedia() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Maybe.fromCallable(() ->
                        DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showMedia, error -> Log.e(TAG, Log.getStackTraceString(error)), () -> {
                    media = null;
                    title.setText(R.string.car_mode_nothing);
                    show.setText("");
                    time.setText("");
                    progress.setProgress(0);
                    updatePlayButton();
                });
    }

    private void showMedia(FeedMedia loaded) {
        media = loaded;
        title.setText(loaded.getEpisodeTitle());
        show.setText(loaded.getFeedTitle());
        showPosition(loaded.getPosition(), loaded.getDuration());
        Glide.with(this)
                .load(loaded.getImageLocation())
                .error(Glide.with(this).load(ImageResourceUtils.getFallbackImageLocation(loaded)))
                .into(artwork);
        updatePlayButton();
    }

    private void showPosition(int position, int duration) {
        progress.setProgress(duration > 0 ? (int) (1000L * position / duration) : 0);
        time.setText(Converter.getDurationStringLong(position) + " / " + Converter.getDurationStringLong(duration));
    }

    private void updatePlayButton() {
        boolean playing = PlaybackService.isRunning;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_48dp);
        play.setContentDescription(getString(playing ? R.string.pause_label : R.string.play_label));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPositionUpdate(PlaybackPositionEvent event) {
        showPosition(event.getPosition(), event.getDuration());
        updatePlayButton();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        loadMedia();
    }
}
