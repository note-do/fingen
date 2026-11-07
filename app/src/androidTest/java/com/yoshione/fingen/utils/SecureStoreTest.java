package com.yoshione.fingen.utils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers encrypt/decrypt on Android Keystore, including the key loss cases.
 */
@RunWith(AndroidJUnit4.class)
public class SecureStoreTest {

    private static void deleteKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(SecureStore.KEY_ALIAS)) {
            keyStore.deleteEntry(SecureStore.KEY_ALIAS);
        }
    }

    @Test
    public void roundTrip_plainEmptyAndUnicode() {
        String[] values = {"p@ssw0rd", "", "Пароль é中文 😀"};
        for (String value : values) {
            String encrypted = SecureStore.encrypt(value);
            assertNotNull(encrypted);
            assertTrue(encrypted.startsWith("v1:"));
            assertEquals(value, SecureStore.decrypt(encrypted));
        }
    }

    @Test
    public void sameValue_differentIv() {
        String first = SecureStore.encrypt("secret");
        String second = SecureStore.encrypt("secret");
        assertNotNull(first);
        assertNotEquals(first, second);
        assertEquals("secret", SecureStore.decrypt(first));
        assertEquals("secret", SecureStore.decrypt(second));
    }

    @Test
    public void brokenInput_returnsNull() {
        assertNull(SecureStore.decrypt(null));
        assertNull(SecureStore.decrypt(""));
        assertNull(SecureStore.decrypt("plain text"));
        assertNull(SecureStore.decrypt("v1:"));
        assertNull(SecureStore.decrypt("v1:!!!not base64!!!"));
        assertNull(SecureStore.decrypt("v1:AAAA"));
        String encrypted = SecureStore.encrypt("secret");
        assertNotNull(encrypted);
        // damaged ciphertext: GCM tag check must fail
        char last = encrypted.charAt(encrypted.length() - 5);
        String damaged = encrypted.substring(0, encrypted.length() - 5) + (last == 'A' ? 'B' : 'A')
                + encrypted.substring(encrypted.length() - 4);
        assertNull(SecureStore.decrypt(damaged));
    }

    @Test
    public void deletedKey_decryptReturnsNull_newEncryptWorks() throws Exception {
        String encrypted = SecureStore.encrypt("secret");
        assertNotNull(encrypted);

        deleteKey();

        assertNull(SecureStore.decrypt(encrypted));
        String fresh = SecureStore.encrypt("new secret");
        assertNotNull(fresh);
        assertEquals("new secret", SecureStore.decrypt(fresh));
        // value of the old key is still not readable
        assertNull(SecureStore.decrypt(encrypted));
    }

    @Test
    public void twoThreads_noFailures() throws Exception {
        deleteKey();
        final AtomicInteger errors = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        Thread[] threads = new Thread[2];
        for (int t = 0; t < threads.length; t++) {
            final int id = t;
            threads[t] = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 20; i++) {
                        String value = "thread" + id + "-" + i;
                        if (!value.equals(SecureStore.decrypt(SecureStore.encrypt(value)))) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
            threads[t].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(0, errors.get());
    }
}
