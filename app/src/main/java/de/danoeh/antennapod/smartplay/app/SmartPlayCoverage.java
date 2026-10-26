package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;

public final class SmartPlayCoverage {

    private SmartPlayCoverage() {
    }

    public static String describe(Context context, Feed feed) {
        Set<String> categories = new HashSet<>(CategoryChips.categoriesOf(feed));
        List<String> names = new ArrayList<>();
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            if (stored.getDefinition().coversFeed(feed.getId(), categories)) {
                names.add(stored.getDefinition().getName());
            }
        }
        if (names.isEmpty()) {
            return context.getString(R.string.smartplay_not_included);
        }
        return context.getString(R.string.smartplay_included_in, TextUtils.join(", ", names));
    }
}
