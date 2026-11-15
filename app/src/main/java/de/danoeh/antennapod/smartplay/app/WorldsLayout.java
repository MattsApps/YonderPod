package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import de.danoeh.antennapod.smartplay.worlds.SwipeFeel;

public class WorldsLayout extends FrameLayout {
    private static final String TAG = "WorldSwipe";

    private final int slop;
    private ViewPager2 pager;
    private Runnable onSwipeUp;
    private float startX;
    private float startY;
    private int direction = SwipeDirection.UNDECIDED;
    private VelocityTracker velocity;
    private int pageAtDown;
    private float downX;
    private float downY;
    private long downTime;
    private boolean pagerAllowed;
    private boolean waiting;
    private boolean fakeDragging;
    private float lastX;

    public WorldsLayout(@NonNull Context context) {
        this(context, null);
    }

    public WorldsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPager(ViewPager2 pager) {
        this.pager = pager;
    }

    public void setOnSwipeUp(Runnable onSwipeUp) {
        this.onSwipeUp = onSwipeUp;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && pager != null) {
            float x = event.getX();
            pagerAllowed = x > backZone(true) && x < getWidth() - backZone(false);
            pager.setUserInputEnabled(pagerAllowed);
            pageAtDown = pager.getCurrentItem();
            downX = x;
            downY = event.getY();
            downTime = event.getEventTime();
        } else if (pager != null && (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            logOutcome(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        track(event);
        if (direction == SwipeDirection.UNDECIDED && !waiting && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int first = SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop);
            if (first == SwipeDirection.VERTICAL && scrollsAt(this, event.getRawX(), event.getRawY(),
                    event.getY() > startY ? -1 : 1)) {
                direction = SwipeDirection.HORIZONTAL;
            } else if (first != SwipeDirection.UNDECIDED) {
                waiting = true;
            }
        }
        return waiting || direction == SwipeDirection.VERTICAL || super.onInterceptTouchEvent(event);
    }

    private static boolean scrollsAt(View view, float rawX, float rawY, int way) {
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains((int) rawX, (int) rawY)) {
            return false;
        }
        if (view.canScrollVertically(way)) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (scrollsAt(group.getChildAt(i), rawX, rawY, way)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            track(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (fakeDragging) {
                    pager.fakeDragBy(event.getX() - lastX);
                    lastX = event.getX();
                } else if (waiting) {
                    if (Math.hypot(event.getX() - startX, event.getY() - startY) >= dp(SwipeFeel.DECIDE_AFTER_DP)
                            && SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop)
                            == SwipeDirection.HORIZONTAL) {
                        decide(event);
                    }
                } else if (direction == SwipeDirection.UNDECIDED && SwipeDirection.classify(event.getX() - startX,
                        event.getY() - startY, slop) != SwipeDirection.UNDECIDED) {
                    waiting = true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (fakeDragging) {
                    fakeDragging = false;
                    pager.endFakeDrag();
                    flickIfQuick(event.getX() - startX, true);
                } else if (waiting) {
                    finishAtLift(event);
                }
                waiting = false;
                if (direction != SwipeDirection.UNDECIDED) {
                    Log.d(TAG, "Ended " + Math.round((event.getX() - startX) / dp(1)) + " dp sideways, "
                            + Math.round((startY - event.getY()) / dp(1)) + " dp up"
                            + (direction == SwipeDirection.VERTICAL
                            ? (isSwipeUp(event) ? ": opens Tend" : ": not far enough up, so nothing") : ""));
                }
                if (direction == SwipeDirection.VERTICAL && isSwipeUp(event)) {
                    if (onSwipeUp != null) {
                        onSwipeUp.run();
                    }
                }
                reset();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (fakeDragging) {
                    fakeDragging = false;
                    pager.endFakeDrag();
                }
                waiting = false;
                reset();
                return true;
            default:
                return true;
        }
    }

    private void track(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();
            direction = SwipeDirection.UNDECIDED;
            waiting = false;
            if (fakeDragging && pager != null) {
                fakeDragging = false;
                pager.endFakeDrag();
            }
            if (velocity != null) {
                velocity.recycle();
            }
            velocity = VelocityTracker.obtain();
        }
        if (velocity != null) {
            velocity.addMovement(event);
        }
    }

    private boolean isSwipeUp(MotionEvent event) {
        float speed = 0;
        if (velocity != null) {
            velocity.computeCurrentVelocity(1000);
            speed = -velocity.getYVelocity();
        }
        return SwipeFeel.isEnough(startY - event.getY(), speed, getHeight(),
                getResources().getDisplayMetrics().density);
    }

    private void reset() {
        direction = SwipeDirection.UNDECIDED;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    /**
     * The strips at each side that Android keeps for its Back gesture, so changing worlds leaves them alone.
     * Before Android 10, and with button navigation, there are none.
     */
    private int backZone(boolean left) {
        WindowInsets insets = getRootWindowInsets();
        if (Build.VERSION.SDK_INT < 29 || insets == null) {
            return 0;
        }
        return left ? insets.getSystemGestureInsets().left : insets.getSystemGestureInsets().right;
    }

    /** Decides a swipe from its whole movement so far; sideways, Yonder moves the pages itself from here. */
    private void decide(MotionEvent event) {
        waiting = false;
        direction = SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop);
        logDecision(event, "worlds");
        if (direction == SwipeDirection.HORIZONTAL && pagerAllowed && pager != null && pager.beginFakeDrag()) {
            fakeDragging = true;
            pager.fakeDragBy(event.getX() - startX);
            lastX = event.getX();
        }
    }

    /** Up or down is only decided as the thumb lifts, from the whole swipe; so is a short sideways flick. */
    private void finishAtLift(MotionEvent event) {
        float dx = event.getX() - startX;
        float dy = event.getY() - startY;
        int decided = SwipeDirection.classify(dx, dy, slop);
        Log.d(TAG, "Decided at lift: " + (decided == SwipeDirection.VERTICAL ? "up/down" : "sideways") + ", "
                + Math.round(dx / dp(1)) + " dp sideways, " + Math.round(-dy / dp(1)) + " dp up, "
                + Math.round(Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)))) + " degrees from flat"
                + (decided == SwipeDirection.VERTICAL ? (isSwipeUp(event) ? ": opens Tend" : ": nothing") : ""));
        if (decided == SwipeDirection.VERTICAL && isSwipeUp(event)) {
            if (onSwipeUp != null) {
                onSwipeUp.run();
            }
        } else if (decided == SwipeDirection.HORIZONTAL && pagerAllowed) {
            flickIfQuick(dx, false);
        }
    }

    /** A quick flick sideways always moves one world, judged by Yonder's own measure of the thumb's speed. */
    private void flickIfQuick(float dx, boolean afterDrag) {
        if (velocity == null || pager == null) {
            return;
        }
        velocity.computeCurrentVelocity(1000);
        float speed = velocity.getXVelocity();
        if (Math.signum(speed) != Math.signum(dx) || !SwipeFeel.isEnough(Math.abs(dx), Math.abs(speed), getWidth(),
                getResources().getDisplayMetrics().density)) {
            return;
        }
        boolean forward = (dx < 0) != (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        int target = pageAtDown + (forward ? 1 : -1);
        int count = pager.getAdapter() != null ? pager.getAdapter().getItemCount() : 0;
        if (target >= 0 && target < count && (!afterDrag || pager.getCurrentItem() != target)) {
            Log.d(TAG, "Quick flick: to page " + target + (afterDrag ? " (after the drag)" : ""));
            pager.setCurrentItem(target);
        }
    }

    private void logOutcome(MotionEvent event) {
        float dx = event.getX() - downX;
        float dy = downY - event.getY();
        if (Math.abs(dx) < dp(40) && event.getActionMasked() == MotionEvent.ACTION_UP) {
            return;
        }
        int before = pageAtDown;
        String what = (event.getActionMasked() == MotionEvent.ACTION_CANCEL ? "Cancelled" : "Lifted") + " after "
                + Math.round(dx / dp(1)) + " dp sideways, " + Math.round(dy / dp(1)) + " dp up, in "
                + (event.getEventTime() - downTime) + " ms";
        postDelayed(() -> Log.d(TAG, what + ": " + (pager.getCurrentItem() != before
                ? "world changed" : "WORLD DID NOT CHANGE") + " (page " + before + " to " + pager.getCurrentItem()
                + " of 0 to " + (pager.getAdapter() != null ? pager.getAdapter().getItemCount() - 1 : -1) + ")"), 700);
    }

    private void logDecision(MotionEvent event, String where) {
        if (direction == SwipeDirection.UNDECIDED) {
            return;
        }
        float dx = event.getX() - startX;
        float dy = event.getY() - startY;
        Log.d(TAG, (direction == SwipeDirection.VERTICAL ? "Up/down" : "Sideways") + " (" + where + "), decided after "
                + Math.round(Math.hypot(dx, dy) / dp(1)) + " dp at "
                + Math.round(Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)))) + " degrees from flat; started "
                + Math.round(startX / dp(1)) + " dp from the left, " + Math.round((getWidth() - startX) / dp(1))
                + " dp from the right" + (pager != null && !pager.isUserInputEnabled() ? " (edge strip)" : "")
                + "; Android's Back zones " + Math.round(backZone(true) / dp(1)) + " and "
                + Math.round(backZone(false) / dp(1)) + " dp");
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
