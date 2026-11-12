package de.danoeh.antennapod.smartplay.radio;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RecordingCipherTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final class TestKeys implements RecordingCipher.KeyWrap {
        private final SecretKeySpec key;

        TestKeys(byte seed) {
            byte[] bytes = new byte[32];
            Arrays.fill(bytes, seed);
            key = new SecretKeySpec(bytes, "AES");
        }

        @Override
        public byte[] wrap(byte[] plain) throws GeneralSecurityException {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain);
            byte[] result = new byte[12 + sealed.length];
            System.arraycopy(iv, 0, result, 0, 12);
            System.arraycopy(sealed, 0, result, 12, sealed.length);
            return result;
        }

        @Override
        public byte[] unwrap(byte[] wrapped) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, wrapped, 0, 12));
            return cipher.doFinal(wrapped, 12, wrapped.length - 12);
        }
    }

    private static byte[] audio(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data);
        byte[] marker = "ID3 radio show audio".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(marker, 0, data, 0, marker.length);
        return data;
    }

    private File encrypted(byte[] plain, RecordingCipher.KeyWrap keys) throws IOException {
        File source = folder.newFile("show.m4a");
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write(plain);
        }
        File target = new File(folder.getRoot(), "show" + RecordingCipher.EXTENSION);
        RecordingCipher.encrypt(source, target, keys);
        return target;
    }

    @Test
    public void roundTripsFromAnyPosition() throws IOException {
        byte[] plain = audio(200_003);
        TestKeys keys = new TestKeys((byte) 7);
        File file = encrypted(plain, keys);
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, keys)) {
            assertEquals(plain.length, reader.length());
            byte[] all = new byte[plain.length];
            int done = 0;
            while (done < all.length) {
                int read = reader.read(done, all, done, Math.min(65_536, all.length - done));
                assertTrue(read > 0);
                done += read;
            }
            assertArrayEquals(plain, all);
            for (long position : new long[] {0, 1, 15, 16, 17, 4095, 100_000, 199_990}) {
                byte[] part = new byte[13];
                int read = reader.read(position, part, 0, part.length);
                assertArrayEquals(Arrays.copyOfRange(plain, (int) position, (int) position + read),
                        Arrays.copyOf(part, read));
            }
            assertEquals(-1, reader.read(plain.length, new byte[4], 0, 4));
        }
    }

    @Test
    public void matchesStandardAesCounterMode() throws Exception {
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 0xff);
        new Random(1).nextBytes(key);
        byte[] plain = audio(1000);
        Cipher standard = Cipher.getInstance("AES/CTR/NoPadding");
        standard.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] expected = standard.doFinal(plain);
        Cipher blocks = Cipher.getInstance("AES/ECB/NoPadding");
        blocks.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] ours = plain.clone();
        RecordingCipher.xorKeystream(blocks, iv, 0, ours, 0, 500);
        RecordingCipher.xorKeystream(blocks, iv, 500, ours, 500, 500);
        assertArrayEquals(expected, ours);
    }

    @Test
    public void aCopiedFileIsUselessWithoutTheKey() throws IOException {
        byte[] plain = audio(10_000);
        File file = encrypted(plain, new TestKeys((byte) 7));
        byte[] stored = Files.readAllBytes(file.toPath());
        String asText = new String(stored, StandardCharsets.US_ASCII);
        assertFalse(asText.contains("ID3 radio show audio"));
        try {
            RecordingCipher.open(file, new TestKeys((byte) 8)).close();
            fail("Opened with the wrong key");
        } catch (IOException expected) {
            assertTrue(expected.getCause() instanceof GeneralSecurityException);
        }
    }

    @Test
    public void rejectsAPlainFile() throws IOException {
        File plain = folder.newFile("plain.mp3");
        try (FileOutputStream out = new FileOutputStream(plain)) {
            out.write(audio(100));
        }
        try {
            RecordingCipher.open(plain, new TestKeys((byte) 7)).close();
            fail("Opened a plain file");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().startsWith("Not a Yonder recording"));
        }
    }

    @Test
    public void encryptsAStream() throws IOException {
        byte[] plain = audio(70_000);
        TestKeys keys = new TestKeys((byte) 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RecordingCipher.encrypt(new ByteArrayInputStream(plain), out, keys);
        File file = folder.newFile("stream" + RecordingCipher.EXTENSION);
        try (FileOutputStream write = new FileOutputStream(file)) {
            write.write(out.toByteArray());
        }
        assertTrue(RecordingCipher.isEncrypted(file.getPath()));
        assertFalse(RecordingCipher.isEncrypted("show.m4a"));
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, keys)) {
            byte[] start = new byte[64];
            assertEquals(64, reader.read(0, start, 0, 64));
            assertArrayEquals(Arrays.copyOf(plain, 64), start);
        }
    }

    @Test
    public void writesEncryptedAsItGoesAndAPartialFileStillOpens() throws IOException {
        byte[] plain = audio(200_000);
        TestKeys keys = new TestKeys((byte) 9);
        File file = folder.newFile("live.part" + RecordingCipher.EXTENSION);
        FileOutputStream raw = new FileOutputStream(file);
        java.io.OutputStream out = RecordingCipher.writer(raw, keys);
        // Odd-sized writes, as segments arrive from a stream.
        int at = 0;
        int[] sizes = {1, 15, 17, 1000, 33_333, 65_536, 70_000};
        for (int size : sizes) {
            out.write(plain, at, size);
            at += size;
        }
        // Nothing readable reaches the file, even before it's closed (as after a force stop).
        byte[] onDisk = Files.readAllBytes(file.toPath());
        assertFalse(new String(onDisk, StandardCharsets.ISO_8859_1).contains("ID3 radio show audio"));
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, keys)) {
            assertEquals(at, reader.length());
            byte[] back = new byte[at];
            int read = 0;
            while (read < at) {
                read += reader.read(read, back, read, at - read);
            }
            assertArrayEquals(Arrays.copyOf(plain, at), back);
        }
        out.write(plain, at, plain.length - at);
        out.close();
        try (RecordingCipher.Reader reader = RecordingCipher.open(file, keys)) {
            byte[] back = new byte[plain.length];
            int read = 0;
            while (read < plain.length) {
                read += reader.read(read, back, read, plain.length - read);
            }
            assertArrayEquals(plain, back);
        }
    }

    @Test
    public void theWriterDoesNotChangeTheCallersBuffer() throws IOException {
        byte[] plain = audio(1000);
        byte[] copy = plain.clone();
        RecordingCipher.writer(new ByteArrayOutputStream(), new TestKeys((byte) 1)).write(plain, 0, plain.length);
        assertArrayEquals(copy, plain);
    }
}
