package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

public class WorldsLayout extends FrameLayout {
    private static final int EDGE_DP = 32;
    private static final int SWIPE_UP_DP = 60;
    private static final int FLING_UP_DP = 24;
    private static final int FLING_VELOCITY = 600;

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
            pager.setUserInputEnabled(x > dp(EDGE_DP) && x < getWidth() - dp(EDGE_DP));
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        track(event);
        if (direction == SwipeDirection.UNDECIDED && event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            direction = SwipeDirection.classify(event.getX() - startX, event.getY() - startY, slop);
        }
        return direction == SwipeDirection.VERTICAL || super.onInterceptTouchEvent(event);
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
        float dy = event.getY() - startY;
        if (-dy > dp(SWIPE_UP_DP)) {
            return true;
        }
        if (velocity == null) {
            return false;
        }
        velocity.computeCurrentVelocity(1000);
        return velocity.getYVelocity() < -FLING_VELOCITY * getResources().getDisplayMetrics().density
                && -dy > dp(FLING_UP_DP);
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
