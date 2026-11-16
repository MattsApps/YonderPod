package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import androidx.fragment.app.Fragment;

/**
 * F-Droid build: no Google libraries, so no in-app tips. "tip me" opens the tip code screen: four Buy Me a
 * Coffee shop items (see TipCodes) and a field for the code they give.
 */
final class TipShop {
    private TipShop() {
    }

    static boolean canTip(Context context) {
        return true;
    }

    /** Nothing to ask: the F-Droid build only knows the codes entered on this phone. */
    static void refresh(Context context) {
    }

    static void open(Fragment fragment) {
        TendRows.open(fragment, new TipCodeFragment());
    }
}
