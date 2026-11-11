package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

public class TendScrollView extends NestedScrollView {
    public TendScrollView(@NonNull Context context) {
        this(context, null);
    }

    public TendScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
}
