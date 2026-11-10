package de.danoeh.antennapod.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.media3.common.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final String TAG = "WidgetUpdater";
    private static final float WIDE_PAD = 14;
    private static final float SLIM_PAD = 10;
    private static final float WIDE_MIN_WIDTH = 250;
    private static final float WIDE_MIN_HEIGHT = 110;
    private static final float SLIM_MIN_HEIGHT = 40;
    private static final float OLD_BUTTON_WIDTH = 48;
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
            Bundle options = manager.getAppWidgetOptions(id);
            RemoteViews views = null;
            if (Build.VERSION.SDK_INT >= 31) {
                views = bySize(context, widgetState, slim, options, palette, smartPlay, startMediaPlayer, startApp);
            }
            if (views == null) {
                float[] size = reportedSize(context, options, slim);
                views = build(context, widgetState, slim, size[0], size[1], palette, smartPlay,
                        startMediaPlayer, startApp);
            }
            manager.updateAppWidget(id, views);
        }
    }

    @RequiresApi(31)
    private static RemoteViews bySize(Context context, WidgetState widgetState, boolean slim, Bundle options,
                                      Palette palette, String smartPlay, PendingIntent startMediaPlayer,
                                      PendingIntent startApp) {
        List<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Map<SizeF, RemoteViews> views = new HashMap<>();
        for (SizeF size : sizes) {
            if (size.getWidth() > 0 && size.getHeight() > 0 && views.size() < 16) {
                views.put(size, build(context, widgetState, slim, size.getWidth(), size.getHeight(), palette,
                        smartPlay, startMediaPlayer, startApp));
            }
        }
        return views.isEmpty() ? null : new RemoteViews(views);
    }

    private static float[] reportedSize(Context context, Bundle options, boolean slim) {
        boolean portrait = context.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE;
        float width = options.getInt(portrait ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
                : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
        float height = options.getInt(portrait ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
                : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        Log.d(TAG, "Widget size " + width + "x" + height + (portrait ? " portrait" : " landscape"));
        return new float[] {width > 0 ? width : WIDE_MIN_WIDTH,
                height > 0 ? height : (slim ? SLIM_MIN_HEIGHT : WIDE_MIN_HEIGHT)};
    }

    private static RemoteViews build(Context context, WidgetState widgetState, boolean slim, float widthDp,
                                     float heightDp, Palette palette, String smartPlay,
                                     PendingIntent startMediaPlayer, PendingIntent startApp) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                slim ? R.layout.player_widget_slim : R.layout.player_widget);
        views.setInt(R.id.widgetBackground, "setColorFilter", palette.background);
        views.setInt(R.id.butPlayCircle, "setColorFilter", palette.accent);
        views.setInt(R.id.butPlay, "setColorFilter", palette.onAccent);
        views.setInt(R.id.butSkip, "setColorFilter", palette.text);
        views.setTextColor(R.id.txtvTitle, palette.text);
        boolean playing = widgetState.status == PlayerStatus.PLAYING;
        views.setImageViewResource(R.id.butPlay, playing ? R.drawable.wildink_pause : R.drawable.wildink_play_centred);
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
        if (slim) {
            placeSlim(context, views, widthDp, heightDp);
        } else {
            fillWide(context, views, widgetState, palette, smartPlay, startMediaPlayer, startApp);
            placeWide(context, views, widthDp, heightDp);
        }
        return views;
    }

    private static void placeWide(Context context, RemoteViews views, float widthDp, float heightDp) {
        float content = Math.max(0, heightDp - 2 * WIDE_PAD);
        float titleBlock = content / 2;
        float row = content / 4;
        textSize(views, R.id.txtvShow, titleBlock * 0.17f);
        textSize(views, R.id.txtvTitle, titleBlock * 0.27f);
        textSize(views, R.id.txtvProgress, row * 0.34f);
        textSize(views, R.id.txtvSmartPlay, row * 0.37f);
        textSize(views, R.id.butRew, row * 0.37f);
        textSize(views, R.id.butFastForward, row * 0.37f);
        float button = OLD_BUTTON_WIDTH;
        if (Build.VERSION.SDK_INT >= 31) {
            button = Math.min(row * 1.25f, (widthDp - 2 * WIDE_PAD) / 6);
            views.setViewLayoutWidth(R.id.butRew, button, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutWidth(R.id.butFastForward, button, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutWidth(R.id.butSkip, button, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.imgvProgress, row * 0.13f, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutMargin(R.id.txtvProgress, RemoteViews.MARGIN_START, row * 0.2f,
                    TypedValue.COMPLEX_UNIT_DIP);
        }
        float icon = Math.min(row, button) * 0.5f;
        int across = px(context, (button - icon) / 2);
        int down = px(context, (row - icon) / 2);
        views.setViewPadding(R.id.butSkip, across, down, across, down);
    }

    private static void placeSlim(Context context, RemoteViews views, float widthDp, float heightDp) {
        textSize(views, R.id.txtvTitle, heightDp * 0.28f);
        float button = Math.min(heightDp * 0.8f, (widthDp - 2 * SLIM_PAD) / 5);
        if (Build.VERSION.SDK_INT >= 31) {
            views.setViewLayoutWidth(R.id.butSkip, button, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.butSkip, button, TypedValue.COMPLEX_UNIT_DIP);
        } else {
            button = OLD_BUTTON_WIDTH;
        }
        int inset = px(context, button * 0.27f);
        views.setViewPadding(R.id.butSkip, inset, inset, inset, inset);
    }

    private static void textSize(RemoteViews views, int id, float dp) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, dp);
    }

    private static int px(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static void fillWide(Context context, RemoteViews views, WidgetState widgetState, Palette palette,
                                 String smartPlay, PendingIntent startMediaPlayer, PendingIntent startApp) {
        views.setTextColor(R.id.txtvShow, palette.secondary);
        views.setTextColor(R.id.txtvProgress, palette.secondary);
        views.setTextColor(R.id.txtvSmartPlay, palette.secondary);
        views.setTextColor(R.id.butRew, palette.text);
        views.setTextColor(R.id.butFastForward, palette.text);
        views.setTextViewText(R.id.txtvSmartPlay, smartPlay.toLowerCase(Locale.getDefault()));
        views.setTextViewText(R.id.butRew, context.getString(R.string.widget_rewind_seconds,
                UserPreferences.getRewindSecs()));
        views.setTextViewText(R.id.butFastForward, context.getString(R.string.widget_forward_seconds,
                UserPreferences.getFastForwardSecs()));
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
                    : context.getResources().getQuantityString(R.plurals.world_minutes, (int) minutes, (int) minutes);
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
