package de.danoeh.antennapod.smartplay.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;

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

    public void showWorld(int world) {
        if (pager != null) {
            pager.setCurrentItem(world, true);
        }
    }
}
