package de.danoeh.antennapod.smartplay.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import de.danoeh.antennapod.BuildConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.smartplay.support.LogScrubber;
import de.danoeh.antennapod.system.CrashReportWriter;

public final class Feedback {
    private static final String TAG = "Feedback";

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
        send.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.feedback_choose)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.feedback_no_email, Toast.LENGTH_LONG).show();
        }
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
