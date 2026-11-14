package de.danoeh.antennapod.smartplay.app;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import de.danoeh.antennapod.R;

/**
 * Places live pause's stack on the radio screen (0.9.26) the way PlayCircleFit places the play circle: by
 * measuring, never by fixed sizes. The big circle stays where PlayCircleFit put it. The stack (a small live
 * circle, "PAUSED" and "12 MIN BEHIND", a small stop circle, with even gaps) shares out the space left of
 * the circle, centred in the band from the circle's top edge to its bottom edge, with a gap so nothing
 * touches the circle.
 */
final class LivePauseFit {
    /** The gap between the stack and the big circle, as a share of the circle. */
    private static final float GAP_SHARE = 0.06f;
    /** The stack fills at most this share of the band. */
    private static final float BAND_SHARE = 0.9f;
    /** The largest small circle, as a share of the big one. */
    private static final float DOT_MAX_SHARE = 0.3f;
    /** The even gap between items, as a share of a small circle. */
    private static final float ITEM_GAP_SHARE = 0.3f;
    /** The word "live" in its circle, and the stop square, as shares of the small circle. */
    private static final float LIVE_TEXT_SHARE = 0.3f;
    private static final float STOP_ICON_SHARE = 0.56f;

    private LivePauseFit() {
    }

    static void bind(View root) {
        View play = root.findViewById(R.id.worldPlay);
        View stack = root.findViewById(R.id.worldLivePause);
        if (play == null || stack == null) {
            return;
        }
        ViewTreeObserver.OnPreDrawListener listener = () -> fit(play, stack);
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

    private static boolean fit(View play, View stack) {
        if (stack.getVisibility() != View.VISIBLE || play.getWidth() == 0 || !(play.getParent() instanceof View)) {
            return true;
        }
        View holder = (View) play.getParent();
        TextView live = stack.findViewById(R.id.worldLiveCircle);
        TextView paused = stack.findViewById(R.id.worldLivePaused);
        TextView behind = stack.findViewById(R.id.worldLiveBehind);
        View stop = stack.findViewById(R.id.worldLiveStop);
        View stopIcon = stack.findViewById(R.id.worldLiveStopIcon);
        TextView label = holder.findViewById(R.id.worldLive);

        int size = play.getHeight();
        int room = play.getLeft() - holder.getPaddingLeft() - Math.round(size * GAP_SHARE);
        if (room <= 0) {
            return true;
        }
        // The two lines keep the LIVE label's size, shrunk only if they wouldn't fit beside the circle.
        float textSize = label != null ? label.getTextSize() : behind.getTextSize();
        float widest = Math.max(paused.getVisibility() == View.VISIBLE ? width(paused, textSize) : 0,
                width(behind, textSize));
        if (widest > room) {
            textSize = textSize * room / widest;
        }
        boolean changed = setTextSize(paused, textSize) | setTextSize(behind, textSize);
        int lineHeight = behind.getLineHeight();
        int text = lineHeight * (paused.getVisibility() == View.VISIBLE ? 2 : 1);

        int dot = Math.round((size * BAND_SHARE - text) / (2 + 2 * ITEM_GAP_SHARE));
        dot = Math.max(0, Math.min(dot, Math.min(room, Math.round(size * DOT_MAX_SHARE))));
        int gap = Math.round(dot * ITEM_GAP_SHARE);
        int height = 2 * dot + 2 * gap + text;
        int top = play.getTop() + Math.max(0, (size - height) / 2);

        changed |= setSize(live, dot);
        changed |= setSize(stop, dot);
        changed |= setTopMargin(paused.getVisibility() == View.VISIBLE ? paused : behind, gap);
        if (paused.getVisibility() == View.VISIBLE) {
            changed |= setTopMargin(behind, 0);
        }
        changed |= setTopMargin(stop, gap);
        changed |= setTextSize(live, dot * LIVE_TEXT_SHARE);
        int icon = Math.round(dot * STOP_ICON_SHARE);
        changed |= setSize(stopIcon, icon);
        changed |= setTopMargin(stack, top);
        return !changed;
    }

    private static float width(TextView view, float textSize) {
        float old = view.getPaint().getTextSize();
        view.getPaint().setTextSize(textSize);
        float width = view.getPaint().measureText(view.getText().toString()) + view.getPaddingLeft()
                + view.getPaddingRight();
        view.getPaint().setTextSize(old);
        return width;
    }

    private static boolean setTextSize(TextView view, float px) {
        if (Math.abs(view.getTextSize() - px) < 0.5f) {
            return false;
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        return true;
    }

    private static boolean setSize(View view, int size) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width == size && params.height == size) {
            return false;
        }
        params.width = size;
        params.height = size;
        view.setLayoutParams(params);
        return true;
    }

    private static boolean setTopMargin(View view, int margin) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) {
            return false;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
        if (params.topMargin == margin) {
            return false;
        }
        params.topMargin = margin;
        view.setLayoutParams(params);
        return true;
    }
}
