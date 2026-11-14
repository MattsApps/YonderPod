package de.danoeh.antennapod.playback.service;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.greenrobot.eventbus.EventBus;

import de.danoeh.antennapod.event.playback.NextUpQuestionEvent;
import de.danoeh.antennapod.storage.preferences.NextUpPreferences;
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter;
import de.danoeh.antennapod.ui.notifications.NotificationUtils;

/**
 * Asks a "what plays next" question from the player. An open Yonder shows it as a dialog (it listens for
 * {@link NextUpQuestionEvent}, and checks the waiting questions whenever it opens); with the phone locked or
 * Yonder in the background, a notification carries the question with both answers.
 */
public final class NextUpAsk {
    private static final String TAG = "NextUpAsk";
    static final int NOTIFICATION_ID = 6201;
    /** Set beside a question asked by the player: "keep playing" then carries on playing. */
    public static final int FROM_PLAYER = 64;

    private NextUpAsk() {
    }

    static void ask(Context context, int questions) {
        NextUpPreferences.addPending(questions | FROM_PLAYER);
        Log.d(TAG, "Asking " + questions);
        notifyPending(context);
        EventBus.getDefault().post(new NextUpQuestionEvent());
    }

    /** The first waiting question as a notification, or none if nothing is waiting. */
    public static void notifyPending(Context context) {
        int pending = NextUpPreferences.getPending();
        int question;
        int title;
        int text;
        int yes;
        int no;
        if ((pending & NextUpPreferences.QUESTION_CONTINUOUS) != 0) {
            question = NextUpPreferences.QUESTION_CONTINUOUS;
            title = R.string.next_up_title;
            text = R.string.next_up_message;
            yes = R.string.next_up_keep_playing;
            no = R.string.next_up_choose;
        } else if ((pending & NextUpPreferences.QUESTION_CONTINUOUS_AGAIN) != 0) {
            question = NextUpPreferences.QUESTION_CONTINUOUS_AGAIN;
            title = R.string.next_up_again_title;
            text = R.string.next_up_again_message;
            yes = R.string.next_up_keep_playing;
            no = R.string.next_up_choose;
        } else if ((pending & NextUpPreferences.QUESTION_HALF_PLAYED) != 0) {
            question = NextUpPreferences.QUESTION_HALF_PLAYED;
            title = R.string.next_up_half_title;
            text = R.string.next_up_half_message;
            yes = R.string.next_up_half_include;
            no = R.string.next_up_half_leave_out;
        } else {
            cancel(context);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // Shown as a dialog when Yonder is next opened.
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NotificationUtils.CHANNEL_ID_USER_ACTION)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(text))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(text)))
                .setContentIntent(new MainActivityStarter(context).getPendingIntent())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(false)
                .addAction(0, context.getString(yes), answer(context, question, true))
                .addAction(0, context.getString(no), answer(context, question, false));
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.d(TAG, "Can't show the question as a notification: " + e.getMessage());
        }
    }

    public static void cancel(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static PendingIntent answer(Context context, int question, boolean yes) {
        Intent intent = new Intent(context, NextUpAnswerReceiver.class)
                .putExtra(NextUpAnswerReceiver.EXTRA_QUESTION, question)
                .putExtra(NextUpAnswerReceiver.EXTRA_YES, yes);
        return PendingIntent.getBroadcast(context, question * 2 + (yes ? 1 : 0), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
