package com.yoshione.fingen.dropbox;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.SystemDialogsRule;
import com.yoshione.fingen.utils.SecureStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers stored Dropbox credential handling. No network: getClient only builds the client object.
 * The Dropbox prefs are saved before each test and restored after it.
 */
@RunWith(AndroidJUnit4.class)
public class DropboxClientTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private static final String KEY_LEGACY_TOKEN_CLEARED = "legacy-token-cleared";

    private Context mContext;
    private Map<String, Object> mSavedPrefs;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        mSavedPrefs = new HashMap<>(prefs.getAll());
        prefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, Object> entry : mSavedPrefs.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) value);
            }
        }
        editor.commit();
    }

    private static String validCredentialJson() {
        DbxCredential credential = new DbxCredential("access", System.currentTimeMillis() + 3600_000L, "refresh", "app key");
        return DbxCredential.Writer.writeToString(credential);
    }

    @Test
    public void noData_notLinked_noCredential_noClient() {
        assertFalse(DropboxClient.isLinked(mContext));
        assertNull(DropboxClient.getCredential(mContext));
        assertNull(DropboxClient.getClient(mContext));
    }

    @Test
    public void legacyTokenOnly_migrationRemovesTokenAndAccount_andSetsFlag() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_TOKEN, "legacy-token")
                .putString(FgConst.PREF_DROPBOX_ACCOUNT, "user@example.com")
                .commit();

        DropboxClient.migrateLegacyToken(mContext);

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertFalse(prefs.contains(DropboxClient.KEY_TOKEN));
        assertFalse(prefs.contains(FgConst.PREF_DROPBOX_ACCOUNT));
        assertTrue(prefs.getBoolean(KEY_LEGACY_TOKEN_CLEARED, false));
        assertFalse(DropboxClient.isLinked(mContext));
    }

    @Test
    public void migrationRunsOnce_secondCallKeepsNewCredential() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_TOKEN, "legacy-token")
                .commit();
        DropboxClient.migrateLegacyToken(mContext);

        DropboxClient.saveCredential(mContext,
                new DbxCredential("access", System.currentTimeMillis() + 3600_000L, "refresh", "app key"));
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_TOKEN, "token-after-migration")
                .commit();

        DropboxClient.migrateLegacyToken(mContext);

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertTrue(DropboxClient.isLinked(mContext));
        assertEquals("token-after-migration", prefs.getString(DropboxClient.KEY_TOKEN, null));
    }

    @Test
    public void validCredential_isLinked_andClientCreated() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL, validCredentialJson())
                .commit();

        assertTrue(DropboxClient.isLinked(mContext));

        DbxCredential credential = DropboxClient.getCredential(mContext);
        assertNotNull(credential);
        assertEquals("access", credential.getAccessToken());
        assertEquals("refresh", credential.getRefreshToken());
        assertEquals("app key", credential.getAppKey());

        DbxClientV2 client = DropboxClient.getClient(mContext);
        assertNotNull(client);
    }

    @Test
    public void brokenCredentialJson_returnsNull_withoutCrash() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL, "{not valid json")
                .commit();

        assertNull(DropboxClient.getCredential(mContext));
        assertFalse(DropboxClient.isLinked(mContext));
        assertNull(DropboxClient.getClient(mContext));
    }

    @Test
    public void unlink_removesCredentialTokenAndAccount() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL, validCredentialJson())
                .putString(DropboxClient.KEY_TOKEN, "legacy-token")
                .putString(FgConst.PREF_DROPBOX_ACCOUNT, "user@example.com")
                .commit();
        assertTrue(DropboxClient.isLinked(mContext));

        DropboxClient.unlink(mContext);

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertFalse(prefs.contains(DropboxClient.KEY_CREDENTIAL));
        assertFalse(prefs.contains(DropboxClient.KEY_TOKEN));
        assertFalse(prefs.contains(FgConst.PREF_DROPBOX_ACCOUNT));
        assertFalse(DropboxClient.isLinked(mContext));
        assertNull(DropboxClient.getClient(mContext));
    }

    @Test
    public void plainCredential_migrated_encrypted_plainRemoved_linked() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL, validCredentialJson())
                .commit();

        DropboxClient.migrateCredential(mContext);

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertFalse(prefs.contains(DropboxClient.KEY_CREDENTIAL));
        String encrypted = prefs.getString(DropboxClient.KEY_CREDENTIAL_ENC, null);
        assertNotNull(encrypted);
        assertFalse(encrypted.contains("refresh"));
        assertTrue(prefs.getBoolean(DropboxClient.KEY_CREDENTIAL_MIGRATED, false));
        assertTrue(DropboxClient.isLinked(mContext));
        assertEquals("refresh", DropboxClient.getCredential(mContext).getRefreshToken());

        // second run changes nothing
        DropboxClient.migrateCredential(mContext);
        assertEquals(encrypted, prefs.getString(DropboxClient.KEY_CREDENTIAL_ENC, null));
    }

    @Test
    public void saveCredential_storesEncryptedOnly() {
        DropboxClient.saveCredential(mContext,
                new DbxCredential("access", System.currentTimeMillis() + 3600_000L, "refresh", "app key"));

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertFalse(prefs.contains(DropboxClient.KEY_CREDENTIAL));
        assertTrue(prefs.contains(DropboxClient.KEY_CREDENTIAL_ENC));
        assertTrue(DropboxClient.isLinked(mContext));
        assertEquals("access", DropboxClient.getCredential(mContext).getAccessToken());
    }

    @Test
    public void undecryptableCredential_notLinked_keyRemoved() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL_ENC, "v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                .commit();

        assertNull(DropboxClient.getCredential(mContext));
        assertFalse(DropboxClient.isLinked(mContext));
        assertNull(DropboxClient.getClient(mContext));
        assertFalse(DropboxClient.getPrefs(mContext).contains(DropboxClient.KEY_CREDENTIAL_ENC));
    }

    @Test
    public void unlink_removesBothCredentialKeys() {
        DropboxClient.getPrefs(mContext).edit()
                .putString(DropboxClient.KEY_CREDENTIAL, validCredentialJson())
                .putString(DropboxClient.KEY_CREDENTIAL_ENC, SecureStore.encrypt(validCredentialJson()))
                .commit();

        DropboxClient.unlink(mContext);

        SharedPreferences prefs = DropboxClient.getPrefs(mContext);
        assertFalse(prefs.contains(DropboxClient.KEY_CREDENTIAL));
        assertFalse(prefs.contains(DropboxClient.KEY_CREDENTIAL_ENC));
        assertFalse(DropboxClient.isLinked(mContext));
    }
}
