package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.ColourOverride;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.widget.WidgetUpdaterWorker;

public final class LookDialogs {
    public interface KeyCallback {
        void onChosen(String key);
    }

    private LookDialogs() {
    }

    public static void changed(Activity activity) {
        WidgetUpdaterWorker.enqueueWork(activity);
        activity.recreate();
    }

    public static View swatchRow(Context context, @Nullable String label, String selected, KeyCallback callback) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (10 * density), 0, (int) (4 * density));
        if (label != null) {
            TextView name = new TextView(context);
            TextViewCompat.setTextAppearance(name, R.style.TextAppearance_WildInk_DetailStrong);
            name.setText(label);
            row.addView(name, new LinearLayout.LayoutParams((int) (52 * density),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        Palette current = Palette.of(selected);
        for (String key : Palette.keys()) {
            Palette palette = Palette.of(key);
            boolean isSelected = key.equals(current.key);
            FrameLayout swatch = new FrameLayout(context);
            GradientDrawable ground = new GradientDrawable();
            ground.setShape(GradientDrawable.OVAL);
            ground.setColor(palette.background);
            ground.setStroke((int) ((isSelected ? 3 : 2) * density), isSelected ? palette.accent : palette.raised);
            swatch.setBackground(ground);
            View dot = new View(context);
            GradientDrawable accent = new GradientDrawable();
            accent.setShape(GradientDrawable.OVAL);
            accent.setColor(palette.accent);
            dot.setBackground(accent);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams((int) (12 * density),
                    (int) (12 * density));
            dotParams.gravity = Gravity.CENTER;
            swatch.addView(dot, dotParams);
            swatch.setContentDescription(context.getString(R.string.tune_palette_label,
                    context.getString(palette.name)));
            swatch.setSelected(isSelected);
            swatch.setOnClickListener(v -> callback.onChosen(key));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (36 * density),
                    (int) (36 * density));
            params.setMarginEnd((int) (8 * density));
            row.addView(swatch, params);
        }
        return row;
    }

    public static void dayNight(Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (24 * density), (int) (8 * density), (int) (24 * density), 0);
        MaterialSwitch enabled = new MaterialSwitch(activity);
        enabled.setText(R.string.look_day_night_switch);
        enabled.setChecked(UserPreferences.isPaletteDayNight());
        content.addView(enabled);
        String[] chosen = {UserPreferences.getDayPalette(), UserPreferences.getNightPalette()};
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            rows.removeAllViews();
            rows.addView(swatchRow(activity, activity.getString(R.string.look_day), chosen[0], key -> {
                chosen[0] = key;
                fill[0].run();
            }));
            rows.addView(swatchRow(activity, activity.getString(R.string.look_night), chosen[1], key -> {
                chosen[1] = key;
                fill[0].run();
            }));
        };
        fill[0].run();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.look_day_night)
                .setView(content)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    UserPreferences.setPaletteDayNight(enabled.isChecked());
                    UserPreferences.setDayNightPalettes(chosen[0], chosen[1]);
                    changed(activity);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void wallpaper(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            Toast.makeText(activity, R.string.look_wallpaper_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperColors colours = null;
        try {
            colours = WallpaperManager.getInstance(activity).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception ignored) {
            colours = null;
        }
        if (colours == null) {
            Toast.makeText(activity, R.string.look_wallpaper_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        int background = colours.getPrimaryColor().toArgb();
        int accent;
        if (colours.getSecondaryColor() != null) {
            accent = colours.getSecondaryColor().toArgb();
        } else if (colours.getTertiaryColor() != null) {
            accent = colours.getTertiaryColor().toArgb();
        } else {
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(background, hsl);
            accent = ColorUtils.HSLToColor(new float[] {(hsl[0] + 180) % 360, 0.85f, 0.55f});
        }
        custom(activity, background, accent);
    }

    public static void custom(Activity activity, int startBackground, int startAccent) {
        View content = LayoutInflater.from(activity).inflate(R.layout.look_custom_dialog, null);
        int[] background = {startBackground | 0xFF000000};
        int[] accent = {startAccent | 0xFF000000};
        Runnable preview = () -> showPreview(activity, content, background[0], accent[0]);
        bindSliders(content, R.id.customBackgroundHue, R.id.customBackgroundSaturation,
                R.id.customBackgroundLightness, background, preview);
        bindSliders(content, R.id.customAccentHue, R.id.customAccentSaturation,
                R.id.customAccentLightness, accent, preview);
        preview.run();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.look_custom)
                .setView(content)
                .setPositiveButton(R.string.look_custom_save, (dialog, which) -> {
                    UserPreferences.setCustomPalette(background[0], accent[0]);
                    if (UserPreferences.isPalettePerWorld()) {
                        UserPreferences.setWorldPalette(Palette.PODCASTS, Palette.CUSTOM);
                    } else {
                        UserPreferences.setPalette(Palette.CUSTOM);
                    }
                    changed(activity);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static boolean customSupported() {
        return ColourOverride.isSupported();
    }

    private static void bindSliders(View content, int hueId, int saturationId, int lightnessId, int[] colour,
                                    Runnable onChange) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(colour[0], hsl);
        SeekBar hue = content.findViewById(hueId);
        SeekBar saturation = content.findViewById(saturationId);
        SeekBar lightness = content.findViewById(lightnessId);
        hue.setMax(360);
        saturation.setMax(100);
        lightness.setMax(100);
        hue.setProgress(Math.round(hsl[0]));
        saturation.setProgress(Math.round(hsl[1] * 100));
        lightness.setProgress(Math.round(hsl[2] * 100));
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                colour[0] = ColorUtils.HSLToColor(new float[] {hue.getProgress(), saturation.getProgress() / 100f,
                    lightness.getProgress() / 100f});
                onChange.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        hue.setOnSeekBarChangeListener(listener);
        saturation.setOnSeekBarChangeListener(listener);
        lightness.setOnSeekBarChangeListener(listener);
    }

    private static void showPreview(Context context, View content, int chosenBackground, int chosenAccent) {
        Palette palette = Palette.custom(chosenBackground, chosenAccent);
        content.findViewById(R.id.customPreview).setBackgroundColor(palette.background);
        ((TextView) content.findViewById(R.id.customPreviewTitle)).setTextColor(palette.text);
        ((TextView) content.findViewById(R.id.customPreviewDetail)).setTextColor(palette.secondary);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(palette.accent);
        TextView play = content.findViewById(R.id.customPreviewPlay);
        play.setBackground(circle);
        play.setTextColor(palette.onAccent);
        TextView block = content.findViewById(R.id.customPreviewBlock);
        block.setBackgroundColor(palette.raised);
        block.setTextColor(palette.text);
        content.findViewById(R.id.customPreviewBar).setBackgroundColor(palette.text);
        TextView note = content.findViewById(R.id.customNudged);
        boolean nudged = palette.background != (chosenBackground | 0xFF000000)
                || palette.accent != (chosenAccent | 0xFF000000);
        note.setVisibility(nudged ? View.VISIBLE : View.GONE);
        note.setText(context.getString(R.string.look_custom_nudged));
    }
}
