package de.danoeh.antennapod.smartplay.radio;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class RecordingKeys implements RecordingCipher.KeyWrap {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "yonder_recordings";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static RecordingKeys instance;
    private SecretKey key;

    private RecordingKeys() {
    }

    public static synchronized RecordingKeys get() {
        if (instance == null) {
            instance = new RecordingKeys();
        }
        return instance;
    }

    private synchronized SecretKey key() throws GeneralSecurityException {
        if (key != null) {
            return key;
        }
        KeyStore store = KeyStore.getInstance(STORE);
        try {
            store.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        Key existing = store.getKey(ALIAS, null);
        if (existing instanceof SecretKey) {
            key = (SecretKey) existing;
            return key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        key = generator.generateKey();
        return key;
    }

    @Override
    public byte[] wrap(byte[] plain) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] sealed = cipher.doFinal(plain);
        byte[] result = new byte[IV_BYTES + sealed.length];
        System.arraycopy(iv, 0, result, 0, IV_BYTES);
        System.arraycopy(sealed, 0, result, IV_BYTES, sealed.length);
        return result;
    }

    @Override
    public byte[] unwrap(byte[] wrapped) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES));
        return cipher.doFinal(wrapped, IV_BYTES, wrapped.length - IV_BYTES);
    }
}
