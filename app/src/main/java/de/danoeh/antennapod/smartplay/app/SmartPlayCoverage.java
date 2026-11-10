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
        String status = "";
        boolean recording = Recordings.isRecordingsFeed(feed);
        Set<Long> chosen = new HashSet<>();
        if (recording) {
            RecordingStore.Show show = RecordingStore.getInstance(context).showForFeed(feed.getId());
            if (show != null) {
                chosen.addAll(Recordings.playsIn(context, show));
            }
            status = (show != null ? Recordings.summary(context, show)
                    : context.getString(R.string.recording_stopped)) + "\n";
        }
        Set<String> categories = new HashSet<>(CategoryChips.categoriesOf(feed));
        List<String> names = new ArrayList<>();
        boolean book = !recording && Books.isBook(context, feed.getId());
        for (SmartPlayStore.StoredSmartPlay stored : SmartPlayStore.getInstance(context).getAll()) {
            if (recording && !chosen.contains(stored.getId())) {
                continue;
            }
            if (stored.getDefinition().coversFeed(feed.getId(), categories, book)) {
                names.add(stored.getDefinition().getName());
            }
        }
        if (names.isEmpty()) {
            return status + context.getString(book
                    ? R.string.book_not_included : R.string.smartplay_not_included);
        }
        return status + context.getString(R.string.smartplay_included_in,
                TextUtils.join(context.getString(R.string.list_separator), names));
    }
}
