package com.yoshione.fingen.dropbox;

import android.content.Context;
import android.content.SharedPreferences;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.json.JsonReadException;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import com.yoshione.fingen.BuildConfig;
import com.yoshione.fingen.FgConst;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by slv on 01.11.2016.
 *
 */

public class DropboxClient {

    public static final String PREFS_NAME = "com.yoshione.fingen.dropbox";
    // Legacy long-lived access token (before PKCE), no longer used
    public static final String KEY_TOKEN = "dropbox-token";
    public static final String KEY_CREDENTIAL = "dropbox-credential";
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

    public static DbxCredential getCredential(Context context) {
        String json = getPrefs(context).getString(KEY_CREDENTIAL, null);
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
        getPrefs(context).edit()
                .putString(KEY_CREDENTIAL, DbxCredential.Writer.writeToString(credential))
                .apply();
    }

    public static boolean isLinked(Context context) {
        return getCredential(context) != null;
    }

    public static void unlink(Context context) {
        getPrefs(context).edit()
                .remove(KEY_CREDENTIAL)
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

