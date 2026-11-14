package de.danoeh.antennapod.playback.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.smartplay.support.NextInShow;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;

/**
 * The one "what plays next" rule. Every route asks it: an episode ending, skip to next, and play pressed
 * with nothing loaded (widget, notification, Bluetooth or other media buttons, the app). No route decides
 * for itself. Call it on a background thread.
 *
 * <p>In order: the next in line in the playlist (the Smart Play's list), then the Smart Play rebuilt if the
 * list ran out. In "I'll choose" mode, or until the listener has chosen, Yonder stops at the end of each
 * episode, and if the Smart Play has nothing it takes the next newer episode of the show just finished,
 * otherwise that show's newest unplayed one (earlier parts first), otherwise nothing ("Nothing new in …").
 *
 * <p>Half-played episodes are left out when Yonder picks, if the listener turned them off. The episode a
 * detour interrupted always resumes. Picking an episode yourself never comes here.
 */
public final class NextUpRule {
    private static final String TAG = "NextUpRule";
    private static final int TAKE = 0;
    private static final int LEAVE_OUT = 1;
    private static final int TAKE_AND_ASK = 2;

    private NextUpRule() {
    }

    /** What the rule chose. item is null for nothing. */
    public static final class Choice {
        @Nullable
        public final FeedItem item;
        /** Start playing now (otherwise it's loaded, paused, ready for play). */
        public final boolean play;
        /** NextUpPreferences.QUESTION_* to ask now. */
        public final int questions;
        /** The show with nothing new, for "Nothing new in …". */
        @Nullable
        public final String nothingNewShow;
        public final String reason;

        Choice(@Nullable FeedItem item, boolean play, int questions, @Nullable String nothingNewShow,
               String reason) {
            this.item = item;
            this.play = play;
            this.questions = questions;
            this.nothingNewShow = nothingNewShow;
            this.reason = reason;
        }
    }

    /** Rebuilds the Smart Play and returns the new playlist (empty if nothing came). */
    public interface Rebuild {
        List<FeedItem> rebuiltQueue(FeedItem ended);
    }

    /**
     * After an episode ended (ended) or the listener skipped to the next (skipped). queue is the playlist as
     * it was before the finished episode was taken out of it.
     */
    public static Choice afterEpisode(FeedItem finished, boolean ended, boolean skipped, List<FeedItem> queue,
                                      Rebuild rebuild) {
        boolean asked = NextUpPreferences.isContinuousAsked();
        boolean keep = NextUpPreferences.keepPlaying();
        boolean auto = ended && !skipped;
        // Moving on by itself: only in "keep playing" mode, and only when an episode ends.
        boolean byItself = auto && keep;
        int[] questions = {auto && !asked ? NextUpPreferences.QUESTION_CONTINUOUS : 0};
        String reason = "next in the playlist";
        FeedItem next = afterInQueue(queue, finished, byItself, questions);
        if (next == null && skipped) {
            next = firstInQueue(queue, finished.getId(), false, questions);
        }
        if (next == null && auto && contains(queue, finished.getId())) {
            next = firstInQueue(rebuild.rebuiltQueue(finished), finished.getId(), byItself, questions);
            reason = "first after rebuilding the Smart Play";
        }
        String nothingNew = null;
        if (next == null && auto && !keep) {
            next = fromShow(finished);
            reason = "from " + showTitle(finished);
            if (next == null) {
                nothingNew = showTitle(finished);
                reason = "nothing new in " + nothingNew;
            }
        }
        boolean play = next != null && (skipped || (keep && questions[0] == 0));
        Log.d(TAG, "After " + finished.getTitle() + (skipped ? " (skipped)" : "") + ": "
                + (next != null ? next.getTitle() : "nothing") + ", " + reason
                + (play ? ", playing" : ", stopped") + (questions[0] != 0 ? ", asking " + questions[0] : ""));
        return new Choice(next, play, questions[0], nothingNew, reason);
    }

    /**
     * Play pressed with nothing loaded: what to load. The episode last loaded (in PlaybackPreferences)
     * comes before this; this is only for when there isn't one.
     */
    public static Choice whenNothingLoaded() {
        FeedItem next = firstInQueue(DBReader.getQueue(), -1, false, null);
        String reason = "first in the playlist";
        if (next == null && !NextUpPreferences.keepPlaying()) {
            long lastId = NextUpPreferences.getLastFinished();
            FeedItem last = lastId > 0 ? DBReader.getFeedItem(lastId) : null;
            if (last != null) {
                next = fromShow(last);
                reason = "from " + showTitle(last);
            }
        }
        String nothingNew = next == null ? NextUpPreferences.getNothingNew() : null;
        Log.d(TAG, "Nothing loaded: " + (next != null ? next.getTitle() : "nothing") + ", " + reason);
        return new Choice(next, true, 0, nothingNew, reason);
    }

    /** "Nothing new in <show>", briefly, when play is pressed and there's nothing to play. */
    public static void sayNothingNew(Context context, String show) {
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(app,
                app.getString(R.string.next_up_nothing_new, show), Toast.LENGTH_SHORT).show());
    }

    /** The first episode in the playlist the rule would play, for showing on the home screen. */
    @Nullable
    public static FeedItem firstInPlaylist(List<FeedItem> queue) {
        return firstInQueue(queue, -1, false, null);
    }

    private static boolean contains(List<FeedItem> queue, long itemId) {
        for (FeedItem item : queue) {
            if (item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static FeedItem afterInQueue(List<FeedItem> queue, FeedItem finished, boolean byItself,
                                         int[] questions) {
        boolean passed = false;
        for (FeedItem item : queue) {
            if (item.getId() == finished.getId()) {
                passed = true;
                continue;
            }
            if (passed && take(item, byItself, questions)) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    private static FeedItem firstInQueue(List<FeedItem> queue, long except, boolean byItself,
                                         @Nullable int[] questions) {
        for (FeedItem item : queue) {
            if (item.getId() != except && take(item, byItself, questions)) {
                return item;
            }
        }
        return null;
    }

    private static boolean take(FeedItem item, boolean byItself, @Nullable int[] questions) {
        FeedMedia media = item.getMedia();
        if (media == null || media.isLiveRadio()) {
            return false;
        }
        switch (halfPlayed(item, byItself)) {
            case LEAVE_OUT:
                return false;
            case TAKE_AND_ASK:
                if (questions != null) {
                    questions[0] |= NextUpPreferences.QUESTION_HALF_PLAYED;
                }
                return true;
            case TAKE:
            default:
                return true;
        }
    }

    private static int halfPlayed(FeedItem item, boolean byItself) {
        if (!item.isInProgress() || item.getMedia().getId() == NextUpPreferences.getDetourResume()) {
            return TAKE;
        }
        if (!NextUpPreferences.isHalfPlayedAsked()) {
            // The first time Yonder is about to move into a half-played episode by itself: stop there, ask.
            return byItself ? TAKE_AND_ASK : TAKE;
        }
        return NextUpPreferences.includeHalfPlayed() ? TAKE : LEAVE_OUT;
    }

    /** "I'll choose" when the Smart Play has nothing: from the show just finished. */
    @Nullable
    private static FeedItem fromShow(FeedItem finished) {
        Feed feed = DBReader.getFeed(finished.getFeedId(), false, 0, Integer.MAX_VALUE);
        if (feed == null || feed.getItems() == null) {
            return null;
        }
        boolean leaveOutHalf = NextUpPreferences.isHalfPlayedAsked() && !NextUpPreferences.includeHalfPlayed();
        List<NextInShow.Episode> episodes = new ArrayList<>();
        Map<Long, FeedItem> byId = new HashMap<>();
        for (FeedItem item : feed.getItems()) {
            if (item.getMedia() == null || item.getMedia().isLiveRadio()) {
                continue;
            }
            byId.put(item.getId(), item);
            episodes.add(new NextInShow.Episode(item.getId(), item.getTitle(), date(item), item.isPlayed(),
                    leaveOutHalf && item.isInProgress()));
        }
        NextInShow.Episode pick = NextInShow.choose(episodes, finished.getId(), date(finished));
        return pick != null ? byId.get(pick.id) : null;
    }

    private static long date(FeedItem item) {
        return item.getPubDate() != null ? item.getPubDate().getTime() : 0;
    }

    private static String showTitle(FeedItem item) {
        Feed feed = item.getFeed() != null ? item.getFeed() : DBReader.getFeed(item.getFeedId(), false, 0, 0);
        return feed != null && feed.getTitle() != null ? feed.getTitle() : "";
    }
}
