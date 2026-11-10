package de.danoeh.antennapod.smartplay.app;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.color.MaterialColors;

import de.danoeh.antennapod.R;

/** The wordmark at the top of each surface: "yonder", "tip me", or a brief "thank you". */
final class TipMark {
    private static final float THANKS_SP = 40;

    private TipMark() {
    }

    static void bind(Fragment fragment, @Nullable View root) {
        if (root == null || fragment.getContext() == null) {
            return;
        }
        TextView mark = root.findViewById(R.id.worldMark);
        if (mark == null) {
            return;
        }
        Context context = fragment.requireContext();
        Tip.checkOnce(context);
        mark.removeCallbacks((Runnable) mark.getTag(R.id.tip_mark_back));
        mark.animate().cancel();
        mark.setAlpha(1f);
        TextViewCompat.setTextAppearance(mark, R.style.TextAppearance_WildInk_Wordmark);
        mark.setOnClickListener(null);
        mark.setOnLongClickListener(null);
        mark.setClickable(false);
        mark.setLongClickable(false);
        mark.setBackground(null);
        mark.setContentDescription(null);

        long thanks = Tip.thanksLeft(context);
        if (thanks > 0) {
            mark.setText(R.string.tip_thank_you_mark);
            mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, THANKS_SP);
            mark.setTextColor(MaterialColors.getColor(mark, R.attr.colorPrimary));
            mark.setAlpha(0f);
            mark.animate().alpha(1f).setDuration(400).start();
            Runnable back = () -> bind(fragment, root);
            mark.setTag(R.id.tip_mark_back, back);
            mark.postDelayed(back, thanks);
            return;
        }
        if (Tip.showTipMe(context)) {
            mark.setText(R.string.tip_me_mark);
            mark.setTextColor(MaterialColors.getColor(mark, R.attr.colorPrimary));
            mark.setBackground(selectable(mark.getContext()));
            mark.setContentDescription(context.getString(R.string.tip_me_description));
            mark.setOnClickListener(v -> TipShop.open(fragment));
            mark.setOnLongClickListener(v -> {
                Tip.dismissForToday(context);
                Toast.makeText(context, R.string.tip_dismissed, Toast.LENGTH_SHORT).show();
                return true;
            });
            return;
        }
        mark.setText(R.string.yonder_wordmark);
    }

    @Nullable
    private static Drawable selectable(Context context) {
        TypedArray array = context.obtainStyledAttributes(new int[] {android.R.attr.selectableItemBackground});
        try {
            return array.getDrawable(0);
        } finally {
            array.recycle();
        }
    }
}
