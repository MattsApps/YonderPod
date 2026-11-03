package de.danoeh.antennapod.smartplay.app;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecretBox {
    private static final String TAG = "SecretBox";
    private static final String KEY_STORE = "AndroidKeyStore";
    private static final String ALIAS = "yonder-backup-password";
    private static final String PREFIX = "v1:";

    private SecretBox() {
    }

    static boolean isSealed(@Nullable String value) {
        return value != null && value.startsWith(PREFIX);
    }

    @Nullable
    static String seal(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(sealed, Base64.NO_WRAP);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Can't protect the password: " + e.getClass().getSimpleName());
            return null;
        }
    }

    @Nullable
    static String open(String sealed) {
        try {
            String[] parts = sealed.substring(PREFIX.length()).split(":");
            if (parts.length != 2) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            Log.e(TAG, "Can't read the password: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEY_STORE);
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            return (SecretKey) store.getKey(ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
