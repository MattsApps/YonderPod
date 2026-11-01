package de.danoeh.antennapod.ui.preferences.preference;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.WildInkPalette;
import de.danoeh.antennapod.ui.preferences.R;

public class PalettePreference extends Preference {
    private static final int COLUMNS = 3;

    public PalettePreference(Context context) {
        super(context);
        setLayoutResource(R.layout.palette_preference);
    }

    public PalettePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.palette_preference);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View found = holder.itemView.findViewById(R.id.paletteContainer);
        if (!(found instanceof LinearLayout) && holder.itemView instanceof LinearLayout) {
            found = holder.itemView;
        }
        if (!(found instanceof LinearLayout)) {
            return;
        }
        LinearLayout container = (LinearLayout) found;
        container.removeAllViews();
        WildInkPalette active = WildInkPalette.current();
        WildInkPalette[] palettes = WildInkPalette.values();
        LinearLayout row = null;
        for (int i = 0; i < palettes.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            params.setMargins(dp(4), dp(4), dp(4), dp(8));
            row.addView(swatch(palettes[i], palettes[i] == active), params);
        }
    }

    private View swatch(WildInkPalette palette, boolean selected) {
        LinearLayout swatch = new LinearLayout(getContext());
        swatch.setOrientation(LinearLayout.VERTICAL);
        swatch.setMinimumHeight(dp(48));
        TypedValue ripple = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        swatch.setForeground(ResourcesCompat.getDrawable(getContext().getResources(), ripple.resourceId,
                getContext().getTheme()));
        swatch.setContentDescription(getContext().getString(palette.name));

        FrameLayout tile = new FrameLayout(getContext());
        tile.setBackgroundColor(palette.background);
        TextView sample = new TextView(getContext());
        sample.setText("Aa");
        sample.setTextColor(palette.text);
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        sample.setTypeface(ResourcesCompat.getFont(getContext(), R.font.bricolage_extrabold));
        FrameLayout.LayoutParams sampleParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        sampleParams.setMargins(dp(10), dp(6), 0, 0);
        tile.addView(sample, sampleParams);
        View sun = new View(getContext());
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(palette.accent);
        sun.setBackground(circle);
        FrameLayout.LayoutParams sunParams = new FrameLayout.LayoutParams(dp(36), dp(36),
                Gravity.BOTTOM | Gravity.END);
        sunParams.setMargins(0, 0, dp(8), dp(8));
        tile.addView(sun, sunParams);
        swatch.addView(tile, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(84)));

        TextView name = new TextView(getContext());
        name.setText(getContext().getString(palette.name).toLowerCase(Locale.getDefault()));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(ResourcesCompat.getFont(getContext(),
                selected ? R.font.bricolage_extrabold : R.font.bricolage_regular), Typeface.NORMAL);
        name.setPadding(0, dp(6), 0, dp(4));
        swatch.addView(name);

        View bar = new View(getContext());
        bar.setBackgroundColor(selected ? palette.accent : 0);
        swatch.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        swatch.setSelected(selected);
        swatch.setOnClickListener(v -> {
            if (palette == WildInkPalette.current()) {
                return;
            }
            UserPreferences.setPalette(palette.key);
            if (getOnPreferenceChangeListener() != null) {
                getOnPreferenceChangeListener().onPreferenceChange(this, palette.key);
            }
            notifyChanged();
        });
        return swatch;
    }

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }
}
