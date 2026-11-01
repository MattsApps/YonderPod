package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.smartplay.radio.StreamLinks;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioFragment extends Fragment {
    public static final String TAG = "RadioFragment";

    private final List<FeedItem> stations = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.tend_fragment, container, false);
        TendRows.setUp(this, root, R.string.world_radio, R.string.tend_find_station,
                () -> TendRows.open(this, new RadioSearchFragment()));
        ((TextView) root.findViewById(R.id.tendListLabel)).setText(R.string.tend_stations);
        LinearLayout extra = root.findViewById(R.id.tendExtra);
        TextView paste = new TextView(requireContext());
        TextViewCompat.setTextAppearance(paste, R.style.Widget_WildInk_Link);
        paste.setText(R.string.find_paste_stream);
        paste.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        paste.setGravity(Gravity.CENTER_VERTICAL);
        paste.setTextColor(paste.getTextColors().withAlpha(200));
        paste.setOnClickListener(v -> showAddLink(requireContext(), this::load));
        extra.addView(paste);
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStationsChanged(Radio.ChangedEvent event) {
        load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            load();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        showStations();
    }

    private void showStations() {
        if (root == null) {
            return;
        }
        LinearLayout list = root.findViewById(R.id.tendList);
        list.removeAllViews();
        for (FeedItem station : stations) {
            String meta = details(requireContext(), RadioPreferences.get(station.getId()));
            if (Radio.isPlaying(station)) {
                meta = meta.isEmpty() ? getString(R.string.radio_playing)
                        : getString(R.string.radio_playing) + " · " + meta;
            }
            View row = TendRows.row(this, list, station.getTitle(), meta, station.getImageUrl(), "", "",
                    v -> TendRows.open(this, RadioStationFragment.newInstance(station.getId())));
            row.setOnLongClickListener(v -> {
                showStationMenu(v, station);
                return true;
            });
            list.addView(row);
        }
        if (stations.isEmpty()) {
            TextView empty = new TextView(requireContext());
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_WildInk_Detail);
            empty.setText(R.string.radio_empty);
            empty.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
            list.addView(empty);
        }
    }

    public static void play(Context context, FeedItem station) {
        if (station.getMedia() != null) {
            Radio.setLastStation(context, station.getId());
            new PlaybackServiceStarter(context, station.getMedia()).callEvenIfRunning(true).start();
        }
    }

    private void load() {
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> Radio.stations(app))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> {
                    stations.clear();
                    stations.addAll(loaded);
                    showStations();
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    static void showAddLink(Context context, Runnable onAdded) {
        View view = LayoutInflater.from(context).inflate(R.layout.radio_add_link_dialog, null);
        EditText url = view.findViewById(R.id.link_url);
        EditText name = view.findViewById(R.id.link_name);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.radio_add_link)
                .setView(view)
                .setPositiveButton(R.string.radio_add_button, (dialog, which) ->
                        addLink(context, url.getText().toString().trim(), name.getText().toString().trim(), onAdded))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void addLink(Context context, String link, String name, Runnable onAdded) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            Toast.makeText(context, R.string.radio_link_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        Context app = context.getApplicationContext();
        Toast.makeText(context, R.string.radio_checking, Toast.LENGTH_SHORT).show();
        Disposable ignored = Single.fromCallable(() -> {
            StreamCheck.Result check = StreamCheck.check(link);
            String title = TextUtils.isEmpty(name) ? StreamLinks.nameFromUrl(check.streamUrl) : name;
            return Radio.add(app, title, check.streamUrl, check.mimeType, "", "",
                    new RadioPreferences.Station("", "", "", 0, "", RadioPreferences.Quality.STANDARD));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(station -> {
                    onAdded.run();
                    RadioStationDialog.show(context, station, onAdded);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.radio_link_failed, Toast.LENGTH_LONG).show();
                });
    }

    private void showStationMenu(View anchor, FeedItem station) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.radio_edit);
        menu.getMenu().add(0, 3, 1, R.string.record_a_show);
        menu.getMenu().add(0, 2, 2, R.string.radio_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                RadioStationDialog.show(requireContext(), station, this::load);
            } else if (item.getItemId() == 3) {
                RecordShowDialog.create(requireContext(), station);
            } else {
                confirmDelete(station);
            }
            return true;
        });
        menu.show();
    }

    private void confirmDelete(FeedItem station) {
        Context app = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.radio_delete_question, station.getTitle()))
                .setPositiveButton(R.string.radio_delete, (dialog, which) ->
                        disposables.add(Single.fromCallable(() -> Radio.delete(app, station))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(deleted -> {
                                    if (!deleted) {
                                        Toast.makeText(app, R.string.radio_delete_playing, Toast.LENGTH_LONG).show();
                                    }
                                    load();
                                }, error -> Log.e(TAG, Log.getStackTraceString(error)))))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    static String details(Context context, RadioPreferences.Station details) {
        List<String> parts = new ArrayList<>();
        if (!details.country.isEmpty()) {
            parts.add(details.country);
        }
        String quality = quality(context, details.codec, details.bitrate);
        if (!quality.isEmpty()) {
            parts.add(quality);
        }
        return TextUtils.join(" · ", parts);
    }

    static String quality(Context context, String codec, int bitrate) {
        if (bitrate > 0) {
            return context.getString(R.string.radio_bitrate, bitrate, codec).trim();
        }
        return codec;
    }
}
