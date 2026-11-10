package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.util.Consumer;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.danoeh.antennapod.R;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class ShelfPicker {
    private static final float HEIGHT_SHARE = 0.6f;

    private ShelfPicker() {
    }

    private static int availableHeight(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper && !(current instanceof Activity)) {
            current = ((ContextWrapper) current).getBaseContext();
        }
        if (current instanceof Activity) {
            View decor = ((Activity) current).getWindow().getDecorView();
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(decor);
            if (insets != null && decor.getHeight() > 0) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                return decor.getHeight() - bars.top - bars.bottom;
            }
        }
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    public static Disposable fill(View picker, Set<String> leaveOut, Consumer<AppShelves.App> onPick) {
        Context context = picker.getContext();
        EditText search = picker.findViewById(R.id.shelfSearch);
        RecyclerView list = picker.findViewById(R.id.shelfChoices);
        TextView showAll = picker.findViewById(R.id.shelfShowAll);
        boolean[] everything = {false};
        List<AppShelves.App> all = new ArrayList<>();
        List<AppShelves.App> shown = new ArrayList<>();
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.shelf_pick_row, parent, false);
                row.findViewById(R.id.shelfPickCheck).setVisibility(View.GONE);
                row.setBackgroundResource(android.R.drawable.list_selector_background);
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                AppShelves.App app = shown.get(position);
                ((ImageView) holder.itemView.findViewById(R.id.shelfPickIcon)).setImageDrawable(app.icon);
                ((TextView) holder.itemView.findViewById(R.id.shelfPickName)).setText(app.label);
                holder.itemView.setOnClickListener(v -> onPick.accept(app));
            }

            @Override
            public int getItemCount() {
                return shown.size();
            }
        };
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        Runnable filter = () -> {
            String query = search.getText().toString().trim().toLowerCase(Locale.getDefault());
            shown.clear();
            boolean hidden = false;
            for (AppShelves.App app : all) {
                if (!everything[0] && !app.media) {
                    hidden = true;
                } else if (query.isEmpty() || app.label.toString().toLowerCase(Locale.getDefault()).contains(query)) {
                    shown.add(app);
                }
            }
            showAll.setVisibility(hidden ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        };
        showAll.setOnClickListener(v -> {
            everything[0] = true;
            filter.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                filter.run();
            }
        });
        Context app = context.getApplicationContext();
        return Single.fromCallable(() -> AppShelves.launchable(app))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(apps -> {
                    all.clear();
                    for (AppShelves.App choice : apps) {
                        if (!leaveOut.contains(choice.packageName)) {
                            all.add(choice);
                        }
                    }
                    filter.run();
                }, error -> filter.run());
    }

    public static void dialog(Context context, CharSequence title, Set<String> leaveOut,
                              Consumer<AppShelves.App> onPick) {
        View picker = LayoutInflater.from(context).inflate(R.layout.shelf_picker, null, false);
        int height = Math.round(availableHeight(context) * HEIGHT_SHARE);
        picker.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        AlertDialog[] shown = new AlertDialog[1];
        Disposable loading = fill(picker, leaveOut, app -> {
            if (shown[0] != null) {
                shown[0].dismiss();
            }
            onPick.accept(app);
        });
        shown[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(picker)
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .setOnDismissListener(dialog -> loading.dispose())
                .show();
        picker.getLayoutParams().height = height;
        picker.requestLayout();
    }
}
