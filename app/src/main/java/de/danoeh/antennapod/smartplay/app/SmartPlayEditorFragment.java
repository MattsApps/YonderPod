package de.danoeh.antennapod.smartplay.app;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.smartplay.EpisodeSource;
import de.danoeh.antennapod.smartplay.SmartPlayDefinition;
import de.danoeh.antennapod.smartplay.SmartPlayRule;
import de.danoeh.antennapod.storage.database.DBReader;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SmartPlayEditorFragment extends Fragment {
    private static final String TAG = "SmartPlayEditor";
    private static final String ARG_ID = "smartPlayId";
    private static final int MAX_COUNT = 20;
    private static final SmartPlayRule.Order[] ORDERS = {
        SmartPlayRule.Order.LATEST, SmartPlayRule.Order.OLDEST, SmartPlayRule.Order.RANDOM
    };
    private static final SmartPlayDefinition.PriorityOrder[] PRIORITY_ORDERS = {
        SmartPlayDefinition.PriorityOrder.OLDEST_FIRST, SmartPlayDefinition.PriorityOrder.LATEST_FIRST
    };

    private final List<RuleDraft> rules = new ArrayList<>();
    private final List<PriorityDraft> priorities = new ArrayList<>();
    private final List<SourceOption> sourceOptions = new ArrayList<>();
    private final List<Feed> feeds = new ArrayList<>();
    private final Map<Long, String> allFeedTitles = new HashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    private long smartPlayId;
    private EditText nameView;
    private TextView coverageWarning;
    private LinearLayout priorityContainer;
    private RulesAdapter rulesAdapter;
    private ItemTouchHelper touchHelper;
    private boolean loaded = false;

    private static final class RuleDraft {
        SmartPlayRule.Order order = SmartPlayRule.Order.LATEST;
        int count = 1;
        EpisodeSource source = EpisodeSource.allFeeds();
    }

    private static final class PriorityDraft {
        long feedId;
        SmartPlayDefinition.PriorityOrder order = SmartPlayDefinition.PriorityOrder.OLDEST_FIRST;
    }

    private static final class SourceOption {
        final String label;
        final EpisodeSource source;

        SourceOption(String label, EpisodeSource source) {
            this.label = label;
            this.source = source;
        }

        boolean matches(EpisodeSource other) {
            if (other.getType() != source.getType()) {
                return false;
            }
            switch (source.getType()) {
                case FEED:
                    return other.getFeedId() == source.getFeedId();
                case CATEGORY:
                    return source.getCategory().equals(other.getCategory());
                default:
                    return true;
            }
        }
    }

    private static final class LoadResult {
        SmartPlayStore.StoredSmartPlay stored;
        List<Feed> feeds;
        Map<Long, String> allTitles;
    }

    public static SmartPlayEditorFragment newInstance(long smartPlayId) {
        SmartPlayEditorFragment fragment = new SmartPlayEditorFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_ID, smartPlayId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        smartPlayId = getArguments() != null ? getArguments().getLong(ARG_ID, -1) : -1;
        View root = inflater.inflate(R.layout.smartplay_editor_fragment, container, false);
        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        ((MainActivity) getActivity()).setupToolbarToggle(toolbar, true);
        toolbar.inflateMenu(R.menu.smartplay_editor);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.smartplay_save) {
                save();
                return true;
            }
            return false;
        });

        nameView = root.findViewById(R.id.smartplay_name);
        coverageWarning = root.findViewById(R.id.smartplay_coverage_warning);
        priorityContainer = root.findViewById(R.id.smartplay_priority_container);

        RecyclerView rulesView = root.findViewById(R.id.smartplay_rules);
        rulesView.setLayoutManager(new LinearLayoutManager(getContext()));
        rulesAdapter = new RulesAdapter();
        rulesView.setAdapter(rulesAdapter);
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPosition = from.getBindingAdapterPosition();
                int toPosition = to.getBindingAdapterPosition();
                if (fromPosition < 0 || toPosition < 0) {
                    return false;
                }
                Collections.swap(rules, fromPosition, toPosition);
                rulesAdapter.notifyItemMoved(fromPosition, toPosition);
                updateCoverageWarning();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                rulesAdapter.notifyDataSetChanged();
            }
        });
        touchHelper.attachToRecyclerView(rulesView);

        root.findViewById(R.id.smartplay_add_rule).setOnClickListener(v -> {
            rules.add(new RuleDraft());
            rulesAdapter.notifyItemInserted(rules.size() - 1);
            updateCoverageWarning();
        });
        root.findViewById(R.id.smartplay_add_priority).setOnClickListener(v -> pickPriorityShow());

        load();
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }

    private void load() {
        disposables.add(Observable.fromCallable(() -> {
            LoadResult result = new LoadResult();
            result.stored = smartPlayId >= 0 ? SmartPlayStore.getInstance(requireContext()).get(smartPlayId) : null;
            List<Feed> subscribed = new ArrayList<>();
            result.allTitles = new HashMap<>();
            for (Feed feed : DBReader.getFeedList()) {
                result.allTitles.put(feed.getId(), title(feed));
                if (feed.getState() == Feed.STATE_SUBSCRIBED) {
                    subscribed.add(feed);
                }
            }
            Collections.sort(subscribed, (a, b) -> title(a).compareToIgnoreCase(title(b)));
            result.feeds = subscribed;
            return result;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onLoaded, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    @SuppressLint("NotifyDataSetChanged")
    private void onLoaded(LoadResult result) {
        feeds.clear();
        feeds.addAll(result.feeds);
        allFeedTitles.clear();
        allFeedTitles.putAll(result.allTitles);
        sourceOptions.clear();
        sourceOptions.add(new SourceOption(getString(R.string.smartplay_source_all), EpisodeSource.allFeeds()));
        Map<String, Integer> categoryCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Feed feed : feeds) {
            for (String category : CategoryChips.categoriesOf(feed)) {
                Integer count = categoryCounts.get(category);
                categoryCounts.put(category, count == null ? 1 : count + 1);
            }
        }
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            sourceOptions.add(new SourceOption(getResources().getQuantityString(R.plurals.smartplay_category_option,
                    entry.getValue(), entry.getKey(), entry.getValue()), EpisodeSource.category(entry.getKey())));
        }
        for (Feed feed : feeds) {
            sourceOptions.add(new SourceOption(title(feed), EpisodeSource.feed(feed.getId())));
        }

        rules.clear();
        priorities.clear();
        if (result.stored != null) {
            SmartPlayDefinition definition = result.stored.getDefinition();
            nameView.setText(definition.getName());
            for (SmartPlayRule rule : definition.getRules()) {
                RuleDraft draft = new RuleDraft();
                draft.order = rule.getOrder();
                draft.count = rule.getCount();
                draft.source = rule.getSource();
                rules.add(draft);
                ensureSourceOption(rule.getSource());
            }
            for (SmartPlayDefinition.PriorityFeed feed : definition.getPriorityFeeds()) {
                PriorityDraft draft = new PriorityDraft();
                draft.feedId = feed.getFeedId();
                draft.order = feed.getOrder();
                priorities.add(draft);
            }
        } else {
            nameView.setText(R.string.smartplay_new_name);
            rules.add(new RuleDraft());
        }
        loaded = true;
        rulesAdapter.notifyDataSetChanged();
        showPriorities();
        updateCoverageWarning();
    }

    private void updateCoverageWarning() {
        if (!loaded || coverageWarning == null) {
            return;
        }
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder("check");
        for (PriorityDraft draft : priorities) {
            builder.priorityFeed(draft.feedId, draft.order);
        }
        boolean pointsAtUnsubscribed = false;
        for (RuleDraft draft : rules) {
            builder.rule(SmartPlayRule.of(draft.order, draft.count, draft.source));
            if (draft.source.getType() == EpisodeSource.Type.FEED && !isSubscribed(draft.source.getFeedId())) {
                pointsAtUnsubscribed = true;
            }
        }
        SmartPlayDefinition definition = builder.build();
        List<String> uncovered = new ArrayList<>();
        for (Feed feed : feeds) {
            if (!definition.coversFeed(feed.getId(), new HashSet<>(CategoryChips.categoriesOf(feed)))) {
                uncovered.add(title(feed));
            }
        }
        StringBuilder text = new StringBuilder();
        if (!uncovered.isEmpty()) {
            text.append(getResources().getQuantityString(R.plurals.smartplay_uncovered_shows,
                    uncovered.size(), uncovered.size(), TextUtils.join(", ", uncovered)));
        }
        if (pointsAtUnsubscribed) {
            if (text.length() > 0) {
                text.append("\n");
            }
            text.append(getString(R.string.smartplay_rule_unsubscribed));
        }
        coverageWarning.setText(text.toString());
        coverageWarning.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private boolean isSubscribed(long feedId) {
        for (Feed feed : feeds) {
            if (feed.getId() == feedId) {
                return true;
            }
        }
        return false;
    }

    private void ensureSourceOption(EpisodeSource source) {
        for (SourceOption option : sourceOptions) {
            if (option.matches(source)) {
                return;
            }
        }
        String label;
        if (source.getType() == EpisodeSource.Type.CATEGORY) {
            label = getResources().getQuantityString(R.plurals.smartplay_category_option, 0,
                    source.getCategory(), 0);
        } else if (allFeedTitles.containsKey(source.getFeedId())) {
            label = getString(R.string.smartplay_unsubscribed_show, allFeedTitles.get(source.getFeedId()));
        } else {
            label = getString(R.string.smartplay_unknown_show);
        }
        sourceOptions.add(new SourceOption(label, source));
    }

    private int sourceIndex(EpisodeSource source) {
        for (int i = 0; i < sourceOptions.size(); i++) {
            if (sourceOptions.get(i).matches(source)) {
                return i;
            }
        }
        return 0;
    }

    private static String title(Feed feed) {
        String title = feed.getTitle();
        return title != null ? title : "";
    }

    private String feedTitle(long feedId) {
        for (Feed feed : feeds) {
            if (feed.getId() == feedId) {
                return title(feed);
            }
        }
        if (allFeedTitles.containsKey(feedId)) {
            return getString(R.string.smartplay_unsubscribed_show, allFeedTitles.get(feedId));
        }
        return getString(R.string.smartplay_unknown_show);
    }

    private ArrayAdapter<String> spinnerAdapter(List<String> labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private List<String> orderLabels() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.smartplay_order_latest));
        labels.add(getString(R.string.smartplay_order_oldest));
        labels.add(getString(R.string.smartplay_order_random));
        return labels;
    }

    private List<String> countLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= MAX_COUNT; i++) {
            labels.add(getResources().getQuantityString(R.plurals.smartplay_episode_count, i, i));
        }
        return labels;
    }

    private List<String> sourceLabels() {
        List<String> labels = new ArrayList<>();
        for (SourceOption option : sourceOptions) {
            labels.add(option.label);
        }
        return labels;
    }

    private void showPriorities() {
        priorityContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<String> orderLabels = new ArrayList<>();
        orderLabels.add(getString(R.string.smartplay_priority_oldest));
        orderLabels.add(getString(R.string.smartplay_priority_newest));
        for (PriorityDraft draft : priorities) {
            View row = inflater.inflate(R.layout.smartplay_priority_item, priorityContainer, false);
            ((TextView) row.findViewById(R.id.priority_title)).setText(feedTitle(draft.feedId));
            Spinner order = row.findViewById(R.id.priority_order);
            order.setAdapter(spinnerAdapter(orderLabels));
            order.setSelection(draft.order == SmartPlayDefinition.PriorityOrder.OLDEST_FIRST ? 0 : 1, false);
            order.setOnItemSelectedListener(new SimpleSelectionListener(position ->
                    draft.order = PRIORITY_ORDERS[position]));
            row.findViewById(R.id.priority_remove).setOnClickListener(v -> {
                priorities.remove(draft);
                showPriorities();
                updateCoverageWarning();
            });
            priorityContainer.addView(row);
        }
    }

    private void pickPriorityShow() {
        if (!loaded || feeds.isEmpty()) {
            return;
        }
        String[] titles = new String[feeds.size()];
        for (int i = 0; i < feeds.size(); i++) {
            titles[i] = title(feeds.get(i));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.smartplay_add_priority)
                .setItems(titles, (dialog, which) -> {
                    long feedId = feeds.get(which).getId();
                    for (PriorityDraft existing : priorities) {
                        if (existing.feedId == feedId) {
                            return;
                        }
                    }
                    PriorityDraft draft = new PriorityDraft();
                    draft.feedId = feedId;
                    priorities.add(draft);
                    showPriorities();
                    updateCoverageWarning();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void save() {
        if (!loaded) {
            return;
        }
        String name = nameView.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.smartplay_name_missing, Toast.LENGTH_LONG).show();
            return;
        }
        if (rules.isEmpty() && priorities.isEmpty()) {
            Toast.makeText(requireContext(), R.string.smartplay_needs_rule, Toast.LENGTH_LONG).show();
            return;
        }
        SmartPlayDefinition.Builder builder = SmartPlayDefinition.builder(name);
        for (PriorityDraft draft : priorities) {
            builder.priorityFeed(draft.feedId, draft.order);
        }
        for (RuleDraft draft : rules) {
            builder.rule(SmartPlayRule.of(draft.order, draft.count, draft.source));
        }
        SmartPlayDefinition definition = builder.build();
        boolean isActive = smartPlayId >= 0 && smartPlayId == SmartPlayQueueManager.getInstance().getActiveId();
        disposables.add(Observable.fromCallable(() -> {
            SmartPlayStore store = SmartPlayStore.getInstance(requireContext());
            if (smartPlayId >= 0) {
                store.update(smartPlayId, definition);
                return smartPlayId;
            }
            return store.insert(definition);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(id -> {
                    SmartPlayQueueManager.getInstance().onSmartPlaysChanged();
                    if (isActive) {
                        askToRebuild();
                    } else {
                        getParentFragmentManager().popBackStack();
                    }
                }, error -> Log.e(TAG, Log.getStackTraceString(error))));
    }

    private void askToRebuild() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.smartplay_rebuild_question)
                .setMessage(R.string.smartplay_rebuild_question_message)
                .setPositiveButton(R.string.smartplay_rebuild_now, (dialog, which) ->
                        SmartPlayQueueManager.getInstance().rebuildNow())
                .setNegativeButton(R.string.smartplay_later, null)
                .setOnDismissListener(dialog -> {
                    if (isAdded()) {
                        getParentFragmentManager().popBackStack();
                    }
                })
                .show();
    }

    private interface PositionCallback {
        void onSelected(int position);
    }

    private final class SimpleSelectionListener implements AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;

        SimpleSelectionListener(PositionCallback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            callback.onSelected(position);
            updateCoverageWarning();
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private final class RuleHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final Spinner order;
        final Spinner count;
        final Spinner source;

        @SuppressLint("ClickableViewAccessibility")
        RuleHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.rule_title);
            order = itemView.findViewById(R.id.rule_order);
            count = itemView.findViewById(R.id.rule_count);
            source = itemView.findViewById(R.id.rule_source);
            itemView.findViewById(R.id.rule_drag).setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(this);
                }
                return false;
            });
            itemView.findViewById(R.id.rule_delete).setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position >= 0) {
                    rules.remove(position);
                    rulesAdapter.notifyDataSetChanged();
                    updateCoverageWarning();
                }
            });
        }
    }

    private final class RulesAdapter extends RecyclerView.Adapter<RuleHolder> {
        @NonNull
        @Override
        public RuleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.smartplay_rule_item, parent, false);
            return new RuleHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RuleHolder holder, int position) {
            RuleDraft draft = rules.get(position);
            holder.title.setText(getString(position == 0 ? R.string.smartplay_rule_start
                    : R.string.smartplay_rule_then, position + 1));

            holder.order.setOnItemSelectedListener(null);
            holder.order.setAdapter(spinnerAdapter(orderLabels()));
            holder.order.setSelection(draft.order.ordinal(), false);
            holder.order.setOnItemSelectedListener(new SimpleSelectionListener(selected ->
                    draft.order = ORDERS[selected]));

            holder.count.setOnItemSelectedListener(null);
            holder.count.setAdapter(spinnerAdapter(countLabels()));
            holder.count.setSelection(Math.min(draft.count, MAX_COUNT) - 1, false);
            holder.count.setOnItemSelectedListener(new SimpleSelectionListener(selected ->
                    draft.count = selected + 1));

            holder.source.setOnItemSelectedListener(null);
            holder.source.setAdapter(spinnerAdapter(sourceLabels()));
            holder.source.setSelection(sourceIndex(draft.source), false);
            holder.source.setOnItemSelectedListener(new SimpleSelectionListener(selected ->
                    draft.source = sourceOptions.get(selected).source));
        }

        @Override
        public int getItemCount() {
            return rules.size();
        }
    }
}
