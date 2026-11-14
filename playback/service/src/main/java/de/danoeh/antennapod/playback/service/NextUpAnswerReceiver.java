package de.danoeh.antennapod.playback.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.media3.session.MediaController;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.playback.NextUpQuestionEvent;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;

/** An answer tapped on the question notification. */
public class NextUpAnswerReceiver extends BroadcastReceiver {
    private static final String TAG = "NextUpAnswerReceiver";
    static final String EXTRA_QUESTION = "question";
    static final String EXTRA_YES = "yes";

    @Override
    public void onReceive(Context context, Intent intent) {
        int question = intent.getIntExtra(EXTRA_QUESTION, 0);
        boolean yes = intent.getBooleanExtra(EXTRA_YES, false);
        boolean fromPlayer = (NextUpPreferences.getPending() & NextUpAsk.FROM_PLAYER) != 0;
        Log.d(TAG, "Answer to " + question + ": " + yes);
        answer(context, question, yes, fromPlayer);
        NextUpAsk.notifyPending(context);
        EventBus.getDefault().post(new NextUpQuestionEvent());
    }

    /** Records an answer, from the notification or the dialog. "keep playing" from the player plays on. */
    public static void answer(Context context, int question, boolean yes, boolean fromPlayer) {
        if (question == NextUpPreferences.QUESTION_HALF_PLAYED) {
            NextUpPreferences.setIncludeHalfPlayed(yes);
        } else if (question == NextUpPreferences.QUESTION_CONTINUOUS
                || question == NextUpPreferences.QUESTION_CONTINUOUS_AGAIN) {
            NextUpPreferences.setKeepPlaying(yes, true);
            if (question == NextUpPreferences.QUESTION_CONTINUOUS_AGAIN) {
                NextUpPreferences.setContinuousAskedAgain();
            }
            if (yes && fromPlayer) {
                PlaybackController.bindToMedia3Service(context, MediaController::play);
            }
        }
        int left = NextUpPreferences.getPending() & ~NextUpAsk.FROM_PLAYER;
        if (left == 0) {
            NextUpPreferences.clearPending(NextUpAsk.FROM_PLAYER);
        }
    }
}
