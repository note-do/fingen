package com.yoshione.fingen.db;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.db.migrations.Migration36;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Migration tests on separate database files. DBHelper.onUpgrade makes a zip backup of the real
 * fingen.db when the storage permission is granted; such files are removed after each test.
 * <p>
 * Fixtures of versions 16, 21, 23 and 30 (assets/migrations) are RECONSTRUCTED from UpdateHelper, not taken
 * from real databases, see the header of each fixture. For them the schema is compared with a clean
 * database by tables, columns (ignoring identifier case) and indexes, not by the SQL text of tables:
 * ALTER TABLE ADD COLUMN changes that text and the column order.
 * <p>
 * Assumptions of the v21 and v16 tests (in addition to the fixture headers):
 * <ul>
 * <li>v21 = tableInfo21 (schema after update18..update20); update21 (NULL Comment of templates -> '') is already applied.</li>
 * <li>v16 = columns of tableInfo17 with types of tableInfo18, no sync columns, no constraints, no ref_Senders.
 * It is the least accurate fixture: a real v16 database may differ in types, constraints and column order.</li>
 * <li>v16 data has no sender markers (ref_Sms_Parser_Patterns.Type = 5), so update17 creates a sender for the
 * source Sender value of each SMS and keeps ALL rows of log_Incoming_SMS. This is asserted.</li>
 * <li>v16 DateTime is the text "yyyy-MM-dd HH:mm:ss" in the time zone of the device; update18 converts it to
 * milliseconds with the same pattern and the default time zone, so the expected value is computed in the test with
 * the same pattern. The test does not depend on the time zone of the device.</li>
 * <li>update18 recreates log_Transactions and inserts rows WITHOUT _id, so transactions of v16 get new ids 1, 2, 3
 * (in the order of the old table); the v16 test finds them by Comment.</li>
 * <li>update18 reads Payee, Category, Project, Department, Location with getLong: NULL becomes 0; update22 then
 * changes Project, Department and Location 0 to -1, while Payee and Category stay 0. Migration36 turns every -1 and
 * every 0 without a parent row into NULL.</li>
 * <li>update31 calls ColorUtils.getColor, which increments "color_ind" in the default SharedPreferences;
 * the value is saved before each test and restored after it.</li>
 * <li>Version 36 (Migration36) works in minor units: amounts are INTEGER, 10.005 of a currency with 2 digits is 1001,
 * exchange rate x10^6, quantity x10^3. The expectations of the v16..v30 tests are in these units.</li>
 * <li>Not covered: the real v16 with sender markers (see the ignored test), backups of other versions,
 * the effect of legacy_alter_table on the SQL text of foreign keys (only columns and indexes are compared).</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationTest {

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
        // update31 moves the color index of projects (ColorUtils.getColor)
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        if (mHadColorInd) {
            editor.putInt("color_ind", mColorInd);
        } else {
            editor.remove("color_ind");
        }
        editor.apply();
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
            assertEquals(36, migrated.getVersion());

            Map<String, Set<String>> migratedColumns = MigrationTestHelper.dumpColumns(migrated);
            Map<String, Set<String>> cleanColumns = MigrationTestHelper.dumpColumns(clean);
            assertEquals("tables and columns", cleanColumns, migratedColumns);
            // SQL text is not compared: the recreated tables are renamed by SQLite (ALTER TABLE ... RENAME)
            assertEquals("indexes", MigrationTestHelper.dumpIndexes(clean), MigrationTestHelper.dumpIndexes(migrated));

            try (Cursor c = migrated.rawQuery("SELECT Name, StartBalance, CurrencyId FROM ref_Accounts WHERE _id = 100", null)) {
                assertTrue(c.moveToFirst());
                assertEquals("Migration account", c.getString(0));
                // StartBalance is minor units of the currency (2 digits): 12.5 -> 1250
                assertEquals(1250, c.getLong(1));
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
        assertEquals(36, DBHelper.DATABASE_VERSION);
    }

    /** 30 -> 36 runs update31 (Projects.Color), update32 (Split, products), update33 and Migration36. */
    @Test
    public void upgrade30To35_addsColorSplitAndProducts() throws Exception {
        MigrationTestHelper.createFixture(mContext, 30,
                "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (100, 0, 'TST', 'T', 'Test', 2, 0)",
                "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0, 'migration account')",
                "INSERT INTO ref_Projects (_id, Deleted, Name, IsActive, ParentID, OrderNumber, FullName, SearchString) VALUES (100, 0, 'Project A', 1, -1, 0, 'Project A', 'project a')",
                TRANSACTION_V30 + " VALUES (100, 0, 1000, 100, -1, -1, -100, 100, -1, -1, -1, 'Coffee', -1, 1, 0, 0, 0, -1, 11, 12, 13, 'coffee')",
                TRANSACTION_V30 + " VALUES (101, 0, 2000, 100, -1, -1, 250.5, -1, -1, -1, -1, 'Salary', -1, 1, 0, 0, 0, -1, 0, 0, 0, 'salary')",
                TRANSACTION_V30 + " VALUES (102, 1, 3000, 100, -1, -1, -5, -1, -1, -1, -1, 'Deleted', -1, 1, 0, 0, 0, -1, 0, 0, 0, 'deleted')",
                // stale values: Migration36 recalculates the running balance (RunningBalance.rebuild)
                "INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 100, 1000, 0, -999)",
                "INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 101, 2000, 250.5, -999)");

        String name = MigrationTestHelper.dbName("30");
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(36, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);

            assertEquals("Migration account", queryString(migrated, "SELECT Name FROM ref_Accounts WHERE _id = 100"));
            assertEquals(1250, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 100"));

            // update31: Color of each project is "#RRGGBB" from ColorUtils
            assertEquals("Project A", queryString(migrated, "SELECT Name FROM ref_Projects WHERE _id = 100"));
            String color = queryString(migrated, "SELECT Color FROM ref_Projects WHERE _id = 100");
            assertTrue("Color: " + color, color != null && color.matches("#[0-9A-F]{6}"));

            // update32: IsSplit = 0 for existing rows, other columns are kept (amounts in minor units)
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE IsSplit = 0"));
            assertEquals(-10000, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = 100"));
            assertEquals("coffee", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
            assertEquals(11, queryLong(migrated, "SELECT FN FROM log_Transactions WHERE _id = 100"));
            assertEquals(12, queryLong(migrated, "SELECT FD FROM log_Transactions WHERE _id = 100"));
            assertEquals(13, queryLong(migrated, "SELECT FP FROM log_Transactions WHERE _id = 100"));

            // update32: default product, one log_Products row per not deleted transaction (update33 adds none)
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
            assertEquals("default_product", queryString(migrated, "SELECT Name FROM ref_Products WHERE _id = 0"));
            assertProducts(migrated, 100, -10000);
            assertProducts(migrated, 101, 25050);
            assertEquals(2, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE TransactionID = 102"));

            // Migration36 rebuilds the balance: income 250.5 of the salary, expense -100 of the coffee
            assertEquals(2, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
            assertBalance(migrated, 100, 100, 0, -10000);
            assertBalance(migrated, 100, 101, 25050, -10000);
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /** 23 -> 36 runs update24 ... update33, Migration36 (including the running balance and full names). */
    @Test
    public void upgrade23To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 23, SEED_V23);

        String name = MigrationTestHelper.dbName("23");
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(36, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);

            assertEquals("Migration account", queryString(migrated, "SELECT Name FROM ref_Accounts WHERE _id = 100"));
            assertEquals(1250, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 100"));
            // update27: SearchString of accounts is transliterated lower-case Name
            assertEquals("migration account", queryString(migrated, "SELECT SearchString FROM ref_Accounts WHERE _id = 100"));

            // update24: NULL ParentID of a category becomes -1 and then NULL (Migration36), update27: FullName from the parent chain
            assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 101"));
            assertEquals("Parent", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 100"));
            assertEquals("Parent\\Child", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 101"));
            // SearchString is built from FullName for tables with a hierarchy
            assertEquals("parent\\child", queryString(migrated, "SELECT SearchString FROM ref_Categories WHERE _id = 101"));

            // update27: Cyrillic name is transliterated in SearchString, FullName keeps the name
            assertEquals("Кофе", queryString(migrated, "SELECT FullName FROM ref_Payees WHERE _id = 100"));
            assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM ref_Payees WHERE _id = 100"));
            assertEquals("template", queryString(migrated, "SELECT SearchString FROM log_Templates WHERE _id = 100"));

            // update31: project color, update27: full name
            String color = queryString(migrated, "SELECT Color FROM ref_Projects WHERE _id = 100");
            assertTrue("Color: " + color, color != null && color.matches("#[0-9A-F]{6}"));
            assertEquals("Project A", queryString(migrated, "SELECT FullName FROM ref_Projects WHERE _id = 100"));

            // update29: new column of senders has the default 0
            assertEquals(0, queryLong(migrated, "SELECT AddCreditLimitToBalance FROM ref_Senders WHERE _id = 100"));

            // update25: debt with transactions gets the currency of the account, debt without transactions is deleted
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_SimpleDebts WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT StartAmount FROM ref_SimpleDebts WHERE _id = 100"));
            assertEquals("debt with tx", queryString(migrated, "SELECT SearchString FROM ref_SimpleDebts WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts WHERE _id = 101"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts"));

            // update24: NULL Lat/Lon -> 0, Accuracy -> -1, SimpleDebt -> -1 and then NULL (all rows, also deleted ones)
            assertEquals(0, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = 100"), 0.0001);
            assertEquals(0, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = 100"), 0.0001);
            assertEquals(-1, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT SimpleDebtId FROM log_Transactions WHERE _id = 100"));
            assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = 101"), 0.0001);
            assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = 101"), 0.0001);
            assertEquals(10, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = 101"));
            assertNull(queryNullableLong(migrated, "SELECT SimpleDebtId FROM log_Transactions WHERE _id = 101"));
            assertNull(queryNullableLong(migrated, "SELECT SimpleDebtId FROM log_Transactions WHERE _id = 103"));

            // update29: SearchString of not deleted transactions, a deleted one is not touched
            assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
            assertEquals("salary", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 101"));
            assertEquals("transfer", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 102"));
            assertNull(queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 103"));

            // update30, update32: new columns of transactions have the default 0
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE FN = 0 AND FD = 0 AND FP = 0 AND IsSplit = 0"));

            // update32: one product per not deleted transaction, price equals the amount (minor units)
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
            assertProducts(migrated, 100, -10000);
            assertProducts(migrated, 101, 25000);
            assertProducts(migrated, 102, -4000);
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));

            // update27/28: running balance of not deleted transactions, transfer adds a row for the destination account
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
            assertBalance(migrated, 100, 100, 0, -10000);
            assertBalance(migrated, 100, 101, 25000, -10000);
            assertBalance(migrated, 100, 102, 25000, -14000);
            assertBalance(migrated, 101, 102, 4000, 0);
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /**
     * update29 must not fail on a not deleted transaction with NULL Comment: SearchString becomes an empty string.
     */
    @Test
    public void upgrade23To35_nullCommentInNotDeletedTransaction() throws Exception {
        MigrationTestHelper.createFixture(mContext, 23,
                "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount) VALUES (100, 0, 'TST', 'T', 'Test', 2)",
                "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0)",
                TRANSACTION_V23 + " VALUES (100, 0, 1000, 100, NULL, NULL, -100, -1, -1, -1, -1, NULL, NULL, -1, 1, 0, 0, 0, -1)");

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("23"));
        try {
            assertEquals(36, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id = 100"));
            assertEquals("", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /** 21 -> 36 runs update22 ... update33, Migration36 (renamed ParentID, new hierarchy columns, currency order, then as for 23). */
    @Test
    public void upgrade21To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 21, SEED_V21);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("21"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(36, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);
            assertMigratedData(migrated, 100, 101, 102, 1000, 2000, 4000);

            // update22: all rows of new hierarchy columns are set, update23: OrderNumber of currencies is 0
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Payees WHERE ParentID <> -1 OR OrderNumber <> 0"));
            assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Currencies WHERE _id = 100"));

            // tables that no step after 21 touches keep their rows
            assertEquals("Bank", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT AddCreditLimitToBalance FROM ref_Senders WHERE _id = 100"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            assertEquals(100, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals("sms body", queryString(migrated, "SELECT Body FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns"));
            assertEquals("pattern", queryString(migrated, "SELECT Pattern FROM ref_Sms_Parser_Patterns WHERE _id = 100"));

            // deleted transaction with NULL Comment: update29 does not touch it, update32/33 give it no product
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions"));
            assertNull(queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 103"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE TransactionID = 103"));
            assertNull(queryNullableLong(migrated, "SELECT ProjectId FROM log_Transactions WHERE _id = 103"));
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /**
     * Same as upgrade23To35_nullCommentInNotDeletedTransaction: update29 handles NULL Comment.
     */
    @Test
    public void upgrade21To35_nullCommentInNotDeletedTransaction() throws Exception {
        MigrationTestHelper.createFixture(mContext, 21,
                "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount) VALUES (100, 0, 'TST', 'T', 'Test', 2)",
                "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0)",
                TRANSACTION_V23 + " VALUES (100, 0, 1000, 100, NULL, NULL, -100, -1, -1, -1, -1, NULL, NULL, -1, 1, 0, 0, 0, -1)");

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("21"));
        try {
            assertEquals(36, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id = 100"));
            assertEquals("", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * 16 -> 36 runs update17 ... update33 and Migration36. The fixture has no sender markers, so update17 creates a sender from
     * the Sender value of the SMS and keeps the SMS (see the class comment). DateTime is converted from the text to milliseconds by update18.
     */
    @Test
    public void upgrade16To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 16, SEED_V16);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("16"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(36, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);

            // update18 parses "yyyy-MM-dd HH:mm:ss" in the default time zone, expected values use the same pattern
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            // update18 inserts transactions without _id: they get new ids 1, 2, 3 in the old order; found by Comment
            long id100 = queryLong(migrated, "SELECT _id FROM log_Transactions WHERE Comment = 'Кофе'");
            long id101 = queryLong(migrated, "SELECT _id FROM log_Transactions WHERE Comment = 'Salary'");
            long id102 = queryLong(migrated, "SELECT _id FROM log_Transactions WHERE Comment = 'Transfer'");
            assertEquals(1, id100);
            assertEquals(2, id101);
            assertEquals(3, id102);
            assertMigratedData(migrated, id100, id101, id102,
                    format.parse("2016-05-17 10:30:00").getTime(),
                    format.parse("2016-05-18 11:00:00").getTime(),
                    format.parse("2016-05-19 12:00:00").getTime());
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions"));
            // update18: NULL Payee and Category become 0 (getLong); Migration36: 0 without a row becomes NULL
            assertNull(queryNullableLong(migrated, "SELECT PayeeId FROM log_Transactions WHERE _id = " + id100));
            assertNull(queryNullableLong(migrated, "SELECT CategoryId FROM log_Transactions WHERE _id = " + id100));
            assertEquals(100, queryLong(migrated, "SELECT PayeeId FROM log_Transactions WHERE _id = " + id101));
            assertEquals(101, queryLong(migrated, "SELECT CategoryId FROM log_Transactions WHERE _id = " + id101));
            // update18 sets Deleted = 0 for all rows
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted = 0"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Accounts WHERE Deleted <> 0 OR Deleted IS NULL"));
            // column TS (sync) is removed in version 36
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM pragma_table_info('ref_Accounts') WHERE name = 'TS'"));

            // update17: no sender markers -> sender is created from the Sender value of the SMS, the SMS is kept
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders"));
            assertEquals("+1000", queryString(migrated, "SELECT Name FROM ref_Senders"));
            assertEquals("+1000", queryString(migrated, "SELECT PhoneNo FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            assertEquals(100, queryLong(migrated, "SELECT _id FROM log_Incoming_SMS"));
            assertEquals("sms body", queryString(migrated, "SELECT Body FROM log_Incoming_SMS"));
            assertEquals(queryLong(migrated, "SELECT _id FROM ref_Senders"),
                    queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns"));
            assertEquals("pattern", queryString(migrated, "SELECT Pattern FROM ref_Sms_Parser_Patterns WHERE _id = 100"));
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /**
     * After update18 every transaction of v16 is not deleted (Deleted = 0), so any NULL Comment reaches update29,
     * which must handle it.
     */
    @Test
    public void upgrade16To35_nullCommentInTransaction() throws Exception {
        MigrationTestHelper.createFixture(mContext, 16,
                "INSERT INTO ref_Currencies (_id, Code, Symbol, Name, DecimalCount) VALUES (100, 'TST', 'T', 'Test', 2)",
                "INSERT INTO ref_Accounts (_id, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 'Migration account', 100, 12.5, 0)",
                TRANSACTION_V16 + " VALUES (100, '2016-05-17 10:30:00', 100, NULL, NULL, -100, NULL, NULL, NULL, NULL, -1, 1, 0, NULL, NULL, NULL, NULL, NULL)");

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("16"));
        try {
            assertEquals(36, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions"));
            assertEquals("", queryString(migrated, "SELECT SearchString FROM log_Transactions"));
        } finally {
            migrated.close();
        }
    }

    /**
     * update17 writes the senders of markers (Type = 5) directly with SQL, without SendersDAO (it waits for the end
     * of the upgrade). SMS of the marker sender refers to the new ref_Senders row.
     */
    @Test
    public void upgrade16To35_senderMarkers() throws Exception {
        MigrationTestHelper.createFixture(mContext, 16,
                "INSERT INTO ref_Sms_Parser_Patterns (_id, Type, Object, Pattern) VALUES (100, 5, 'Bank', '+1000')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (100, '2016-05-17 10:30:00', '+1000', 'sms body')");

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("16"));
        try {
            assertEquals(36, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns WHERE Type = 5"));
            assertEquals("Bank", queryString(migrated, "SELECT Name FROM ref_Senders"));
            assertEquals("+1000", queryString(migrated, "SELECT PhoneNo FROM ref_Senders"));
            assertEquals(0, queryLong(migrated, "SELECT AmountPos FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT BalancePos FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT isActive FROM ref_Senders"));
            assertEquals(queryLong(migrated, "SELECT _id FROM ref_Senders"),
                    queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * update17 keeps ALL SMS: the sender of an SMS is found among the marker senders by the trimmed lower case
     * Sender value, otherwise a sender (Name = PhoneNo = source value) is created; an empty or blank Sender gives Sender = 0 (NULL cannot occur: Sender is NOT NULL in v16).
     * Migration36 then marks the SMS with Sender 0 as deleted and points it to the placeholder sender "(lost)".
     */
    @Test
    public void upgrade16To35_keepsAllSms() throws Exception {
        MigrationTestHelper.createFixture(mContext, 16,
                "INSERT INTO ref_Sms_Parser_Patterns (_id, Type, Object, Pattern) VALUES (100, 5, 'Bank', '+1000')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (1, '2016-05-17 10:30:00', '+1000', 'marker')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (2, '2016-05-17 10:31:00', 'ShopInfo', 'unknown 1')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (3, '2016-05-17 10:32:00', 'shopinfo', 'unknown 2')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (4, '2016-05-17 10:33:00', '+2000', 'unknown 3')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (5, '2016-05-17 10:34:00', '   ', 'blank sender')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (6, '2016-05-17 10:35:00', '', 'empty sender')",
                "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (7, '2016-05-17 10:36:00', 'Bank', 'name of marker')");

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("16"));
        try {
            assertEquals(36, migrated.getVersion());
            assertEquals(7, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            // senders: marker +1000, ShopInfo, +2000, Bank (Name is taken by the marker -> "Bank (SMS)"), service "(unknown)" for Sender 0
            assertEquals(5, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders"));
            long bank = queryLong(migrated, "SELECT _id FROM ref_Senders WHERE PhoneNo = '+1000'");
            long shop = queryLong(migrated, "SELECT _id FROM ref_Senders WHERE PhoneNo = 'ShopInfo'");
            long other = queryLong(migrated, "SELECT _id FROM ref_Senders WHERE PhoneNo = '+2000'");
            long bankName = queryLong(migrated, "SELECT _id FROM ref_Senders WHERE PhoneNo = 'Bank'");
            assertEquals("Bank", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = " + bank));
            assertEquals("ShopInfo", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = " + shop));
            assertEquals("+2000", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = " + other));
            assertEquals("Bank (SMS)", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = " + bankName));
            assertEquals(1, queryLong(migrated, "SELECT isActive FROM ref_Senders WHERE _id = " + shop));
            assertEquals(bank, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 1"));
            assertEquals(shop, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 2"));
            assertEquals(shop, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 3"));
            assertEquals(other, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 4"));
            assertEquals(bankName, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 7"));
            // Sender 0 (blank and empty): stays visible, attached to the service sender "(unknown)" (Deleted = 0, IsActive = 1)
            for (long id : new long[]{5, 6}) {
                assertEquals("SMS " + id, 0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = " + id));
                assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders WHERE _id = "
                        + "(SELECT SenderId FROM log_Incoming_SMS WHERE _id = " + id + ") AND Deleted = 0 AND IsActive = 1 AND Name = '(unknown)'"));
            }
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = 2"));
            assertEquals("blank sender", queryString(migrated, "SELECT Body FROM log_Incoming_SMS WHERE _id = 5"));
        } finally {
            migrated.close();
        }
    }

    /**
     * Checks the data that SEED_V21 and SEED_V16 describe in the same way (see both seeds). Dates of the
     * transactions are passed in milliseconds, the rest of the expectations is the same for both versions.
     */
    private static void assertMigratedData(SQLiteDatabase migrated, long id100, long id101, long id102,
                                           long date100, long date101, long date102) {
        assertEquals("Migration account", queryString(migrated, "SELECT Name FROM ref_Accounts WHERE _id = 100"));
        assertEquals(1250, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"));
        assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 100"));
        assertEquals("migration account", queryString(migrated, "SELECT SearchString FROM ref_Accounts WHERE _id = 100"));
        assertEquals("TST", queryString(migrated, "SELECT Code FROM ref_Currencies WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Currencies WHERE _id = 100"));

        // update22 renames ParentCategory to ParentID, update24: NULL becomes -1, Migration36: -1 becomes NULL; FullName and SearchString
        assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 100"));
        assertEquals(100, queryLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 101"));
        assertEquals("Parent", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 100"));
        assertEquals("Parent\\Child", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 101"));
        assertEquals("parent\\child", queryString(migrated, "SELECT SearchString FROM ref_Categories WHERE _id = 101"));

        // update22: ParentID -1 and OrderNumber 0 in the new columns of payees, projects, locations, departments
        assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Payees WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Payees WHERE _id = 100"));
        assertEquals("Кофе", queryString(migrated, "SELECT FullName FROM ref_Payees WHERE _id = 100"));
        assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM ref_Payees WHERE _id = 100"));
        assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Projects WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Projects WHERE _id = 100"));
        assertEquals("Project A", queryString(migrated, "SELECT FullName FROM ref_Projects WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT IsActive FROM ref_Projects WHERE _id = 100"));
        String color = queryString(migrated, "SELECT Color FROM ref_Projects WHERE _id = 100");
        assertTrue("Color: " + color, color != null && color.matches("#[0-9A-F]{6}"));
        assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Departments WHERE _id = 100"));
        assertEquals("Department A", queryString(migrated, "SELECT FullName FROM ref_Departments WHERE _id = 100"));
        assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Locations WHERE _id = 100"));
        assertEquals("Place A", queryString(migrated, "SELECT FullName FROM ref_Locations WHERE _id = 100"));
        assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM ref_Locations WHERE _id = 100"), 0.0001);
        assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM ref_Locations WHERE _id = 100"), 0.0001);

        assertEquals("template", queryString(migrated, "SELECT SearchString FROM log_Templates WHERE _id = 100"));
        assertEquals("", queryString(migrated, "SELECT Comment FROM log_Templates WHERE _id = 100"));

        // update25: debt with transactions gets the currency of the account, debt without transactions is deleted
        assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_SimpleDebts WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT StartAmount FROM ref_SimpleDebts WHERE _id = 100"));
        assertEquals("debt with tx", queryString(migrated, "SELECT SearchString FROM ref_SimpleDebts WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts"));

        // debts and budgets are not touched by the steps (amounts in minor units)
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Debts"));
        assertEquals(101, queryLong(migrated, "SELECT AccountId FROM ref_Debts WHERE _id = 100"));
        assertEquals(100, queryLong(migrated, "SELECT PayeeId FROM ref_Debts WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_budget"));
        assertEquals(30000, queryLong(migrated, "SELECT Amount FROM ref_budget WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_budget_debts"));
        assertEquals(5000, queryLong(migrated, "SELECT Amount FROM ref_budget_debts WHERE _id = 100"));

        // transactions: dates, update22: 0 -> -1 for Project/Department/Location, Migration36: -1 -> NULL; update24: NULL Lat/Lon/Accuracy/SimpleDebt
        assertEquals(date100, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id100));
        assertEquals(date101, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id101));
        assertEquals(date102, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id102));
        assertEquals(-10000, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = " + id100));
        assertEquals(25000, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = " + id101));
        assertEquals(101, queryLong(migrated, "SELECT DestAccountId FROM log_Transactions WHERE _id = " + id102));
        assertNull(queryNullableLong(migrated, "SELECT ProjectId FROM log_Transactions WHERE _id = " + id100));
        assertNull(queryNullableLong(migrated, "SELECT DepartmentId FROM log_Transactions WHERE _id = " + id100));
        assertNull(queryNullableLong(migrated, "SELECT LocationId FROM log_Transactions WHERE _id = " + id100));
        assertEquals(100, queryLong(migrated, "SELECT ProjectId FROM log_Transactions WHERE _id = " + id101));
        assertEquals(100, queryLong(migrated, "SELECT DepartmentId FROM log_Transactions WHERE _id = " + id101));
        assertEquals(100, queryLong(migrated, "SELECT LocationId FROM log_Transactions WHERE _id = " + id101));
        assertNull(queryNullableLong(migrated, "SELECT ProjectId FROM log_Transactions WHERE _id = " + id102));
        assertEquals(0, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = " + id100), 0.0001);
        assertEquals(0, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = " + id100), 0.0001);
        assertEquals(-1, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = " + id100));
        assertEquals(100, queryLong(migrated, "SELECT SimpleDebtId FROM log_Transactions WHERE _id = " + id100));
        assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = " + id101), 0.0001);
        assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = " + id101), 0.0001);
        assertEquals(10, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = " + id101));
        assertNull(queryNullableLong(migrated, "SELECT SimpleDebtId FROM log_Transactions WHERE _id = " + id101));
        assertEquals("Кофе", queryString(migrated, "SELECT Comment FROM log_Transactions WHERE _id = " + id100));
        assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id100));
        assertEquals("salary", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id101));
        assertEquals("transfer", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id102));
        assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id IN (" + id100 + ", " + id101 + ", " + id102 + ") AND FN = 0 AND FD = 0 AND FP = 0 AND IsSplit = 0"));

        // update32: one product per not deleted transaction, price equals the amount (minor units)
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
        assertProducts(migrated, id100, -10000);
        assertProducts(migrated, id101, 25000);
        assertProducts(migrated, id102, -4000);
        assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));

        // Migration36 rebuilds the running balance: transfer adds a row for the destination account
        assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
        assertBalance(migrated, 100, id100, 0, -10000);
        assertBalance(migrated, 100, id101, 25000, -10000);
        assertBalance(migrated, 100, id102, 25000, -14000);
        assertBalance(migrated, 101, id102, 4000, 0);
    }

    private static final String TRANSACTION_V30 = "INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, "
            + "Project, SimpleDebt, Department, Location, Comment, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy, FN, FD, FP, SearchString)";

    private static final String TRANSACTION_V23 = "INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, "
            + "Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy)";

    private static final String[] SEED_V23 = new String[]{
            "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount) VALUES (100, 0, 'TST', 'T', 'Test', 2)",
            "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0)",
            "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (101, 0, 0, 'Second account', 100, 0, 0)",
            "INSERT INTO ref_Categories (_id, Deleted, Name, ParentID, OrderNumber, Sign) VALUES (100, 0, 'Parent', NULL, 0, 1)",
            "INSERT INTO ref_Categories (_id, Deleted, Name, ParentID, OrderNumber, Sign) VALUES (101, 0, 'Child', 100, 0, 1)",
            "INSERT INTO ref_Payees (_id, Deleted, Name, ParentID, OrderNumber) VALUES (100, 0, 'Кофе', -1, 0)",
            "INSERT INTO ref_Projects (_id, Deleted, Name, isActive, ParentID, OrderNumber) VALUES (100, 0, 'Project A', 1, -1, 0)",
            "INSERT INTO ref_SimpleDebts (_id, Deleted, Name, isActive) VALUES (100, 0, 'Debt with tx', 1)",
            "INSERT INTO ref_SimpleDebts (_id, Deleted, Name, isActive) VALUES (101, 0, 'Debt without tx', 1)",
            "INSERT INTO log_Templates (_id, Deleted, SrcAccount, Amount, Name, DestAccount, ExchangeRate, Type, Comment) VALUES (100, 0, 100, -10, 'Template', -1, 1, 0, '')",
            "INSERT INTO ref_Senders (_id, Deleted, Name, PhoneNo, isActive) VALUES (100, 0, 'Bank', '+1000', 1)",
            TRANSACTION_V23 + " VALUES (100, 0, 1000, 100, NULL, NULL, -100, -1, 100, -1, -1, 'Кофе', NULL, -1, 1, 0, NULL, NULL, NULL)",
            TRANSACTION_V23 + " VALUES (101, 0, 2000, 100, NULL, NULL, 250, -1, NULL, -1, -1, 'Salary', NULL, -1, 1, 0, 2.5, 1.5, 10)",
            TRANSACTION_V23 + " VALUES (102, 0, 4000, 100, NULL, NULL, -40, -1, NULL, -1, -1, 'Transfer', NULL, 101, 1, 0, 0, 0, -1)",
            TRANSACTION_V23 + " VALUES (103, 1, 3000, 100, NULL, NULL, -5, -1, NULL, -1, -1, NULL, NULL, -1, 1, 0, 0, 0, -1)"
    };

    private static final String TRANSACTION_V16 = "INSERT INTO log_Transactions (_id, DateTime, SrcAccount, Payee, Category, Amount, "
            + "Project, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lat, Lon, Accuracy, Department, SimpleDebt)";

    /**
     * Version 21 (tableInfo21): ParentCategory instead of ParentID, no hierarchy columns in payees, projects, locations,
     * departments, no OrderNumber in currencies. Transaction 100: Project/Department/Location 0 (become -1),
     * NULL Lat/Lon/Accuracy; 101: has all references; 102: transfer; 103: deleted, NULL Comment (allowed for deleted).
     */
    static final String[] SEED_V21 = new String[]{
            "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount) VALUES (100, 0, 'TST', 'T', 'Test', 2)",
            "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0)",
            "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed) VALUES (101, 0, 0, 'Second account', 100, 0, 0)",
            "INSERT INTO ref_Categories (_id, Deleted, Name, ParentCategory, OrderNumber, Sign) VALUES (100, 0, 'Parent', NULL, 0, 1)",
            "INSERT INTO ref_Categories (_id, Deleted, Name, ParentCategory, OrderNumber, Sign) VALUES (101, 0, 'Child', 100, 0, 1)",
            "INSERT INTO ref_Payees (_id, Deleted, Name) VALUES (100, 0, 'Кофе')",
            "INSERT INTO ref_Projects (_id, Deleted, Name, isActive) VALUES (100, 0, 'Project A', 1)",
            "INSERT INTO ref_Departments (_id, Deleted, Name, isActive) VALUES (100, 0, 'Department A', 1)",
            "INSERT INTO ref_Locations (_id, Deleted, Name, Lon, Lat) VALUES (100, 0, 'Place A', 2.5, 1.5)",
            "INSERT INTO ref_SimpleDebts (_id, Deleted, Name, isActive) VALUES (100, 0, 'Debt with tx', 1)",
            "INSERT INTO ref_SimpleDebts (_id, Deleted, Name, isActive) VALUES (101, 0, 'Debt without tx', 1)",
            "INSERT INTO log_Templates (_id, Deleted, SrcAccount, Amount, Name, DestAccount, ExchangeRate, Type, Comment) VALUES (100, 0, 100, -10, 'Template', -1, 1, 0, '')",
            "INSERT INTO ref_Senders (_id, Deleted, Name, PhoneNo, isActive) VALUES (100, 0, 'Bank', '+1000', 1)",
            "INSERT INTO log_Incoming_SMS (_id, Deleted, DateTime, Sender, Body) VALUES (100, 0, '1577872800000', 100, 'sms body')",
            "INSERT INTO ref_Sms_Parser_Patterns (_id, Deleted, Type, Object, Pattern) VALUES (100, 0, 1, 'object', 'pattern')",
            "INSERT INTO ref_Debts (_id, Deleted, Account, Payee, Category, Closed) VALUES (100, 0, 101, 100, 100, 0)",
            "INSERT INTO ref_budget (_id, Deleted, Year, Month, Category, Amount, Currency) VALUES (100, 0, 2020, 1, 100, 300, 100)",
            "INSERT INTO ref_budget_debts (_id, Deleted, Year, Month, Debt, Amount) VALUES (100, 0, 2020, 1, 100, 50)",
            TRANSACTION_V23 + " VALUES (100, 0, 1000, 100, 100, NULL, -100, 0, 100, 0, 0, 'Кофе', NULL, -1, 1, 0, NULL, NULL, NULL)",
            TRANSACTION_V23 + " VALUES (101, 0, 2000, 100, 100, 101, 250, 100, NULL, 100, 100, 'Salary', NULL, -1, 1, 0, 2.5, 1.5, 10)",
            TRANSACTION_V23 + " VALUES (102, 0, 4000, 100, NULL, NULL, -40, -1, NULL, -1, -1, 'Transfer', NULL, 101, 1, 0, 0, 0, -1)",
            TRANSACTION_V23 + " VALUES (103, 1, 3000, 100, NULL, NULL, -5, -1, NULL, -1, -1, NULL, NULL, -1, 1, 0, 0, 0, -1)"
    };

    /**
     * Version 16 (columns of tableInfo17, no sync columns, DateTime as text). Same entities as SEED_V21 except the SMS,
     * senders and the deleted transaction (there is no Deleted). Payee/Category/Project/... NULL: update18 turns them into 0.
     * No sender markers (Type = 5), see the class comment.
     */
    static final String[] SEED_V16 = new String[]{
            "INSERT INTO ref_Currencies (_id, Code, Symbol, Name, DecimalCount) VALUES (100, 'TST', 'T', 'Test', 2)",
            "INSERT INTO ref_Accounts (_id, Type, Name, Currency, StartBalance, IsClosed) VALUES (100, 0, 'Migration account', 100, 12.5, 0)",
            "INSERT INTO ref_Accounts (_id, Type, Name, Currency, StartBalance, IsClosed) VALUES (101, 0, 'Second account', 100, 0, 0)",
            "INSERT INTO ref_Categories (_id, Name, ParentCategory, OrderNumber, Sign) VALUES (100, 'Parent', NULL, 0, 1)",
            "INSERT INTO ref_Categories (_id, Name, ParentCategory, OrderNumber, Sign) VALUES (101, 'Child', 100, 0, 1)",
            "INSERT INTO ref_Payees (_id, Name) VALUES (100, 'Кофе')",
            "INSERT INTO ref_Projects (_id, Name, isActive) VALUES (100, 'Project A', 1)",
            "INSERT INTO ref_Departments (_id, Name, isActive) VALUES (100, 'Department A', 1)",
            "INSERT INTO ref_Locations (_id, Name, Lon, Lat) VALUES (100, 'Place A', 2.5, 1.5)",
            "INSERT INTO ref_SimpleDebts (_id, Name, isActive) VALUES (100, 'Debt with tx', 1)",
            "INSERT INTO ref_SimpleDebts (_id, Name, isActive) VALUES (101, 'Debt without tx', 1)",
            "INSERT INTO log_Templates (_id, SrcAccount, Amount, Name, DestAccount, ExchangeRate, Type) VALUES (100, 100, -10, 'Template', -1, 1, 0)",
            "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (100, '1463475600000', '+1000', 'sms body')",
            "INSERT INTO ref_Sms_Parser_Patterns (_id, Type, Object, Pattern) VALUES (100, 1, 'object', 'pattern')",
            "INSERT INTO ref_Debts (_id, Account, Payee, Category, Closed) VALUES (100, 101, 100, 100, 0)",
            "INSERT INTO ref_budget (_id, Year, Month, Category, Amount, Currency) VALUES (100, 2016, 5, 100, 300, 100)",
            "INSERT INTO ref_budget_debts (_id, Year, Month, Debt, Amount) VALUES (100, 2016, 5, 100, 50)",
            TRANSACTION_V16 + " VALUES (100, '2016-05-17 10:30:00', 100, NULL, NULL, -100, 0, 0, 'Кофе', NULL, -1, 1, 0, NULL, NULL, NULL, 0, 100)",
            TRANSACTION_V16 + " VALUES (101, '2016-05-18 11:00:00', 100, 100, 101, 250, 100, 100, 'Salary', NULL, -1, 1, 0, 1.5, 2.5, 10, 100, NULL)",
            TRANSACTION_V16 + " VALUES (102, '2016-05-19 12:00:00', 100, NULL, NULL, -40, NULL, NULL, 'Transfer', NULL, 101, 1, 0, 0, 0, -1, NULL, NULL)"
    };

    /** Asserts the same tables, columns and indexes as a clean version 36 database. */
    private static void assertMatchesCleanSchema(SQLiteDatabase clean, SQLiteDatabase migrated) {
        assertEquals("tables and columns", MigrationTestHelper.dumpColumnsIgnoreCase(clean),
                MigrationTestHelper.dumpColumnsIgnoreCase(migrated));
        assertEquals("indexes", MigrationTestHelper.dumpIndexes(clean), MigrationTestHelper.dumpIndexes(migrated));
    }

    /** Product of a transaction in minor units: ProductId 0, no category and no project (NULL), quantity 1 = 1000. */
    private static void assertProducts(SQLiteDatabase db, long transactionId, long priceMinor) {
        try (Cursor c = db.rawQuery("SELECT ProductId, CategoryId, ProjectId, Price, Quantity FROM log_Products "
                + "WHERE TransactionId = " + transactionId, null)) {
            assertEquals("products of transaction " + transactionId, 1, c.getCount());
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getLong(0));
            assertTrue("CategoryId of transaction " + transactionId, c.isNull(1));
            assertTrue("ProjectId of transaction " + transactionId, c.isNull(2));
            assertEquals(priceMinor, c.getLong(3));
            assertEquals(1000, c.getLong(4));
        }
    }

    /** Running balance row in minor units: cumulative income and expense of the account up to the transaction. */
    private static void assertBalance(SQLiteDatabase db, long accountId, long transactionId, long income, long expense) {
        try (Cursor c = db.rawQuery("SELECT Income, Expense FROM log_Running_Balance WHERE AccountId = "
                + accountId + " AND TransactionId = " + transactionId, null)) {
            assertTrue("running balance " + accountId + "/" + transactionId, c.moveToFirst());
            assertEquals("income " + accountId + "/" + transactionId, income, c.getLong(0));
            assertEquals("expense " + accountId + "/" + transactionId, expense, c.getLong(1));
        }
    }

    private static String queryString(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(sql, c.moveToFirst());
            return c.getString(0);
        }
    }

    private static long queryLong(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(sql, c.moveToFirst());
            return c.getLong(0);
        }
    }

    private static double queryDouble(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(sql, c.moveToFirst());
            return c.getDouble(0);
        }
    }

    //<editor-fold desc="35 -> 36 (Migration36, assets/migrations/fingen_v35.sql)">

    /**
     * Full fixture as it is (its comments list the boundary values). Expected: version 36, clean schema.
     * Fails with the defect described in {@link #upgrade35To36_minusOneDestAccountBecomesNull} (the same error).
     * Other rules of the fixture are checked in the tests with the unblocked fixture (migrateUnblockedV35Only).
     */
    @Test
    public void upgrade35To36_fixtureMigrates() throws Exception {
        createV35();
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertIntegrity(migrated);
            assertMatchesCleanSchema(clean, migrated);
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /**
     * -1 in DestAccount (non-transfer transaction, template) must become NULL. Fails now: Migration36.cleanup ->
     * nullify (lines 341, 376) runs UPDATE ... SET DestAccount = NULL on the v35 table, where DestAccount is NOT NULL,
     * so the upgrade aborts with "NOT NULL constraint failed: log_Transactions.DestAccount" (the same error as in the
     * other tests on the full fixture and in the v16/v21/v23 tests).
     */
    @Test
    public void upgrade35To36_minusOneDestAccountBecomesNull() throws Exception {
        createV35();
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        try {
            assertIntegrity(migrated);
            assertNull(queryNullableLong(migrated, "SELECT DestAccountId FROM log_Transactions WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT DestAccountId FROM log_Templates WHERE _id = 100"));
            assertEquals(101, queryLong(migrated, "SELECT DestAccountId FROM log_Transactions WHERE _id = 101"));
        } finally {
            migrated.close();
        }
    }

    /** Without the NOT NULL defect: the fixture migrates, the schema and the foreign keys are clean. */
    @Test
    public void upgrade35To36_unblockedFixtureIntegrity() throws Exception {
        migrateUnblockedV35Only();
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertIntegrity(migrated);
            assertMatchesCleanSchema(clean, migrated);
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /**
     * -1 becomes NULL in optional references; a valid reference (ProductId 0, CategoryId 100) is kept. Account 102 has
     * Currency -1: it gets the default currency (no live RUB in v35, first live by _id is AAA, _id 100), not NULL.
     */
    @Test
    public void upgrade35To36_minusOneBecomesNull() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 102"));
            assertNull(queryNullableLong(migrated, "SELECT CategoryId FROM log_Transactions WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT DefaultCategoryId FROM ref_Payees WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Payees WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT CategoryId FROM log_Products WHERE _id = 102"));
            assertNull(queryNullableLong(migrated, "SELECT ProjectId FROM log_Products WHERE _id = 102"));
            // ProductId 0 is a real product (default_product) and is not touched
            assertEquals(0, queryLong(migrated, "SELECT ProductId FROM log_Products WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT CategoryId FROM log_Products WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * Dangling references in optional columns become NULL (Payee 77, ParentID 555). Account 103 has Currency 999 (dangling):
     * it gets the default currency (first live by _id, AAA, _id 100), not NULL.
     */
    @Test
    public void upgrade35To36_danglingOptionalReferenceBecomesNull() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertNull(queryNullableLong(migrated, "SELECT PayeeId FROM log_Transactions WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 103"));
            assertNull(queryNullableLong(migrated, "SELECT ParentId FROM ref_Categories WHERE _id = 103"));
            // valid references are kept
            assertEquals(100, queryLong(migrated, "SELECT DefaultCategoryId FROM ref_Payees WHERE _id = 102"));
            assertEquals(100, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * Account without currency (NULL) gets the default currency: live RUB (_id 103 comes from NO_CURRENCY_V35, the v35
     * fixture has no RUB). StartBalance 10.5 and Amount 12.34 keep 2 digits: 1050 and 1234 minor units.
     */
    @Test
    public void upgrade35To36_accountWithoutCurrencyGetsDefaultCurrency() throws Exception {
        List<String> seeds = new ArrayList<>(Arrays.asList(LEGACY_V35));
        seeds.addAll(Arrays.asList(UNBLOCK_V35));
        seeds.addAll(Arrays.asList(NO_CURRENCY_V35));
        MigrationTestHelper.createFixture(mContext, 35, seeds.toArray(new String[0]));
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        try {
            assertIntegrity(migrated);
            assertEquals(103, queryLong(migrated, "SELECT CurrencyId FROM ref_Accounts WHERE _id = 200"));
            assertEquals(1050, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 200"));
            assertEquals(1234, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = 200"));
        } finally {
            migrated.close();
        }
    }

    /**
     * NOT NULL dangling reference: the row is marked deleted (Deleted = max + 1) and points to the placeholder row
     * of the parent table (Deleted &gt; 0, Name '(lost)'). Template 101 has SrcAccount 999. SMS 102 has Sender 0: it stays
     * visible and goes to the service sender "(unknown)" (Deleted = 0, IsActive = 1), see upgrade16To35_keepsAllSms.
     */
    @Test
    public void upgrade35To36_danglingNotNullMarkedDeletedAndRedirected() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertTrue(queryLong(migrated, "SELECT Deleted FROM log_Templates WHERE _id = 101") > 0);
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Accounts WHERE _id = "
                    + "(SELECT SrcAccountId FROM log_Templates WHERE _id = 101) AND Deleted > 0 AND Name = '(lost)'"));
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Templates WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT SrcAccountId FROM log_Templates WHERE _id = 100"));

            // SMS with Sender 0 stays visible and goes to the service sender "(unknown)" (Deleted = 0, IsActive = 1)
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = 102"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders WHERE _id = "
                    + "(SELECT SenderId FROM log_Incoming_SMS WHERE _id = 102) AND Deleted = 0 AND IsActive = 1 AND Name = '(unknown)'"));
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT SenderId FROM log_Incoming_SMS WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * Debt 100 of the full fixture: Payee 88 (dangling) and Category -1 are NOT NULL in v35, nullable in v36.
     * Expected by the v36 schema: both become NULL, the debt stays active. The plan of the migration mentioned
     * "Deleted and redirect" for Payee; the code (and the v36 schema, PayeeId nullable) gives NULL.
     * Fails now with the NOT NULL defect of upgrade35To36_minusOneDestAccountBecomesNull.
     */
    @Test
    public void upgrade35To36_debtReferencesBecomeNullAndDebtStaysActive() throws Exception {
        createV35();
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        try {
            assertIntegrity(migrated);
            assertNull(queryNullableLong(migrated, "SELECT PayeeId FROM ref_Debts WHERE _id = 100"));
            assertNull(queryNullableLong(migrated, "SELECT CategoryId FROM ref_Debts WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM ref_Debts WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT AccountId FROM ref_Debts WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /** Duplicates of a name under one parent (-1 and NULL are both "no parent") get the suffix " (2)". */
    @Test
    public void upgrade35To36_duplicateNameGetsSuffix() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertEquals("Food", queryString(migrated, "SELECT Name FROM ref_Categories WHERE _id = 100"));
            assertEquals("Food (2)", queryString(migrated, "SELECT Name FROM ref_Categories WHERE _id = 101"));
            assertEquals("Ozon", queryString(migrated, "SELECT Name FROM ref_Payees WHERE _id = 100"));
            assertEquals("Ozon (2)", queryString(migrated, "SELECT Name FROM ref_Payees WHERE _id = 101"));
        } finally {
            migrated.close();
        }
    }

    /** Quantity -1 becomes 0, quantity 1 becomes 1000 (x10^3); prices are minor units of the account currency. */
    @Test
    public void upgrade35To36_negativeQuantityBecomesZero() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertEquals(0, queryLong(migrated, "SELECT Quantity FROM log_Products WHERE _id = 102"));
            assertEquals(1000, queryLong(migrated, "SELECT Quantity FROM log_Products WHERE _id = 100"));
            assertEquals(1001, queryLong(migrated, "SELECT Price FROM log_Products WHERE _id = 100"));
            assertEquals(29, queryLong(migrated, "SELECT Price FROM log_Products WHERE _id = 102"));
        } finally {
            migrated.close();
        }
    }

    /** Product row with an unknown ProductID (555) is deleted, the default product (0) row is kept. */
    @Test
    public void upgrade35To36_unknownProductRowDeleted() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE _id = 101"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE ProductId NOT IN (SELECT _id FROM ref_Products)"));
            assertEquals(2, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));
            assertEquals(0, queryLong(migrated, "SELECT ProductId FROM log_Products WHERE _id = 102"));
        } finally {
            migrated.close();
        }
    }

    /**
     * DecimalCount -1 becomes 2, 6 stays 6 (the limit is 8, only above 8 becomes 8). Amounts are minor units:
     * 10.005 -> 1001 (HALF_UP, 2 digits), 0.285 -> 29, 0.285 with 6 digits -> 285000, exchange rate x10^12
     * (1.0 -> 1000000000000, 0.0123456 -> 12345600000).
     */
    @Test
    public void upgrade35To36_decimalCountClampedAndAmountsScaled() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            assertEquals(2, queryLong(migrated, "SELECT DecimalCount FROM ref_Currencies WHERE _id = 100"));
            assertEquals(2, queryLong(migrated, "SELECT DecimalCount FROM ref_Currencies WHERE _id = 101"));
            assertEquals(6, queryLong(migrated, "SELECT DecimalCount FROM ref_Currencies WHERE _id = 102"));
            assertEquals(1001, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"));
            assertEquals(29, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 102"));
            assertEquals(285000, queryLong(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 104"));
            assertEquals(29, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = 101"));
            assertEquals(-50, queryLong(migrated, "SELECT Amount FROM log_Transactions WHERE _id = 102"));
            assertEquals(1000000000000L, queryLong(migrated, "SELECT ExchangeRate FROM log_Transactions WHERE _id = 100"));
            assertEquals(12345600000L, queryLong(migrated, "SELECT ExchangeRate FROM log_Transactions WHERE _id = 101"));
        } finally {
            migrated.close();
        }
    }

    /** Totals of every money column in minor units equal the HALF_UP sums of the v35 values (per row, then summed). */
    @Test
    public void upgrade35To36_moneySumsMatchHalfUpBeforeMigration() throws Exception {
        migrateUnblockedV35Only();
        String name = MigrationTestHelper.dbName("35");
        long accounts;
        long transactions;
        long templates;
        long products;
        long budget;
        long budgetDebts;
        SQLiteDatabase before = openRaw(name);
        try {
            accounts = sumMinor(before, "SELECT a.StartBalance, IFNULL(cur.DecimalCount, 2) FROM ref_Accounts a "
                    + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency");
            transactions = sumMinor(before, "SELECT t.Amount, IFNULL(cur.DecimalCount, 2) FROM log_Transactions t "
                    + "JOIN ref_Accounts a ON a._id = t.SrcAccount LEFT JOIN ref_Currencies cur ON cur._id = a.Currency");
            templates = sumMinor(before, "SELECT t.Amount, IFNULL(cur.DecimalCount, 2) FROM log_Templates t "
                    + "LEFT JOIN ref_Accounts a ON a._id = t.SrcAccount LEFT JOIN ref_Currencies cur ON cur._id = a.Currency");
            // the product with ProductID 555 is deleted by the migration, it is not in the totals
            products = sumMinor(before, "SELECT p.Price, IFNULL(cur.DecimalCount, 2) FROM log_Products p "
                    + "JOIN log_Transactions t ON t._id = p.TransactionID JOIN ref_Accounts a ON a._id = t.SrcAccount "
                    + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency WHERE p.ProductID IN (SELECT _id FROM ref_Products)");
            budget = sumMinor(before, "SELECT b.Amount, IFNULL(cur.DecimalCount, 2) FROM ref_budget b "
                    + "LEFT JOIN ref_Currencies cur ON cur._id = b.Currency");
            budgetDebts = sumMinor(before, "SELECT b.Amount, IFNULL(cur.DecimalCount, 2) FROM ref_budget_debts b "
                    + "JOIN ref_Debts d ON d._id = b.Debt JOIN ref_Accounts a ON a._id = d.Account "
                    + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency");
        } finally {
            before.close();
        }
        // accounts: 1001 + 123450 + 29 + 29 + 285000 (Acc E, currency DecimalCount 6)
        assertEquals(409509, accounts);
        assertEquals(980, transactions);
        assertEquals(1101, templates);
        assertEquals(1030, products);
        assertEquals(29, budget);
        assertEquals(29, budgetDebts);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        try {
            assertIntegrity(migrated);
            assertEquals(accounts, queryLong(migrated, "SELECT SUM(StartBalance) FROM ref_Accounts"));
            assertEquals(transactions, queryLong(migrated, "SELECT SUM(Amount) FROM log_Transactions"));
            assertEquals(templates, queryLong(migrated, "SELECT SUM(Amount) FROM log_Templates"));
            assertEquals(products, queryLong(migrated, "SELECT SUM(Price) FROM log_Products"));
            assertEquals(budget, queryLong(migrated, "SELECT SUM(Amount) FROM ref_budget"));
            assertEquals(budgetDebts, queryLong(migrated, "SELECT SUM(Amount) FROM ref_budget_debts"));
        } finally {
            migrated.close();
        }
    }

    /** SMS: Sender 0 is deleted; DateTime "yyyyMMdd  HH:mm" becomes milliseconds in the device time zone. */
    @Test
    public void upgrade35To36_smsDateParsedInDeviceZone() throws Exception {
        SQLiteDatabase migrated = migrateUnblockedV35();
        try {
            assertIntegrity(migrated);
            Calendar expected = Calendar.getInstance();
            expected.clear();
            expected.set(2024, Calendar.JANUARY, 5, 13, 45, 0);
            assertEquals(expected.getTimeInMillis(), queryLong(migrated, "SELECT DateTime FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT Deleted FROM log_Incoming_SMS WHERE _id = 101"));
        } finally {
            migrated.close();
        }
    }

    /** SMS with a date that does not parse ("мусор") gets the migration time (between the two measurements). */
    @Test
    public void upgrade35To36_unparsableSmsDateUsesMigrationTime() throws Exception {
        migrateUnblockedV35Only();
        long before = System.currentTimeMillis();
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        long after = System.currentTimeMillis();
        try {
            assertIntegrity(migrated);
            long date = queryLong(migrated, "SELECT DateTime FROM log_Incoming_SMS WHERE _id = 101");
            assertTrue("DateTime " + date + " not in [" + before + ", " + after + "]", date >= before && date <= after);
        } finally {
            migrated.close();
        }
    }

    /** sqlite_sequence is kept: the next _id after the migration is above the deleted id 500 (not reused). */
    @Test
    public void upgrade35To36_sqliteSequenceKept() throws Exception {
        migrateUnblockedV35Only();
        SQLiteDatabase before = openRaw(MigrationTestHelper.dbName("35"));
        long seqBefore;
        try {
            seqBefore = queryLong(before, "SELECT seq FROM sqlite_sequence WHERE name = 'ref_Payees'");
        } finally {
            before.close();
        }
        assertEquals(500, seqBefore);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
        try {
            assertIntegrity(migrated);
            assertEquals(500, queryLong(migrated, "SELECT seq FROM sqlite_sequence WHERE name = 'ref_Payees'"));
            ContentValues cv = new ContentValues();
            cv.put("Name", "New payee");
            long id = migrated.insertOrThrow("ref_Payees", null, cv);
            assertTrue("new _id " + id, id > 500);
        } finally {
            migrated.close();
        }
    }

    /**
     * prepare() copies fingen.db to fingen.db.pre36 before the first write: the copy is byte-equal to the v35 file.
     */
    @Test
    public void upgrade35To36_preMigrationCopyIsVersion35() throws Exception {
        String name = MigrationTestHelper.dbName("35");
        migrateUnblockedV35Only();
        File original = mContext.getDatabasePath(name);
        byte[] originalBytes = Files.readAllBytes(original.toPath());

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        migrated.close();

        File copy = new File(mContext.getDatabasePath(name).getPath() + Migration36.PRE_COPY_SUFFIX);
        assertTrue("copy " + copy.getPath(), copy.isFile());
        assertArrayEquals("copy equals v35 file", originalBytes, Files.readAllBytes(copy.toPath()));
        SQLiteDatabase copyDb = SQLiteDatabase.openDatabase(copy.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        try {
            assertEquals(35, copyDb.getVersion());
        } finally {
            copyDb.close();
        }
    }

    /**
     * If the migration fails, the upgrade is rolled back: the file stays version 35 and the data are unchanged.
     * Failure is injected by foreign_keys = ON (Migration36.requireForeignKeysOff, set in onConfigure): the only
     * other way to fail (free space) cannot be simulated, see the report.
     */
    @Test
    public void upgrade35To36_failureKeepsVersion35() throws Exception {
        migrateUnblockedV35Only();
        String name = MigrationTestHelper.dbName("35");
        try {
            MigrationTestHelper.openMigratedWithPragmas(mContext, name, "PRAGMA foreign_keys = ON");
            fail("upgrade must fail when foreign keys are on");
        } catch (DbUpgradeException e) {
            assertEquals(DbUpgradeException.CODE_FAILED, e.getCode());
        }
        SQLiteDatabase raw = openRaw(name);
        try {
            assertEquals(35, raw.getVersion());
            assertEquals(-1, queryLong(raw, "SELECT Currency FROM ref_Accounts WHERE _id = 102"));
            assertEquals(1, queryLong(raw, "SELECT COUNT(*) FROM ref_Currencies WHERE DecimalCount = -1"));
            assertEquals(3, queryLong(raw, "SELECT COUNT(*) FROM log_Transactions"));
            assertEquals(0, queryLong(raw, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '%_new'"));
        } finally {
            raw.close();
        }
    }

    /** Creates the full v35 fixture (with LEGACY_V35 only); returns its file. */
    private File createV35() throws IOException {
        return MigrationTestHelper.createFixture(mContext, 35, LEGACY_V35);
    }

    /** Creates the v35 fixture with the NOT NULL defect cases replaced by valid values (see UNBLOCK_V35). */
    private SQLiteDatabase migrateUnblockedV35() throws IOException {
        migrateUnblockedV35Only();
        return MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("35"));
    }

    private void migrateUnblockedV35Only() throws IOException {
        List<String> seeds = new ArrayList<>(Arrays.asList(LEGACY_V35));
        seeds.addAll(Arrays.asList(UNBLOCK_V35));
        MigrationTestHelper.createFixture(mContext, 35, seeds.toArray(new String[0]));
    }

    /** Asserts version 36, quick_check = ok and no foreign key violations. */
    private static void assertIntegrity(SQLiteDatabase db) {
        assertEquals(36, db.getVersion());
        assertEquals("ok", queryString(db, "PRAGMA quick_check"));
        assertEquals("foreign_key_check rows", 0, countRows(db, "PRAGMA foreign_key_check"));
    }

    private SQLiteDatabase openRaw(String name) {
        return SQLiteDatabase.openDatabase(mContext.getDatabasePath(name).getPath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    /** Sum of HALF_UP minor units of each row; column 1 is DecimalCount (-1 and NULL give 2, above 8 gives 8). */
    private static long sumMinor(SQLiteDatabase db, String sql) {
        long sum = 0;
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                int decimalCount = c.getInt(1);
                int digits = decimalCount < 0 ? 2 : Math.min(decimalCount, 8);
                double value = c.isNull(0) ? 0 : c.getDouble(0);
                sum += BigDecimal.valueOf(value).movePointRight(digits).setScale(0, RoundingMode.HALF_UP).longValueExact();
            }
        }
        return sum;
    }

    private static long countRows(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.getCount();
        }
    }

    private static Long queryNullableLong(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            assertTrue(sql, c.moveToFirst());
            return c.isNull(0) ? null : c.getLong(0);
        }
    }

    /**
     * log_Products.Quantity has CHECK (Quantity &gt;= 0) in v35, so the app cannot write -1. A legacy row with -1 is
     * written with the CHECK disabled (the fixture file keeps a valid value 1 in that row).
     */
    private static final String[] LEGACY_V35 = new String[]{
            "PRAGMA ignore_check_constraints = ON",
            "UPDATE log_Products SET Quantity = -1 WHERE _id = 102",
            "PRAGMA ignore_check_constraints = OFF"};

    /**
     * Account 200 with CurrencyId NULL and its transaction; RUB (_id 103) is the only live RUB, so it is the default.
     * DestAccount is a valid account (NOT NULL in v35). Added on top of LEGACY_V35 and UNBLOCK_V35.
     */
    private static final String[] NO_CURRENCY_V35 = new String[]{
            "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (103, 0, 'RUB', '₽', 'Russian Ruble', 2, 3);",
            "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (200, 0, 0, 'Acc no currency', NULL, 10.5, 0, 'acc no currency');",
            "INSERT INTO log_Transactions (_id, Deleted, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, DestAccount, ExchangeRate, AutoCreated, SearchString) VALUES (200, 0, 1704110400000, 200, NULL, NULL, 12.34, -1, -1, -1, -1, 'T200', 101, 1.0, 0, 't200');"};

    /**
     * Replaces the values of the v35 fixture that hit the NOT NULL defect of Migration36.cleanup (see
     * upgrade35To36_minusOneDestAccountBecomesNull): DestAccount -1 of a transaction and of templates, and the debt
     * references. The other rules are tested on the unblocked fixture, so they do not depend on that defect.
     */
    private static final String[] UNBLOCK_V35 = new String[]{
            "UPDATE log_Transactions SET DestAccount = 101 WHERE _id = 100",
            "UPDATE log_Templates SET DestAccount = 101 WHERE _id IN (100, 101)",
            "UPDATE ref_Debts SET Payee = 100, Category = 100 WHERE _id = 100"};

    //</editor-fold>
}
