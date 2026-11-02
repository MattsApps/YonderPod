package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

public class TendScrollView extends NestedScrollView {
    private static final float RESISTANCE = 0.5f;
    private static final int RETURN_DP = 96;

    private final int slop;
    private Runnable onPullDown;
    private float startX;
    private float startY;
    private boolean pulling;
    private boolean decided;

    public TendScrollView(@NonNull Context context) {
        this(context, null);
    }

    public TendScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnPullDown(@Nullable Runnable onPullDown) {
        this.onPullDown = onPullDown;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (watch(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (watch(ev)) {
            return true;
        }
        if (pulling) {
            return handlePull(ev);
        }
        return super.onTouchEvent(ev);
    }

    private boolean watch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                pulling = false;
                decided = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (decided || pulling || onPullDown == null) {
                    return false;
                }
                float dy = ev.getY() - startY;
                int direction = SwipeDirection.classify(ev.getX() - startX, dy, slop);
                if (direction == SwipeDirection.UNDECIDED) {
                    return false;
                }
                decided = true;
                if (direction == SwipeDirection.VERTICAL && dy > 0 && getScrollY() == 0) {
                    pulling = true;
                    startY = ev.getY();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private boolean handlePull(MotionEvent ev) {
        View content = getChildAt(0);
        if (content == null) {
            pulling = false;
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                content.setTranslationY(Math.max(0, (ev.getY() - startY) * RESISTANCE));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pulling = false;
                boolean far = content.getTranslationY() > RETURN_DP * getResources().getDisplayMetrics().density;
                content.animate().translationY(0).setDuration(200).start();
                if (far && ev.getActionMasked() == MotionEvent.ACTION_UP && onPullDown != null) {
                    onPullDown.run();
                }
                return true;
            default:
                return true;
        }
    }
}
