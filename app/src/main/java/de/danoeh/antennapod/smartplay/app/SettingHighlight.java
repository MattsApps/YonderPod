package de.danoeh.antennapod.smartplay.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.danoeh.antennapod.R;

public final class SettingHighlight {
    private static final int TRIES = 25;
    private static final long TRY_EVERY_MS = 120;
    private static final long SETTLE_MS = 350;
    private static final long FLASH_MS = 1800;
    private static final int FLASH_ALPHA = 110;

    private SettingHighlight() {
    }

    public static void text(Fragment fragment, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        whenViewReady(fragment, root -> findText(root, text.trim().toLowerCase(Locale.getDefault()), 0));
    }

    public static void preference(Fragment fragment, @Nullable String key) {
        if (key == null || key.isEmpty() || !(fragment instanceof PreferenceFragmentCompat)) {
            return;
        }
        PreferenceFragmentCompat preferences = (PreferenceFragmentCompat) fragment;
        whenViewReady(fragment, root -> root.postDelayed(() -> findPreference(preferences, key, 0), TRY_EVERY_MS));
    }

    private interface ViewAction {
        void run(View root);
    }

    private static void whenViewReady(Fragment fragment, ViewAction action) {
        fragment.getViewLifecycleOwnerLiveData().observeForever(new Observer<LifecycleOwner>() {
            @Override
            public void onChanged(LifecycleOwner owner) {
                if (owner == null || fragment.getView() == null) {
                    return;
                }
                fragment.getViewLifecycleOwnerLiveData().removeObserver(this);
                View root = fragment.getView();
                root.postDelayed(() -> action.run(root), TRY_EVERY_MS);
            }
        });
    }

    private static void findText(View root, String text, int attempt) {
        if (!root.isAttachedToWindow()) {
            if (attempt < TRIES) {
                root.postDelayed(() -> findText(root, text, attempt + 1), TRY_EVERY_MS);
            }
            return;
        }
        TextView found = search(root, text);
        if (found == null || found.getHeight() == 0) {
            if (found != null) {
                reveal(found, root);
            }
            if (attempt < TRIES) {
                root.postDelayed(() -> findText(root, text, attempt + 1), TRY_EVERY_MS);
            }
            return;
        }
        List<View> targets = new ArrayList<>();
        View row = rowOf(found, root);
        targets.add(row);
        if (row == found && found.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) found.getParent();
            int index = parent.indexOfChild(found);
            if (index + 1 < parent.getChildCount() && !(parent.getChildAt(index + 1) instanceof TextView)) {
                targets.add(parent.getChildAt(index + 1));
            }
        }
        scrollTo(row);
        root.postDelayed(() -> {
            for (View target : targets) {
                flash(target);
            }
        }, SETTLE_MS);
    }

    @Nullable
    private static TextView search(View view, String text) {
        if (view instanceof TextView) {
            CharSequence shown = ((TextView) view).getText();
            if (shown != null && shown.toString().trim().toLowerCase(Locale.getDefault()).equals(text)) {
                return (TextView) view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = search(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void reveal(View view, View root) {
        View current = view;
        while (current != null && current != root) {
            if (current.getVisibility() == View.GONE) {
                current.setVisibility(View.VISIBLE);
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
    }

    private static View rowOf(TextView text, View root) {
        View current = text;
        for (int level = 0; level < 2; level++) {
            if (!(current.getParent() instanceof View)) {
                break;
            }
            View parent = (View) current.getParent();
            if (parent == root || parent instanceof ScrollView || parent instanceof NestedScrollView
                    || parent.getParent() instanceof ScrollView || parent.getParent() instanceof NestedScrollView) {
                break;
            }
            if (parent.isClickable()) {
                return parent;
            }
            current = parent;
        }
        return text;
    }

    private static void scrollTo(View target) {
        View current = target;
        while (current.getParent() instanceof View) {
            View parent = (View) current.getParent();
            if (parent instanceof ScrollView || parent instanceof NestedScrollView) {
                Rect rect = new Rect();
                target.getDrawingRect(rect);
                ((ViewGroup) parent).offsetDescendantRectToMyCoords(target, rect);
                int top = Math.max(0, rect.top - parent.getHeight() / 4);
                if (parent instanceof ScrollView) {
                    ((ScrollView) parent).smoothScrollTo(0, top);
                } else {
                    ((NestedScrollView) parent).smoothScrollTo(0, top);
                }
                return;
            }
            current = parent;
        }
    }

    private static void findPreference(PreferenceFragmentCompat fragment, String key, int attempt) {
        RecyclerView list = fragment.getListView();
        if (list == null) {
            return;
        }
        if (!list.isAttachedToWindow()) {
            if (attempt < TRIES) {
                list.postDelayed(() -> findPreference(fragment, key, attempt + 1), TRY_EVERY_MS);
            }
            return;
        }
        RecyclerView.Adapter<?> adapter = list.getAdapter();
        int position = adapter instanceof PreferenceGroup.PreferencePositionCallback
                ? ((PreferenceGroup.PreferencePositionCallback) adapter).getPreferenceAdapterPosition(key)
                : RecyclerView.NO_POSITION;
        if (position == RecyclerView.NO_POSITION) {
            if (attempt < TRIES) {
                list.postDelayed(() -> findPreference(fragment, key, attempt + 1), TRY_EVERY_MS);
            }
            return;
        }
        fragment.scrollToPreference(key);
        list.postDelayed(() -> {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                flash(holder.itemView);
            }
        }, SETTLE_MS);
    }

    private static void flash(View view) {
        int accent = MaterialColors.getColor(view, R.attr.colorPrimary);
        Drawable before = view.getForeground();
        ColorDrawable glow = new ColorDrawable(accent);
        glow.setAlpha(FLASH_ALPHA);
        view.setForeground(glow);
        ValueAnimator fade = ValueAnimator.ofInt(FLASH_ALPHA, 0);
        fade.setStartDelay(FLASH_MS / 3);
        fade.setDuration(FLASH_MS);
        fade.addUpdateListener(animation -> glow.setAlpha((int) animation.getAnimatedValue()));
        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setForeground(before);
            }
        });
        fade.start();
    }
}
