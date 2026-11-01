package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

public class WorldsLayout extends FrameLayout {
    private static final int EDGE_DP = 32;
    private static final int SWIPE_UP_DP = 60;
    private static final int FLING_VELOCITY = 600;

    private final GestureDetector detector;
    private ViewPager2 pager;
    private Runnable onSwipeUp;

    public WorldsLayout(@NonNull Context context) {
        this(context, null);
    }

    public WorldsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(@Nullable MotionEvent start, @NonNull MotionEvent end,
                                   float velocityX, float velocityY) {
                if (start == null || onSwipeUp == null) {
                    return false;
                }
                float dy = end.getY() - start.getY();
                float dx = end.getX() - start.getX();
                if (velocityY < -FLING_VELOCITY && -dy > dp(SWIPE_UP_DP) && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                    onSwipeUp.run();
                    return true;
                }
                return false;
            }
        });
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
        detector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
