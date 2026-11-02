package de.danoeh.antennapod.smartplay.backup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

public final class BackupFiles {
    public static final String EXTENSION = ".yonder";
    private static final Pattern AUTOMATIC = Pattern.compile("Yonder-auto-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.yonder");
    public static final int DEFAULT_KEEP = 5;

    public static final class Found {
        public final String name;
        public final long modified;

        public Found(String name, long modified) {
            this.name = name;
            this.modified = modified;
        }
    }

    private BackupFiles() {
    }

    public static String manualName(long time, TimeZone zone) {
        return "Yonder-backup-" + stamp(time, zone) + EXTENSION;
    }

    public static String automaticName(long time, TimeZone zone) {
        return "Yonder-auto-" + stamp(time, zone) + EXTENSION;
    }

    public static boolean isAutomatic(String name) {
        return name != null && AUTOMATIC.matcher(name).matches();
    }

    public static List<String> toDelete(List<Found> files, int keep) {
        List<Found> automatic = new ArrayList<>();
        for (Found file : files) {
            if (isAutomatic(file.name)) {
                automatic.add(file);
            }
        }
        Collections.sort(automatic, (a, b) -> {
            int byTime = Long.compare(b.modified, a.modified);
            return byTime != 0 ? byTime : b.name.compareTo(a.name);
        });
        List<String> result = new ArrayList<>();
        for (int i = Math.max(1, keep); i < automatic.size(); i++) {
            result.add(automatic.get(i).name);
        }
        return result;
    }

    public static long intervalDays(String frequency) {
        return "weekly".equals(frequency) ? 7 : 1;
    }

    private static String stamp(long time, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US);
        format.setTimeZone(zone);
        return format.format(new Date(time));
    }
}
