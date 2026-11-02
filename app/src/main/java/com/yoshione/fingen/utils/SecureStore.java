package com.yoshione.fingen.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts small secrets (backup password, Dropbox credential) with an AES-256-GCM key
 * stored in Android Keystore. The key never leaves the device and is not part of Auto Backup,
 * so on a new device or after a Keystore failure decrypt() returns null and the secret must be entered again.
 * Format of the encrypted value: "v1:" + Base64(iv(12) || ciphertext || tag).
 */
public class SecureStore {

    /**
     * Prefs file for encrypted secrets. Excluded from Auto Backup (res/xml/backup_rules.xml, data_extraction_rules.xml)
     */
    public static final String PREFS_NAME = "com.yoshione.fingen.secure";

    private static final String TAG = "SecureStore";
    private static final String KEYSTORE = "AndroidKeyStore";
    static final String KEY_ALIAS = "fingen_secure_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final Object LOCK = new Object();

    private SecureStore() {
    }

    /**
     * @return encrypted value or null on any error (never throws)
     */
    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            synchronized (LOCK) {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
                byte[] iv = cipher.getIV();
                byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
                byte[] result = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, result, 0, iv.length);
                System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
                return PREFIX + Base64.encodeToString(result, Base64.NO_WRAP);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't encrypt: " + e);
            return null;
        }
    }

    /**
     * @return plain text or null if the value is damaged, was encrypted with another key or Keystore is unavailable
     */
    public static String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] data = Base64.decode(encrypted.substring(PREFIX.length()), Base64.NO_WRAP);
            if (data.length < IV_LENGTH + TAG_LENGTH_BITS / 8) {
                return null;
            }
            synchronized (LOCK) {
                KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
                keyStore.load(null);
                SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
                if (key == null) {
                    return null;
                }
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH));
                byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
                return new String(plain, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Wrong key or damaged data are normal cases (new device), keep the key as is
            Log.w(TAG, "Can't decrypt: " + e);
            return null;
        }
    }

    // Must be called under LOCK
    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key != null) {
            return key;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }

    // Damaged key: drop it, the next encrypt() creates a new one
    private static void resetKey() {
        try {
            synchronized (LOCK) {
                KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
                keyStore.load(null);
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't reset key: " + e);
        }
    }
}
