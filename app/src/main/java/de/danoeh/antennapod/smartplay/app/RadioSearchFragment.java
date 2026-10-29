package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;
import de.danoeh.antennapod.storage.preferences.RadioPreferences;
import de.danoeh.antennapod.ui.common.Keyboard;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RadioSearchFragment extends Fragment {
    private static final String TAG = "RadioSearch";

    private final List<RadioBrowser.Station> results = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private ResultAdapter adapter;
    private ProgressBar progress;
    private TextView message;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.librivox_search_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.radio_search_title);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        ((TextView) root.findViewById(R.id.librivox_about)).setText(R.string.radio_search_about);
        progress = root.findViewById(R.id.librivox_progress);
        message = root.findViewById(R.id.librivox_message);
        ListView list = root.findViewById(R.id.librivox_results);
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> addAndPlay(results.get(position)));
        EditText query = root.findViewById(R.id.librivox_query);
        query.setHint(R.string.radio_search_hint);
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null) {
                search(query.getText().toString());
                Keyboard.hide(getActivity());
                return true;
            }
            return false;
        });
        query.requestFocus();
        Keyboard.show(getContext(), query);
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void search(String query) {
        if (TextUtils.isEmpty(query.trim())) {
            return;
        }
        disposables.clear();
        results.clear();
        adapter.notifyDataSetChanged();
        message.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        disposables.add(Single.fromCallable(() -> RadioBrowser.search(query))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(stations -> {
                    progress.setVisibility(View.GONE);
                    results.addAll(stations);
                    adapter.notifyDataSetChanged();
                    if (stations.isEmpty()) {
                        message.setText(getString(R.string.radio_no_results, query.trim()));
                        message.setVisibility(View.VISIBLE);
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    progress.setVisibility(View.GONE);
                    message.setText(R.string.radio_search_error);
                    message.setVisibility(View.VISIBLE);
                }));
    }

    private void addAndPlay(RadioBrowser.Station found) {
        Context app = requireContext().getApplicationContext();
        Toast.makeText(app, getString(R.string.radio_adding, found.name), Toast.LENGTH_SHORT).show();
        disposables.add(Single.fromCallable(() -> {
            String mimeType = found.hls ? MediaItemAdapter.MIME_HLS : null;
            String streamUrl = found.streamUrl;
            try {
                StreamCheck.Result check = StreamCheck.check(found.streamUrl);
                streamUrl = check.streamUrl;
                mimeType = check.mimeType;
            } catch (Exception e) {
                Log.d(TAG, "Could not check " + found.streamUrl);
            }
            RadioBrowser.countClick(found.id);
            return Radio.add(app, found.name, streamUrl, mimeType, found.homepage, found.logoUrl,
                    new RadioPreferences.Station(found.country, found.countryCode, found.codec, found.bitrate,
                            found.id, RadioPreferences.Quality.STANDARD));
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(station -> {
                    RadioFragment.play(app, station);
                    if (isAdded()) {
                        getParentFragmentManager().popBackStack();
                    }
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    Toast.makeText(app, R.string.radio_link_failed, Toast.LENGTH_LONG).show();
                }));
    }

    private final class ResultAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public RadioBrowser.Station getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.librivox_result_item, parent, false);
            RadioBrowser.Station station = getItem(position);
            ((TextView) view.findViewById(R.id.librivox_book_title)).setText(station.name);
            TextView country = view.findViewById(R.id.librivox_book_author);
            country.setText(station.country);
            country.setVisibility(station.country.isEmpty() ? View.GONE : View.VISIBLE);
            ((TextView) view.findViewById(R.id.librivox_book_details))
                    .setText(RadioFragment.quality(parent.getContext(), station.codec, station.bitrate));
            ImageView logo = view.findViewById(R.id.librivox_cover);
            Glide.with(RadioSearchFragment.this).load(station.logoUrl).placeholder(R.drawable.ic_radio)
                    .error(R.drawable.ic_radio).into(logo);
            return view;
        }
    }
}
