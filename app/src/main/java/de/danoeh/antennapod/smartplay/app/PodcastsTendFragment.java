package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.NavDrawerData;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PodcastsTendFragment extends Fragment {
    private static final String TAG = "PodcastsTend";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;

    private static final class State {
        List<SmartPlayStore.StoredSmartPlay> smartPlays = new ArrayList<>();
        long activeId = -1;
        Map<String, List<Feed>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Feed> uncategorised = new ArrayList<>();
        Map<Long, Integer> newCounts;
        Map<Long, Integer> savedCounts;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.tend_fragment, container, false);
        TendRows.setUp(this, root, R.string.world_podcasts, R.string.tend_find_podcast,
                () -> TendRows.open(this, new FindPodcastsFragment()));
        ((TextView) root.findViewById(R.id.tendListLabel)).setText(R.string.tend_subscriptions);
        ((TextView) root.findViewById(R.id.tendListNote)).setText(R.string.tend_new_saved);
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
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            State state = new State();
            state.smartPlays = SmartPlayStore.getInstance(app).getAll();
            SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
            state.activeId = manager != null ? manager.getActiveId() : -1;
            NavDrawerData data = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL, FeedCounter.SHOW_NEW,
                    Feed.STATE_SUBSCRIBED);
            state.newCounts = data.feedCounters;
            state.savedCounts = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL,
                    FeedCounter.SHOW_DOWNLOADED, Feed.STATE_SUBSCRIBED).feedCounters;
            Set<Long> books = Books.bookIds(app);
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED || feed.isLocalFeed() || books.contains(feed.getId())
                        || Radio.isRadioFeed(feed)) {
                    continue;
                }
                List<String> categories = CategoryChips.categoriesOf(feed);
                if (categories.isEmpty()) {
                    state.uncategorised.add(feed);
                }
                for (String category : categories) {
                    List<Feed> group = state.groups.get(category);
                    if (group == null) {
                        group = new ArrayList<>();
                        state.groups.put(category, group);
                    }
                    group.add(feed);
                }
            }
            return state;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::show, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void show(State state) {
        if (root == null) {
            return;
        }
        LinearLayout top = root.findViewById(R.id.tendTop);
        top.removeAllViews();
        TextView label = new TextView(requireContext());
        TextViewCompat.setTextAppearance(label, R.style.TextAppearance_WildInk_Label);
        label.setText(R.string.tend_smart_plays);
        label.setPadding(0, dp(24), 0, dp(8));
        top.addView(label);
        HorizontalScrollView scroller = new HorizontalScrollView(requireContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        LinearLayout blocks = new LinearLayout(requireContext());
        blocks.setOrientation(LinearLayout.HORIZONTAL);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (SmartPlayStore.StoredSmartPlay smartPlay : state.smartPlays) {
            boolean active = smartPlay.getId() == state.activeId;
            TextView block = (TextView) inflater.inflate(R.layout.tend_block, blocks, false);
            if (active) {
                TextViewCompat.setTextAppearance(block, R.style.Widget_WildInk_Block_Accent);
                block.setBackgroundResource(R.drawable.wildink_block_accent);
            }
            block.setText(smartPlay.getDefinition().getName().toLowerCase(Locale.getDefault()));
            block.setOnClickListener(v -> {
                SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
                if (manager != null) {
                    manager.activate(smartPlay.getId());
                }
                v.postDelayed(this::load, 300);
            });
            block.setOnLongClickListener(v -> {
                TendRows.open(this, SmartPlayEditorFragment.newInstance(smartPlay.getId()));
                return true;
            });
            blocks.addView(block);
        }
        TextView add = (TextView) inflater.inflate(R.layout.tend_block, blocks, false);
        add.setBackgroundResource(android.R.color.transparent);
        add.setText("+");
        add.setTextSize(22);
        add.setContentDescription(getString(R.string.tend_new_smart_play));
        add.setOnClickListener(v -> TendRows.open(this, SmartPlayEditorFragment.newInstance(-1)));
        blocks.addView(add);
        scroller.addView(blocks);
        top.addView(scroller);

        LinearLayout list = root.findViewById(R.id.tendList);
        list.removeAllViews();
        if (!state.uncategorised.isEmpty()) {
            addGroup(list, getString(R.string.tend_uncategorised), state.uncategorised, state);
        }
        for (Map.Entry<String, List<Feed>> group : state.groups.entrySet()) {
            addGroup(list, group.getKey().toLowerCase(Locale.getDefault()), group.getValue(), state);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(requireContext());
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_WildInk_Detail);
            empty.setText(R.string.tend_nothing_yet);
            empty.setPadding(0, dp(12), 0, 0);
            list.addView(empty);
        }
    }

    private void addGroup(LinearLayout list, String name, List<Feed> feeds, State state) {
        list.addView(TendRows.heading(list, name));
        for (Feed feed : feeds) {
            Integer newCount = state.newCounts.get(feed.getId());
            Integer saved = state.savedCounts.get(feed.getId());
            list.addView(TendRows.row(this, list, feed.getTitle(), null, feed.getImageUrl(),
                    TendRows.count(newCount != null ? newCount : 0), TendRows.count(saved != null ? saved : 0),
                    v -> TendRows.open(this, FeedItemlistFragment.newInstance(feed.getId()))));
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
