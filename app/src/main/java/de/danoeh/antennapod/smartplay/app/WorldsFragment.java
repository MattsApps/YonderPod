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

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.ui.common.Palette;

public class WorldsFragment extends Fragment {
    public static final String TAG = "WorldsFragment";
    private static final String ARG_PAGE = "page";

    private ViewPager2 pager;
    private WorldPages pages;
    private boolean swiped = false;

    public static WorldsFragment newInstance(int page) {
        WorldsFragment fragment = new WorldsFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        WorldsLayout layout = (WorldsLayout) inflater.inflate(R.layout.worlds_fragment, container, false);
        pages = AppShelves.pages(requireContext());
        pager = layout.findViewById(R.id.worldsPager);
        pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                int page = pages.pageAt(position);
                switch (page) {
                    case WorldSurface.BOOKS:
                        return new BooksSurface();
                    case WorldSurface.RADIO:
                        return new RadioSurface();
                    case WorldPages.LEFT_SHELF:
                    case WorldPages.RIGHT_SHELF:
                        return ShelfSurface.newInstance(page);
                    case WorldSurface.PODCASTS:
                    default:
                        return new PodcastsSurface();
                }
            }

            @Override
            public int getItemCount() {
                return pages.size();
            }
        });
        pager.setOffscreenPageLimit(Math.max(1, pages.size() - 1));
        int[] grounds = new int[pages.size()];
        for (int position = 0; position < grounds.length; position++) {
            grounds[position] = Palette.forWorld(requireContext(), worldOf(pages.pageAt(position))).background;
        }
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float offset, int offsetPixels) {
                int next = Math.min(position + 1, grounds.length - 1);
                layout.setBackgroundColor(ColorUtils.blendARGB(grounds[position], grounds[next], offset));
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    swiped = true;
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    swiped = false;
                }
            }

            @Override
            public void onPageSelected(int position) {
                refreshStatusBar();
                int page = pages.pageAt(position);
                if (swiped && pages.opensDirectly(page)) {
                    openOnlyApp(page);
                }
            }
        });
        if (savedInstanceState == null) {
            int first = getArguments() != null ? getArguments().getInt(ARG_PAGE, WorldSurface.PODCASTS)
                    : WorldSurface.PODCASTS;
            if (pages.opensDirectly(first)) {
                first = pages.worldBeside(first);
            }
            pager.setCurrentItem(pages.positionOf(first), false);
        }
        layout.setPager(pager);
        layout.setOnSwipeUp(() -> {
            int page = currentPage();
            if (WorldPages.isWorld(page) && getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openWorld(page);
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

    @Override
    public void onResume() {
        super.onResume();
        if (isOutOfDate() && pager != null) {
            pager.post(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).refreshWorlds();
                }
            });
        }
    }

    private int worldOf(int page) {
        return WorldPages.isShelf(page) ? pages.worldBeside(page) : page;
    }

    private void openOnlyApp(int shelf) {
        List<AppShelves.App> apps = AppShelves.installed(requireContext(), AppShelves.isRight(shelf));
        int back = pages.positionOf(pages.worldBeside(shelf));
        if (apps.isEmpty()) {
            pager.setCurrentItem(back, true);
            return;
        }
        AppShelves.open(requireActivity(), apps.get(0), () -> pager.postDelayed(() -> {
            if (pager != null) {
                pager.setCurrentItem(back, false);
            }
        }, 400));
    }

    public int currentPage() {
        return pager != null && pages != null ? pages.pageAt(pager.getCurrentItem()) : WorldSurface.PODCASTS;
    }

    public boolean isOutOfDate() {
        return pages != null && getContext() != null && !AppShelves.pages(requireContext()).sameAs(pages);
    }

    public void refreshStatusBar() {
        if (pager != null && getActivity() != null) {
            TendRows.statusBar(getActivity(), Palette.forWorld(requireContext(), worldOf(currentPage())));
        }
    }

    public void showPage(int page) {
        if (pager == null) {
            return;
        }
        if (pages.opensDirectly(page)) {
            openOnlyApp(page);
        } else {
            pager.setCurrentItem(pages.positionOf(page), true);
        }
    }
}
