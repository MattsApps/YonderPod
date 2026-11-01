package de.danoeh.antennapod.ui.common;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.Locale;

public class LowercaseTextView extends AppCompatTextView {
    public LowercaseTextView(@NonNull Context context) {
        super(context);
    }

    public LowercaseTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LowercaseTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text == null ? null : text.toString().toLowerCase(Locale.getDefault()), type);
    }
}
