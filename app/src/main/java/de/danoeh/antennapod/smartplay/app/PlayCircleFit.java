package de.danoeh.antennapod.smartplay.app;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Space;

import de.danoeh.antennapod.R;

final class PlayCircleFit {
    private static final float WIDTH_SHARE = 0.656f;
    private static final float OVERHANG_SHARE = 0.268f;
    private static final float ICON_SHARE = 92 / 252f;
    private static final float ICON_START_SHARE = 80 / 252f;

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
        int size = Math.max(0, Math.min(Math.round(root.getWidth() * WIDTH_SHARE), room));
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
}
