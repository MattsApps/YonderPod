package de.danoeh.antennapod.smartplay.backup;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BackupCryptoTest {
    private static final byte[] DATA = "PK backup contents with a private feed link".getBytes(StandardCharsets.UTF_8);

    private static byte[] encrypted(String password) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream out = BackupCrypto.encrypt(bytes, password, 2000)) {
            out.write(DATA);
        }
        return bytes.toByteArray();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    @Test
    public void roundTrip() throws IOException {
        byte[] locked = encrypted("correct horse");
        assertTrue(BackupCrypto.isEncrypted(locked));
        try (InputStream in = BackupCrypto.decrypt(new ByteArrayInputStream(locked), "correct horse")) {
            assertArrayEquals(DATA, readAll(in));
        }
    }

    @Test
    public void contentIsNotReadable() throws IOException {
        String locked = new String(encrypted("pw"), StandardCharsets.ISO_8859_1);
        assertFalse(locked.contains("private feed link"));
    }

    @Test
    public void wrongPasswordIsRefused() throws IOException {
        byte[] locked = encrypted("right");
        try {
            BackupCrypto.decrypt(new ByteArrayInputStream(locked), "wrong");
            fail();
        } catch (BackupCrypto.WrongPasswordException expected) {
            assertTrue(true);
        }
    }

    @Test
    public void plainZipIsNotEncrypted() {
        assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004rest".getBytes(StandardCharsets.ISO_8859_1)));
    }
}
