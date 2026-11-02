package com.yoshione.fingen;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CompoundButton;
import androidx.preference.PreferenceManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.backup.BackupPassword;
import com.yoshione.fingen.utils.SecureStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;

/**
 * ActivityBackup: the password protection switch cannot be turned on without a password.
 * The switch is changed in code (setChecked runs the same listener as a tap). The toast is not asserted:
 * UiAutomator does not see toast windows on the test emulator.
 * Default and secure prefs are saved before each test and restored after it.
 * Lives in the app package: ScreenTestUtils is package-private.
 */
@RunWith(AndroidJUnit4.class)
public class ActivityBackupPasswordToggleTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private Context mContext;
    private SharedPreferences mDefaultPrefs;
    private SharedPreferences mSecurePrefs;
    private Map<String, Object> mSavedDefault;
    private Map<String, Object> mSavedSecure;
    private Activity mActivity;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mSecurePrefs = mContext.getSharedPreferences(SecureStore.PREFS_NAME, Context.MODE_PRIVATE);
        mSavedDefault = new HashMap<>(mDefaultPrefs.getAll());
        mSavedSecure = new HashMap<>(mSecurePrefs.getAll());
        // no password, protection off
        BackupPassword.set(mContext, "");
        mDefaultPrefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, false).commit();
    }

    @After
    public void tearDown() {
        ScreenTestUtils.finish(mActivity);
        restore(mDefaultPrefs, mSavedDefault);
        restore(mSecurePrefs, mSavedSecure);
    }

    private static void restore(SharedPreferences prefs, Map<String, Object> saved) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, Object> entry : saved.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            }
        }
        editor.commit();
    }

    @Test
    public void enableWithoutPassword_switchReturnsOff_prefStaysFalse() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityBackup.class, null);
        final CompoundButton toggle = (CompoundButton) mActivity.findViewById(R.id.switchCompatEnablePasswordProtection);
        final boolean[] checkedAfter = {true};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                toggle.setChecked(true);
                checkedAfter[0] = toggle.isChecked();
            }
        });

        assertFalse("switch must return to off without a password", checkedAfter[0]);
        assertFalse(BackupPassword.isProtectionEnabled(mContext));
        assertFalse(mDefaultPrefs.getBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, true));
    }
}
