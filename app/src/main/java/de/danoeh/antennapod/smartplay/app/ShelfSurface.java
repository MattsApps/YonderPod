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

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;

public class ShelfSurface extends Fragment {
    private static final String ARG_SHELF = "shelf";

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
        WorldPages pages = AppShelves.pages(requireContext());
        View root = TendRows.worldInflater(this, inflater, pages.worldBeside(shelf))
                .inflate(R.layout.world_shelf, container, false);
        ((TextView) root.findViewById(R.id.worldName)).setText(R.string.world_short_apps);
        WorldSurface.neighbours(this, root, pages, shelf);
        List<AppShelves.App> apps = AppShelves.installed(requireContext(), AppShelves.isRight(shelf));
        RecyclerView tiles = root.findViewById(R.id.shelfTiles);
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
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
        return root;
    }
}
