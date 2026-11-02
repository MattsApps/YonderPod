package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.worlds.WorldPages;
import de.danoeh.antennapod.storage.preferences.UserPreferences;

public final class AppShelves {
    private static final Set<String> SUGGESTED = new HashSet<>(Arrays.asList(
            "com.spotify.music",
            "com.audible.application",
            "bbc.iplayer.radio",
            "com.google.android.apps.youtube.music",
            "com.soundcloud.android",
            "com.mixcloud.player",
            "com.apple.android.music",
            "deezer.android.app",
            "com.aspiro.tidal",
            "com.amazon.mp3",
            "com.overdrive.mobile.android.libby",
            "grit.storytel.app",
            "com.clearchannel.iheartradio.controller",
            "tunein.player",
            "com.bandcamp.android"));

    public static final class App {
        public final String packageName;
        public final CharSequence label;
        public final Drawable icon;
        final boolean suggested;

        App(String packageName, CharSequence label, Drawable icon, boolean suggested) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.suggested = suggested;
        }
    }

    private AppShelves() {
    }

    public static boolean isRight(int shelf) {
        return shelf == WorldPages.RIGHT_SHELF;
    }

    public static WorldPages pages(Context context) {
        return new WorldPages(UserPreferences.isWorldShown(WorldPages.BOOKS),
                UserPreferences.isWorldShown(WorldPages.RADIO),
                installed(context, false).size(), installed(context, true).size());
    }

    public static List<App> installed(Context context, boolean right) {
        PackageManager packageManager = context.getPackageManager();
        List<App> apps = new ArrayList<>();
        for (String packageName : UserPreferences.getShelfApps(right)) {
            App app = load(packageManager, packageName);
            if (app != null) {
                apps.add(app);
            }
        }
        return apps;
    }

    public static void save(boolean right, List<App> apps) {
        List<String> packages = new ArrayList<>();
        for (App app : apps) {
            packages.add(app.packageName);
        }
        UserPreferences.setShelfApps(right, packages);
    }

    public static List<App> launchable(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        List<App> apps = new ArrayList<>();
        for (ResolveInfo info : packageManager.queryIntentActivities(launcher, 0)) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName()) || !seen.add(packageName)) {
                continue;
            }
            App app = load(packageManager, packageName);
            if (app != null) {
                apps.add(app);
            }
        }
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (a, b) -> {
            if (a.suggested != b.suggested) {
                return a.suggested ? -1 : 1;
            }
            return collator.compare(a.label.toString(), b.label.toString());
        });
        return apps;
    }

    @Nullable
    private static App load(PackageManager packageManager, String packageName) {
        if (packageManager.getLaunchIntentForPackage(packageName) == null) {
            return null;
        }
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            boolean audio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && info.category == ApplicationInfo.CATEGORY_AUDIO;
            return new App(packageName, packageManager.getApplicationLabel(info),
                    packageManager.getApplicationIcon(info), audio || SUGGESTED.contains(packageName));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    public static void open(Activity activity, App app, @Nullable Runnable afterOpen) {
        if (UserPreferences.isShelfTipShown()) {
            launch(activity, app, afterOpen);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.shelf_tip_title)
                .setMessage(R.string.shelf_tip_message)
                .setPositiveButton(R.string.shelf_tip_ok, (dialog, which) -> {
                    UserPreferences.setShelfTipShown();
                    launch(activity, app, afterOpen);
                })
                .setOnCancelListener(dialog -> {
                    if (afterOpen != null) {
                        afterOpen.run();
                    }
                })
                .show();
    }

    private static void launch(Activity activity, App app, @Nullable Runnable afterOpen) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(app.packageName);
        try {
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, activity.getString(R.string.shelf_cannot_open, app.label),
                    Toast.LENGTH_SHORT).show();
        }
        if (afterOpen != null) {
            afterOpen.run();
        }
    }
}
