package de.danoeh.antennapod.smartplay.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;

public class AppShelfFragment extends Fragment {
    private static final String ARG_RIGHT = "right";

    private boolean right;
    private final List<AppShelves.App> apps = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private ItemTouchHelper touchHelper;
    private TextView hint;

    public static AppShelfFragment newInstance(boolean right) {
        AppShelfFragment fragment = new AppShelfFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_RIGHT, right);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        right = requireArguments().getBoolean(ARG_RIGHT);
        View root = inflater.inflate(R.layout.app_shelf_fragment, container, false);
        ((TextView) root.findViewById(R.id.shelfTitle))
                .setText(right ? R.string.worlds_right_edge : R.string.worlds_left_edge);
        hint = root.findViewById(R.id.shelfHint);
        apps.clear();
        apps.addAll(AppShelves.installed(requireContext(), right));
        RecyclerView list = root.findViewById(R.id.shelfApps);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setNestedScrollingEnabled(false);
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.shelf_app_row, parent, false)) {
                };
            }

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                AppShelves.App app = apps.get(position);
                View row = holder.itemView;
                ((ImageView) row.findViewById(R.id.shelfAppIcon)).setImageDrawable(app.icon);
                ((TextView) row.findViewById(R.id.shelfAppName)).setText(app.label);
                View remove = row.findViewById(R.id.shelfAppRemove);
                remove.setContentDescription(getString(R.string.shelf_remove, app.label));
                remove.setOnClickListener(v -> {
                    int index = holder.getBindingAdapterPosition();
                    if (index != RecyclerView.NO_POSITION) {
                        apps.remove(index);
                        notifyItemRemoved(index);
                        save();
                    }
                });
                View handle = row.findViewById(R.id.shelfAppHandle);
                handle.setContentDescription(getString(R.string.shelf_reorder, app.label));
                handle.setVisibility(apps.size() > 1 ? View.VISIBLE : View.INVISIBLE);
                handle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(holder);
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        };
        list.setAdapter(adapter);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromIndex = from.getBindingAdapterPosition();
                int toIndex = to.getBindingAdapterPosition();
                Collections.swap(apps, fromIndex, toIndex);
                adapter.notifyItemMoved(fromIndex, toIndex);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(recyclerView, holder);
                save();
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
            }
        });
        touchHelper.attachToRecyclerView(list);
        root.findViewById(R.id.shelfAdd).setOnClickListener(v -> pick());
        showHint();
        return root;
    }

    private void save() {
        AppShelves.save(right, apps);
        adapter.notifyItemRangeChanged(0, apps.size());
        showHint();
    }

    private void showHint() {
        String text = getString(right ? R.string.shelf_right_hint : R.string.shelf_left_hint);
        if (apps.size() == 1) {
            text += " " + getString(R.string.shelf_one_hint);
        }
        hint.setText(text);
    }

    private void pick() {
        Set<String> chosen = new HashSet<>();
        for (AppShelves.App app : apps) {
            chosen.add(app.packageName);
        }
        List<AppShelves.App> choices = new ArrayList<>();
        for (AppShelves.App app : AppShelves.launchable(requireContext())) {
            if (!chosen.contains(app.packageName)) {
                choices.add(app);
            }
        }
        if (choices.isEmpty()) {
            Toast.makeText(requireContext(), R.string.shelf_no_apps, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.shelf_pick_title)
                .setAdapter(new BaseAdapter() {
                    @Override
                    public int getCount() {
                        return choices.size();
                    }

                    @Override
                    public Object getItem(int position) {
                        return choices.get(position);
                    }

                    @Override
                    public long getItemId(int position) {
                        return position;
                    }

                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View row = convertView != null ? convertView : LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.shelf_pick_row, parent, false);
                        AppShelves.App app = choices.get(position);
                        ((ImageView) row.findViewById(R.id.shelfPickIcon)).setImageDrawable(app.icon);
                        ((TextView) row.findViewById(R.id.shelfPickName)).setText(app.label);
                        return row;
                    }
                }, (dialog, which) -> {
                    apps.add(choices.get(which));
                    adapter.notifyItemInserted(apps.size() - 1);
                    save();
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }
}
