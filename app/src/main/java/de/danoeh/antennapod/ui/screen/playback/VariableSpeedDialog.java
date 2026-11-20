package de.danoeh.antennapod.ui.screen.playback;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.event.playback.SpeedChangedEvent;
import de.danoeh.antennapod.playback.base.BuildConfig;
import de.danoeh.antennapod.playback.service.PlaybackController;
import de.danoeh.antennapod.event.settings.SpeedPresetChangedEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.ui.episodes.PlaybackSpeedUtils;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.ItemOffsetDecoration;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VariableSpeedDialog extends BottomSheetDialogFragment {
    private static final String TAG = "VariableSpeedDialog";
    private SpeedSelectionAdapter adapter;
    private PlaybackController controller;
    private final List<Float> selectedSpeeds;
    private float pendingDeleteSpeed = -1;
    private PlaybackSpeedSeekBar speedSeekBar;
    private Chip addCurrentSpeedChip;
    private CheckBox skipSilenceCheckbox;
    private Disposable disposable;

    public VariableSpeedDialog() {
        DecimalFormatSymbols format = new DecimalFormatSymbols(Locale.US);
        format.setDecimalSeparator('.');
        selectedSpeeds = new ArrayList<>(UserPreferences.getPlaybackSpeedArray());
    }

    @Override
    public void onStart() {
        super.onStart();
        controller = new PlaybackController(getActivity()) {
            @Override
            public void loadMediaInfo() {
                VariableSpeedDialog.this.loadMediaInfo();
            }
        };
        controller.init();
        EventBus.getDefault().register(this);
        loadMediaInfo();
    }

    @Override
    public void onStop() {
        super.onStop();
        controller.release();
        controller = null;
        EventBus.getDefault().unregister(this);
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void loadMediaInfo() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> {
            if (controller == null) {
                return null;
            }
            // Make sure the media is loaded in case getCurrentPlaybackSpeedMultiplier has to access it
            return controller.getMedia();
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> {
                    if (controller == null) {
                        return;
                    }
                    updateSpeed(new SpeedChangedEvent(controller.getCurrentPlaybackSpeedMultiplier()));
                    showSkipSilence(pair);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void updateSpeed(SpeedChangedEvent event) {
        speedSeekBar.updateSpeed(event.getNewSpeed());
        addCurrentSpeedChip.setText(String.format(Locale.getDefault(), "%1$.2f", event.getNewSpeed()));
    }

    /**
     * "Skip silence in &lt;show&gt;": the playing show's own setting (1.0.1), saved on the show and applied at
     * once. Hidden with nothing loaded, on live radio and on radio recordings.
     */
    private void showSkipSilence(Playable media) {
        skipSilenceCheckbox.setOnCheckedChangeListener(null);
        Feed feed = null;
        if (media instanceof FeedMedia && !((FeedMedia) media).isLiveRadio()
                && ((FeedMedia) media).getItem() != null) {
            feed = ((FeedMedia) media).getItem().getFeed();
        }
        if (feed == null || feed.getPreferences() == null || (feed.getDownloadUrl() != null
                && feed.getDownloadUrl().startsWith(Feed.RECORDINGS_URL_PREFIX))) {
            skipSilenceCheckbox.setVisibility(View.GONE);
            return;
        }
        skipSilenceCheckbox.setText(getString(R.string.player_skip_silence_show, feed.getTitle()));
        skipSilenceCheckbox.setChecked(PlaybackSpeedUtils.getCurrentSkipSilencePreference(media)
                == FeedPreferences.SkipSilence.AGGRESSIVE);
        skipSilenceCheckbox.setVisibility(View.VISIBLE);
        final Feed shown = feed;
        skipSilenceCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeedPreferences.SkipSilence chosen = isChecked
                    ? FeedPreferences.SkipSilence.AGGRESSIVE : FeedPreferences.SkipSilence.OFF;
            shown.getPreferences().setFeedSkipSilence(chosen);
            long feedId = shown.getId();
            Schedulers.io().scheduleDirect(() -> {
                Feed fresh = DBReader.getFeed(feedId, false, 0, 0);
                FeedPreferences preferences = fresh != null && fresh.getPreferences() != null
                        ? fresh.getPreferences() : shown.getPreferences();
                preferences.setFeedSkipSilence(chosen);
                DBWriter.setFeedPreferences(preferences);
                EventBus.getDefault().post(new SpeedPresetChangedEvent(preferences.getFeedPlaybackSpeed(),
                        feedId, chosen));
            });
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = View.inflate(getContext(), R.layout.speed_select_dialog, null);
        speedSeekBar = root.findViewById(R.id.speed_seek_bar);
        speedSeekBar.setProgressChangedListener(multiplier -> {
            UserPreferences.setPlaybackSpeed(multiplier);
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(getContext(),
                        controller -> controller.setPlaybackSpeed(multiplier));
            } else if (controller != null) {
                controller.setPlaybackSpeed(multiplier);
            }
        });
        RecyclerView selectedSpeedsGrid = root.findViewById(R.id.selected_speeds_grid);
        selectedSpeedsGrid.setLayoutManager(new GridLayoutManager(getContext(), 3));
        selectedSpeedsGrid.addItemDecoration(new ItemOffsetDecoration(getContext(), 4));
        adapter = new SpeedSelectionAdapter();
        adapter.setHasStableIds(true);
        selectedSpeedsGrid.setAdapter(adapter);

        addCurrentSpeedChip = root.findViewById(R.id.add_current_speed_chip);
        addCurrentSpeedChip.setCloseIconVisible(true);
        addCurrentSpeedChip.setCloseIconResource(R.drawable.ic_add);
        addCurrentSpeedChip.setOnCloseIconClickListener(v -> addCurrentSpeed());
        addCurrentSpeedChip.setCloseIconContentDescription(getString(R.string.add_preset));
        addCurrentSpeedChip.setOnClickListener(v -> addCurrentSpeed());

        skipSilenceCheckbox = root.findViewById(R.id.skipSilence);
        skipSilenceCheckbox.setVisibility(View.GONE);
        return root;
    }

    private void addCurrentSpeed() {
        float newSpeed = speedSeekBar.getCurrentSpeed();
        if (selectedSpeeds.contains(newSpeed)) {
            Snackbar.make(addCurrentSpeedChip,
                    getString(R.string.preset_already_exists, newSpeed), Snackbar.LENGTH_LONG).show();
        } else {
            selectedSpeeds.add(newSpeed);
            Collections.sort(selectedSpeeds);
            UserPreferences.setPlaybackSpeedArray(selectedSpeeds);
            adapter.notifyDataSetChanged();
        }
    }

    public class SpeedSelectionAdapter extends RecyclerView.Adapter<SpeedSelectionAdapter.ViewHolder> {

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Chip chip = new Chip(getContext());
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            return new ViewHolder(chip);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            float speed = selectedSpeeds.get(position);

            holder.chip.setText(String.format(Locale.getDefault(), "%1$.2f", speed));
            if (pendingDeleteSpeed == speed) {
                holder.chip.setChipBackgroundColor(ColorStateList.valueOf(
                        ThemeUtils.getColorFromAttr(holder.chip.getContext(), R.attr.icon_red)));
                holder.chip.setTextColor(0xffffffff);
                holder.chip.setChipIconResource(R.drawable.ic_delete);
                holder.chip.setChipIconTintResource(R.color.white);
            } else {
                holder.chip.setChipBackgroundColor(null);
                holder.chip.setChipIcon(null);
                holder.chip.setTextColor(ThemeUtils.getColorFromAttr(
                        holder.chip.getContext(), android.R.attr.textColorPrimary));
            }
            holder.chip.setOnLongClickListener(v -> {
                if (pendingDeleteSpeed == speed) {
                    pendingDeleteSpeed = -1;
                } else {
                    pendingDeleteSpeed = speed;
                }
                notifyDataSetChanged();
                return true;
            });
            holder.chip.setOnClickListener(v -> {
                if (pendingDeleteSpeed == speed) {
                    selectedSpeeds.remove(speed);
                    UserPreferences.setPlaybackSpeedArray(selectedSpeeds);
                    pendingDeleteSpeed = -1;
                    notifyDataSetChanged();
                    return;
                }
                UserPreferences.setPlaybackSpeed(speed);
                if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                    PlaybackController.bindToMedia3Service(getContext(),
                            controller -> controller.setPlaybackSpeed(speed));
                } else if (controller != null) {
                    controller.setPlaybackSpeed(speed);
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> dismiss(), 200);
            });
        }

        @Override
        public int getItemCount() {
            return selectedSpeeds.size();
        }

        @Override
        public long getItemId(int position) {
            return selectedSpeeds.get(position).hashCode();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            Chip chip;

            ViewHolder(Chip itemView) {
                super(itemView);
                chip = itemView;
            }
        }
    }
}
