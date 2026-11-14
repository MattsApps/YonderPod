package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.playback.service.NextUpAnswerReceiver;
import de.danoeh.antennapod.playback.service.NextUpAsk;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;

/**
 * The "what plays next" questions as dialogs: keep playing or I'll choose, and half-played episodes.
 * They must be answered (no cancel). Shown when the player asks with Yonder open, whenever Yonder opens with
 * a question waiting (asked by notification while the phone was locked), and when a Smart Play is first set
 * up. Tune holds both answers after that.
 */
public final class NextUpQuestions {
    private static boolean showing = false;

    private NextUpQuestions() {
    }

    /** Shows the first waiting question, then the next, if any. */
    public static void showPending(Activity activity) {
        if (showing || activity == null || activity.isFinishing()) {
            return;
        }
        int pending = NextUpPreferences.getPending();
        boolean fromPlayer = (pending & NextUpAsk.FROM_PLAYER) != 0;
        if ((pending & NextUpPreferences.QUESTION_CONTINUOUS) != 0) {
            ask(activity, NextUpPreferences.QUESTION_CONTINUOUS, R.string.next_up_title,
                    R.string.next_up_message, R.string.next_up_keep_playing, R.string.next_up_choose, fromPlayer);
        } else if ((pending & NextUpPreferences.QUESTION_CONTINUOUS_AGAIN) != 0) {
            ask(activity, NextUpPreferences.QUESTION_CONTINUOUS_AGAIN, R.string.next_up_again_title,
                    R.string.next_up_again_message, R.string.next_up_keep_playing, R.string.next_up_choose,
                    fromPlayer);
        } else if ((pending & NextUpPreferences.QUESTION_HALF_PLAYED) != 0) {
            ask(activity, NextUpPreferences.QUESTION_HALF_PLAYED, R.string.next_up_half_title,
                    R.string.next_up_half_message, R.string.next_up_half_include, R.string.next_up_half_leave_out,
                    fromPlayer);
        } else if (pending != 0) {
            NextUpPreferences.clearPending(pending);
        }
    }

    private static void ask(Activity activity, int question, int title, int message, int yes, int no,
                            boolean fromPlayer) {
        showing = true;
        NextUpAsk.cancel(activity);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(yes, (dialog, which) ->
                        NextUpAnswerReceiver.answer(activity, question, true, fromPlayer))
                .setNegativeButton(no, (dialog, which) ->
                        NextUpAnswerReceiver.answer(activity, question, false, fromPlayer))
                .setOnDismissListener(dialog -> {
                    showing = false;
                    showPending(activity);
                })
                .show();
    }

    /**
     * A Smart Play was saved in the editor. The first time: keep playing or choose (if not asked yet, or asked
     * once more after "I'll choose"), and half-played episodes (if not asked yet). Never again after that.
     */
    public static void onSmartPlaySetUp(Activity activity) {
        int questions = 0;
        if (!NextUpPreferences.isContinuousAsked()) {
            questions |= NextUpPreferences.QUESTION_CONTINUOUS;
            // Asked with a Smart Play already set up, so there's nothing to ask again about later.
            NextUpPreferences.setContinuousAskedAgain();
        } else if (!NextUpPreferences.keepPlaying() && !NextUpPreferences.isContinuousAskedAgain()) {
            questions |= NextUpPreferences.QUESTION_CONTINUOUS_AGAIN;
            NextUpPreferences.setContinuousAskedAgain();
        }
        if (!NextUpPreferences.isHalfPlayedAsked()) {
            questions |= NextUpPreferences.QUESTION_HALF_PLAYED;
        }
        if (questions != 0) {
            NextUpPreferences.addPending(questions);
            showPending(activity);
        }
    }
}
