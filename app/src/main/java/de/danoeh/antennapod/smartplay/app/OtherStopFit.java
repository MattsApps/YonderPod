package de.danoeh.antennapod.smartplay.app;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import de.danoeh.antennapod.R;

/**
 * Places the small stop circle (1.0.2) the way PlayCircleFit places the big one: by measuring, never by fixed
 * sizes. It is about a third of the big circle and hangs a little off the left edge of the screen, less than the
 * big circle hangs off the right (Matt, 21 Nov), low down, level with the lower part of the big circle. It is
 * moved with translation, so showing or hiding it never shifts anything else, and it stays inside the big
 * circle's height, so nothing below it is touched on any screen shape. The holder starts at the screen's left
 * edge (its padding puts everything else back where it was), so the visible part is all tappable.
 */
final class OtherStopFit {
    private static final String TAG = "OtherStopFit";
    /** The small circle's diameter, as a share of the big circle's. */
    private static final float DOT_SHARE = 0.32f;
    /** How much of it is off the left edge: as the big circle is off the right (Matt, 21 Nov). */
    private static final float OVERHANG_SHARE = 0.268f;
    /** Its centre, as a share of the big circle's height from the big circle's top. */
    private static final float CENTRE_HEIGHT_SHARE = 0.83f;
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
        ViewTreeObserver.OnPreDrawListener listener = () -> fit(root, play, stop);
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

    /**
     * Measures where the world page's left edge is on screen and where the small circle's holder is, rather
     * than working it out from margins, so the share off the edge is what's asked for whatever sits in between.
     * The page edge is the screen edge once a swipe settles, and the circle moves with the page during one.
     */
    private static boolean fit(View root, View play, View stop) {
        if (stop.getVisibility() != View.VISIBLE || play.getWidth() == 0 || !(stop.getParent() instanceof View)) {
            return true;
        }
        int size = play.getHeight();
        int dot = Math.round(size * DOT_SHARE);
        boolean changed = setSize(stop, dot);
        changed |= setSize(stop.findViewById(R.id.worldOtherStopIcon), Math.round(dot * STOP_ICON_SHARE));
        View holder = (View) stop.getParent();
        int[] page = new int[2];
        int[] inHolder = new int[2];
        root.getLocationOnScreen(page);
        holder.getLocationOnScreen(inHolder);
        // The page's left edge, in the holder's own coordinates.
        float edge = page[0] - inHolder[0];
        float small = dot / 2f;
        float x = edge - dot * OVERHANG_SHARE - stop.getLeft();
        float y = play.getTop() + size * CENTRE_HEIGHT_SHARE - small - stop.getTop();
        if (Math.abs(stop.getTranslationX() - x) >= 0.5f || Math.abs(stop.getTranslationY() - y) >= 0.5f) {
            stop.setTranslationX(x);
            stop.setTranslationY(y);
            int[] placed = new int[2];
            stop.getLocationOnScreen(placed);
            Log.d(TAG, "Placed: page left " + page[0] + ", holder left " + inHolder[0] + " (getLeft "
                    + holder.getLeft() + "), stop left " + stop.getLeft() + ", circle " + dot + "px, on screen at "
                    + placed[0] + " (" + Math.round(-100f * (placed[0] - page[0]) / Math.max(1, dot))
                    + "% off the page edge), screen width " + root.getResources().getDisplayMetrics().widthPixels
                    + ", page width " + root.getWidth());
        }
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
