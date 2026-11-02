package de.danoeh.antennapod.smartplay.backup;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupFilesTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void names() {
        long time = 1793600000000L;
        assertEquals("Yonder-auto-2026-11-02-0613.yonder", BackupFiles.automaticName(time, UTC));
        assertEquals("Yonder-backup-2026-11-02-0613.yonder", BackupFiles.manualName(time, UTC));
        assertTrue(BackupFiles.isAutomatic(BackupFiles.automaticName(time, UTC)));
        assertFalse(BackupFiles.isAutomatic(BackupFiles.manualName(time, UTC)));
    }

    @Test
    public void keepsNewestAutomaticOnly() {
        List<BackupFiles.Found> files = Arrays.asList(
                new BackupFiles.Found("Yonder-auto-2026-11-01-0400.yonder", 100),
                new BackupFiles.Found("Yonder-auto-2026-11-02-0400.yonder", 200),
                new BackupFiles.Found("Yonder-auto-2026-11-03-0400.yonder", 300),
                new BackupFiles.Found("Yonder-backup-2026-10-01-0900.yonder", 10),
                new BackupFiles.Found("holiday photo.jpg", 5));
        assertEquals(Arrays.asList("Yonder-auto-2026-11-01-0400.yonder"), BackupFiles.toDelete(files, 2));
    }

    @Test
    public void neverDeletesTheOnlyBackup() {
        List<BackupFiles.Found> files = Arrays.asList(
                new BackupFiles.Found("Yonder-auto-2026-11-01-0400.yonder", 100),
                new BackupFiles.Found("Yonder-auto-2026-11-02-0400.yonder", 200));
        assertEquals(Arrays.asList("Yonder-auto-2026-11-01-0400.yonder"), BackupFiles.toDelete(files, 0));
    }

    @Test
    public void intervals() {
        assertEquals(1, BackupFiles.intervalDays("daily"));
        assertEquals(7, BackupFiles.intervalDays("weekly"));
    }
}
