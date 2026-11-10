package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import de.danoeh.antennapod.R;

/**
 * The "tip me" support model: free and fully working, nothing ever locked.
 * For the first 2 weeks after install the wordmark says "yonder"; after that it becomes "tip me"
 * (dismissible for the day). A tip brings "yonder" back for a year.
 *
 * <p>Two preference files. {@link #PREFS} holds the first-install date and today's dismissal, and is
 * part of Yonder's backups, so a restore keeps the original date. {@link #LOCAL_PREFS} holds what Google
 * Play last said and the hidden test switches; it is left out of backups (see YonderBackup).
 */
public final class Tip {
    static final int FREE_DAYS = 14;
    static final String PREFS = "YonderTip";
    static final String LOCAL_PREFS = "YonderTipLocal";

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long THANKS_MILLIS = 8000;

    private static final String KEY_FIRST_SEEN = "firstSeen";
    private static final String KEY_DISMISSED_DAY = "dismissedDay";

    private static final String KEY_SUPPORTER = "supporter";
    private static final String KEY_UNTIL = "until";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_THANKS_AT = "thanksAt";
    private static final String KEY_TEST_DAYS = "testDays";
    private static final String KEY_TEST_IGNORE = "testIgnoreToken";

    private static boolean checkedThisRun;

    /** Posted on EventBus whenever anything here changes. */
    public static final class ChangedEvent {
    }

    private Tip() {
    }

    /** Asks Play (once per app run) whether this listener has tipped. */
    static void checkOnce(Context context) {
        if (!checkedThisRun) {
            checkedThisRun = true;
            TipShop.refresh(context.getApplicationContext());
        }
    }

    /** True when the wordmark should be the "tip me" button. */
    static boolean showTipMe(Context context) {
        if (!TipShop.canTip(context) || isSupporter(context)) {
            return false;
        }
        if (now(context) - firstSeen(context) < FREE_DAYS * DAY) {
            return false;
        }
        return !today().equals(prefs(context).getString(KEY_DISMISSED_DAY, ""));
    }

    /** The web page the F-Droid build opens for a tip (empty until Matt sets it in strings.xml). */
    static String webLink(Context context) {
        return context.getString(R.string.yonder_tip_link).trim();
    }

    static void dismissForToday(Context context) {
        prefs(context).edit().putString(KEY_DISMISSED_DAY, today()).apply();
        changed();
    }

    /**
     * The first-install date. A restored backup brings back the original date; a fresh install with no
     * backup starts from Android's install time. The earlier of the two always wins.
     */
    static long firstSeen(Context context) {
        SharedPreferences prefs = prefs(context);
        long stored = prefs.getLong(KEY_FIRST_SEEN, 0);
        long installed = installTime(context);
        long first = stored > 0 ? Math.min(stored, installed) : installed;
        if (first != stored) {
            prefs.edit().putLong(KEY_FIRST_SEEN, first).apply();
        }
        return first;
    }

    static boolean isSupporter(Context context) {
        return local(context).getBoolean(KEY_SUPPORTER, false);
    }

    /** Best estimate of when the tip runs out, or 0 if unknown. Display only: Play decides. */
    static long supporterUntil(Context context) {
        return local(context).getLong(KEY_UNTIL, 0);
    }

    @Nullable
    static String supporterUntilText(Context context) {
        long until = supporterUntil(context);
        return until <= 0 ? null : DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(until));
    }

    /**
     * What Google Play says after a successful check. token null means no active tip.
     * Only call this when Play actually answered: on errors or no network, leave things as they were.
     */
    static void playSays(Context context, @Nullable String token, long purchaseTime) {
        SharedPreferences local = local(context);
        SharedPreferences.Editor editor = local.edit();
        if (token != null && token.equals(local.getString(KEY_TEST_IGNORE, null))) {
            token = null;
        }
        if (token == null) {
            editor.putBoolean(KEY_SUPPORTER, false).remove(KEY_UNTIL).remove(KEY_TOKEN);
        } else {
            if (!token.equals(local.getString(KEY_TOKEN, null))) {
                // A top-up gets a new token and adds a year to the old end date. After a reinstall the old
                // end date is unknown, so the estimate starts from the latest purchase.
                long previous = local.getLong(KEY_UNTIL, 0);
                editor.putLong(KEY_UNTIL, addYear(previous > purchaseTime ? previous : purchaseTime));
                editor.putString(KEY_TOKEN, token);
            }
            editor.putBoolean(KEY_SUPPORTER, true);
        }
        editor.apply();
        changed();
    }

    /** A tip just went through: the wordmark says thank you for a few seconds. */
    static void thank(Context context) {
        local(context).edit().putLong(KEY_THANKS_AT, System.currentTimeMillis()).apply();
        changed();
    }

    /** Milliseconds of thank-you left to show, or 0. */
    static long thanksLeft(Context context) {
        long at = local(context).getLong(KEY_THANKS_AT, 0);
        long left = at + THANKS_MILLIS - System.currentTimeMillis();
        return left > 0 && left <= THANKS_MILLIS ? left : 0;
    }

    // Hidden test switches (Tune → long-press the tip row). They only change what the wordmark shows.

    static void testJumpTwoWeeks(Context context) {
        local(context).edit().putInt(KEY_TEST_DAYS, FREE_DAYS + 1).apply();
        prefs(context).edit().remove(KEY_DISMISSED_DAY).apply();
        changed();
    }

    static void testForgetTip(Context context) {
        SharedPreferences local = local(context);
        local.edit().putString(KEY_TEST_IGNORE, local.getString(KEY_TOKEN, ""))
                .putBoolean(KEY_SUPPORTER, false).remove(KEY_UNTIL).remove(KEY_TOKEN).remove(KEY_THANKS_AT).apply();
        prefs(context).edit().remove(KEY_DISMISSED_DAY).apply();
        changed();
    }

    static void testBackToNormal(Context context) {
        local(context).edit().remove(KEY_TEST_DAYS).remove(KEY_TEST_IGNORE).apply();
        changed();
        TipShop.refresh(context.getApplicationContext());
    }

    private static long now(Context context) {
        return System.currentTimeMillis() + local(context).getInt(KEY_TEST_DAYS, 0) * DAY;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static long addYear(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.YEAR, 1);
        return calendar.getTimeInMillis();
    }

    private static long installTime(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            return System.currentTimeMillis();
        }
    }

    static void changed() {
        EventBus.getDefault().post(new ChangedEvent());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences local(Context context) {
        return context.getApplicationContext().getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE);
    }
}
