package com.yoshione.fingen.dropbox;

import android.content.Context;
import android.content.SharedPreferences;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
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
    public static final String KEY_TOKEN = "dropbox-token";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getToken(Context context) {
        return getPrefs(context).getString(KEY_TOKEN, null);
    }

    public static void saveToken(Context context, String token) {
        getPrefs(context).edit().putString(KEY_TOKEN, token).apply();
    }

    public static boolean isLinked(Context context) {
        return getToken(context) != null;
    }

    public static void unlink(Context context) {
        getPrefs(context).edit()
                .remove(KEY_TOKEN)
                .remove(FgConst.PREF_DROPBOX_ACCOUNT)
                .apply();
    }

    /**
     * @return client for the stored token or null if Dropbox is not linked
     */
    public static DbxClientV2 getClient(Context context) {
        String token = getToken(context);
        return token == null ? null : getClient(token);
    }

    public static DbxClientV2 getClient(String ACCESS_TOKEN) {
        // Create Dropbox client
        DbxRequestConfig config = new DbxRequestConfig("Fingen/" + BuildConfig.VERSION_NAME);
        return new DbxClientV2(config, ACCESS_TOKEN);
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

