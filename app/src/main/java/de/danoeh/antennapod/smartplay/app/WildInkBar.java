package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;

import de.danoeh.antennapod.R;

public class WildInkBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private boolean tick = true;

    public WildInkBar(Context context) {
        super(context);
    }

    public WildInkBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(1, progress));
        invalidate();
    }

    public void setTickStyle(boolean tick) {
        this.tick = tick;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float bar = 6 * density;
        float width = getWidth();
        float top = (getHeight() - bar) / 2f;
        int accent = MaterialColors.getColor(this, R.attr.colorPrimary);
        if (tick) {
            paint.setColor(MaterialColors.getColor(this, android.R.attr.textColorPrimary));
            canvas.drawRect(0, top, width, top + bar, paint);
            float x = Math.max(bar, progress * width);
            paint.setColor(accent);
            canvas.drawRect(x - bar, 0, x, getHeight(), paint);
        } else {
            paint.setColor(MaterialColors.getColor(this, R.attr.colorSurfaceContainerHigh));
            canvas.drawRect(0, top, width, top + bar, paint);
            paint.setColor(accent);
            canvas.drawRect(0, top, progress * width, top + bar, paint);
        }
    }
}
