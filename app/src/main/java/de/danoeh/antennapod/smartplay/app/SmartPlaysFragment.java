package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SmartPlaysFragment extends Fragment {
    private static final String TAG = "SmartPlaysFragment";

    private final List<SmartPlayStore.StoredSmartPlay> smartPlays = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private SmartPlayListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.smartplay_list_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        toolbar.inflateMenu(R.menu.smartplay_list);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.smartplay_add) {
                openEditor(-1);
                return true;
            }
            return false;
        });

        ListView list = root.findViewById(R.id.smartplay_list);
        adapter = new SmartPlayListAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            SmartPlayQueueManager.getInstance().activate(smartPlays.get(position).getId());
            getParentFragmentManager().popBackStack();
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void load() {
        disposables.add(Observable.fromCallable(() -> SmartPlayStore.getInstance(requireContext()).getAll())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    smartPlays.clear();
                    smartPlays.addAll(result);
                    adapter.notifyDataSetChanged();
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void openEditor(long smartPlayId) {
        ((MainActivity) getActivity()).loadChildFragment(SmartPlayEditorFragment.newInstance(smartPlayId));
    }

    private void showMenu(View anchor, SmartPlayStore.StoredSmartPlay smartPlay) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.smartplay_item);
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.smartplay_edit) {
                openEditor(smartPlay.getId());
            } else if (itemId == R.id.smartplay_duplicate) {
                duplicate(smartPlay);
            } else if (itemId == R.id.smartplay_delete) {
                confirmDelete(smartPlay);
            }
            return true;
        });
        popup.show();
    }

    private void duplicate(SmartPlayStore.StoredSmartPlay smartPlay) {
        SmartPlayDefinition original = smartPlay.getDefinition();
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(
                getString(R.string.smartplay_copy_name, original.getName()));
        for (SmartPlayDefinition.PriorityFeed feed : original.getPriorityFeeds()) {
            builder.priorityFeed(feed.getFeedId(), feed.getOrder());
        }
        for (SmartPlayRule rule : original.getRules()) {
            builder.rule(rule);
        }
        SmartPlayDefinition copy = builder.build();
        disposables.add(Completable.fromAction(() -> SmartPlayStore.getInstance(requireContext()).insert(copy))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::load, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void confirmDelete(SmartPlayStore.StoredSmartPlay smartPlay) {
        if (smartPlays.size() <= 1) {
            Toast.makeText(requireContext(), R.string.smartplay_delete_last, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.smartplay_delete_confirm, smartPlay.getDefinition().getName()))
                .setPositiveButton(R.string.smartplay_delete, (dialog, which) -> delete(smartPlay))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void delete(SmartPlayStore.StoredSmartPlay smartPlay) {
        disposables.add(Completable.fromAction(() -> SmartPlayStore.getInstance(requireContext())
                        .delete(smartPlay.getId()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    SmartPlayQueueManager.getInstance().onSmartPlaysChanged();
                    load();
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private class SmartPlayListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return smartPlays.size();
        }

        @Override
        public Object getItem(int position) {
            return smartPlays.get(position);
        }

        @Override
        public long getItemId(int position) {
            return smartPlays.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smartplay_list_item, parent, false);
            }
            SmartPlayStore.StoredSmartPlay smartPlay = smartPlays.get(position);
            String name = smartPlay.getDefinition().getName();
            TextView nameView = view.findViewById(R.id.smartplay_item_name);
            if (smartPlay.getId() == SmartPlayQueueManager.getInstance().getActiveId()) {
                nameView.setText(getString(R.string.smartplay_active_name, name));
            } else {
                nameView.setText(name);
            }
            View menu = view.findViewById(R.id.smartplay_item_menu);
            menu.setOnClickListener(v -> showMenu(v, smartPlay));
            return view;
        }
    }
}
