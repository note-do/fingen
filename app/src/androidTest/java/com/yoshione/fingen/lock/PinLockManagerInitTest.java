package com.yoshione.fingen.lock;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.FgConst;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * PinLockManager.init: a lock enabled without a PIN hash (old lock library) is switched off once,
 * and the user gets the reset notice. The default prefs are saved before each test and restored after it.
 */
@RunWith(AndroidJUnit4.class)
public class PinLockManagerInitTest {

    private Context mContext;
    private SharedPreferences mPrefs;
    private Map<String, Object> mSaved;

    /**
     * init() registers lifecycle callbacks on the application. This stub drops them,
     * so the test does not change lock behaviour of the running process.
     */
    private static final class NoCallbacksApplication extends Application {
        NoCallbacksApplication(Context base) {
            attachBaseContext(base);
        }

        @Override
        public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        }
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSaved = new HashMap<>(mPrefs.getAll());
    }

    @After
    public void tearDown() {
        // the whole default prefs are rewritten: the test changes the PIN hash and salt, which are private to PinLockManager
        SharedPreferences.Editor editor = mPrefs.edit().clear();
        for (Map.Entry<String, Object> entry : mSaved.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(key, (Float) value);
            } else if (value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> set = (Set<String>) value;
                editor.putStringSet(key, set);
            }
        }
        editor.commit();
    }

    private void init() {
        PinLockManager.init(new NoCallbacksApplication(mContext));
    }

    @Test
    public void enabledWithoutHash_switchedOff_noticeSet() {
        PinLockManager.clearPin(mContext);
        mPrefs.edit()
                .putBoolean(PinLockManager.PREF_PIN_ENABLED, true)
                .putBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, false)
                .commit();

        init();

        assertFalse(PinLockManager.isPinSet(mContext));
        assertFalse(mPrefs.getBoolean(PinLockManager.PREF_PIN_ENABLED, true));
        assertTrue(mPrefs.getBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, false));
    }

    @Test
    public void enabledWithHash_keptEnabled_noNoticeSet() {
        PinLockManager.setPin(mContext, "12345");
        mPrefs.edit().putBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, false).commit();

        init();

        assertTrue(PinLockManager.isPinSet(mContext));
        assertTrue(PinLockManager.checkPin(mContext, "12345"));
        assertTrue(mPrefs.getBoolean(PinLockManager.PREF_PIN_ENABLED, false));
        assertFalse(mPrefs.getBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, true));
    }

    @Test
    public void disabledWithoutHash_noNoticeSet() {
        PinLockManager.clearPin(mContext);
        mPrefs.edit().putBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, false).commit();

        init();

        assertFalse(mPrefs.getBoolean(PinLockManager.PREF_PIN_ENABLED, true));
        assertFalse(mPrefs.getBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, true));
    }
}
