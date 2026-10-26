package de.danoeh.antennapod.smartplay.app;

import android.content.Context;

import de.danoeh.antennapod.net.download.service.feed.FeedUpdateManagerImpl;

public class YonderFeedUpdateManager extends FeedUpdateManagerImpl {
    @Override
    public void restartUpdateAlarm(Context context, boolean replace) {
        UpdateScheduler.init(context);
    }
}
