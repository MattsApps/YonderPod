package de.danoeh.antennapod.ui.screen.subscriptions;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.SelectableAdapter;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter for subscriptions
 */
public class SubscriptionsRecyclerAdapter extends SelectableAdapter<SubscriptionViewHolder> {
    private static final long GROUP_ID_FACTOR = 1000;

    private final WeakReference<MainActivity> mainActivityRef;
    private List<Object> listItems;
    private List<Long> rowIds;
    private Map<Long, Integer> feedCounters;
    private int columnCount = 3;
    private OnCategoryHeaderClickListener headerClickListener;

    public interface OnCategoryHeaderClickListener {
        void onCategoryHeaderClick(CategoryHeader header);
    }

    public static class CategoryHeader {
        public final String key;
        public final String title;
        public final int count;
        public final boolean collapsed;

        public CategoryHeader(String key, String title, int count, boolean collapsed) {
            this.key = key;
            this.title = title;
            this.count = count;
            this.collapsed = collapsed;
        }
    }

    public SubscriptionsRecyclerAdapter(MainActivity mainActivity) {
        super(mainActivity);
        this.mainActivityRef = new WeakReference<>(mainActivity);
        this.listItems = new ArrayList<>();
        this.rowIds = new ArrayList<>();
        this.feedCounters = Map.of();
        setHasStableIds(true);
    }

    public void setOnCategoryHeaderClickListener(OnCategoryHeaderClickListener listener) {
        this.headerClickListener = listener;
    }

    public boolean isHeader(int position) {
        return position >= 0 && position < listItems.size() && listItems.get(position) instanceof CategoryHeader;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public Object getItem(int position) {
        return listItems.get(position);
    }

    @NonNull
    @Override
    public SubscriptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == R.id.view_type_subscription_category_header) {
            View itemView = LayoutInflater.from(mainActivityRef.get())
                    .inflate(R.layout.subscription_category_header, parent, false);
            return new CategoryHeaderViewHolder(itemView, mainActivityRef.get());
        }
        if (viewType == R.id.view_type_subscription_list) {
            View itemView = LayoutInflater.from(mainActivityRef.get())
                    .inflate(R.layout.subscription_list_item, parent, false);
            return new SubscriptionViewHolder(itemView, mainActivityRef.get());
        }
        View itemView = LayoutInflater.from(mainActivityRef.get())
                .inflate(R.layout.subscription_grid_item, parent, false);
        itemView.findViewById(R.id.titleLabel).setVisibility(
                viewType == R.id.view_type_subscription_grid_with_title ? View.VISIBLE : View.GONE);
        return new SubscriptionViewHolder(itemView, mainActivityRef.get());
    }

    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position) {
        if (holder instanceof CategoryHeaderViewHolder) {
            CategoryHeader header = (CategoryHeader) listItems.get(position);
            ((CategoryHeaderViewHolder) holder).headerTitle.setText(holder.itemView.getContext().getString(
                    header.collapsed ? R.string.smartplay_category_header_collapsed
                            : R.string.smartplay_category_header_expanded, header.title, header.count));
            holder.itemView.setOnClickListener(v -> {
                if (headerClickListener != null) {
                    headerClickListener.onCategoryHeaderClick(header);
                }
            });
            return;
        }
        Feed feed = (Feed) listItems.get(position);
        holder.bind(feed, columnCount, feedCounters.containsKey(feed.getId()) ? feedCounters.get(feed.getId()) : 0);
        int cardMargin = 0;
        if (inActionMode()) {
            if (holder.selectIcon != null) {
                holder.selectIcon.setVisibility(View.VISIBLE);
                holder.gradient.setVisibility(View.VISIBLE);
                holder.itemView.setSelected(isSelected(position));
                holder.selectIcon.setImageResource(isSelected(position)
                        ? R.drawable.circle_checked : R.drawable.circle_unchecked);
                cardMargin = isSelected(position) ? (int) convertDpToPixel(
                        holder.itemView.getContext(), 12f) : 0;
                holder.count.setVisibility(View.GONE);
            } else {
                holder.itemView.setSelected(isSelected(position));
                holder.itemView.setBackgroundResource(android.R.color.transparent);
                if (isSelected(position)) {
                    holder.itemView.setBackgroundColor(0x88000000
                            + (0xffffff & ThemeUtils.getColorFromAttr(mainActivityRef.get(), R.attr.colorAccent)));
                }
            }
        } else {
            holder.itemView.setSelected(false);
            holder.itemView.setBackgroundResource(android.R.color.transparent);
            if (holder.selectIcon != null) {
                holder.selectIcon.setVisibility(View.GONE);
                holder.gradient.setVisibility(View.GONE);
            }
        }
        animateCardMargin(holder, cardMargin);

        holder.itemView.setOnLongClickListener(v -> {
            if (!inActionMode()) {
                startSelectMode(holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
        holder.itemView.setOnClickListener(v -> {
            if (inActionMode()) {
                toggleSelection(holder.getBindingAdapterPosition());
            } else {
                Fragment fragment = FeedItemlistFragment.newInstance(feed.getId());
                mainActivityRef.get().loadChildFragment(fragment);
            }
        });
    }

    @Override
    public void setSelected(int pos, boolean selected) {
        if (isHeader(pos)) {
            return;
        }
        super.setSelected(pos, selected);
    }

    @Override
    protected void toggleSelection(int pos) {
        setSelected(pos, !isSelected(pos));
        notifyItemChanged(pos, Boolean.TRUE);
        if (getSelectedCount() == 0) {
            endSelectMode();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull SubscriptionViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty() || holder.selectIcon == null) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }
        holder.itemView.setSelected(isSelected(position));
        holder.selectIcon.setImageResource(isSelected(position)
                ? R.drawable.circle_checked : R.drawable.circle_unchecked);
        int targetMargin = isSelected(position)
                ? (int) convertDpToPixel(holder.itemView.getContext(), 12f) : 0;
        animateCardMargin(holder, targetMargin);
    }

    private void animateCardMargin(@NonNull SubscriptionViewHolder holder, int targetMargin) {
        if (holder.selectIcon == null) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.card.getLayoutParams();
        int startMargin = params.leftMargin;
        if (startMargin == targetMargin) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(startMargin, targetMargin);
        animator.setDuration(100);
        animator.addUpdateListener(a -> {
            int margin = (int) a.getAnimatedValue();
            params.leftMargin = margin;
            params.topMargin = margin;
            params.rightMargin = margin;
            params.bottomMargin = margin;
            holder.card.setLayoutParams(params);
        });
        animator.start();
    }

    @Override
    public int getItemCount() {
        return listItems.size();
    }

    @Override
    public long getItemId(int position) {
        if (position >= listItems.size()) {
            return RecyclerView.NO_ID; // Dummy views
        }
        return rowIds.get(position);
    }

    public List<Feed> getSelectedItems() {
        List<Feed> items = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (int i = 0; i < getItemCount(); i++) {
            if (!isHeader(i) && isSelected(i)) {
                Feed feed = (Feed) listItems.get(i);
                if (!seen.contains(feed.getId())) {
                    seen.add(feed.getId());
                    items.add(feed);
                }
            }
        }
        return items;
    }

    public void setItems(List<Feed> listItems, Map<Long, Integer> feedCounters) {
        this.listItems = new ArrayList<>(listItems);
        this.rowIds = new ArrayList<>();
        for (Feed feed : listItems) {
            rowIds.add(feed.getId());
        }
        this.feedCounters = feedCounters;
        notifyDataSetChanged();
    }

    public void setGroupedItems(List<Object> rows, Map<Long, Integer> feedCounters) {
        this.listItems = new ArrayList<>(rows);
        this.rowIds = new ArrayList<>();
        int group = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof CategoryHeader) {
                group++;
                rowIds.add(-1L - i);
            } else {
                rowIds.add(((Feed) row).getId() * GROUP_ID_FACTOR + group);
            }
        }
        this.feedCounters = feedCounters;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (isHeader(position)) {
            return R.id.view_type_subscription_category_header;
        }
        if (columnCount == 1) {
            return R.id.view_type_subscription_list;
        } else if (UserPreferences.shouldShowSubscriptionTitle()) {
            return R.id.view_type_subscription_grid_with_title;
        } else {
            return R.id.view_type_subscription_grid_without_title;
        }
    }

    public static float convertDpToPixel(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static class GridDividerItemDecorator extends RecyclerView.ItemDecoration {
        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            super.onDraw(c, parent, state);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect,
                                   @NonNull View view,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            Context context = parent.getContext();
            int insetOffset = (int) convertDpToPixel(context, 1f);
            outRect.set(insetOffset, insetOffset, insetOffset, insetOffset);
        }
    }
}
