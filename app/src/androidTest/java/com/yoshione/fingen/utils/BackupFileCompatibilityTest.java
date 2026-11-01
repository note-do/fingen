package com.yoshione.fingen.utils;

import android.database.sqlite.SQLiteDatabase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.interfaces.IOnUnzipComplete;
import com.yoshione.fingen.utils.winzipaes.AesZipFileDecrypter;
import com.yoshione.fingen.utils.winzipaes.impl.AESDecrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.ExtZipEntry;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Backup file compatibility: plain and WinZip AES encrypted archives as produced by backupDB,
 * read back through the same FileUtils calls as restoreDB. restoreDB itself is not called (System.exit).
 * All files live in the test cache dir; the real fingen.db is not touched.
 */
@RunWith(AndroidJUnit4.class)
public class BackupFileCompatibilityTest {

    private static final String PASSWORD = "p@ssw0rd-test";
    private static final int DB_VERSION = 35;

    private File mDir;
    private File mDb;
    private byte[] mDbBytes;

    @Before
    public void setUp() throws IOException {
        mDir = new File(InstrumentationRegistry.getTargetContext().getCacheDir(), "backup_compat_" + System.nanoTime());
        assertTrue(mDir.mkdirs());
        File src = new File(mDir, "src");
        assertTrue(src.mkdirs());
        mDb = new File(src, "fingen.db");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(mDb, null);
        try {
            db.execSQL("CREATE TABLE t (_id INTEGER PRIMARY KEY, v TEXT)");
            db.execSQL("INSERT INTO t (v) VALUES ('Fingen'), ('тест')");
            db.setVersion(DB_VERSION);
        } finally {
            db.close();
        }
        mDbBytes = readAll(mDb);
    }

    @After
    public void tearDown() {
        deleteRecursive(mDir);
    }

    @Test
    public void plainZip_restoresIdenticalFile() throws Exception {
        File zip = new File(mDir, "plain.zip");
        FileUtils.zip(new String[]{mDb.getAbsolutePath()}, zip.getAbsolutePath());

        // as restoreDB does for a backup without password
        Result result = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), "", result);
        assertEquals("complete", result.state);
        File out = new File(mDir, "fingen.db.ex");
        assertArrayEquals(mDbBytes, readAll(out));
        assertEquals(DB_VERSION, userVersion(out));

        // direct unzip
        File out2 = new File(mDir, "direct.db");
        FileUtils.unzip(zip.getAbsolutePath(), out2.getAbsolutePath());
        assertArrayEquals(mDbBytes, readAll(out2));
    }

    @Test
    public void encryptedZip_restoresWithPassword() throws Exception {
        File zip = encrypt(PASSWORD);

        Result result = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), PASSWORD, result);
        assertEquals("complete", result.state);
        File out = new File(mDir, "fingen.db.ex");
        assertArrayEquals(mDbBytes, readAll(out));
        assertEquals(DB_VERSION, userVersion(out));
    }

    @Test
    public void encryptedZip_wrongPassword_reportsWithoutOutput() throws Exception {
        File zip = encrypt(PASSWORD);

        Result wrong = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), PASSWORD + "x", wrong);
        assertEquals("wrongPassword", wrong.state);

        Result empty = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), "", empty);
        assertTrue(empty.state, "wrongPassword".equals(empty.state) || "error".equals(empty.state));
    }

    @Test
    public void encryptedZip_asciiPasswordIsCharsetIndependent() throws Exception {
        // pwStr.getBytes() in AESEncrypterBC/AESDecrypterBC: for ASCII every charset gives the same bytes
        String ascii = "Passw0rd!";
        assertArrayEquals(ascii.getBytes("UTF-8"), ascii.getBytes());
        File zip = encrypt(ascii);
        Result result = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), ascii, result);
        assertEquals("complete", result.state);
    }

    @Test
    public void encryptedZip_cyrillicPassword_roundTripUsesPlatformCharset() throws Exception {
        // AES*BC use getBytes() = Charset.defaultCharset(); on Android this is always UTF-8.
        // Round trip is consistent on the same platform; compatibility with a password typed on an
        // older app version is unknown (no old fixture), see cyrillicPassword_legacyFixtureNeeded.
        String cyr = "пароль123";
        assertEquals(Charset.forName("UTF-8"), Charset.defaultCharset());
        assertArrayEquals(cyr.getBytes("UTF-8"), cyr.getBytes());
        File zip = encrypt(cyr);
        Result ok = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), cyr, ok);
        assertEquals("complete", ok.state);
        Result wrong = new Result();
        FileUtils.unzipAndDecrypt(zip.getAbsolutePath(), mDir.getAbsolutePath(), "пароль124", wrong);
        assertEquals("wrongPassword", wrong.state);
    }

    @Test
    public void backupArchives_containOnlyFingenDb() throws Exception {
        File plain = new File(mDir, "plain.zip");
        FileUtils.zip(new String[]{mDb.getAbsolutePath()}, plain.getAbsolutePath());
        assertEquals(Arrays.asList("fingen.db"), entryNames(plain));

        File enc = encrypt(PASSWORD);
        assertEquals(Arrays.asList("fingen.db"), entryNames(enc));
    }

    @Ignore("legacy fixture needed: real encrypted backup from app 2.x (WinZip AES, PBKDF2-SHA1 1000 iterations) "
            + "supplied by the user; BouncyCastle-1.59-built archives are not reproduced here on purpose")
    @Test
    public void legacyEncryptedBackup_restores() {
    }

    @Ignore("legacy fixture needed: old-version backup with Cyrillic password; unknown whether older versions "
            + "used UTF-8 (Android getBytes() default) for the password bytes")
    @Test
    public void cyrillicPassword_legacyFixtureNeeded() {
    }

    private File encrypt(String password) throws IOException {
        File zip = new File(mDir, "enc.zip");
        FileUtils.zipAndEncrypt(mDb.getAbsolutePath(), zip.getAbsolutePath(), password);
        assertTrue(zip.length() > 0);
        return zip;
    }

    private static int userVersion(File f) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(f.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            return db.getVersion();
        } finally {
            db.close();
        }
    }

    private static List<String> entryNames(File zip) throws IOException {
        List<String> names = new ArrayList<>();
        AesZipFileDecrypter decrypter = new AesZipFileDecrypter(zip, new AESDecrypterBC());
        try {
            for (ExtZipEntry e : decrypter.getEntryList()) {
                names.add(e.getName());
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            decrypter.close();
        }
        return names;
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursive(c);
            }
        }
        f.delete();
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
