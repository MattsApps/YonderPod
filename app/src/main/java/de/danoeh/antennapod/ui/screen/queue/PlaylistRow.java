package de.danoeh.antennapod.ui.screen.queue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.playback.service.PlaybackStatus;
import de.danoeh.antennapod.ui.common.Converter;
import de.danoeh.antennapod.ui.common.DateFormatter;
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemViewHolder;

final class PlaylistRow {
    private PlaylistRow() {
    }

    static void style(EpisodeItemViewHolder holder, View.OnClickListener onMenu) {
        FeedItem item = holder.getFeedItem();
        if (item == null || item.getFeed() == null) {
            return;
        }
        View row = holder.itemView;
        Context context = row.getContext();
        TextView show = row.findViewById(R.id.txtvShow);
        show.setText(item.getFeed().getDisplayTitle());
        show.setVisibility(View.VISIBLE);
        TextView title = row.findViewById(R.id.txtvTitle);
        title.setTypeface(ResourcesCompat.getFont(context, R.font.bricolage_extrabold));
        row.findViewById(R.id.size).setVisibility(View.GONE);
        row.findViewById(R.id.sizeSeparator).setVisibility(View.GONE);
        FeedMedia media = item.getMedia();
        boolean started = media != null && media.getDuration() > 0
                && (item.isInProgress() || PlaybackStatus.isPlaying(media));
        if (started) {
            showLeft(holder, media.getDuration(), media.getPosition());
        } else {
            TextView date = row.findViewById(R.id.txtvPubDate);
            String text = DateFormatter.formatAbbrev(context, item.getPubDate());
            if (media != null && media.getDuration() > 0) {
                text = context.getString(R.string.playlist_date_length, text,
                        Converter.getDurationStringLocalized(context, media.getDuration()));
            }
            date.setText(text);
            hideTimes(row);
        }
        holder.secondaryActionIcon.setImageResource(R.drawable.dots_vertical);
        holder.secondaryActionButton.setContentDescription(context.getString(R.string.playlist_more));
        holder.secondaryActionButton.setOnClickListener(onMenu);
    }

    static void showLeft(EpisodeItemViewHolder holder, int duration, int position) {
        if (duration <= 0 || position == Playable.INVALID_TIME) {
            return;
        }
        View row = holder.itemView;
        Context context = row.getContext();
        TextView date = row.findViewById(R.id.txtvPubDate);
        date.setText(context.getString(R.string.playlist_time_left,
                Converter.getDurationStringLocalized(context, Math.max(0, duration - position))));
        hideTimes(row);
    }

    private static void hideTimes(View row) {
        row.findViewById(R.id.txtvPosition).setVisibility(View.GONE);
        row.findViewById(R.id.txtvDuration).setVisibility(View.GONE);
    }
}
