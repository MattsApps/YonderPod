package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.view.View;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedPreferences;

public final class CategoryChips {

    private CategoryChips() {
    }

    public static List<String> categoriesOf(Feed feed) {
        List<String> result = new ArrayList<>();
        FeedPreferences preferences = feed.getPreferences();
        if (preferences == null) {
            return result;
        }
        for (String tag : preferences.getTags()) {
            if (!FeedPreferences.TAG_ROOT.equals(tag) && !FeedPreferences.TAG_UNTAGGED.equals(tag)) {
                result.add(tag);
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static void fill(ChipGroup group, Collection<String> categories) {
        group.removeAllViews();
        if (categories.isEmpty()) {
            group.setVisibility(View.GONE);
            return;
        }
        group.setVisibility(View.VISIBLE);
        Context context = group.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        for (String category : categories) {
            Chip chip = new Chip(context);
            chip.setText(category);
            chip.setTextSize(12);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(24 * density);
            chip.setClickable(false);
            chip.setFocusable(false);
            group.addView(chip);
        }
    }
}
