package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.PlayerStatusEvent;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.playback.service.PlaybackServiceStarter;
import de.danoeh.antennapod.smartplay.radio.StreamLinks;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioFragment extends Fragment {
    public static final String TAG = "RadioFragment";

    private final List<FeedItem> stations = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private StationAdapter adapter;
    private TextView empty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.radio_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar,
                getParentFragmentManager().getBackStackEntryCount() != 0);
        toolbar.inflateMenu(R.menu.radio);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.radio_add) {
                chooseHowToAdd();
                return true;
            }
            return false;
        });
        empty = root.findViewById(R.id.radio_empty);
        ListView list = root.findViewById(R.id.radio_list);
        adapter = new StationAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> play(requireContext(), stations.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showStationMenu(view, stations.get(position));
            return true;
        });
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
    public void onPlayerStatusChanged(PlayerStatusEvent event) {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public static void play(Context context, FeedItem station) {
        if (station.getMedia() != null) {
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
                    adapter.notifyDataSetChanged();
                    empty.setVisibility(stations.isEmpty() ? View.VISIBLE : View.GONE);
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void chooseHowToAdd() {
        String[] choices = {getString(R.string.radio_add_search), getString(R.string.radio_add_link)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.radio_add)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        ((MainActivity) getActivity()).loadChildFragment(new RadioSearchFragment());
                    } else {
                        showAddLink();
                    }
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void showAddLink() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.radio_add_link_dialog, null);
        EditText url = view.findViewById(R.id.link_url);
        EditText name = view.findViewById(R.id.link_name);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.radio_add_link)
                .setView(view)
                .setPositiveButton(R.string.radio_add_button, (dialog, which) ->
                        addLink(url.getText().toString().trim(), name.getText().toString().trim()))
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private void addLink(String link, String name) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            Toast.makeText(getContext(), R.string.radio_link_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        Context app = requireContext().getApplicationContext();
        Toast.makeText(getContext(), R.string.radio_checking, Toast.LENGTH_SHORT).show();
        disposables.add(Single.fromCallable(() -> {
            StreamCheck.Result check = StreamCheck.check(link);
            String title = TextUtils.isEmpty(name) ? StreamLinks.nameFromUrl(check.streamUrl) : name;
            return Radio.add(app, title, check.streamUrl, check.mimeType, "", "",
                    new RadioPreferences.Station("", "", "", 0, "", RadioPreferences.Quality.STANDARD));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(station -> {
                    load();
                    if (isAdded()) {
                        RadioStationDialog.show(requireContext(), station, this::load);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.radio_link_failed, Toast.LENGTH_LONG).show();
                }));
    }

    private void showStationMenu(View anchor, FeedItem station) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.radio_edit);
        menu.getMenu().add(0, 2, 1, R.string.radio_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                RadioStationDialog.show(requireContext(), station, this::load);
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

    private final class StationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return stations.size();
        }

        @Override
        public FeedItem getItem(int position) {
            return stations.get(position);
        }

        @Override
        public long getItemId(int position) {
            return stations.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.librivox_result_item, parent, false);
            FeedItem station = getItem(position);
            ((TextView) view.findViewById(R.id.librivox_book_title)).setText(station.getTitle());
            TextView playing = view.findViewById(R.id.librivox_book_author);
            playing.setText(R.string.radio_playing);
            playing.setVisibility(Radio.isPlaying(station) ? View.VISIBLE : View.GONE);
            ((TextView) view.findViewById(R.id.librivox_book_details))
                    .setText(details(parent.getContext(), RadioPreferences.get(station.getId())));
            ImageView logo = view.findViewById(R.id.librivox_cover);
            Glide.with(RadioFragment.this).load(station.getImageUrl()).placeholder(R.drawable.ic_radio)
                    .error(R.drawable.ic_radio).into(logo);
            return view;
        }
    }
}
