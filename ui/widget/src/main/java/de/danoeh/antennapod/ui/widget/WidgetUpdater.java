package de.danoeh.antennapod.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.RemoteViews;

import androidx.media3.common.Player;

import java.util.Locale;

import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.MediaType;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.base.PlayerStatus;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.danoeh.antennapod.ui.appstartintent.MediaButtonStarter;
import de.danoeh.antennapod.ui.appstartintent.VideoPlayerActivityStarter;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.episodes.TimeSpeedConverter;

/**
 * Updates the state of the player widget.
 */
public abstract class WidgetUpdater {
    private static final String SMART_PLAY_PREFS = "SmartPlayState";
    private static final String KEY_ACTIVE_NAME = "activeName";
    private static final int PROGRESS_WIDTH = 300;

    public static class WidgetState {
        final Playable media;
        final PlayerStatus status;
        final int position;
        final int duration;
        final float playbackSpeed;

        public WidgetState(Playable media, PlayerStatus status, int position, int duration, float playbackSpeed) {
            this.media = media;
            this.status = status;
            this.position = position;
            this.duration = duration;
            this.playbackSpeed = playbackSpeed;
        }

        public WidgetState(PlayerStatus status) {
            this(null, status, Playable.INVALID_TIME, Playable.INVALID_TIME, 1.0f);
        }
    }

    /**
     * Update the widgets with the given parameters. Must be called in a background thread.
     */
    public static void updateWidget(Context context, WidgetState widgetState) {
        if (!PlayerWidget.isEnabled(context) || widgetState == null) {
            return;
        }
        Palette palette = Palette.forWorld(context, Palette.PODCASTS);
        PendingIntent startMediaPlayer;
        if (widgetState.media != null && widgetState.media.getMediaType() == MediaType.VIDEO) {
            startMediaPlayer = new VideoPlayerActivityStarter(context).getPendingIntent();
        } else {
            startMediaPlayer = new MainActivityStarter(context)
                    .withOpenPlayer().withClearBackStack().getPendingIntent();
        }
        PendingIntent startApp = new MainActivityStarter(context).getPendingIntent();
        String smartPlay = context.getSharedPreferences(SMART_PLAY_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ACTIVE_NAME, "");
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int id : PlayerWidget.widgetIds(context)) {
            boolean slim = PlayerWidget.isSlim(context, id);
            RemoteViews views = new RemoteViews(context.getPackageName(),
                    slim ? R.layout.player_widget_slim : R.layout.player_widget);
            views.setInt(R.id.widgetBackground, "setColorFilter", palette.background);
            views.setInt(R.id.butPlayCircle, "setColorFilter", palette.accent);
            views.setInt(R.id.butPlay, "setColorFilter", palette.onAccent);
            views.setInt(R.id.butSkip, "setColorFilter", palette.text);
            views.setTextColor(R.id.txtvTitle, palette.text);
            boolean playing = widgetState.status == PlayerStatus.PLAYING;
            views.setImageViewResource(R.id.butPlay, playing ? R.drawable.wildink_pause : R.drawable.wildink_play);
            views.setContentDescription(R.id.butPlayCircle,
                    context.getString(playing ? R.string.pause_label : R.string.play_label));
            if (widgetState.media != null) {
                views.setTextViewText(R.id.txtvTitle, widgetState.media.getEpisodeTitle());
                views.setOnClickPendingIntent(R.id.txtvTitle, startMediaPlayer);
                views.setOnClickPendingIntent(R.id.butPlayCircle,
                        MediaButtonStarter.createPendingIntent(context, Player.COMMAND_PLAY_PAUSE));
                views.setOnClickPendingIntent(R.id.butSkip,
                        MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
            } else {
                views.setTextViewText(R.id.txtvTitle, context.getString(R.string.no_media_playing_label));
                views.setOnClickPendingIntent(R.id.txtvTitle, startApp);
                views.setOnClickPendingIntent(R.id.butPlayCircle, startApp);
                views.setOnClickPendingIntent(R.id.butSkip, startApp);
            }
            if (!slim) {
                fillWide(context, views, widgetState, palette, smartPlay, startMediaPlayer, startApp);
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static void fillWide(Context context, RemoteViews views, WidgetState widgetState, Palette palette,
                                 String smartPlay, PendingIntent startMediaPlayer, PendingIntent startApp) {
        views.setTextColor(R.id.txtvShow, palette.secondary);
        views.setTextColor(R.id.txtvProgress, palette.secondary);
        views.setTextColor(R.id.txtvSmartPlay, palette.secondary);
        views.setTextColor(R.id.butRew, palette.text);
        views.setTextColor(R.id.butFastForward, palette.text);
        views.setTextViewText(R.id.txtvSmartPlay, smartPlay.toLowerCase(Locale.getDefault()));
        views.setTextViewText(R.id.butRew, "\u2212" + UserPreferences.getRewindSecs());
        views.setTextViewText(R.id.butFastForward, "+" + UserPreferences.getFastForwardSecs());
        views.setOnClickPendingIntent(R.id.layout_center, widgetState.media != null ? startMediaPlayer : startApp);
        if (widgetState.media == null) {
            views.setViewVisibility(R.id.txtvShow, View.GONE);
            views.setViewVisibility(R.id.progressRow, View.GONE);
            views.setOnClickPendingIntent(R.id.butRew, startApp);
            views.setOnClickPendingIntent(R.id.butFastForward, startApp);
            return;
        }
        views.setViewVisibility(R.id.txtvShow, View.VISIBLE);
        views.setViewVisibility(R.id.progressRow, View.VISIBLE);
        views.setTextViewText(R.id.txtvShow, widgetState.media.getFeedTitle());
        views.setOnClickPendingIntent(R.id.butRew,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_BACK));
        views.setOnClickPendingIntent(R.id.butFastForward,
                MediaButtonStarter.createPendingIntent(context, Player.COMMAND_SEEK_FORWARD));
        boolean live = widgetState.media instanceof FeedMedia && ((FeedMedia) widgetState.media).isLiveRadio();
        float progress = 0;
        String progressText = "";
        if (live) {
            progressText = context.getString(R.string.radio_live);
        } else if (widgetState.position >= 0 && widgetState.duration > 0) {
            progress = (float) widgetState.position / widgetState.duration;
            TimeSpeedConverter converter = new TimeSpeedConverter(widgetState.playbackSpeed);
            long minutes = Math.round(converter.convert(Math.max(0, widgetState.duration - widgetState.position))
                    / 60000.0);
            progressText = minutes >= 60
                    ? context.getString(R.string.world_hours_minutes, (int) (minutes / 60), (int) (minutes % 60))
                    : context.getString(R.string.world_minutes, (int) minutes);
        }
        views.setTextViewText(R.id.txtvProgress, progressText);
        views.setImageViewBitmap(R.id.imgvProgress, progressBar(progress, palette.raised, palette.text));
    }

    private static Bitmap progressBar(float progress, int track, int fill) {
        Bitmap bitmap = Bitmap.createBitmap(PROGRESS_WIDTH, 10, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(track);
        Paint paint = new Paint();
        paint.setColor(fill);
        canvas.drawRect(0, 0, Math.max(0, Math.min(1, progress)) * PROGRESS_WIDTH, 10, paint);
        return bitmap;
    }
}
