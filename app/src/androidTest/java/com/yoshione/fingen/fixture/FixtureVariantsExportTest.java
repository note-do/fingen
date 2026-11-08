package com.yoshione.fingen.fixture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertNotNull;

/**
 * Service test, not a check: builds every variant of {@link FixtureDb#VARIANTS} through the DAOs,
 * runs verify and saves the file as &lt;name&gt;.db to shared Downloads
 * (MediaStore, /storage/emulated/0/Download/fingen_fixtures/; survives the uninstall after the run).
 * Skipped without the argument exportFixtures=true:
 * <pre>
 * -Pandroid.testInstrumentationRunnerArguments.class=com.yoshione.fingen.fixture.FixtureVariantsExportTest
 * -Pandroid.testInstrumentationRunnerArguments.exportFixtures=true
 * </pre>
 * Then adb pull /storage/emulated/0/Download/fingen_fixtures/ and copy the files into
 * app/src/androidTest/assets/fixtures/ (see CLAUDE.md).
 */
@RunWith(AndroidJUnit4.class)
public class FixtureVariantsExportTest {

    private static final String DOWNLOAD_DIR = "Download/fingen_fixtures/";

    @Test
    public void exportVariants() throws Exception {
        Assume.assumeTrue("pass exportFixtures=true to export",
                "true".equals(InstrumentationRegistry.getArguments().getString("exportFixtures")));
        Context context = InstrumentationRegistry.getTargetContext();
        for (String name : FixtureDb.VARIANTS) {
            FixtureDb.build(context, FixtureDb.variantSpec(name)); // includes verify
            DBHelper.getInstance(context).close();
            publish(context, new File(context.getDatabasePath("fingen.db").getPath()), name + ".db");
        }
    }

    /**
     * Copies the file to shared Downloads (MediaStore, survives the uninstall that Gradle does after the
     * run); an older file of the same name is replaced.
     */
    private static void publish(Context context, File src, String displayName) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                MediaStore.Downloads.DISPLAY_NAME + " = ? AND " + MediaStore.Downloads.RELATIVE_PATH + " LIKE ?",
                new String[]{displayName, DOWNLOAD_DIR + "%"});
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_DIR);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull("MediaStore insert failed: " + displayName, uri);
        try (InputStream in = new FileInputStream(src);
             OutputStream out = resolver.openOutputStream(uri)) {
            assertNotNull("openOutputStream failed: " + displayName, out);
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
    }
}
