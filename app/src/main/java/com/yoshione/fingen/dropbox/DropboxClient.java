package com.yoshione.fingen.dropbox;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import com.yoshione.fingen.BuildConfig;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.utils.SecureStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by slv on 01.11.2016.
 *
 */

public class DropboxClient {

    private static final String TAG = "DropboxClient";
    public static final String PREFS_NAME = "com.yoshione.fingen.dropbox";
    // Legacy long-lived access token (before PKCE), no longer used
    public static final String KEY_TOKEN = "dropbox-token";
    // Plain text credential of the previous versions, only read until the migration is done
    public static final String KEY_CREDENTIAL = "dropbox-credential";
    // Credential encrypted by SecureStore
    public static final String KEY_CREDENTIAL_ENC = "dropbox-credential-enc";
    public static final String KEY_CREDENTIAL_MIGRATED = "dropbox-credential-migrated";
    private static final String KEY_LEGACY_TOKEN_CLEARED = "legacy-token-cleared";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * One-time forced re-login: drops the old token and account, the user signs in again via PKCE
     */
    public static void migrateLegacyToken(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.getBoolean(KEY_LEGACY_TOKEN_CLEARED, false)) {
            prefs.edit()
                    .remove(KEY_TOKEN)
                    .remove(FgConst.PREF_DROPBOX_ACCOUNT)
                    .putBoolean(KEY_LEGACY_TOKEN_CLEARED, true)
                    .apply();
        }
    }

    /**
     * One-time move of the plain text credential to SecureStore. Safe to call many times.
     * If Keystore is unavailable the plain value stays and the move is retried on the next start.
     */
    public static void migrateCredential(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs.getBoolean(KEY_CREDENTIAL_MIGRATED, false)) {
            return;
        }
        String plain = prefs.getString(KEY_CREDENTIAL, null);
        if (plain != null) {
            String encrypted = SecureStore.encrypt(plain);
            if (encrypted == null) {
                Log.w(TAG, "Keystore is unavailable, migration postponed");
                return;
            }
            prefs.edit().putString(KEY_CREDENTIAL_ENC, encrypted).commit();
            if (!plain.equals(SecureStore.decrypt(encrypted))) {
                prefs.edit().remove(KEY_CREDENTIAL_ENC).commit();
                Log.w(TAG, "Check of the encrypted credential failed, migration postponed");
                return;
            }
            prefs.edit().remove(KEY_CREDENTIAL).commit();
        }
        prefs.edit().putBoolean(KEY_CREDENTIAL_MIGRATED, true).commit();
    }

    /**
     * @return stored credential or null if Dropbox is not linked. A credential that can't be decrypted
     * (new device, lost Keystore key) is dropped: the user has to sign in again.
     */
    public static DbxCredential getCredential(Context context) {
        SharedPreferences prefs = getPrefs(context);
        String json;
        String encrypted = prefs.getString(KEY_CREDENTIAL_ENC, null);
        if (encrypted != null) {
            json = SecureStore.decrypt(encrypted);
            if (json == null) {
                prefs.edit().remove(KEY_CREDENTIAL_ENC).apply();
                return null;
            }
        } else {
            // not migrated yet (Keystore was unavailable)
            json = prefs.getString(KEY_CREDENTIAL, null);
        }
        if (json == null) {
            return null;
        }
        try {
            return DbxCredential.Reader.readFully(json);
        } catch (JsonReadException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void saveCredential(Context context, DbxCredential credential) {
        String encrypted = SecureStore.encrypt(DbxCredential.Writer.writeToString(credential));
        if (encrypted == null) {
            // Never keep the credential in plain text
            Log.e(TAG, "Can't save credential: Keystore is unavailable");
            return;
        }
        getPrefs(context).edit()
                .putString(KEY_CREDENTIAL_ENC, encrypted)
                .remove(KEY_CREDENTIAL)
                .apply();
    }

    public static boolean isLinked(Context context) {
        return getCredential(context) != null;
    }

    public static void unlink(Context context) {
        getPrefs(context).edit()
                .remove(KEY_CREDENTIAL)
                .remove(KEY_CREDENTIAL_ENC)
                .remove(KEY_TOKEN)
                .remove(FgConst.PREF_DROPBOX_ACCOUNT)
                .apply();
    }

    public static DbxRequestConfig getRequestConfig() {
        return new DbxRequestConfig("Fingen/" + BuildConfig.VERSION_NAME);
    }

    /**
     * @return client for the stored credential (refreshes access token itself) or null if Dropbox is not linked
     */
    public static DbxClientV2 getClient(Context context) {
        DbxCredential credential = getCredential(context);
        return credential == null ? null : new DbxClientV2(getRequestConfig(), credential);
    }

    public static List<Metadata> getListFiles(DbxClientV2 client) {
        ListFolderResult result = null;
        List<Metadata> metadataList = new ArrayList<>();
        try {
            result = client.files().listFolder("");
        } catch (DbxException e) {
            return metadataList;
        }

        while (true) {
            metadataList.addAll(result.getEntries());

            if (!result.getHasMore()) {
                break;
            }

            try {
                result = client.files().listFolderContinue(result.getCursor());
            } catch (DbxException e) {
                break;
            }
        }
        return metadataList;
    }
}

