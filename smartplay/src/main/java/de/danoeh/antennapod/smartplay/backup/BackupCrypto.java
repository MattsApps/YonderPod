package de.danoeh.antennapod.smartplay.backup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class BackupCrypto {
    private static final byte[] MAGIC = "YNDRENC1".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int CHECK_BYTES = 16;
    private static final int DEFAULT_ITERATIONS = 120000;

    public static class WrongPasswordException extends IOException {
        public WrongPasswordException() {
            super("Wrong password");
        }
    }

    private BackupCrypto() {
    }

    public static boolean isEncrypted(byte[] header) {
        return header.length >= MAGIC.length && Arrays.equals(Arrays.copyOf(header, MAGIC.length), MAGIC);
    }

    public static int headerLength() {
        return MAGIC.length;
    }

    public static OutputStream encrypt(OutputStream out, String password) throws IOException {
        return encrypt(out, password, DEFAULT_ITERATIONS);
    }

    static OutputStream encrypt(OutputStream out, String password, int iterations) throws IOException {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);
        byte[] key = deriveKey(password, salt, iterations);
        DataOutputStream header = new DataOutputStream(out);
        header.write(MAGIC);
        header.writeInt(iterations);
        header.write(salt);
        header.write(iv);
        header.write(check(key));
        header.flush();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new CipherOutputStream(out, cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    public static InputStream decrypt(InputStream in, String password) throws IOException {
        DataInputStream header = new DataInputStream(in);
        byte[] magic = new byte[MAGIC.length];
        header.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Not an encrypted backup");
        }
        int iterations = header.readInt();
        if (iterations < 1000 || iterations > 10000000) {
            throw new IOException("Damaged backup");
        }
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        byte[] storedCheck = new byte[CHECK_BYTES];
        header.readFully(salt);
        header.readFully(iv);
        header.readFully(storedCheck);
        byte[] key = deriveKey(password, salt, iterations);
        if (!MessageDigest.isEqual(storedCheck, check(key))) {
            throw new WrongPasswordException();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new CipherInputStream(in, cipher);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] deriveKey(String password, byte[] salt, int iterations) throws IOException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, 256))
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static byte[] check(byte[] key) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("yonder-backup-check".getBytes(StandardCharsets.US_ASCII));
            digest.update(key);
            return Arrays.copyOf(digest.digest(), CHECK_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }
}
