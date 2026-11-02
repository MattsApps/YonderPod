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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.common.CustomPalettes;
import de.danoeh.antennapod.ui.common.Palette;
import de.danoeh.antennapod.ui.common.ThemeUtils;
import de.danoeh.antennapod.ui.widget.WidgetUpdaterWorker;

public final class LookDialogs {
    public static final int EVERYWHERE = -1;
    private static final String[] WORLD_DEFAULTS = {"plum", "charcoal", "night"};
    private static final String NONE = "";

    private LookDialogs() {
    }

    public static void changed(Activity activity) {
        WidgetUpdaterWorker.enqueueWork(activity);
        activity.recreate();
    }

    public static void choose(Activity activity, int target, boolean night, String key) {
        if (night) {
            UserPreferences.setWorldNightPalette(target == EVERYWHERE ? Palette.PODCASTS : target, key);
        } else if (target == EVERYWHERE) {
            UserPreferences.setPalette(key);
        } else {
            UserPreferences.setWorldPalette(target, key);
        }
        changed(activity);
    }

    public static View strip(Activity activity, @Nullable String label, String selected, int target,
                             boolean night) {
        Context context = activity;
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, (int) (10 * density), 0, (int) (2 * density));
        if (label != null) {
            TextView name = new TextView(context);
            TextViewCompat.setTextAppearance(name, R.style.TextAppearance_WildInk_DetailStrong);
            name.setText(label);
            column.addView(name);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
        if (night) {
            row.addView(swatch(context, null, NONE.equals(selected), context.getString(R.string.look_night_none),
                    "–", v -> choose(activity, target, true, NONE), null));
        }
        for (String key : Palette.keys()) {
            Palette palette = Palette.of(key);
            boolean isSelected = key.equals(selected);
            String description = palette.label != null ? palette.label : context.getString(palette.name);
            View.OnLongClickListener longClick = CustomPalettes.isCustom(key)
                    ? v -> {
                        customMenu(activity, v, key);
                        return true;
                    } : null;
            row.addView(swatch(context, palette, isSelected, description, "",
                    v -> choose(activity, target, night, key), longClick));
        }
        if (Palette.customSupported()) {
            row.addView(swatch(context, null, false, context.getString(R.string.look_new), "+",
                    v -> editor(activity, null, target, night, 0xFF1C1B19, 0xFFFF5A2B), null));
        }
        scroller.addView(row);
        column.addView(scroller);
        return column;
    }

    private static View swatch(Context context, @Nullable Palette palette, boolean selected, String description,
                               String symbol, View.OnClickListener onClick,
                               @Nullable View.OnLongClickListener onLongClick) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout holder = new LinearLayout(context);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout circle = new FrameLayout(context);
        GradientDrawable ground = new GradientDrawable();
        ground.setShape(GradientDrawable.OVAL);
        int secondary = colourOf(context, android.R.attr.textColorSecondary);
        if (palette != null) {
            ground.setColor(palette.background);
            ground.setStroke((int) ((selected ? 4 : 2) * density),
                    selected ? colourOf(context, android.R.attr.textColorPrimary) : palette.raised);
            View dot = new View(context);
            GradientDrawable accent = new GradientDrawable();
            accent.setShape(GradientDrawable.OVAL);
            accent.setColor(palette.accent);
            dot.setBackground(accent);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams((int) (12 * density),
                    (int) (12 * density));
            dotParams.gravity = Gravity.CENTER;
            circle.addView(dot, dotParams);
        } else {
            ground.setColor(0x00000000);
            ground.setStroke((int) ((selected ? 3 : 2) * density),
                    selected ? colourOf(context, R.attr.colorPrimary) : secondary);
            TextView plus = new TextView(context);
            plus.setText(symbol);
            plus.setTextColor(secondary);
            plus.setTextSize(18);
            plus.setGravity(Gravity.CENTER);
            circle.addView(plus, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        circle.setBackground(ground);
        circle.setContentDescription(description);
        circle.setSelected(selected);
        circle.setOnClickListener(onClick);
        if (onLongClick != null) {
            circle.setOnLongClickListener(onLongClick);
        }
        holder.addView(circle, new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density)));
        if (palette == null || palette.label != null) {
            TextView name = new TextView(context);
            name.setText(palette != null ? palette.label : description);
            name.setTextSize(11);
            name.setTextColor(secondary);
            name.setMaxLines(1);
            name.setGravity(Gravity.CENTER);
            holder.addView(name, new LinearLayout.LayoutParams((int) (56 * density),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd((int) (4 * density));
        holder.setMinimumWidth((int) (52 * density));
        holder.setLayoutParams(params);
        return holder;
    }

    private static int colourOf(Context context, int attr) {
        return ThemeUtils.getColorFromAttr(context, attr);
    }

    private static void customMenu(Activity activity, View anchor, String key) {
        CustomPalettes.Entry entry = CustomPalettes.get(key);
        if (entry == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 0, 0, R.string.look_edit);
        menu.getMenu().add(0, 1, 1, R.string.look_rename);
        menu.getMenu().add(0, 2, 2, R.string.look_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                editor(activity, key, EVERYWHERE, false, entry.background, entry.accent);
            } else if (item.getItemId() == 1) {
                rename(activity, entry);
            } else {
                confirmDelete(activity, entry);
            }
            return true;
        });
        menu.show();
    }

    private static void rename(Activity activity, CustomPalettes.Entry entry) {
        EditText name = new EditText(activity);
        name.setText(entry.name);
        name.setSelectAllOnFocus(true);
        FrameLayout frame = new FrameLayout(activity);
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
        frame.setPadding(padding, 0, padding, 0);
        frame.addView(name);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.look_rename)
                .setView(frame)
                .setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                    String chosen = name.getText().toString().trim();
                    CustomPalettes.update(entry.key(), chosen.isEmpty() ? entry.name : chosen,
                            entry.background, entry.accent);
                    changed(activity);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    private static void confirmDelete(Activity activity, CustomPalettes.Entry entry) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.look_delete_question, entry.name))
                .setMessage(R.string.look_delete_message)
                .setPositiveButton(R.string.look_delete, (dialog, which) -> {
                    String key = entry.key();
                    CustomPalettes.delete(key);
                    if (key.equals(UserPreferences.getPalette())) {
                        UserPreferences.setPalette("charcoal");
                    }
                    for (int world = Palette.BOOKS; world <= Palette.RADIO; world++) {
                        if (key.equals(UserPreferences.getWorldPalette(world))) {
                            UserPreferences.setWorldPalette(world, WORLD_DEFAULTS[world]);
                        }
                        if (key.equals(UserPreferences.getWorldNightPalette(world))) {
                            UserPreferences.setWorldNightPalette(world, NONE);
                        }
                    }
                    changed(activity);
                })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
    }

    public static void nightPalettes(Activity activity) {
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (24 * density), 0, (int) (24 * density), 0);
        TextView hint = new TextView(activity);
        TextViewCompat.setTextAppearance(hint, R.style.TextAppearance_WildInk_Detail);
        hint.setText(R.string.look_night_hint);
        content.addView(hint);
        if (UserPreferences.isPaletteEverywhere()) {
            content.addView(strip(activity, null, Palette.nightKey(Palette.PODCASTS), EVERYWHERE, true));
        } else {
            int[] labels = {R.string.world_short_books, R.string.world_short_podcasts, R.string.world_short_radio};
            for (int world = Palette.BOOKS; world <= Palette.RADIO; world++) {
                if (!UserPreferences.isWorldShown(world)) {
                    continue;
                }
                content.addView(strip(activity, activity.getString(labels[world]), Palette.nightKey(world), world,
                        true));
            }
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.look_night)
                .setView(content)
                .setPositiveButton(R.string.confirm_label, null)
                .show();
    }

    public static void wallpaper(Activity activity, int target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            Toast.makeText(activity, R.string.look_wallpaper_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperColors colours;
        try {
            colours = WallpaperManager.getInstance(activity).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
        } catch (Exception e) {
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
        editor(activity, null, target, false, background, accent);
    }

    public static void editor(Activity activity, @Nullable String existingKey, int target, boolean night,
                              int startBackground, int startAccent) {
        View content = LayoutInflater.from(activity).inflate(R.layout.look_custom_dialog, null);
        int[] background = {startBackground | 0xFF000000};
        int[] accent = {startAccent | 0xFF000000};
        EditText name = content.findViewById(R.id.customName);
        CustomPalettes.Entry existing = existingKey != null ? CustomPalettes.get(existingKey) : null;
        name.setText(existing != null ? existing.name
                : CustomPalettes.suggestName(activity.getString(R.string.look_custom_name_pattern)));
        int world = target == EVERYWHERE ? Palette.PODCASTS : target;
        int[] worldNames = {R.string.world_books, R.string.world_podcasts, R.string.world_radio};
        ((TextView) content.findViewById(R.id.customPreviewWorld)).setText(worldNames[world]);
        Runnable preview = () -> showPreview(activity, content, background[0], accent[0]);
        bindSliders(content, R.id.customBackgroundHue, R.id.customBackgroundSaturation,
                R.id.customBackgroundLightness, background, preview);
        bindSliders(content, R.id.customAccentHue, R.id.customAccentSaturation,
                R.id.customAccentLightness, accent, preview);
        preview.run();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(existing != null ? R.string.look_edit_custom : R.string.look_new_custom)
                .setView(content)
                .setPositiveButton(existing != null ? R.string.look_custom_save_edit : R.string.look_custom_save,
                        (dialog, which) -> {
                            String chosenName = name.getText().toString().trim();
                            if (existing != null) {
                                CustomPalettes.update(existing.key(),
                                        chosenName.isEmpty() ? existing.name : chosenName,
                                        background[0], accent[0]);
                                changed(activity);
                            } else {
                                String key = CustomPalettes.add(chosenName.isEmpty()
                                        ? CustomPalettes.suggestName(
                                                activity.getString(R.string.look_custom_name_pattern))
                                        : chosenName, background[0], accent[0]);
                                choose(activity, target, night, key);
                            }
                        })
                .setNegativeButton(R.string.cancel_label, null)
                .show();
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
        Palette palette = Palette.custom("", "", chosenBackground, chosenAccent, 0);
        content.findViewById(R.id.customPreview).setBackgroundColor(palette.background);
        ((TextView) content.findViewById(R.id.customPreviewWorld)).setTextColor(palette.accent);
        ((TextView) content.findViewById(R.id.customPreviewTitle)).setTextColor(palette.text);
        ((TextView) content.findViewById(R.id.customPreviewDetail)).setTextColor(palette.secondary);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(palette.accent);
        TextView play = content.findViewById(R.id.customPreviewPlay);
        play.setBackground(circle);
        play.setTextColor(palette.onAccent);
        content.findViewById(R.id.customPreviewBar).setBackgroundColor(palette.text);
        content.findViewById(R.id.customPreviewHandle).setBackgroundColor(palette.accent);
        TextView note = content.findViewById(R.id.customNudged);
        boolean nudged = palette.background != (chosenBackground | 0xFF000000)
                || palette.accent != (chosenAccent | 0xFF000000);
        note.setVisibility(nudged ? View.VISIBLE : View.GONE);
        note.setText(context.getString(R.string.look_custom_nudged));
    }
}
