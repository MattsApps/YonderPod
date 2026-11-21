package de.danoeh.antennapod.ui.screen.playback;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.settings.SoundDefaultsChangedEvent;
import de.danoeh.antennapod.event.settings.VolumeAdaptionChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.ui.screen.feed.preferences.FeedSettingsPreferenceFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;

/**
 * The player's speaker button (coding thread 18): "Volume boost in &lt;show&gt;", or "in &lt;station&gt;" on live
 * radio and live pause. It is the same setting as the show's settings page row (or the station's own boost), heard
 * at once and saved there.
 */
public final class VolumeBoostDialog {
    private static final String TAG = "VolumeBoostDialog";

    private VolumeBoostDialog() {
    }

    public static void show(@NonNull Context context, @NonNull FeedMedia media) {
        if (media.getItem() == null) {
            return;
        }
        if (media.isLiveRadio()) {
            long stationId = media.getItem().getId();
            choose(context, media.getItem().getTitle(), RadioPreferences.boost(stationId), chosen -> {
                RadioPreferences.setBoost(stationId, chosen);
                EventBus.getDefault().post(new SoundDefaultsChangedEvent());
            });
            return;
        }
        long feedId = media.getItem().getFeedId();
        Single.fromCallable(() -> {
            Feed feed = DBReader.getFeed(feedId, false, 0, 0);
            if (feed == null || feed.getPreferences() == null) {
                throw new IllegalStateException("No show for " + feedId);
            }
            return feed;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(feed -> choose(context, feed.getTitle(),
                        feed.getPreferences().getVolumeAdaptionSetting(), chosen -> {
                            FeedPreferences preferences = feed.getPreferences();
                            preferences.setVolumeAdaptionSetting(chosen);
                            DBWriter.setFeedPreferences(preferences);
                            EventBus.getDefault().post(new VolumeAdaptionChangedEvent(chosen, feedId));
                        }), error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

    private interface Chosen {
        void chosen(VolumeAdaptionSetting setting);
    }

    private static void choose(Context context, String name, VolumeAdaptionSetting current, Chosen chosen) {
        List<VolumeAdaptionSetting> settings = FeedSettingsPreferenceFragment.volumeChoices();
        CharSequence[] names = FeedSettingsPreferenceFragment.volumeChoiceNames(context, settings);
        int selected = settings.indexOf(current);
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.player_volume_boost_in, name == null ? "" : name))
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    VolumeAdaptionSetting setting = settings.get(which);
                    if (setting != current) {
                        chosen.chosen(setting);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }
}
