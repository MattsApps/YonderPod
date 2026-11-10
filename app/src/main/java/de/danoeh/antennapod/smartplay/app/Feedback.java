package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.support.LogScrubber;
import de.danoeh.antennapod.system.CrashReportWriter;

public final class Feedback {
    private static final String TAG = "Feedback";
    private static final String GMAIL = "com.google.android.gm";

    private Feedback() {
    }

    public static String details(Context context) {
        return context.getString(R.string.feedback_details, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR, Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT);
    }

    public static String recentLog() {
        try {
            Process logcat = Runtime.getRuntime().exec(new String[] {"logcat", "-d", "-v", "time", "-t", "1500"});
            try (InputStream in = logcat.getInputStream()) {
                return LogScrubber.scrub(IOUtils.toString(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return "";
        }
    }

    public static String crashReport() {
        return LogScrubber.scrub(CrashReportWriter.read());
    }

    public static void send(Activity activity, boolean includeLog, boolean includeCrash) {
        StringBuilder attachment = new StringBuilder();
        if (includeCrash) {
            attachment.append("== crash ==\n").append(crashReport()).append('\n');
        }
        if (includeLog) {
            attachment.append("== recent log ==\n").append(recentLog());
        }
        Uri uri = attachment.length() > 0 ? writeAttachment(activity, attachment.toString()) : null;
        String body = activity.getString(includeCrash ? R.string.feedback_crash_body : R.string.feedback_body)
                + "\n\n" + details(activity);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_EMAIL, new String[] {activity.getString(R.string.yonder_feedback_email)})
                .putExtra(Intent.EXTRA_SUBJECT, activity.getString(includeCrash
                        ? R.string.feedback_crash_subject : R.string.feedback_subject, BuildConfig.VERSION_NAME))
                .putExtra(Intent.EXTRA_TEXT, body);
        if (uri != null) {
            send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        List<String> apps = emailApps(activity);
        if (apps.isEmpty()) {
            showAddress(activity);
            return;
        }
        if (apps.contains(GMAIL)) {
            apps.remove(GMAIL);
            apps.add(0, GMAIL);
        }
        try {
            if (apps.size() == 1 || GMAIL.equals(apps.get(0))) {
                activity.startActivity(new Intent(send).setPackage(apps.get(0)));
                return;
            }
            Intent chooser = Intent.createChooser(new Intent(send).setPackage(apps.get(0)),
                    activity.getString(R.string.feedback_choose));
            List<Intent> others = new ArrayList<>();
            for (int i = 1; i < apps.size(); i++) {
                others.add(new Intent(send).setPackage(apps.get(i)));
            }
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, others.toArray(new Intent[0]));
            activity.startActivity(chooser);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Could not open the email app", e);
            showAddress(activity);
        }
    }

    private static List<String> emailApps(Context context) {
        List<String> packages = new ArrayList<>();
        List<ResolveInfo> found = context.getPackageManager()
                .queryIntentActivities(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0);
        for (ResolveInfo info : found) {
            if (info.activityInfo != null && !packages.contains(info.activityInfo.packageName)) {
                packages.add(info.activityInfo.packageName);
            }
        }
        return packages;
    }

    private static void showAddress(Activity activity) {
        String address = activity.getString(R.string.yonder_feedback_email);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feedback_copy_title)
                .setMessage(activity.getString(R.string.feedback_copy_message, address))
                .setPositiveButton(R.string.feedback_copy, (dialog, which) -> {
                    ClipboardManager clipboard =
                            (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(address, address));
                        Toast.makeText(activity, R.string.feedback_copied, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.yonder_button_cancel, null)
                .show();
    }

    @Nullable
    private static Uri writeAttachment(Context context, String text) {
        try {
            File folder = new File(context.getCacheDir(), "feedback");
            FileUtils.deleteQuietly(folder);
            folder.mkdirs();
            File file = new File(folder, "yonder-report.txt");
            FileUtils.writeStringToFile(file, text, StandardCharsets.UTF_8);
            return FileProvider.getUriForFile(context, context.getString(R.string.provider_authority), file);
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Can't attach the log");
            return null;
        }
    }
}
