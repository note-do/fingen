package com.yoshione.fingen.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import android.util.Log;

import com.yoshione.fingen.interfaces.IOnUnzipComplete;
import com.yoshione.fingen.utils.winzipaes.AesZipFileDecrypter;
import com.yoshione.fingen.utils.winzipaes.AesZipFileEncrypter;
import com.yoshione.fingen.utils.winzipaes.impl.AESDecrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.AESEncrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.ExtZipEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


//import de.idyl.winzipaes.AesZipFileEncrypter;
//import com.yoshione.fingen.utils.winzipaes.impl.AESEncrypterBC;

/**
 * Created by Leonid on 06.02.2016.
 *
 */
@SuppressWarnings("TryFinallyCanBeTryWithResources")
public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final int BUFFER_SIZE = 1024;

    public static File zip(String[] files, String zipFile) throws IOException {
        BufferedInputStream origin;
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));
        File outFile = null;
        try {
            byte data[] = new byte[BUFFER_SIZE];

            for (String file : files) {
                FileInputStream fi = new FileInputStream(file);
                origin = new BufferedInputStream(fi, BUFFER_SIZE);
                try {
                    ZipEntry entry = new ZipEntry(file.substring(file.lastIndexOf("/") + 1));
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                        out.write(data, 0, count);
                    }
                } finally {
                    origin.close();
                    outFile = new File(zipFile);
                }
            }
        }
        finally {
            out.close();
        }
        return outFile;
    }

    public static File zipAndEncrypt(String inFile, String zipFile, String password) throws IOException {
        File outFile = null;
        try {
            AesZipFileEncrypter.zipAndEncrypt(new File(inFile), new File(zipFile), password, new AESEncrypterBC());
        }
        finally {
            outFile = new File(zipFile);
        }
        return outFile;
    }

    /**
     * Extracts only the "fingen.db" entry to outputFile.
     * @throws IOException if the archive is damaged or has no "fingen.db" entry
     */
    public static void unzip(String zipFile, String outputFile) throws IOException {
        boolean found = false;
        try {
            ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile));
            try {
                ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {
                    if (ze.isDirectory() || !"fingen.db".equals(ze.getName())) {
                        continue;
                    }
                    try (BufferedOutputStream bufout = new BufferedOutputStream(new FileOutputStream(outputFile, false))) {
                        byte[] buffer = new byte[2048];
                        int read;
                        while ((read = zin.read(buffer)) != -1) {
                            bufout.write(buffer, 0, read);
                        }
                    } finally {
                        zin.closeEntry();
                    }
                    found = true;
                }
            }
            finally {
                zin.close();
            }
        }
        catch (ZipException e) {
            // ZipException means "wrong password" for the caller, so rethrow as a plain error
            throw new IOException("Unzip exception", e);
        }
        if (!found) {
            new File(outputFile).delete();
            throw new IOException("No fingen.db in archive");
        }
    }

    public static void unzipAndDecrypt(String zipFile, String location, String password, IOnUnzipComplete onCompleteListener) {
        try {
            AesZipFileDecrypter decrypter = new AesZipFileDecrypter(new File(zipFile), new AESDecrypterBC());
            ExtZipEntry entry = decrypter.getEntry("fingen.db");
            if (entry.isEncrypted()) {
                decrypter.extractEntry(entry, new File(location + "/fingen.db.ex"), password);
            } else {
                unzip(zipFile, location + "/fingen.db.ex");
            }
        } catch (ZipException ze) {
            Log.e(TAG, "Wrong password", ze);
            onCompleteListener.onWrongPassword();
            return;
        } catch (Exception e) {
            Log.e(TAG, "Unzip exception", e);
            onCompleteListener.onError();
            return;
        }
        onCompleteListener.onComplete();
    }

    /**
     * Display name of a document (SAF Uri), or empty string if it can't be resolved.
     */
    public static String getDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) {
                    return name;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getDisplayName: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    /**
     * Copies content of a document (SAF Uri) to a new temporary file in the app cache dir.
     * The caller must delete the file.
     */
    public static File copyUriToCache(Context context, Uri uri) throws IOException {
        return copyUriToCache(context, uri, "csv_import", ".csv");
    }

    /**
     * Same as {@link #copyUriToCache(Context, Uri)} with a custom temporary file name pattern.
     */
    public static File copyUriToCache(Context context, Uri uri, String prefix, String suffix) throws IOException {
        File tmp = File.createTempFile(prefix, suffix, context.getCacheDir());
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) {
            tmp.delete();
            throw new IOException("Can't open " + uri);
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            copyStream(in, out);
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
        return tmp;
    }

    /**
     * Copies a local file to a document (SAF Uri).
     */
    public static void copyFileToUri(Context context, File file, Uri uri) throws IOException {
        OutputStream out = context.getContentResolver().openOutputStream(uri);
        if (out == null) {
            throw new IOException("Can't open " + uri);
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            copyStream(in, out);
        } finally {
            out.close();
            if (in != null) {
                in.close();
            }
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        out.flush();
    }

}
