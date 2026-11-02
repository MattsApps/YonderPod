package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.util.Locale;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.common.CustomColours;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.common.ThemeSwitcher;
import de.danoeh.antennapod.ui.screen.PlaybackHistoryFragment;
import de.danoeh.antennapod.ui.screen.download.CompletedDownloadsFragment;

public final class TendRows {
    private TendRows() {
    }

    public static View setUp(Fragment fragment, View root, int world, int hint, Runnable onSearch) {
        ((TextView) root.findViewById(R.id.tendWorld)).setText(world);
        ((TextView) root.findViewById(R.id.tendSearchHint)).setText(hint);
        root.findViewById(R.id.tendSearch).setOnClickListener(v -> onSearch.run());
        Runnable toSurface = () -> {
            if (fragment.getActivity() instanceof MainActivity) {
                ((MainActivity) fragment.getActivity()).showWorlds();
            }
        };
        root.findViewById(R.id.tendHandle).setOnClickListener(v -> toSurface.run());
        ((TendScrollView) root.findViewById(R.id.tendScroll)).setOnPullDown(toSurface);
        root.findViewById(R.id.tendHistory).setOnClickListener(v ->
                open(fragment, new PlaybackHistoryFragment()));
        root.findViewById(R.id.tendDownloads).setOnClickListener(v ->
                open(fragment, new CompletedDownloadsFragment()));
        root.findViewById(R.id.tendTune).setOnClickListener(v -> open(fragment, new TuneFragment()));
        return root;
    }

    public static LayoutInflater worldInflater(Fragment fragment, LayoutInflater inflater, int world) {
        Context context = fragment.requireContext();
        return inflater.cloneInContext(new ContextThemeWrapper(context, ThemeSwitcher.getWorldTheme(context, world)));
    }

    public static void statusBar(@Nullable Activity activity, Palette palette) {
        if (activity == null) {
            return;
        }
        boolean light = CustomColours.isLight(palette.background);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(),
                activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    public static void statusBarForWorld(Fragment fragment, int world) {
        if (fragment.getActivity() != null && !fragment.isHidden()) {
            statusBar(fragment.getActivity(), Palette.forWorld(fragment.requireContext(), world));
        }
    }

    public static void open(Fragment fragment, Fragment child) {
        if (fragment.getActivity() instanceof MainActivity) {
            ((MainActivity) fragment.getActivity()).loadChildFragment(child);
        }
    }

    public static View row(Fragment fragment, ViewGroup parent, String title, @Nullable String meta,
                           @Nullable String artUrl, String newCount, String saved, View.OnClickListener onClick) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.tend_row, parent, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView metaView = row.findViewById(R.id.rowMeta);
        metaView.setText(meta);
        metaView.setVisibility(meta == null || meta.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) row.findViewById(R.id.rowInitials)).setText(initials(title));
        ((TextView) row.findViewById(R.id.rowNew)).setText(newCount);
        ((TextView) row.findViewById(R.id.rowSaved)).setText(saved);
        ImageView art = row.findViewById(R.id.rowArt);
        if (artUrl != null && !artUrl.isEmpty()) {
            Glide.with(fragment).load(artUrl).into(art);
        }
        row.setOnClickListener(onClick);
        return row;
    }

    public static TextView heading(ViewGroup parent, String text) {
        TextView heading = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.tend_heading, parent, false);
        heading.setText(text);
        return heading;
    }

    public static String initials(@Nullable String title) {
        if (title == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (String word : title.trim().split("\\s+")) {
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0))) {
                result.append(word.charAt(0));
            }
            if (result.length() == 2) {
                break;
            }
        }
        return result.toString().toUpperCase(Locale.getDefault());
    }

    public static String count(int value) {
        return value > 0 ? String.valueOf(value) : "";
    }
}
