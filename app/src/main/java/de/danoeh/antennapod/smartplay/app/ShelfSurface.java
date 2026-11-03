package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import io.reactivex.rxjava3.disposables.Disposable;

public class ShelfSurface extends Fragment {
    private static final String ARG_SHELF = "shelf";

    private final List<AppShelves.App> apps = new ArrayList<>();
    private boolean right;
    private View root;
    private RecyclerView tiles;
    private Disposable picking;

    public static ShelfSurface newInstance(int shelf) {
        ShelfSurface fragment = new ShelfSurface();
        Bundle args = new Bundle();
        args.putInt(ARG_SHELF, shelf);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        int shelf = requireArguments().getInt(ARG_SHELF);
        right = AppShelves.isRight(shelf);
        WorldPages pages = AppShelves.pages(requireContext());
        root = TendRows.worldInflater(this, inflater, pages.worldBeside(shelf))
                .inflate(R.layout.world_shelf, container, false);
        ((TextView) root.findViewById(R.id.worldName)).setText(R.string.world_short_apps);
        WorldSurface.neighbours(this, root, pages, shelf);
        tiles = root.findViewById(R.id.shelfTiles);
        tiles.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        tiles.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.shelf_tile, parent, false)) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                AppShelves.App app = apps.get(position);
                ((ImageView) holder.itemView.findViewById(R.id.shelfTileIcon)).setImageDrawable(app.icon);
                ((TextView) holder.itemView.findViewById(R.id.shelfTileName)).setText(app.label);
                holder.itemView.setContentDescription(getString(R.string.shelf_open, app.label));
                holder.itemView.setOnClickListener(v -> AppShelves.open(requireActivity(), app, null));
                holder.itemView.setOnLongClickListener(v -> {
                    options(holder.getBindingAdapterPosition());
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
        show();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (picking != null) {
            picking.dispose();
            picking = null;
        }
    }

    private void show() {
        apps.clear();
        apps.addAll(AppShelves.installed(requireContext(), right));
        tiles.getAdapter().notifyDataSetChanged();
        boolean empty = apps.isEmpty();
        tiles.setVisibility(empty ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.shelfEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        if (picking != null) {
            picking.dispose();
            picking = null;
        }
        if (empty) {
            picking = ShelfPicker.fill(root.findViewById(R.id.shelfEmpty), onShelves(), app -> {
                apps.add(app);
                save();
            });
        }
    }

    private Set<String> onShelves() {
        Set<String> packages = new HashSet<>();
        for (AppShelves.App app : AppShelves.installed(requireContext(), false)) {
            packages.add(app.packageName);
        }
        for (AppShelves.App app : AppShelves.installed(requireContext(), true)) {
            packages.add(app.packageName);
        }
        return packages;
    }

    private void save() {
        AppShelves.save(right, apps);
        show();
    }

    private void options(int position) {
        if (position < 0 || position >= apps.size()) {
            return;
        }
        AppShelves.App app = apps.get(position);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.shelf_change));
        actions.add(() -> ShelfPicker.dialog(requireContext(), getString(R.string.shelf_change_title, app.label),
                onShelves(), chosen -> {
                    apps.set(position, chosen);
                    save();
                }));
        if (position > 0) {
            labels.add(getString(R.string.shelf_move_earlier));
            actions.add(() -> {
                apps.add(position - 1, apps.remove(position));
                save();
            });
        }
        if (position < apps.size() - 1) {
            labels.add(getString(R.string.shelf_move_later));
            actions.add(() -> {
                apps.add(position + 1, apps.remove(position));
                save();
            });
        }
        labels.add(getString(right ? R.string.shelf_move_to_left : R.string.shelf_move_to_right));
        actions.add(() -> {
            List<AppShelves.App> other = AppShelves.installed(requireContext(), !right);
            other.add(apps.remove(position));
            AppShelves.save(!right, other);
            save();
        });
        labels.add(getString(R.string.shelf_remove, app.label));
        actions.add(() -> {
            apps.remove(position);
            save();
        });
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(app.label)
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }
}
