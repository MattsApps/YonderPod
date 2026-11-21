package de.danoeh.antennapod.smartplay.app;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Space;
import android.widget.TextView;

import de.danoeh.antennapod.R;

/**
 * Sizes the big play circle as a share of the measured width. 1.0.2: the circle is the same size and in the
 * same spot in every world, so when a world's words would leave it too little height, the title gives way
 * first (its text shrinks, down to a floor) and the circle shrinks only if that isn't enough.
 */
final class PlayCircleFit {
    private static final float WIDTH_SHARE = 0.656f;
    private static final float OVERHANG_SHARE = 0.268f;
    private static final float ICON_SHARE = 92 / 252f;
    private static final float ICON_START_SHARE = 80 / 252f;
    /** The title never shrinks below this share of the size the screen gave it. */
    private static final float TITLE_FLOOR_SHARE = 0.7f;

    private PlayCircleFit() {
    }

    static void bind(View root) {
        View play = root.findViewById(R.id.worldPlay);
        View icon = root.findViewById(R.id.worldPlayIcon);
        if (!(root instanceof ViewGroup) || play == null || icon == null) {
            return;
        }
        ViewTreeObserver.OnPreDrawListener listener = () -> fit((ViewGroup) root, play, icon);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                view.getViewTreeObserver().addOnPreDrawListener(listener);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.getViewTreeObserver().removeOnPreDrawListener(listener);
            }
        });
    }

    private static boolean fit(ViewGroup root, View play, View icon) {
        if (root.getWidth() == 0 || !(play.getParent() instanceof View)) {
            return true;
        }
        View holder = (View) play.getParent();
        int used = root.getPaddingTop() + root.getPaddingBottom();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getVisibility() == View.GONE || child instanceof Space) {
                continue;
            }
            used += child.getMeasuredHeight();
            if (child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                used += margins.topMargin + margins.bottomMargin;
            }
        }
        int room = holder.getHeight() + root.getHeight() - used;
        int target = Math.round(root.getWidth() * WIDTH_SHARE);
        if (titleGivesWay(root.findViewById(R.id.worldTitle), target - room)) {
            return false;
        }
        int size = Math.max(0, Math.min(target, room));
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) play.getLayoutParams();
        if (Math.abs(params.width - size) <= 1) {
            return true;
        }
        params.width = size;
        params.height = size;
        params.setMarginEnd(-Math.round(size * OVERHANG_SHARE));
        play.setLayoutParams(params);
        ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
        iconParams.width = Math.round(size * ICON_SHARE);
        iconParams.height = iconParams.width;
        iconParams.setMarginStart(Math.round(size * ICON_START_SHARE));
        icon.setLayoutParams(iconParams);
        return false;
    }

    /** What the title's size was set to by its screen, and what this class last set it to. */
    private static final class TitleSize {
        float given;
        float applied;
        String text;
    }

    /**
     * Shrinks the title by the height the circle is short of, never below its floor. A new title or a size set
     * by the screen starts again from the screen's size. Returns true when it changed the size (lay out again).
     */
    private static boolean titleGivesWay(View view, int missing) {
        if (!(view instanceof TextView) || view.getVisibility() != View.VISIBLE || view.getHeight() == 0) {
            return false;
        }
        TextView title = (TextView) view;
        float current = title.getTextSize();
        String text = String.valueOf(title.getText());
        TitleSize size = (TitleSize) title.getTag(R.id.worldTitle);
        if (size == null || Math.abs(current - size.applied) >= 0.5f) {
            size = new TitleSize();
            size.given = current;
            size.applied = current;
            size.text = text;
            title.setTag(R.id.worldTitle, size);
        } else if (!text.equals(size.text)) {
            size.text = text;
            if (Math.abs(current - size.given) >= 0.5f) {
                size.applied = size.given;
                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, size.given);
                return true;
            }
        }
        float floor = size.given * TITLE_FLOOR_SHARE;
        if (missing <= 1 || current <= floor + 0.5f) {
            return false;
        }
        int height = title.getHeight();
        float smaller = Math.max(floor, current * Math.max(0f, height - missing) / height);
        size.applied = smaller;
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, smaller);
        return true;
    }
}
