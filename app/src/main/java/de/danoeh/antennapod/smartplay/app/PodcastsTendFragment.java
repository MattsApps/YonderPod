package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedCounter;
import de.danoeh.antennapod.model.feed.FeedOrder;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;
import de.danoeh.antennapod.ui.screen.queue.QueueFragment;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PodcastsTendFragment extends Fragment {
    private static final String TAG = "PodcastsTend";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private View root;
    private boolean updating;
    private boolean singleRefresh;
    private long updateStart;
    private long lastProgress;
    private String progressText = "";

    private static final class State {
        List<SmartPlayStore.StoredSmartPlay> smartPlays = new ArrayList<>();
        long activeId = -1;
        Map<String, List<Feed>> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Feed> uncategorised = new ArrayList<>();
        Map<Long, Integer> newCounts;
        Map<Long, Integer> savedCounts;
        Set<String> notRefreshing = new HashSet<>();
        long lastUpdate;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = TendRows.worldInflater(this, inflater, WorldSurface.PODCASTS)
                .inflate(R.layout.tend_fragment, container, false);
        TendRows.setUp(this, root, R.string.world_podcasts, R.string.tend_find_podcast,
                () -> TendRows.open(this, new FindPodcastsFragment()));
        ((TextView) root.findViewById(R.id.tendListLabel)).setText(R.string.tend_subscriptions);
        ((TextView) root.findViewById(R.id.tendListNote)).setText(R.string.tend_new_saved);
        TextView updated = root.findViewById(R.id.tendUpdated);
        updated.setVisibility(View.VISIBLE);
        updated.setOnClickListener(v -> {
            if (updating) {
                return;
            }
            updated.setText(R.string.tend_updating);
            FeedUpdateManager.getInstance().runOnceOrAsk(requireContext());
        });
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        TendRows.statusBarForWorld(this, WorldSurface.PODCASTS);
        load();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            TendRows.statusBarForWorld(this, WorldSurface.PODCASTS);
            load();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        if (updating) {
            showProgress();
        } else {
            load();
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onFeedUpdateRunning(FeedUpdateRunningEvent event) {
        if (event.isFeedUpdateRunning && !updating) {
            updateStart = System.currentTimeMillis() - 2000;
            progressText = getString(R.string.tend_updating);
        }
        updating = event.isFeedUpdateRunning;
        if (!updating) {
            singleRefresh = false;
            load();
        } else {
            showProgress();
        }
    }

    private void showProgress() {
        if (root == null) {
            return;
        }
        ((TextView) root.findViewById(R.id.tendUpdated)).setText(progressText);
        long now = System.currentTimeMillis();
        if (singleRefresh || now - lastProgress < 500) {
            return;
        }
        lastProgress = now;
        long since = updateStart;
        Context app = requireContext().getApplicationContext();
        disposables.add(Single.fromCallable(() -> {
            int total = 0;
            int done = 0;
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED || feed.isAppManaged() || feed.getPreferences() == null
                        || !feed.getPreferences().getKeepUpdated() || !UpdateScheduler.refreshesWithAll(app, feed)) {
                    continue;
                }
                total++;
                if (feed.getLastRefreshAttempt() >= since) {
                    done++;
                }
            }
            return new int[] {done, total};
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(counts -> {
                    if (!updating || singleRefresh || root == null || counts[1] == 0) {
                        return;
                    }
                    progressText = getString(R.string.tend_updating_progress, counts[0], counts[1]);
                    ((TextView) root.findViewById(R.id.tendUpdated)).setText(progressText);
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
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
            state.newCounts = DBReader.getRecentUnplayedCounts(
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
            state.savedCounts = DBReader.getNavDrawerData(null, FeedOrder.ALPHABETICAL,
                    FeedCounter.SHOW_DOWNLOADED, Feed.STATE_SUBSCRIBED).feedCounters;
            Set<Long> books = Books.bookIds(app);
            boolean booksHere = !UserPreferences.isWorldShown(WorldPages.BOOKS);
            boolean recordingsHere = !UserPreferences.isWorldShown(WorldPages.RADIO);
            for (Feed feed : DBReader.getFeedList()) {
                if (feed.getState() != Feed.STATE_SUBSCRIBED || Radio.isRadioFeed(feed)) {
                    continue;
                }
                if (Recordings.isRecordingsFeed(feed) ? !recordingsHere
                        : (feed.isLocalFeed() || books.contains(feed.getId())) && !booksHere) {
                    continue;
                }
                state.lastUpdate = Math.max(state.lastUpdate, feed.getLastRefreshAttempt());
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
            for (String category : state.groups.keySet()) {
                if (!UpdateScheduler.refreshesEachNight(app, category)) {
                    state.notRefreshing.add(category);
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
        TextView updated = root.findViewById(R.id.tendUpdated);
        if (updating) {
            updated.setText(progressText);
        } else if (state.lastUpdate > 0) {
            updated.setText(getString(R.string.tend_updated, DateUtils.getRelativeTimeSpanString(
                    state.lastUpdate, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)));
        } else {
            updated.setText(R.string.tend_never_updated);
        }
        LinearLayout top = root.findViewById(R.id.tendTop);
        top.removeAllViews();
        TextView label = new TextView(root.getContext());
        TextViewCompat.setTextAppearance(label, R.style.TextAppearance_WildInk_Label);
        label.setText(R.string.tend_smart_plays);
        label.setPadding(0, dp(24), 0, dp(8));
        top.addView(label);
        HorizontalScrollView scroller = new HorizontalScrollView(root.getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        LinearLayout blocks = new LinearLayout(root.getContext());
        blocks.setOrientation(LinearLayout.HORIZONTAL);
        LayoutInflater inflater = LayoutInflater.from(root.getContext());
        for (SmartPlayStore.StoredSmartPlay smartPlay : state.smartPlays) {
            boolean active = smartPlay.getId() == state.activeId;
            TextView block = (TextView) inflater.inflate(R.layout.tend_block, blocks, false);
            if (active) {
                TextViewCompat.setTextAppearance(block, R.style.Widget_WildInk_Block_Accent);
                block.setBackgroundResource(R.drawable.wildink_block_accent);
            }
            block.setText(smartPlay.getDefinition().getName().toLowerCase(Locale.getDefault()));
            block.setOnClickListener(v -> {
                if (active) {
                    TendRows.open(this, new QueueFragment());
                    return;
                }
                // "Playing from": only the highlight moves. The loaded episode plays on, then this one takes over.
                SmartPlayQueueManager manager = SmartPlayQueueManager.getInstance();
                if (manager != null) {
                    manager.highlight(smartPlay.getId());
                }
                v.postDelayed(this::load, 300);
            });
            block.setOnLongClickListener(v -> {
                smartPlayMenu(v, smartPlay, state.smartPlays.size());
                return true;
            });
            blocks.addView(block);
        }
        TextView add = (TextView) inflater.inflate(R.layout.tend_block, blocks, false);
        add.setBackgroundResource(android.R.color.transparent);
        add.setText(R.string.symbol_add);
        add.setTextSize(22);
        add.setContentDescription(getString(R.string.tend_new_smart_play));
        add.setOnClickListener(v -> TendRows.open(this, SmartPlayEditorFragment.newInstance(-1)));
        blocks.addView(add);
        scroller.addView(blocks);
        top.addView(scroller);

        LinearLayout list = root.findViewById(R.id.tendList);
        list.removeAllViews();
        if (!state.uncategorised.isEmpty()) {
            addGroup(list, getString(R.string.tend_uncategorised), null, state.uncategorised, state);
        }
        for (Map.Entry<String, List<Feed>> group : state.groups.entrySet()) {
            String name = Books.CATEGORY.equalsIgnoreCase(group.getKey()) ? getString(R.string.world_books)
                    : group.getKey().toLowerCase(Locale.getDefault());
            addGroup(list, name, group.getKey(), group.getValue(), state);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(root.getContext());
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_WildInk_Detail);
            empty.setText(R.string.tend_nothing_yet);
            empty.setPadding(0, dp(12), 0, 0);
            list.addView(empty);
        }
    }

    private void smartPlayMenu(View anchor, SmartPlayStore.StoredSmartPlay smartPlay, int total) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.tend_smart_play_edit);
        menu.getMenu().add(0, 1, 1, R.string.tend_smart_play_duplicate);
        menu.getMenu().add(0, 2, 2, R.string.tend_smart_play_delete).setEnabled(total > 1);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                TendRows.open(this, SmartPlayEditorFragment.newInstance(smartPlay.getId()));
            } else if (item.getItemId() == 1) {
                duplicate(smartPlay);
            } else {
                confirmDelete(smartPlay);
            }
            return true;
        });
        menu.show();
    }

    private void duplicate(SmartPlayStore.StoredSmartPlay smartPlay) {
        Context app = requireContext().getApplicationContext();
        SmartPlayDefinition original = smartPlay.getDefinition();
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(
                getString(R.string.tend_smart_play_copy_name, original.getName()));
        for (SmartPlayDefinition.PriorityFeed feed : original.getPriorityFeeds()) {
            builder.priorityFeed(feed.getFeedId(), feed.getOrder());
        }
        for (SmartPlayRule rule : original.getRules()) {
            builder.rule(rule);
        }
        SmartPlayDefinition copy = builder.neverInclude(original.getNeverInclude()).build();
        disposables.add(Completable.fromAction(() -> SmartPlayStore.getInstance(app).insert(copy))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::load, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void confirmDelete(SmartPlayStore.StoredSmartPlay smartPlay) {
        Context app = requireContext().getApplicationContext();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.tend_smart_play_delete_question, smartPlay.getDefinition().getName()))
                .setMessage(R.string.tend_smart_play_delete_message)
                .setPositiveButton(R.string.yonder_button_delete, (dialog, which) ->
                        disposables.add(Completable.fromAction(() -> {
                            if (SmartPlayStore.getInstance(app).getAll().size() <= 1) {
                                return;
                            }
                            SmartPlayStore.getInstance(app).delete(smartPlay.getId());
                            SmartPlayHours.set(app, smartPlay.getId(), null);
                            SmartPlayHours.reschedule(app);
                            SmartPlayQueueManager.getInstance().onSmartPlayDeleted(smartPlay.getId());
                        })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> root.postDelayed(this::load, 300),
                                        error -> Log.e(TAG, Log.getStackTraceString(error)))))
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    private void addGroup(LinearLayout list, String name, @Nullable String category, List<Feed> feeds, State state) {
        TextView heading = TendRows.heading(list, name);
        if (category != null) {
            heading.setOnLongClickListener(v -> {
                categoryMenu(v, category, !state.notRefreshing.contains(category));
                return true;
            });
        }
        list.addView(heading);
        for (Feed feed : feeds) {
            Integer newCount = state.newCounts.get(feed.getId());
            Integer saved = state.savedCounts.get(feed.getId());
            View row = TendRows.row(this, list, feed.getTitle(), null, feed.getImageUrl(),
                    newCount != null && newCount > 99 ? getString(R.string.tend_count_many)
                            : TendRows.count(newCount != null ? newCount : 0),
                    TendRows.count(saved != null ? saved : 0),
                    v -> TendRows.open(this, FeedItemlistFragment.newInstance(feed.getId())));
            if (ShowActions.applies(feed)) {
                row.setOnLongClickListener(v -> {
                    ShowActions.showMenu(this, v, feed, showListener);
                    return true;
                });
            }
            list.addView(row);
        }
    }

    private void categoryMenu(View anchor, String category, boolean refreshing) {
        Context app = requireContext().getApplicationContext();
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.tend_category_refresh_nightly).setCheckable(true).setChecked(refreshing);
        menu.setOnMenuItemClickListener(item -> {
            disposables.add(Completable.fromAction(() -> UpdateScheduler.setRefreshesEachNight(app, category,
                    !refreshing))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::load, error -> Log.e(TAG, Log.getStackTraceString(error))));
            return true;
        });
        menu.show();
    }

    private final ShowActions.Listener showListener = new ShowActions.Listener() {
        @Override
        public void onChanged() {
            if (root != null) {
                load();
            }
        }

        @Override
        public void onRefreshing() {
            // One show: "updating…" without the all-shows count, which would read "1 of 80".
            singleRefresh = true;
        }

        @Override
        public void onDeleting() {
        }
    };

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
