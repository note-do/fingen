package com.yoshione.fingen.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Behaviour of PRAGMA legacy_alter_table for ALTER TABLE RENAME in update17..update23.
 * <p>
 * DBHelper has foreign_keys OFF, so the rewritten REFERENCES have no effect for users now; the risk appears
 * when foreign keys are enabled. SQLite below 3.26 (and with legacy_alter_table=ON) does not rewrite REFERENCES
 * of other tables on RENAME; 3.26+ with OFF rewrites them to the *_old name, and the dropped *_old tables stay
 * referenced. The expected state depends on the SQLite version of the device, so the default mode is asserted
 * by version, and the actual state is written to the log (tag LegacyAlterTable).
 * <p>
 * The PRAGMA is set in onConfigure, i.e. before onUpgrade (it is a connection setting and is read back in the test).
 * Not covered: other SQLite versions, vendor builds, real user databases (fixtures are reconstructed).
 */
@RunWith(AndroidJUnit4.class)
public class LegacyAlterTableTest {

    private static final String TAG = "LegacyAlterTable";

    private Context mContext;
    private Set<String> mBackupsBefore;
    private boolean mHadColorInd;
    private int mColorInd;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mHadColorInd = preferences.contains("color_ind");
        mColorInd = preferences.getInt("color_ind", 0);
        MigrationTestHelper.deleteTestDatabases(mContext);
        mBackupsBefore = MigrationTestHelper.listBackups();
    }

    @After
    public void tearDown() {
        MigrationTestHelper.deleteTestDatabases(mContext);
        MigrationTestHelper.deleteNewBackups(mBackupsBefore);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        if (mHadColorInd) {
            editor.putInt("color_ind", mColorInd);
        } else {
            editor.remove("color_ind");
        }
        editor.commit();
    }

    @Test
    public void defaults_areLogged() throws Exception {
        MigrationTestHelper.createFixture(mContext, 21, DatabaseMigrationTest.SEED_V21);
        SQLiteDatabase db = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("21"));
        try {
            String message = "sqlite_version=" + queryString(db, "SELECT sqlite_version()")
                    + ", legacy_alter_table(default)=" + queryString(db, "PRAGMA legacy_alter_table")
                    + ", foreign_keys=" + queryString(db, "PRAGMA foreign_keys");
            Log.i(TAG, message);
            // a failing assert shows the values in the report
            assertTrue(message, true);
        } finally {
            db.close();
        }
    }

    @Test
    public void migrate21_defaultVsLegacyOn() throws Exception {
        compareModes(21);
    }

    @Test
    public void migrate16_defaultVsLegacyOn() throws Exception {
        compareModes(16);
    }

    private void compareModes(int version) throws Exception {
        String name = MigrationTestHelper.dbName(String.valueOf(version));

        resetColorIndex();
        MigrationTestHelper.createFixture(mContext, version, seed(version));
        SQLiteDatabase def = MigrationTestHelper.openMigrated(mContext, name);
        String defaultDump;
        List<String> defaultRefs;
        String sqliteVersion;
        String defaultPragma;
        try {
            assertEquals(36, def.getVersion());
            sqliteVersion = queryString(def, "SELECT sqlite_version()");
            defaultPragma = queryString(def, "PRAGMA legacy_alter_table");
            defaultDump = dumpData(def);
            defaultRefs = oldReferences(def);
            assertNoViewsAndTriggers(def, version, "default");
        } finally {
            def.close();
        }
        Log.i(TAG, "v" + version + " sqlite=" + sqliteVersion + " default legacy_alter_table=" + defaultPragma
                + " references to *_old after migration: " + defaultRefs);

        mContext.deleteDatabase(name);
        resetColorIndex();
        MigrationTestHelper.createFixture(mContext, version, seed(version));
        SQLiteDatabase on = MigrationTestHelper.openMigratedWithPragmas(mContext, name, "PRAGMA legacy_alter_table=ON");
        try {
            assertEquals(36, on.getVersion());
            // the connection setting is applied (the connection is the one that ran onUpgrade)
            assertEquals("legacy_alter_table in ON mode", "1", queryString(on, "PRAGMA legacy_alter_table"));
            List<String> onRefs = oldReferences(on);
            Log.i(TAG, "v" + version + " references to *_old with legacy_alter_table=ON: " + onRefs);
            assertTrue("references to *_old with legacy_alter_table=ON: " + onRefs, onRefs.isEmpty());
            assertEquals("data of the default mode and of legacy_alter_table=ON", defaultDump, dumpData(on));
            assertNoViewsAndTriggers(on, version, "ON");
        } finally {
            on.close();
        }

        // OFF mode is informational: it shows what the references would be without the Android default (not asserted)
        mContext.deleteDatabase(name);
        resetColorIndex();
        MigrationTestHelper.createFixture(mContext, version, seed(version));
        SQLiteDatabase off = MigrationTestHelper.openMigratedWithPragmas(mContext, name, "PRAGMA legacy_alter_table=OFF");
        try {
            assertEquals(36, off.getVersion());
            assertEquals("legacy_alter_table in OFF mode", "0", queryString(off, "PRAGMA legacy_alter_table"));
            Log.i(TAG, "v" + version + " references to *_old with legacy_alter_table=OFF: " + oldReferences(off));
            assertEquals("data of the default mode and of legacy_alter_table=OFF", defaultDump, dumpData(off));
        } finally {
            off.close();
        }

        // the state of the default mode depends on the SQLite version and the default of the device
        int[] parts = parseVersion(sqliteVersion);
        boolean rewrites = parts[0] > 3 || (parts[0] == 3 && parts[1] >= 26);
        if ("1".equals(defaultPragma) || !rewrites) {
            assertTrue("sqlite " + sqliteVersion + ", default legacy_alter_table=" + defaultPragma
                    + ": references are not rewritten, found " + defaultRefs, defaultRefs.isEmpty());
        } else {
            Log.w(TAG, "sqlite " + sqliteVersion + " rewrites references by default (legacy_alter_table="
                    + defaultPragma + "), found " + defaultRefs);
        }
    }

    private String[] seed(int version) {
        return version == 21 ? DatabaseMigrationTest.SEED_V21 : DatabaseMigrationTest.SEED_V16;
    }

    private void resetColorIndex() {
        // update31 takes the next color from "color_ind": both runs must start from the same value
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove("color_ind").commit();
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    /** "table: sql fragment mentioning *_old" for every table or index whose SQL contains "_old". */
    private static List<String> oldReferences(SQLiteDatabase db) {
        List<String> result = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT type, name, sql FROM sqlite_master WHERE sql LIKE '%\\_old%' ESCAPE '\\' "
                + "ORDER BY name", null)) {
            while (c.moveToNext()) {
                String sql = c.getString(2).replaceAll("\\s+", " ");
                Set<String> refs = new TreeSet<>();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\"`\\[]?(\\w+_old)[\"`\\]]?").matcher(sql);
                while (m.find()) {
                    refs.add(m.group(1));
                }
                result.add(c.getString(0) + " " + c.getString(1) + " -> " + refs);
            }
        }
        return result;
    }

    private static void assertNoViewsAndTriggers(SQLiteDatabase db, int version, String mode) {
        List<String> found = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT type, name FROM sqlite_master WHERE type IN ('view','trigger')", null)) {
            while (c.moveToNext()) {
                found.add(c.getString(0) + " " + c.getString(1));
            }
        }
        Log.i(TAG, "v" + version + " " + mode + " views and triggers: " + found);
        assertTrue("views and triggers after migration (v" + version + ", " + mode + "): " + found, found.isEmpty());
    }

    /** All rows of all tables as strings, sorted, so that the order of rows does not matter. */
    private static String dumpData(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                + "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name", null)) {
            while (c.moveToNext()) {
                tables.add(c.getString(0));
            }
        }
        StringBuilder result = new StringBuilder();
        for (String table : tables) {
            List<String> rows = new ArrayList<>();
            try (Cursor c = db.rawQuery("SELECT * FROM \"" + table + "\"", null)) {
                while (c.moveToNext()) {
                    StringBuilder row = new StringBuilder();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        row.append(c.getColumnName(i).toLowerCase()).append('=').append(c.getString(i)).append('|');
                    }
                    rows.add(row.toString());
                }
            }
            Collections.sort(rows);
            result.append(table).append(" (").append(rows.size()).append(")\n");
            for (String row : rows) {
                result.append(row).append('\n');
            }
        }
        return result.toString();
    }

    private static String queryString(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(sql, c.moveToFirst());
            return c.getString(0);
        }
    }
}
