package com.yoshione.fingen.utils;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.interfaces.IOnUnzipComplete;
import com.yoshione.fingen.utils.winzipaes.impl.AESDecrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.AESEncrypterBC;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks WinZip AES (PBKDF2-SHA1, AES-CTR with little-endian counter, HMAC-SHA1) of the backup encryption:
 * known-answer checks against JCA and a round trip through the same FileUtils calls the backup uses.
 */
@RunWith(AndroidJUnit4.class)
public class AESEncrypterRoundTripTest {

    private static final String PASSWORD = "p@ssw0rd-test";

    private File mDir;

    @Before
    public void setUp() throws IOException {
        mDir = new File(InstrumentationRegistry.getTargetContext().getCacheDir(), "aes_test_" + System.nanoTime());
        assertTrue(mDir.mkdirs());
    }

    @After
    public void tearDown() {
        File[] files = mDir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        mDir.delete();
    }

    @Test
    public void encrypter_matchesJcaPrimitives() throws Exception {
        byte[] plain = new byte[40];
        for (int i = 0; i < plain.length; i++) {
            plain[i] = (byte) (i * 7 + 3);
        }

        AESEncrypterBC encrypter = new AESEncrypterBC();
        encrypter.init(PASSWORD, 256);
        byte[] salt = encrypter.getSalt();
        byte[] data = plain.clone();
        encrypter.encrypt(data, data.length);

        // PBKDF2-HMAC-SHA1, 1000 iterations, 32 (AES key) + 32 (HMAC key) + 2 (verifier) bytes
        PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray(), salt, 1000, (32 + 32 + 2) * 8);
        byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        byte[] aesKey = Arrays.copyOfRange(derived, 0, 32);
        byte[] macKey = Arrays.copyOfRange(derived, 32, 64);
        byte[] verifier = Arrays.copyOfRange(derived, 64, 66);

        assertArrayEquals(verifier, encrypter.getPwVerification());

        // AES-CTR keystream: AES-ECB of 16-byte little-endian counter starting at 1
        Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
        ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
        byte[] expected = new byte[plain.length];
        for (int block = 0; block * 16 < plain.length; block++) {
            byte[] counter = new byte[16];
            counter[0] = (byte) (block + 1);
            byte[] keystream = ecb.doFinal(counter);
            for (int i = 0; i < 16 && block * 16 + i < plain.length; i++) {
                expected[block * 16 + i] = (byte) (plain[block * 16 + i] ^ keystream[i]);
            }
        }
        assertArrayEquals(expected, data);

        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec(macKey, "HmacSHA1"));
        byte[] expectedMac = Arrays.copyOf(hmac.doFinal(expected), 10);
        assertArrayEquals(expectedMac, encrypter.getFinalAuthentication());
    }

    @Test
    public void decrypter_restoresBytes() throws Exception {
        byte[] plain = "Fingen backup тест 0123456789".getBytes("UTF-8");

        AESEncrypterBC encrypter = new AESEncrypterBC();
        encrypter.init(PASSWORD, 256);
        byte[] data = plain.clone();
        encrypter.encrypt(data, data.length);
        assertFalse(Arrays.equals(plain, data));
        byte[] encMac = encrypter.getFinalAuthentication();

        AESDecrypterBC decrypter = new AESDecrypterBC();
        decrypter.init(PASSWORD, 256, encrypter.getSalt(), encrypter.getPwVerification());
        decrypter.decrypt(data, data.length);
        assertArrayEquals(plain, data);
        assertArrayEquals(encMac, decrypter.getFinalAuthentication());
    }

    @Test
    public void backupFile_roundTrip() throws Exception {
        File dir = new File(mDir, "in");
        assertTrue(dir.mkdirs());
        File in = new File(dir, "fingen.db");
        byte[] content = new byte[5000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31 + i / 7);
        }
        FileOutputStream out = new FileOutputStream(in);
        try {
            out.write(content);
        } finally {
            out.close();
        }
        File zip = new File(mDir, "backup.zip");

        FileUtils.zipAndEncrypt(in.getAbsolutePath(), zip.getAbsolutePath(), PASSWORD);
        assertTrue(zip.length() > 0);

        Result result = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), PASSWORD, result);
        assertEquals("complete", result.state);
        assertArrayEquals(content, readAll(new File(mDir, "fingen.db.ex")));

        Result wrong = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), PASSWORD + "x", wrong);
        assertEquals("wrongPassword", wrong.state);
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        try {
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
        } finally {
            in.close();
        }
        return buf;
    }

    private static class Result implements IOnUnzipComplete {
        String state = "none";

        @Override
        public void onComplete() {
            state = "complete";
        }

        @Override
        public void onError() {
            state = "error";
        }

        @Override
        public void onWrongPassword() {
            state = "wrongPassword";
        }
    }
}
