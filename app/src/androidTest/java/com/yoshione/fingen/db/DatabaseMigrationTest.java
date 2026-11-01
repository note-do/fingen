package com.yoshione.fingen.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Migration tests on separate database files. DBHelper.onUpgrade makes a zip backup of the real
 * fingen.db when the storage permission is granted; such files are removed after each test.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

    private Context mContext;
    private Set<String> mBackupsBefore;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MigrationTestHelper.deleteTestDatabases(mContext);
        mBackupsBefore = MigrationTestHelper.listBackups();
    }

    @After
    public void tearDown() {
        MigrationTestHelper.deleteTestDatabases(mContext);
        MigrationTestHelper.deleteNewBackups(mBackupsBefore);
    }

    @Test
    public void upgrade34To35_keepsDataAndMatchesCleanSchema() throws Exception {
        MigrationTestHelper.createFixture(mContext, 34,
                "INSERT INTO ref_Currencies (_id, Code, Symbol, Name, DecimalCount) VALUES (100, 'TST', 'T', 'Test', 2)",
                "INSERT INTO ref_Accounts (_id, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 'Migration account', 100, 12.5, 0)");

        String name = MigrationTestHelper.dbName("34");
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(35, migrated.getVersion());

            Map<String, Set<String>> migratedColumns = MigrationTestHelper.dumpColumns(migrated);
            Map<String, Set<String>> cleanColumns = MigrationTestHelper.dumpColumns(clean);
            assertEquals("tables and columns", cleanColumns, migratedColumns);
            assertEquals("tables and indexes (sql)", MigrationTestHelper.dumpSchema(clean),
                    MigrationTestHelper.dumpSchema(migrated));

            try (Cursor c = migrated.rawQuery("SELECT Name, StartBalance, Currency FROM ref_Accounts WHERE _id = 100", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("Migration account", c.getString(0));
                assertEquals(12.5, c.getDouble(1), 0.0001);
                assertEquals(100, c.getLong(2));
            }
            try (Cursor c = migrated.rawQuery("SELECT Code FROM ref_Currencies WHERE _id = 100", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("TST", c.getString(0));
            }
        } finally {
            migrated.close();
            clean.close();
        }
        assertEquals(35, DBHelper.DATABASE_VERSION);
    }
}
