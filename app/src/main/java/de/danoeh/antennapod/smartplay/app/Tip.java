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
 * For the first calendar month after install the wordmark says "yonder"; after that it becomes "tip me"
 * (dismissible for the day). A tip brings "yonder" back for a year.
 *
 * <p>Two preference files. {@link #PREFS} holds the first-install date and today's dismissal, and is
 * part of Yonder's backups, so a restore keeps the original date. {@link #LOCAL_PREFS} holds what Google
 * Play last said and the hidden test switches; it is left out of backups (see YonderBackup).
 */
public final class Tip {
    static final String PREFS = "YonderTip";
    static final String LOCAL_PREFS = "YonderTipLocal";

    private static final long THANKS_MILLIS = 8000;

    private static final String KEY_FIRST_SEEN = "firstSeen";
    private static final String KEY_DISMISSED_DAY = "dismissedDay";
    private static final String KEY_WAS_DUE = "wasDue";
    private static final String KEY_NOTE = "note";

    private static final String KEY_SUPPORTER = "supporter";
    private static final String KEY_UNTIL = "until";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_THANKS_AT = "thanksAt";
    private static final String KEY_TEST_DAYS = "testDays";
    private static final String KEY_TEST_MONTH_PASSED = "testMonthPassed";
    private static final String KEY_TEST_IGNORE = "testIgnoreToken";
    private static final String KEY_EVER_TIPPED = "everTipped";
    private static final String KEY_TEST_TIPPED = "testTipped";

    /** The note under "tip me": none, the free month is up, or a tipped year is up. */
    static final int NOTE_NONE = 0;
    static final int NOTE_MONTH = 1;
    static final int NOTE_YEAR = 2;

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
        return tipMeDue(context) && !today().equals(prefs(context).getString(KEY_DISMISSED_DAY, ""));
    }

    /** "tip me" is due (free month over, no tip in the last year), whether or not it's hidden for today. */
    private static boolean tipMeDue(Context context) {
        if (!TipShop.canTip(context) || isSupporter(context)) {
            return false;
        }
        return local(context).getBoolean(KEY_TEST_MONTH_PASSED, false)
                || System.currentTimeMillis() >= addMonth(firstSeen(context));
    }

    /**
     * The note to show under "tip me", once per change: each time "tip me" replaces "yonder" (the free month
     * ends, or a tipped year runs out) the note comes back until "got it". Hiding "tip me" for the day with
     * a long press isn't a change.
     */
    static int note(Context context) {
        SharedPreferences prefs = prefs(context);
        boolean due = tipMeDue(context);
        boolean wasDue = prefs.getBoolean(KEY_WAS_DUE, false);
        if (due && !wasDue) {
            prefs.edit().putBoolean(KEY_WAS_DUE, true)
                    .putInt(KEY_NOTE, everTipped(context) ? NOTE_YEAR : NOTE_MONTH).apply();
        } else if (!due && wasDue) {
            prefs.edit().putBoolean(KEY_WAS_DUE, false).remove(KEY_NOTE).apply();
        }
        return showTipMe(context) ? prefs.getInt(KEY_NOTE, NOTE_NONE) : NOTE_NONE;
    }

    /** "got it": hidden until "tip me" next comes back. */
    static void noteSeen(Context context) {
        prefs(context).edit().remove(KEY_NOTE).apply();
        changed();
    }

    private static boolean everTipped(Context context) {
        SharedPreferences local = local(context);
        return local.getBoolean(KEY_EVER_TIPPED, false) || local.getBoolean(KEY_TEST_TIPPED, false);
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

    /**
     * True for a year from the latest tip. Play says whether a tip is active; the year is counted here
     * from the day of the latest tip (decided 14 Nov), even where Play's own end date is later.
     */
    static boolean isSupporter(Context context) {
        SharedPreferences local = local(context);
        if (!local.getBoolean(KEY_SUPPORTER, false)) {
            return false;
        }
        long until = local.getLong(KEY_UNTIL, 0);
        return until <= 0 || System.currentTimeMillis() < until;
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
                // Any tip gives "yonder" for a year from the day of that tip (decided 14 Nov): a tip in the
                // free month, or a top-up while a year is running, restarts the year from the new tip.
                // Play's top-up adds a year to its own end date instead; Yonder only uses the tip's date.
                editor.putLong(KEY_UNTIL, addYear(purchaseTime > 0 ? purchaseTime : System.currentTimeMillis()));
                editor.putString(KEY_TOKEN, token);
            }
            editor.putBoolean(KEY_SUPPORTER, true).putBoolean(KEY_EVER_TIPPED, true);
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

    static void testJumpMonth(Context context) {
        local(context).edit().putBoolean(KEY_TEST_MONTH_PASSED, true).remove(KEY_TEST_DAYS)
                .remove(KEY_TEST_TIPPED).apply();
        prefs(context).edit().remove(KEY_DISMISSED_DAY).remove(KEY_WAS_DUE).remove(KEY_NOTE).apply();
        changed();
    }

    /** Debug builds only: as if a tipped year had just run out, so the second note shows. */
    static void testYearRanOut(Context context) {
        SharedPreferences local = local(context);
        local.edit().putBoolean(KEY_TEST_MONTH_PASSED, true).putBoolean(KEY_TEST_TIPPED, true)
                .putString(KEY_TEST_IGNORE, local.getString(KEY_TOKEN, ""))
                .putBoolean(KEY_SUPPORTER, false).remove(KEY_UNTIL).remove(KEY_TOKEN).remove(KEY_THANKS_AT).apply();
        prefs(context).edit().remove(KEY_DISMISSED_DAY).remove(KEY_WAS_DUE).remove(KEY_NOTE).apply();
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
        local(context).edit().remove(KEY_TEST_DAYS).remove(KEY_TEST_MONTH_PASSED).remove(KEY_TEST_IGNORE)
                .remove(KEY_TEST_TIPPED).apply();
        changed();
        TipShop.refresh(context.getApplicationContext());
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static long addMonth(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.MONTH, 1);
        return calendar.getTimeInMillis();
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
