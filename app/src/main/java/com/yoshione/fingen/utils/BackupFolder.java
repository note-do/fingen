package com.yoshione.fingen.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.yoshione.fingen.FgConst;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local backup folder chosen by the user (SAF tree Uri with persistable permission).
 * Works with DocumentsContract directly, no DocumentFile dependency.
 * Backups are always built in the app cache dir and then copied to the folder.
 */
public class BackupFolder {
    private static final String TAG = "BackupFolder";
    private static final String CACHE_SUBDIR = "backup";
    private static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";
    private static final String DEFAULT_FOLDER_DOC_ID = "primary:Fingen/Backup";
    private static final String ZIP_MIME = "application/zip";

    public static class Item {
        public final String name;
        public final Uri uri;
        public final long modified;

        Item(String name, Uri uri, long modified) {
            this.name = name;
            this.uri = uri;
            this.modified = modified;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Cache dir where backup files are built (and where the pre-migration copy lives).
     */
    public static File getCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Can't create " + dir);
        }
        return dir;
    }

    /**
     * Deletes all files in the backup cache dir except the given one (may be null).
     */
    public static void cleanCache(Context context, File keep) {
        File[] files = getCacheDir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (keep == null || !file.getAbsoluteFile().equals(keep.getAbsoluteFile())) {
                file.delete();
            }
        }
    }

    public static Uri getFolderUri(Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context).getString(FgConst.PREF_BACKUP_FOLDER_URI, "");
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    /**
     * Intent for the system folder picker, opens in Fingen/Backup where the provider supports it.
     */
    public static Intent createPickFolderIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, DEFAULT_FOLDER_DOC_ID));
        }
        return intent;
    }

    /**
     * Stores the picked folder, takes the persistable permission and releases the previous one.
     *
     * @return false if the permission can't be taken
     */
    public static boolean setFolder(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Uri old = getFolderUri(context);
        try {
            resolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.e(TAG, "Can't take permission for " + uri + ": " + e.getMessage());
            return false;
        }
        if (old != null && !old.equals(uri)) {
            try {
                resolver.releasePersistableUriPermission(old,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException e) {
                Log.e(TAG, "Can't release permission for " + old + ": " + e.getMessage());
            }
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(FgConst.PREF_BACKUP_FOLDER_URI, uri.toString());
        editor.putBoolean(FgConst.PREF_BACKUP_FOLDER_UNAVAILABLE, false);
        editor.apply();
        return true;
    }

    private static Uri getRootDocumentUri(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    /**
     * Folder is chosen, permission is still persisted and the folder exists and accepts new files.
     * Does IO, call not from the main thread when possible.
     */
    public static boolean isAvailable(Context context) {
        Uri tree = getFolderUri(context);
        if (tree == null) {
            return false;
        }
        ContentResolver resolver = context.getContentResolver();
        boolean permitted = false;
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (permission.getUri().equals(tree) && permission.isReadPermission() && permission.isWritePermission()) {
                permitted = true;
                break;
            }
        }
        if (!permitted) {
            return false;
        }
        Cursor cursor = null;
        try {
            cursor = resolver.query(getRootDocumentUri(tree),
                    new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null);
            return cursor != null && cursor.moveToFirst()
                    && (cursor.getInt(0) & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0;
        } catch (Exception e) {
            Log.e(TAG, "isAvailable: " + e.getMessage());
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Display name of the chosen folder, empty string if none.
     */
    public static String getFolderName(Context context) {
        Uri tree = getFolderUri(context);
        if (tree == null) {
            return "";
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(getRootDocumentUri(tree),
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getFolderName: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = tree.getLastPathSegment();
        return last == null ? "" : last;
    }

    /**
     * Copies a backup file from the cache to the chosen folder. The provider may rename the file (" (1)").
     */
    public static Uri copyToFolder(Context context, File file) throws IOException {
        Uri tree = getFolderUri(context);
        if (tree == null) {
            throw new IOException("Backup folder is not selected");
        }
        Uri doc;
        try {
            doc = DocumentsContract.createDocument(context.getContentResolver(), getRootDocumentUri(tree), ZIP_MIME, file.getName());
        } catch (Exception e) {
            throw new IOException("Can't create document: " + e.getMessage(), e);
        }
        if (doc == null) {
            throw new IOException("Can't create document " + file.getName());
        }
        try {
            FileUtils.copyFileToUri(context, file, doc);
        } catch (IOException | RuntimeException e) {
            try {
                DocumentsContract.deleteDocument(context.getContentResolver(), doc);
            } catch (Exception ignored) {
            }
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        return doc;
    }

    /**
     * Zip files of the chosen folder (no subfolders), newest first. Empty list if the folder is unavailable.
     */
    public static List<Item> listBackups(Context context) {
        List<Item> items = new ArrayList<>();
        Uri tree = getFolderUri(context);
        if (tree == null) {
            return items;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                String name = cursor.getString(1);
                String mime = cursor.getString(3);
                if (name == null || DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)
                        || !name.toLowerCase().endsWith(".zip")) {
                    continue;
                }
                items.add(new Item(name, DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)),
                        cursor.getLong(2)));
            }
        } catch (Exception e) {
            Log.e(TAG, "listBackups: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                int c = Long.compare(b.modified, a.modified);
                return c != 0 ? c : b.name.compareTo(a.name);
            }
        });
        return items;
    }
}
