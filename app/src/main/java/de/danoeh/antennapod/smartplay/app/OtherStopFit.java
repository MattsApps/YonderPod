package de.danoeh.antennapod.smartplay.app;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import de.danoeh.antennapod.R;

/**
 * Places the small stop circle (1.0.2) the way PlayCircleFit places the big one: by measuring, never by fixed
 * sizes. It is a share of the big circle, hangs off the big circle's lower-left edge and overlaps it slightly,
 * with its square at the centre. It is moved with translation, so showing or hiding it never shifts anything
 * else, and it stays inside the big circle's height, so nothing below it is touched on any screen shape.
 */
final class OtherStopFit {
    /** The small circle's diameter, as a share of the big circle's. */
    private static final float DOT_SHARE = 0.24f;
    /** How much of the small circle's radius overlaps the big circle. */
    private static final float OVERLAP_SHARE = 0.4f;
    /** Where it hangs: this many degrees below the big circle's leftmost point. */
    private static final double ANGLE_DEGREES = 38;
    /** The stop square, as a share of the small circle (as live pause's stop circle). */
    private static final float STOP_ICON_SHARE = 0.56f;

    private OtherStopFit() {
    }

    static void bind(View root) {
        View play = root.findViewById(R.id.worldPlay);
        View stop = root.findViewById(R.id.worldOtherStop);
        if (play == null || stop == null) {
            return;
        }
        ViewTreeObserver.OnPreDrawListener listener = () -> fit(play, stop);
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

    private static boolean fit(View play, View stop) {
        if (stop.getVisibility() != View.VISIBLE || play.getWidth() == 0) {
            return true;
        }
        int size = play.getHeight();
        int dot = Math.round(size * DOT_SHARE);
        boolean changed = setSize(stop, dot);
        changed |= setSize(stop.findViewById(R.id.worldOtherStopIcon), Math.round(dot * STOP_ICON_SHARE));
        float radius = size / 2f;
        float small = dot / 2f;
        float reach = radius + small * (1 - OVERLAP_SHARE);
        double angle = Math.toRadians(ANGLE_DEGREES);
        float centreX = play.getLeft() + radius - (float) (reach * Math.cos(angle));
        float centreY = play.getTop() + radius + (float) (reach * Math.sin(angle));
        stop.setTranslationX(centreX - small - stop.getLeft());
        stop.setTranslationY(centreY - small - stop.getTop());
        return !changed;
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
}
