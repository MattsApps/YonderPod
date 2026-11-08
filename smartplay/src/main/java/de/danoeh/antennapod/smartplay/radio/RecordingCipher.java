package de.danoeh.antennapod.smartplay.radio;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class RecordingCipher {
    public static final String EXTENSION = ".yrec";
    private static final byte[] MAGIC = {'Y', 'R', 'E', 'C', 1};
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 16;
    private static final int BLOCK = 16;
    private static final int BUFFER = 64 * 1024;

    public interface KeyWrap {
        byte[] wrap(byte[] key) throws GeneralSecurityException;

        byte[] unwrap(byte[] wrapped) throws GeneralSecurityException;
    }

    private RecordingCipher() {
    }

    public static boolean isEncrypted(String path) {
        return path != null && path.endsWith(EXTENSION);
    }

    public static void encrypt(File in, File out, KeyWrap wrap) throws IOException {
        try (InputStream input = new FileInputStream(in); OutputStream output = new FileOutputStream(out)) {
            encrypt(input, output, wrap);
        }
    }

    public static void encrypt(InputStream in, OutputStream out, KeyWrap wrap) throws IOException {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[KEY_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(key);
        random.nextBytes(iv);
        try {
            byte[] wrapped = wrap.wrap(key);
            DataOutputStream header = new DataOutputStream(out);
            header.write(MAGIC);
            header.writeShort(wrapped.length);
            header.write(wrapped);
            header.write(iv);
            header.flush();
            Cipher blocks = blockCipher(key);
            byte[] buffer = new byte[BUFFER];
            long position = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                xorKeystream(blocks, iv, position, buffer, 0, read);
                out.write(buffer, 0, read);
                position += read;
            }
            out.flush();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public static Reader open(File file, KeyWrap wrap) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            byte[] magic = new byte[MAGIC.length];
            raf.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IOException("Not a Yonder recording: " + file.getName());
            }
            byte[] wrapped = new byte[raf.readUnsignedShort()];
            raf.readFully(wrapped);
            byte[] iv = new byte[IV_BYTES];
            raf.readFully(iv);
            byte[] key = wrap.unwrap(wrapped);
            try {
                return new Reader(raf, blockCipher(key), iv, raf.getFilePointer());
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        } catch (IOException e) {
            raf.close();
            throw e;
        } catch (GeneralSecurityException | RuntimeException e) {
            raf.close();
            throw new IOException(e);
        }
    }

    private static Cipher blockCipher(byte[] key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher;
    }

    static void xorKeystream(Cipher blocks, byte[] iv, long position, byte[] data, int offset, int length)
            throws GeneralSecurityException {
        if (length <= 0) {
            return;
        }
        long firstBlock = position / BLOCK;
        int skip = (int) (position % BLOCK);
        int count = (skip + length + BLOCK - 1) / BLOCK;
        byte[] counters = new byte[count * BLOCK];
        for (int i = 0; i < count; i++) {
            counter(iv, firstBlock + i, counters, i * BLOCK);
        }
        byte[] stream = blocks.doFinal(counters);
        for (int i = 0; i < length; i++) {
            data[offset + i] ^= stream[skip + i];
        }
    }

    private static void counter(byte[] iv, long add, byte[] out, int at) {
        int carry = 0;
        long value = add;
        for (int i = BLOCK - 1; i >= 0; i--) {
            int sum = (iv[i] & 0xff) + (int) (value & 0xff) + carry;
            out[at + i] = (byte) sum;
            carry = sum >> 8;
            value >>>= 8;
        }
    }

    public static final class Reader implements Closeable {
        private final RandomAccessFile file;
        private final Cipher blocks;
        private final byte[] iv;
        private final long dataStart;

        Reader(RandomAccessFile file, Cipher blocks, byte[] iv, long dataStart) {
            this.file = file;
            this.blocks = blocks;
            this.iv = iv;
            this.dataStart = dataStart;
        }

        public long length() throws IOException {
            return Math.max(0, file.length() - dataStart);
        }

        public synchronized int read(long position, byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position < 0 || position >= length()) {
                return -1;
            }
            file.seek(dataStart + position);
            int read = file.read(buffer, offset, length);
            if (read <= 0) {
                return -1;
            }
            try {
                xorKeystream(blocks, iv, position, buffer, offset, read);
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
            return read;
        }

        @Override
        public synchronized void close() throws IOException {
            file.close();
        }
    }
}
