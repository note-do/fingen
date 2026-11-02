package com.yoshione.fingen;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Checks of the restore helpers: DBHelper.isRestoredDbValid (validation of the unpacked file) and
 * DBHelper.deleteRestoreBackup (removal of fingen.db.bak after a successful start).
 * <p>
 * Files of the check are created in cacheDir, the real fingen.db is not touched. Preference
 * PREF_RESTORE_BAK_PENDING and an existing fingen.db.bak are saved before each test and restored after it.
 * restoreDB as a whole is not tested: it shows dialogs and calls Runtime.exit.
 */
@RunWith(AndroidJUnit4.class)
public class RestoreDbTest {

    private static final String BAK_NAME = "fingen.db.bak";
    private static final String SAVED_BAK_NAME = "restore_test_saved_fingen.db.bak";

    private Context mContext;
    private SharedPreferences mPrefs;
    private boolean mSavedFlag;
    private boolean mSavedFlagPresent;
    private File mDbDir;
    private File mBak;
    private File mSavedBak;
    private boolean mSavedBakExisted;
    private final String mFileSuffix = "_" + System.nanoTime();
    private final List<File> mTempFiles = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        mContext = InstrumentationRegistry.getTargetContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSavedFlagPresent = mPrefs.contains(FgConst.PREF_RESTORE_BAK_PENDING);
        mSavedFlag = mPrefs.getBoolean(FgConst.PREF_RESTORE_BAK_PENDING, false);

        mDbDir = mContext.getDatabasePath("fingen.db").getParentFile();
        mBak = new File(mDbDir, BAK_NAME);
        mSavedBak = new File(mContext.getCacheDir(), SAVED_BAK_NAME);
        // Move a real .bak aside, so the test works with its own file only
        mSavedBakExisted = mBak.exists();
        if (mSavedBakExisted && !mBak.renameTo(mSavedBak)) {
            throw new IOException("Can't move aside " + mBak.getPath());
        }
    }

    @After
    public void tearDown() {
        if (mBak.exists()) {
            mBak.delete();
        }
        if (mSavedBakExisted) {
            mSavedBak.renameTo(mBak);
        }
        if (mSavedFlagPresent) {
            mPrefs.edit().putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, mSavedFlag).commit();
        } else {
            mPrefs.edit().remove(FgConst.PREF_RESTORE_BAK_PENDING).commit();
        }
        for (File file : mTempFiles) {
            file.delete();
        }
    }

    // isRestoredDbValid

    @Test
    public void isRestoredDbValid_missingFile_false() {
        File file = tempFile("missing");
        assertFalse(newHelper().isRestoredDbValid(file));
    }

    @Test
    public void isRestoredDbValid_textGarbage_false() throws IOException {
        File file = tempFile("garbage");
        writeBytes(file, "this is not a sqlite database, just some text, just some text, just some text\n".getBytes("UTF-8"));
        assertFalse(newHelper().isRestoredDbValid(file));
    }

    @Test
    public void isRestoredDbValid_userVersion15_false() {
        assertFalse(newHelper().isRestoredDbValid(sqliteWithUserVersion("v15", 15)));
    }

    @Test
    public void isRestoredDbValid_userVersion16_true() {
        assertTrue(newHelper().isRestoredDbValid(sqliteWithUserVersion("v16", 16)));
    }

    @Test
    public void isRestoredDbValid_currentVersion_true() {
        assertTrue(newHelper().isRestoredDbValid(sqliteWithUserVersion("current", DBHelper.DATABASE_VERSION)));
    }

    @Test
    public void isRestoredDbValid_currentVersionPlusOne_false() {
        assertFalse(newHelper().isRestoredDbValid(
                sqliteWithUserVersion("future", DBHelper.DATABASE_VERSION + 1)));
    }

    // deleteRestoreBackup

    @Test
    public void deleteRestoreBackup_flagSet_removesBakAndResetsFlag() throws IOException {
        writeBytes(mBak, "old database".getBytes("UTF-8"));
        mPrefs.edit().putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, true).commit();

        DBHelper.deleteRestoreBackup(mContext);

        assertFalse(mBak.exists());
        assertFalse(mPrefs.getBoolean(FgConst.PREF_RESTORE_BAK_PENDING, true));
    }

    @Test
    public void deleteRestoreBackup_flagNotSet_keepsBak() throws IOException {
        writeBytes(mBak, "old database".getBytes("UTF-8"));
        mPrefs.edit().putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, false).commit();

        DBHelper.deleteRestoreBackup(mContext);

        assertTrue(mBak.exists());
        assertEquals(false, mPrefs.getBoolean(FgConst.PREF_RESTORE_BAK_PENDING, true));
    }

    // helpers

    /**
     * DBHelper is a singleton and its constructor calls deleteRestoreBackup. The state of
     * the prefs and .bak is already restored by tearDown, so this is safe.
     */
    private DBHelper newHelper() {
        return DBHelper.getInstance(mContext);
    }

    private File tempFile(String name) {
        File file = new File(mContext.getCacheDir(), "restore_test_" + name + mFileSuffix + ".db");
        mTempFiles.add(file);
        return file;
    }

    private File sqliteWithUserVersion(String name, int version) {
        File file = tempFile(name);
        file.delete();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS probe (_id INTEGER PRIMARY KEY)");
            db.setVersion(version);
        } finally {
            db.close();
        }
        return file;
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }
}
