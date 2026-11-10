package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.fragment.app.Fragment;

import de.danoeh.antennapod.ui.common.IntentUtils;

/**
 * F-Droid build: no Google libraries, so no in-app tips. "tip me" opens a web page instead, and only
 * once Matt has set yonder_tip_link in strings.xml. Until then "yonder" always shows.
 */
final class TipShop {
    private TipShop() {
    }

    static boolean canTip(Context context) {
        return !Tip.webLink(context).isEmpty();
    }

    /** Nothing to ask: the F-Droid build can't know who has tipped. */
    static void refresh(Context context) {
    }

    static void open(Fragment fragment) {
        Context context = fragment.requireContext();
        if (canTip(context)) {
            IntentUtils.openInBrowser(context, Tip.webLink(context));
            Tip.dismissForToday(context);
        }
    }
}
