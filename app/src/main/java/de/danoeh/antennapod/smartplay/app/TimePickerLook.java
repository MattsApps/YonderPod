package de.danoeh.antennapod.smartplay.app;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import de.danoeh.antennapod.R;

public final class TimePickerLook {
    private static final int[] SELECTED = {android.R.attr.state_activated};
    private static final int[] CHECKED = {android.R.attr.state_checked};
    private static final float DIMMED_ALPHA = 0.4f;

    private TimePickerLook() {
    }

    public static void show(TimePickerDialog dialog) {
        dialog.setOnShowListener(d -> {
            View root = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
            if (root != null) {
                markChoice(root, "am_label");
                markChoice(root, "pm_label");
            }
        });
        dialog.show();
    }

    private static void markChoice(View root, String name) {
        Context context = root.getContext();
        int id = context.getResources().getIdentifier(name, "id", "android");
        View found = id == 0 ? null : root.findViewById(id);
        if (!(found instanceof TextView)) {
            return;
        }
        TextView label = (TextView) found;
        int highlight = MaterialColors.getColor(label, R.attr.colorPrimary, Color.WHITE);
        int pill = MaterialColors.getColor(label, android.R.attr.colorBackground, Color.BLACK);
        ColorStateList was = label.getTextColors();
        int onHeader = was.getColorForState(SELECTED, was.getDefaultColor());
        int dimmed = Color.argb(Math.round(Color.alpha(onHeader) * DIMMED_ALPHA),
                Color.red(onHeader), Color.green(onHeader), Color.blue(onHeader));
        label.setTextColor(new ColorStateList(new int[][]{SELECTED, CHECKED, {}},
                new int[]{highlight, highlight, dimmed}));
        StateListDrawable background = new StateListDrawable();
        background.addState(SELECTED, new ColorDrawable(pill));
        background.addState(CHECKED, new ColorDrawable(pill));
        background.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        label.setBackground(background);
        label.setGravity(Gravity.CENTER);
    }
}
