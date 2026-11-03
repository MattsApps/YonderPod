package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuItemImpl;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.worlds.SwipeFeel;
import de.danoeh.antennapod.ui.common.Palette;

public class SheetView extends LinearLayout {
    private static final int MAX_ICONS = 2;
    private static final float BIG_TITLE_SP = 24;

    public interface Listener {
        void onDragStart(SheetView sheet);

        void onDragCancelled(SheetView sheet);

        void onCloseRequested(SheetView sheet);
    }

    private final int slop;
    private final View header;
    private final View grab;
    private final TextView name;
    private final LinearLayout actions;
    private final TextView more;
    private ViewGroup content;
    private View moving = this;
    private Listener listener;
    private Palette palette;
    private View fragmentView;
    private Toolbar toolbar;
    private String actionsShown = "";
    private float startRawX;
    private float startRawY;
    private boolean fromHeader;
    private boolean decided;
    private boolean dragging;
    private VelocityTracker velocity;
    private final ViewTreeObserver.OnGlobalLayoutListener refresher = this::refresh;

    public SheetView(@NonNull Context context) {
        this(context, null);
    }

    public SheetView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setClickable(true);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        header = LayoutInflater.from(context).inflate(R.layout.sheet_header, this, false);
        addView(header, 0);
        grab = header.findViewById(R.id.sheetGrab);
        name = header.findViewById(R.id.sheetName);
        actions = header.findViewById(R.id.sheetActions);
        more = header.findViewById(R.id.sheetMore);
        header.setOnClickListener(v -> scrollToTop());
        more.setOnClickListener(v -> showMenu());
        ViewCompat.addAccessibilityAction(header, context.getString(R.string.sheet_close), (view, arguments) -> {
            if (listener != null) {
                listener.onCloseRequested(this);
            }
            return true;
        });
        ViewCompat.replaceAccessibilityAction(header,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.sheet_to_top), null);
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(0, bars.top, 0, 0);
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                    .build();
        });
        setPalette(Palette.app(context));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(refresher);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(refresher);
        super.onDetachedFromWindow();
    }

    public static SheetView create(Context context, int contentId) {
        SheetView sheet = new SheetView(context);
        FrameLayout content = new FrameLayout(context);
        content.setId(contentId);
        sheet.addView(content);
        sheet.useContent(content);
        return sheet;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() > 1) {
            useContent((ViewGroup) getChildAt(1));
        }
    }

    private void useContent(ViewGroup view) {
        content = view;
        content.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    public int contentId() {
        return content.getId();
    }

    public ViewGroup content() {
        return content;
    }

    public void setMoving(View moving) {
        this.moving = moving;
    }

    public View moving() {
        return moving;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPalette(Palette palette) {
        this.palette = palette;
        float radius = 20 * getResources().getDisplayMetrics().density;
        GradientDrawable ground = new GradientDrawable();
        ground.setColor(palette.background);
        ground.setCornerRadii(new float[] {radius, radius, radius, radius, 0, 0, 0, 0});
        setBackground(ground);
        name.setTextColor(palette.text);
        more.setTextColor(palette.text);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(ColorUtils.setAlphaComponent(palette.text, 0x66));
        bar.setCornerRadius(radius);
        grab.setBackground(bar);
        actionsShown = "";
        refresh();
    }

    public void bind(View view) {
        unbind(fragmentView);
        fragmentView = view;
        toolbar = findToolbar(view);
        refresh();
    }

    public void unbind(@Nullable View view) {
        if (view == null || view != fragmentView) {
            return;
        }
        fragmentView = null;
        toolbar = null;
        actionsShown = "";
        name.setText(null);
        actions.removeAllViews();
        more.setVisibility(View.GONE);
    }

    private void refresh() {
        if (fragmentView == null) {
            return;
        }
        CharSequence title = null;
        if (toolbar != null) {
            if (toolbar.getNavigationIcon() != null) {
                toolbar.setNavigationIcon(null);
            }
            int wanted = keepToolbar(toolbar) ? View.VISIBLE : View.GONE;
            if (toolbar.getVisibility() != wanted) {
                toolbar.setVisibility(wanted);
            }
            title = toolbar.getTitle();
        }
        if (title == null || title.length() == 0) {
            title = bigTitle(fragmentView);
        }
        if (title == null || title.length() == 0) {
            View named = fragmentView.findViewById(R.id.txtvTitle);
            title = named instanceof TextView ? ((TextView) named).getText() : null;
        }
        String text = title == null ? "" : title.toString();
        if (!text.equalsIgnoreCase(name.getText().toString())) {
            name.setText(text);
            header.setContentDescription(text);
        }
        showActions();
    }

    private static boolean keepToolbar(Toolbar toolbar) {
        Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).isActionViewExpanded()) {
                return true;
            }
        }
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE && !(child instanceof ActionMenuView)
                    && !(child instanceof ImageView) && !(child instanceof TextView)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Toolbar findToolbar(View view) {
        if (view instanceof Toolbar) {
            return (Toolbar) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Toolbar found = findToolbar(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private CharSequence bigTitle(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            float big = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BIG_TITLE_SP,
                    getResources().getDisplayMetrics());
            return text.getTextSize() >= big && text.length() > 0 ? text.getText() : null;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CharSequence found = bigTitle(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<MenuItem> visibleItems() {
        List<MenuItem> items = new ArrayList<>();
        if (toolbar == null || keepToolbar(toolbar)) {
            return items;
        }
        Menu menu = toolbar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).isVisible()) {
                items.add(menu.getItem(i));
            }
        }
        return items;
    }

    private static boolean wantsIcon(MenuItem item) {
        return item.getIcon() != null && !item.hasSubMenu() && item instanceof MenuItemImpl
                && (((MenuItemImpl) item).requiresActionButton() || ((MenuItemImpl) item).requestsActionButton());
    }

    private void showActions() {
        List<MenuItem> items = visibleItems();
        List<MenuItem> icons = new ArrayList<>();
        boolean overflow = false;
        StringBuilder signature = new StringBuilder();
        for (MenuItem item : items) {
            if (icons.size() < MAX_ICONS && wantsIcon(item)) {
                icons.add(item);
                signature.append(item.getItemId()).append(item.isEnabled()).append(item.getIcon().hashCode())
                        .append(',');
            } else {
                overflow = true;
            }
        }
        signature.append(overflow);
        if (signature.toString().equals(actionsShown)) {
            return;
        }
        actionsShown = signature.toString();
        actions.removeAllViews();
        int size = (int) (44 * getResources().getDisplayMetrics().density);
        for (MenuItem item : icons) {
            ImageButton button = new ImageButton(getContext());
            button.setImageDrawable(item.getIcon());
            button.setImageTintList(ColorStateList.valueOf(palette.text));
            button.setBackgroundResource(android.R.drawable.list_selector_background);
            button.setContentDescription(item.getTitle());
            button.setEnabled(item.isEnabled());
            int id = item.getItemId();
            button.setOnClickListener(v -> perform(id));
            actions.addView(button, new LinearLayout.LayoutParams(size, size));
        }
        more.setVisibility(overflow ? View.VISIBLE : View.GONE);
    }

    private void perform(int id) {
        if (toolbar != null) {
            toolbar.getMenu().performIdentifierAction(id, 0);
        }
    }

    private void showMenu() {
        if (toolbar == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(getContext(), more);
        List<MenuItem> items = visibleItems();
        int shownAsIcons = 0;
        for (MenuItem item : items) {
            if (shownAsIcons < MAX_ICONS && wantsIcon(item)) {
                shownAsIcons++;
                continue;
            }
            copy(item, popup.getMenu());
        }
        popup.setOnMenuItemClickListener(item -> {
            if (!item.hasSubMenu()) {
                perform(item.getItemId());
            }
            return true;
        });
        popup.show();
    }

    private static void copy(MenuItem item, Menu target) {
        if (item.hasSubMenu()) {
            SubMenu sub = target.addSubMenu(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
            Menu source = item.getSubMenu();
            for (int i = 0; i < source.size(); i++) {
                if (source.getItem(i).isVisible()) {
                    copy(source.getItem(i), sub);
                }
            }
            return;
        }
        MenuItem copy = target.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
        copy.setCheckable(item.isCheckable());
        copy.setChecked(item.isChecked());
        copy.setEnabled(item.isEnabled());
    }

    public void scrollToTop() {
        if (fragmentView == null) {
            return;
        }
        toTop(fragmentView);
    }

    private static void toTop(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof AppBarLayout) {
            ((AppBarLayout) view).setExpanded(true, true);
        }
        if (view.canScrollVertically(-1)) {
            if (view instanceof RecyclerView) {
                ((RecyclerView) view).scrollToPosition(0);
            } else if (view instanceof NestedScrollView) {
                ((NestedScrollView) view).smoothScrollTo(0, 0);
            } else if (view instanceof ScrollView) {
                ((ScrollView) view).smoothScrollTo(0, 0);
            } else if (view instanceof AbsListView) {
                ((AbsListView) view).setSelection(0);
            } else {
                view.scrollTo(0, 0);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                toTop(group.getChildAt(i));
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return watch(event) || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (watch(event)) {
            return true;
        }
        if (dragging) {
            return drag(event);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean watch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawX = event.getRawX();
                startRawY = event.getRawY();
                fromHeader = event.getY() < header.getBottom();
                decided = false;
                dragging = false;
                track(event, true);
                return false;
            case MotionEvent.ACTION_MOVE:
                track(event, false);
                if (dragging || decided || listener == null) {
                    return false;
                }
                float dx = event.getRawX() - startRawX;
                float dy = event.getRawY() - startRawY;
                int direction = SwipeDirection.classify(dx, dy, slop);
                if (direction == SwipeDirection.UNDECIDED) {
                    return false;
                }
                decided = true;
                if (direction == SwipeDirection.VERTICAL && dy > 0
                        && (fromHeader || !contentCanScrollUp(startRawX, startRawY))) {
                    dragging = true;
                    startRawY = event.getRawY();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    moving.animate().cancel();
                    listener.onDragStart(this);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean drag(MotionEvent event) {
        track(event, false);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moving.setTranslationY(Math.max(0, event.getRawY() - startRawY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                float speed = 0;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    speed = velocity.getYVelocity();
                }
                boolean close = event.getActionMasked() == MotionEvent.ACTION_UP
                        && SwipeFeel.isEnough(moving.getTranslationY(), speed, travel(),
                        getResources().getDisplayMetrics().density);
                if (close && listener != null) {
                    listener.onCloseRequested(this);
                } else {
                    springBack();
                }
                return true;
            default:
                return true;
        }
    }

    private void track(MotionEvent event, boolean restart) {
        if (restart || velocity == null) {
            if (velocity != null) {
                velocity.recycle();
            }
            velocity = VelocityTracker.obtain();
        }
        MotionEvent raw = MotionEvent.obtain(event);
        raw.setLocation(event.getRawX(), event.getRawY());
        velocity.addMovement(raw);
        raw.recycle();
    }

    private boolean contentCanScrollUp(float rawX, float rawY) {
        return content != null && (collapsedBar(content) || scrollsUpAt(content, (int) rawX, (int) rawY));
    }

    private static boolean collapsedBar(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (view instanceof AppBarLayout && view.getTop() < 0) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (collapsedBar(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scrollsUpAt(View view, int rawX, int rawY) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(rawX, rawY)) {
            return false;
        }
        if (view.canScrollVertically(-1)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (scrollsUpAt(group.getChildAt(i), rawX, rawY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private float travel() {
        View parent = moving.getParent() instanceof View ? (View) moving.getParent() : moving;
        float height = Math.max(moving.getHeight(), parent.getHeight());
        return height > 0 ? height : getResources().getDisplayMetrics().heightPixels;
    }

    public void follow(float progress) {
        moving.animate().cancel();
        moving.setTranslationY(travel() * SwipeFeel.BACK_GESTURE_TRAVEL * progress);
    }

    public void springBack() {
        moving.animate().cancel();
        moving.animate().translationY(0).setDuration(SwipeFeel.SPRING_BACK_MS).withEndAction(() -> {
            if (listener != null) {
                listener.onDragCancelled(this);
            }
        }).start();
    }

    public void slideIn(@Nullable Runnable after) {
        moving.animate().cancel();
        moving.setTranslationY(travel());
        moving.animate().translationY(0).setDuration(SwipeFeel.OPEN_MS).withEndAction(() -> {
            if (after != null) {
                after.run();
            }
        }).start();
    }

    public void slideOut(@Nullable Runnable after) {
        moving.animate().cancel();
        moving.animate().translationY(travel()).setDuration(SwipeFeel.CLOSE_MS).withEndAction(() -> {
            if (after != null) {
                after.run();
            }
        }).start();
    }

    public boolean isShowing() {
        return moving.getTranslationY() < travel() - 1;
    }
}
