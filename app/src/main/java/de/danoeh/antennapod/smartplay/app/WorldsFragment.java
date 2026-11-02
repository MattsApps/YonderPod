package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.ui.common.Palette;

public class WorldsFragment extends Fragment {
    public static final String TAG = "WorldsFragment";

    private ViewPager2 pager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        WorldsLayout layout = (WorldsLayout) inflater.inflate(R.layout.worlds_fragment, container, false);
        pager = layout.findViewById(R.id.worldsPager);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case WorldSurface.BOOKS:
                        return new BooksSurface();
                    case WorldSurface.RADIO:
                        return new RadioSurface();
                    case WorldSurface.PODCASTS:
                    default:
                        return new PodcastsSurface();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });
        pager.setOffscreenPageLimit(2);
        int[] grounds = new int[3];
        for (int world = 0; world < grounds.length; world++) {
            grounds[world] = Palette.forWorld(requireContext(), world).background;
        }
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                int next = Math.min(position + 1, grounds.length - 1);
                layout.setBackgroundColor(ColorUtils.blendARGB(grounds[position], grounds[next], offset));
            }

            @Override
            public void onPageSelected(int position) {
                refreshStatusBar();
            }
        });
        if (savedInstanceState == null) {
            pager.setCurrentItem(WorldSurface.PODCASTS, false);
        }
        layout.setPager(pager);
        layout.setOnSwipeUp(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openWorld(pager.getCurrentItem());
            }
        });
        float density = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(layout, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top + (int) (28 * density), bars.right, bars.bottom + (int) (8 * density));
            return insets;
        });
        return layout;
    }

    public void refreshStatusBar() {
        if (pager != null && getActivity() != null) {
            TendRows.statusBar(getActivity(), Palette.forWorld(requireContext(), pager.getCurrentItem()));
        }
    }

    public void showWorld(int world) {
        if (pager != null) {
            pager.setCurrentItem(world, true);
        }
    }
}
