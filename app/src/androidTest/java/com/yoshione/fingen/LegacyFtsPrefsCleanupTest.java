package com.yoshione.fingen;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * FGApplication.cleanLegacyFtsPrefs: removes the settings of the removed FNS receipts feature once.
 * The app already ran the cleanup on start, so the flag is reset before each test and restored after it.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyFtsPrefsCleanupTest {

    private static final String UNRELATED_KEY = "fts_cleanup_test_unrelated";
    private static final String[] LEGACY_KEYS = {
            FgConst.PREF_FTS_LOGIN,
            FgConst.PREF_FTS_PASS,
            FgConst.PREF_FTS_CREDENTIALS,
            FgConst.PREF_FTS_DO_NOT_SHOW_AGAIN
    };

    private FGApplication mApp;
    private SharedPreferences mPrefs;
    private Map<String, Object> mSaved;
    private String[] mTouchedKeys;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        mApp = (FGApplication) context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mSaved = new HashMap<>(mPrefs.getAll());
        mTouchedKeys = new String[LEGACY_KEYS.length + 2];
        System.arraycopy(LEGACY_KEYS, 0, mTouchedKeys, 0, LEGACY_KEYS.length);
        mTouchedKeys[LEGACY_KEYS.length] = FgConst.PREF_FTS_PREFS_CLEANED;
        mTouchedKeys[LEGACY_KEYS.length + 1] = UNRELATED_KEY;
        // force the cleanup to run again in this test
        mPrefs.edit().putBoolean(FgConst.PREF_FTS_PREFS_CLEANED, false).commit();
    }

    @After
    public void tearDown() {
        // restore only the keys this test touches to the values they had before it
        SharedPreferences.Editor editor = mPrefs.edit();
        for (String key : mTouchedKeys) {
            Object value = mSaved.get(key);
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else {
                editor.remove(key);
            }
        }
        editor.commit();
    }

    private void writeLegacyPrefs() {
        mPrefs.edit()
                .putString(FgConst.PREF_FTS_LOGIN, "login")
                .putString(FgConst.PREF_FTS_PASS, "secret")
                .putString(FgConst.PREF_FTS_CREDENTIALS, "{\"token\":\"x\"}")
                .putBoolean(FgConst.PREF_FTS_DO_NOT_SHOW_AGAIN, true)
                .commit();
    }

    @Test
    public void firstRun_removesLegacyKeys_keepsOtherKeys_setsFlag() {
        writeLegacyPrefs();
        mPrefs.edit().putString(UNRELATED_KEY, "keep").commit();

        mApp.cleanLegacyFtsPrefs();

        for (String key : LEGACY_KEYS) {
            assertFalse(key + " must be removed", mPrefs.contains(key));
        }
        assertTrue(mPrefs.getBoolean(FgConst.PREF_FTS_PREFS_CLEANED, false));
        assertEquals("keep", mPrefs.getString(UNRELATED_KEY, null));
    }

    @Test
    public void secondRun_doesNotTouchKeys_whenFlagSet() {
        mApp.cleanLegacyFtsPrefs();
        assertTrue(mPrefs.getBoolean(FgConst.PREF_FTS_PREFS_CLEANED, false));

        // the flag is set: keys written after the first run are left alone
        writeLegacyPrefs();
        mApp.cleanLegacyFtsPrefs();

        assertEquals("login", mPrefs.getString(FgConst.PREF_FTS_LOGIN, null));
        assertEquals("secret", mPrefs.getString(FgConst.PREF_FTS_PASS, null));
        assertEquals("{\"token\":\"x\"}", mPrefs.getString(FgConst.PREF_FTS_CREDENTIALS, null));
        assertTrue(mPrefs.getBoolean(FgConst.PREF_FTS_DO_NOT_SHOW_AGAIN, false));
    }
}
