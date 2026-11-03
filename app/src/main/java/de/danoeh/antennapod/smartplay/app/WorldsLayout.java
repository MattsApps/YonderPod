package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import de.danoeh.antennapod.smartplay.worlds.SwipeFeel;

public class WorldsLayout extends FrameLayout {

    private final int slop;
    private ViewPager2 pager;
    private Runnable onSwipeUp;
    private float startX;
    private float startY;
    private int direction = SwipeDirection.UNDECIDED;
    private VelocityTracker velocity;

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
            pager.setUserInputEnabled(x > dp(SwipeFeel.EDGE_DP) && x < getWidth() - dp(SwipeFeel.EDGE_DP));
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        track(event);
        if (direction == SwipeDirection.UNDECIDED && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            direction = SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop);
            if (direction == SwipeDirection.VERTICAL && scrollsAt(this, event.getRawX(), event.getRawY(),
                    event.getY() > startY ? -1 : 1)) {
                direction = SwipeDirection.HORIZONTAL;
            }
        }
        return direction == SwipeDirection.VERTICAL || super.onInterceptTouchEvent(event);
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
                if (direction == SwipeDirection.UNDECIDED) {
                    direction = SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (direction == SwipeDirection.VERTICAL && isSwipeUp(event)) {
                    if (onSwipeUp != null) {
                        onSwipeUp.run();
                    }
                }
                reset();
                return true;
            case MotionEvent.ACTION_CANCEL:
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

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
