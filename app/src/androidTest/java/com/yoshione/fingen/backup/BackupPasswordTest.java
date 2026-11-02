package com.yoshione.fingen.backup;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.SystemDialogsRule;
import com.yoshione.fingen.utils.SecureStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the backup password storage: migration from plain prefs, lost password handling.
 * Default prefs and secure prefs are saved before each test and restored after it.
 */
@RunWith(AndroidJUnit4.class)
public class BackupPasswordTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private Context mContext;
    private SharedPreferences mDefaultPrefs;
    private SharedPreferences mSecurePrefs;
    private Map<String, Object> mSavedDefault;
    private Map<String, Object> mSavedSecure;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSecurePrefs = mContext.getSharedPreferences(SecureStore.PREFS_NAME, Context.MODE_PRIVATE);
        mSavedDefault = new HashMap<>(mDefaultPrefs.getAll());
        mSavedSecure = new HashMap<>(mSecurePrefs.getAll());
        mDefaultPrefs.edit()
                .remove(FgConst.PREF_BACKUP_PASSWORD)
                .remove(FgConst.PREF_ENABLE_BACKUP_PASSWORD)
                .commit();
        mSecurePrefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        restore(mDefaultPrefs, mSavedDefault, FgConst.PREF_BACKUP_PASSWORD, FgConst.PREF_ENABLE_BACKUP_PASSWORD);
        restore(mSecurePrefs, mSavedSecure);
    }

    // Restores only the given keys of the default prefs (all keys if none given)
    private static void restore(SharedPreferences prefs, Map<String, Object> saved, String... keys) {
        SharedPreferences.Editor editor = prefs.edit();
        if (keys == null || keys.length == 0) {
            editor.clear();
        } else {
            for (String key : keys) {
                editor.remove(key);
            }
        }
        for (Map.Entry<String, Object> entry : saved.entrySet()) {
            if (keys != null && keys.length > 0 && !contains(keys, entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            }
        }
        editor.commit();
    }

    private static boolean contains(String[] keys, String key) {
        for (String k : keys) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void plainPassword_migrated_plainRemoved_getMatches_flagSet() {
        mDefaultPrefs.edit()
                .putString(FgConst.PREF_BACKUP_PASSWORD, "p@ss")
                .putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, true)
                .commit();

        BackupPassword.migrate(mContext);

        assertFalse(mDefaultPrefs.contains(FgConst.PREF_BACKUP_PASSWORD));
        assertTrue(mSecurePrefs.contains(FgConst.SECURE_BACKUP_PASSWORD));
        assertFalse(mSecurePrefs.getString(FgConst.SECURE_BACKUP_PASSWORD, "").contains("p@ss"));
        assertTrue(mSecurePrefs.getBoolean(FgConst.SECURE_BACKUP_PASSWORD_MIGRATED, false));
        assertEquals("p@ss", BackupPassword.get(mContext));
        assertTrue(BackupPassword.isProtectionEnabled(mContext));
        assertFalse(BackupPassword.isBackupBlocked(mContext));
    }

    @Test
    public void secondMigration_doesNothing() {
        mDefaultPrefs.edit().putString(FgConst.PREF_BACKUP_PASSWORD, "first").commit();
        BackupPassword.migrate(mContext);
        String encrypted = mSecurePrefs.getString(FgConst.SECURE_BACKUP_PASSWORD, null);
        assertNotNull(encrypted);

        // plain value appears again (e.g. restored): flag is set, so it is left alone
        mDefaultPrefs.edit().putString(FgConst.PREF_BACKUP_PASSWORD, "second").commit();
        BackupPassword.migrate(mContext);

        assertEquals(encrypted, mSecurePrefs.getString(FgConst.SECURE_BACKUP_PASSWORD, null));
        assertEquals("second", mDefaultPrefs.getString(FgConst.PREF_BACKUP_PASSWORD, null));
    }

    @Test
    public void emptyPassword_migrationSetsFlag_getReturnsEmpty() {
        mDefaultPrefs.edit().putString(FgConst.PREF_BACKUP_PASSWORD, "").commit();

        BackupPassword.migrate(mContext);

        assertTrue(mSecurePrefs.getBoolean(FgConst.SECURE_BACKUP_PASSWORD_MIGRATED, false));
        assertFalse(mSecurePrefs.contains(FgConst.SECURE_BACKUP_PASSWORD));
        assertEquals("", BackupPassword.get(mContext));
        assertFalse(BackupPassword.isBackupBlocked(mContext));
        assertFalse(BackupPassword.hasWarning(mContext));
    }

    @Test
    public void setAndGet_roundTrip_andEmptyRemoves() {
        assertTrue(BackupPassword.set(mContext, "Пароль 1"));
        assertEquals("Пароль 1", BackupPassword.get(mContext));

        assertTrue(BackupPassword.set(mContext, ""));
        assertEquals("", BackupPassword.get(mContext));
        assertFalse(mSecurePrefs.contains(FgConst.SECURE_BACKUP_PASSWORD));
    }

    @Test
    public void undecryptableValue_blocksBackup_setsWarning_newPasswordClearsIt() throws Exception {
        mDefaultPrefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, true).commit();
        mSecurePrefs.edit().putString(FgConst.SECURE_BACKUP_PASSWORD, "v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").commit();

        assertNull(BackupPassword.get(mContext));
        assertTrue(BackupPassword.isBackupBlocked(mContext));
        assertTrue(BackupPassword.hasWarning(mContext));

        // backup is not created, even locally
        File backup = DBHelper.getInstance(mContext).backupDB(false);
        assertNull(backup);
        assertTrue(mSecurePrefs.getBoolean(FgConst.SECURE_BACKUP_PASSWORD_WARNING, false));

        assertTrue(BackupPassword.set(mContext, "new"));
        assertEquals("new", BackupPassword.get(mContext));
        assertFalse(BackupPassword.isBackupBlocked(mContext));
        assertFalse(BackupPassword.hasWarning(mContext));
    }

    @Test
    public void protectionEnabledWithoutPasswordOnDevice_blocksBackup() {
        // settings restored from cloud to a new device: switch is on, secrets are not there
        mDefaultPrefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, true).commit();

        assertNull(BackupPassword.get(mContext));
        assertTrue(BackupPassword.isBackupBlocked(mContext));
    }

    @Test
    public void protectionOff_backupNotBlocked() {
        assertEquals("", BackupPassword.get(mContext));
        assertFalse(BackupPassword.isBackupBlocked(mContext));
    }
}
