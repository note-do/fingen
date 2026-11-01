package com.yoshione.fingen.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
 * <li>v16 data has no sender markers (ref_Sms_Parser_Patterns.Type = 5), so update17 creates no senders and drops
 * ALL rows of log_Incoming_SMS (it keeps only SMS of known senders). This is the behaviour of update17, it is asserted.</li>
 * <li>v16 DateTime is the text "yyyy-MM-dd HH:mm:ss" in the time zone of the device; update18 converts it to
 * milliseconds with the same pattern and the default time zone, so the expected value is computed in the test with
 * the same pattern. The test does not depend on the time zone of the device.</li>
 * <li>update18 recreates log_Transactions and inserts rows WITHOUT _id, so transactions of v16 get new ids 1, 2, 3
 * (in the order of the old table); the v16 test finds them by Comment.</li>
 * <li>update18 reads Payee, Category, Project, Department, Location with getLong: NULL becomes 0; update22 then
 * changes Project, Department and Location 0 to -1, while Payee and Category stay 0.</li>
 * <li>update31 calls ColorUtils.getColor, which increments "color_ind" in the default SharedPreferences;
 * the value is saved before each test and restored after it.</li>
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

    /** 30 -> 35 runs update31 (Projects.Color), update32 (Split, products) and update33. */
    @Test
    public void upgrade30To35_addsColorSplitAndProducts() throws Exception {
        MigrationTestHelper.createFixture(mContext, 30,
                "INSERT INTO ref_Currencies (_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber) VALUES (100, 0, 'TST', 'T', 'Test', 2, 0)",
                "INSERT INTO ref_Accounts (_id, Deleted, Type, Name, Currency, StartBalance, IsClosed, SearchString) VALUES (100, 0, 0, 'Migration account', 100, 12.5, 0, 'migration account')",
                "INSERT INTO ref_Projects (_id, Deleted, Name, IsActive, ParentID, OrderNumber, FullName, SearchString) VALUES (100, 0, 'Project A', 1, -1, 0, 'Project A', 'project a')",
                TRANSACTION_V30 + " VALUES (100, 0, 1000, 100, -1, -1, -100, 100, -1, -1, -1, 'Coffee', -1, 1, 0, 0, 0, -1, 11, 12, 13, 'coffee')",
                TRANSACTION_V30 + " VALUES (101, 0, 2000, 100, -1, -1, 250.5, -1, -1, -1, -1, 'Salary', -1, 1, 0, 0, 0, -1, 0, 0, 0, 'salary')",
                TRANSACTION_V30 + " VALUES (102, 1, 3000, 100, -1, -1, -5, -1, -1, -1, -1, 'Deleted', -1, 1, 0, 0, 0, -1, 0, 0, 0, 'deleted')",
                // update32 and update33 do not touch the running balance: stale values must stay as they are
                "INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 100, 1000, 0, -999)",
                "INSERT INTO log_Running_Balance (AccountID, TransactionID, DateTimeRB, Income, Expense) VALUES (100, 101, 2000, 250.5, -999)");

        String name = MigrationTestHelper.dbName("30");
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(35, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);

            assertEquals("Migration account", queryString(migrated, "SELECT Name FROM ref_Accounts WHERE _id = 100"));
            assertEquals(12.5, queryDouble(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"), 0.0001);
            assertEquals(100, queryLong(migrated, "SELECT Currency FROM ref_Accounts WHERE _id = 100"));

            // update31: Color of each project is "#RRGGBB" from ColorUtils
            assertEquals("Project A", queryString(migrated, "SELECT Name FROM ref_Projects WHERE _id = 100"));
            String color = queryString(migrated, "SELECT Color FROM ref_Projects WHERE _id = 100");
            assertTrue("Color: " + color, color != null && color.matches("#[0-9A-F]{6}"));

            // update32: Split = 0 for existing rows, other columns are kept
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE Split = 0"));
            assertEquals(-100, queryDouble(migrated, "SELECT Amount FROM log_Transactions WHERE _id = 100"), 0.0001);
            assertEquals("coffee", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
            assertEquals(11, queryLong(migrated, "SELECT FN FROM log_Transactions WHERE _id = 100"));
            assertEquals(12, queryLong(migrated, "SELECT FD FROM log_Transactions WHERE _id = 100"));
            assertEquals(13, queryLong(migrated, "SELECT FP FROM log_Transactions WHERE _id = 100"));

            // update32: default product, one log_Products row per not deleted transaction (update33 adds none)
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
            assertEquals("default_product", queryString(migrated, "SELECT Name FROM ref_Products WHERE _id = 0"));
            assertProducts(migrated, 100, -100);
            assertProducts(migrated, 101, 250.5);
            assertEquals(2, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE TransactionID = 102"));

            assertEquals(2, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
            assertEquals(-999, queryDouble(migrated, "SELECT Expense FROM log_Running_Balance WHERE TransactionID = 100"), 0.0001);
            assertEquals(-999, queryDouble(migrated, "SELECT Expense FROM log_Running_Balance WHERE TransactionID = 101"), 0.0001);
        } finally {
            migrated.close();
            clean.close();
        }
    }

    /** 23 -> 35 runs update24 ... update33 (including the running balance and full names). */
    @Test
    public void upgrade23To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 23, SEED_V23);

        String name = MigrationTestHelper.dbName("23");
        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, name);
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(35, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);

            assertEquals("Migration account", queryString(migrated, "SELECT Name FROM ref_Accounts WHERE _id = 100"));
            assertEquals(12.5, queryDouble(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"), 0.0001);
            assertEquals(100, queryLong(migrated, "SELECT Currency FROM ref_Accounts WHERE _id = 100"));
            // update27: SearchString of accounts is transliterated lower-case Name
            assertEquals("migration account", queryString(migrated, "SELECT SearchString FROM ref_Accounts WHERE _id = 100"));

            // update24: NULL ParentID of a category becomes -1, update27: FullName from the parent chain
            assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Categories WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT ParentID FROM ref_Categories WHERE _id = 101"));
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
            assertEquals(100, queryLong(migrated, "SELECT Currency FROM ref_SimpleDebts WHERE _id = 100"));
            assertEquals(0, queryDouble(migrated, "SELECT StartAmount FROM ref_SimpleDebts WHERE _id = 100"), 0.0001);
            assertEquals("debt with tx", queryString(migrated, "SELECT SearchString FROM ref_SimpleDebts WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts WHERE _id = 101"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts"));

            // update24: NULL Lat/Lon -> 0, Accuracy -> -1, SimpleDebt -> -1 (all rows, also deleted ones)
            assertEquals(0, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = 100"), 0.0001);
            assertEquals(0, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = 100"), 0.0001);
            assertEquals(-1, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = 100"));
            assertEquals(100, queryLong(migrated, "SELECT SimpleDebt FROM log_Transactions WHERE _id = 100"));
            assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = 101"), 0.0001);
            assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = 101"), 0.0001);
            assertEquals(10, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = 101"));
            assertEquals(-1, queryLong(migrated, "SELECT SimpleDebt FROM log_Transactions WHERE _id = 101"));
            assertEquals(-1, queryLong(migrated, "SELECT SimpleDebt FROM log_Transactions WHERE _id = 103"));

            // update29: SearchString of not deleted transactions, a deleted one is not touched
            assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
            assertEquals("salary", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 101"));
            assertEquals("transfer", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 102"));
            assertNull(queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 103"));

            // update30, update32: new columns of transactions have the default 0
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE FN = 0 AND FD = 0 AND FP = 0 AND Split = 0"));

            // update32: one product per not deleted transaction, price equals the amount
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
            assertProducts(migrated, 100, -100);
            assertProducts(migrated, 101, 250);
            assertProducts(migrated, 102, -40);
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));

            // update27/28: running balance of not deleted transactions, transfer adds a row for the destination account
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
            assertBalance(migrated, 100, 100, 0, -100);
            assertBalance(migrated, 100, 101, 250, -100);
            assertBalance(migrated, 100, 102, 250, -140);
            assertBalance(migrated, 101, 102, 40, 0);
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
            assertEquals(35, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id = 100"));
            assertEquals("", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /** 21 -> 35 runs update22 ... update33 (renamed ParentID, new hierarchy columns, currency order, then as for 23). */
    @Test
    public void upgrade21To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 21, SEED_V21);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("21"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(35, migrated.getVersion());
            assertMatchesCleanSchema(clean, migrated);
            assertMigratedData(migrated, 100, 101, 102, 1000, 2000, 4000);

            // update22: all rows of new hierarchy columns are set, update23: OrderNumber of currencies is 0
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Payees WHERE ParentID <> -1 OR OrderNumber <> 0"));
            assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Currencies WHERE _id = 100"));

            // tables that no step after 21 touches keep their rows
            assertEquals("Bank", queryString(migrated, "SELECT Name FROM ref_Senders WHERE _id = 100"));
            assertEquals(0, queryLong(migrated, "SELECT AddCreditLimitToBalance FROM ref_Senders WHERE _id = 100"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            assertEquals(100, queryLong(migrated, "SELECT Sender FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals("sms body", queryString(migrated, "SELECT Body FROM log_Incoming_SMS WHERE _id = 100"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns"));
            assertEquals("pattern", queryString(migrated, "SELECT Pattern FROM ref_Sms_Parser_Patterns WHERE _id = 100"));

            // deleted transaction with NULL Comment: update29 does not touch it, update32/33 give it no product
            assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions"));
            assertNull(queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 103"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Products WHERE TransactionID = 103"));
            assertEquals(-1, queryLong(migrated, "SELECT Project FROM log_Transactions WHERE _id = 103"));
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
            assertEquals(35, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id = 100"));
            assertEquals("", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = 100"));
        } finally {
            migrated.close();
        }
    }

    /**
     * 16 -> 35 runs update17 ... update33. The fixture has no sender markers, so update17 drops all SMS
     * (see the class comment). DateTime is converted from the text to milliseconds by update18.
     */
    @Test
    public void upgrade16To35_migratesDataByAllSteps() throws Exception {
        MigrationTestHelper.createFixture(mContext, 16, SEED_V16);

        SQLiteDatabase migrated = MigrationTestHelper.openMigrated(mContext, MigrationTestHelper.dbName("16"));
        SQLiteDatabase clean = MigrationTestHelper.openClean(mContext, MigrationTestHelper.dbName("clean"));
        try {
            assertEquals(35, migrated.getVersion());
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
            // update18: NULL Payee and Category become 0 (getLong), not -1
            assertEquals(0, queryLong(migrated, "SELECT Payee FROM log_Transactions WHERE _id = " + id100));
            assertEquals(0, queryLong(migrated, "SELECT Category FROM log_Transactions WHERE _id = " + id100));
            assertEquals(100, queryLong(migrated, "SELECT Payee FROM log_Transactions WHERE _id = " + id101));
            assertEquals(101, queryLong(migrated, "SELECT Category FROM log_Transactions WHERE _id = " + id101));
            // update18 sets Deleted = 0 for all rows
            assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted = 0"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Accounts WHERE Deleted <> 0 OR Deleted IS NULL"));
            assertEquals(-1, queryLong(migrated, "SELECT TS FROM ref_Accounts WHERE _id = 100"));

            // update17: no sender markers -> no senders, SMS of unknown senders are dropped
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
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
            assertEquals(35, migrated.getVersion());
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
            assertEquals(35, migrated.getVersion());
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM log_Incoming_SMS"));
            assertEquals(0, queryLong(migrated, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns WHERE Type = 5"));
            assertEquals("Bank", queryString(migrated, "SELECT Name FROM ref_Senders"));
            assertEquals("+1000", queryString(migrated, "SELECT PhoneNo FROM ref_Senders"));
            assertEquals(0, queryLong(migrated, "SELECT AmountPos FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT BalancePos FROM ref_Senders"));
            assertEquals(1, queryLong(migrated, "SELECT isActive FROM ref_Senders"));
            assertEquals(queryLong(migrated, "SELECT _id FROM ref_Senders"),
                    queryLong(migrated, "SELECT Sender FROM log_Incoming_SMS WHERE _id = 100"));
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
        assertEquals(12.5, queryDouble(migrated, "SELECT StartBalance FROM ref_Accounts WHERE _id = 100"), 0.0001);
        assertEquals(100, queryLong(migrated, "SELECT Currency FROM ref_Accounts WHERE _id = 100"));
        assertEquals("migration account", queryString(migrated, "SELECT SearchString FROM ref_Accounts WHERE _id = 100"));
        assertEquals("TST", queryString(migrated, "SELECT Code FROM ref_Currencies WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Currencies WHERE _id = 100"));

        // update22 renames ParentCategory to ParentID, update24: NULL becomes -1, update27: FullName and SearchString
        assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Categories WHERE _id = 100"));
        assertEquals(100, queryLong(migrated, "SELECT ParentID FROM ref_Categories WHERE _id = 101"));
        assertEquals("Parent", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 100"));
        assertEquals("Parent\\Child", queryString(migrated, "SELECT FullName FROM ref_Categories WHERE _id = 101"));
        assertEquals("parent\\child", queryString(migrated, "SELECT SearchString FROM ref_Categories WHERE _id = 101"));

        // update22: ParentID -1 and OrderNumber 0 in the new columns of payees, projects, locations, departments
        assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Payees WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Payees WHERE _id = 100"));
        assertEquals("Кофе", queryString(migrated, "SELECT FullName FROM ref_Payees WHERE _id = 100"));
        assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM ref_Payees WHERE _id = 100"));
        assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Projects WHERE _id = 100"));
        assertEquals(0, queryLong(migrated, "SELECT OrderNumber FROM ref_Projects WHERE _id = 100"));
        assertEquals("Project A", queryString(migrated, "SELECT FullName FROM ref_Projects WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT isActive FROM ref_Projects WHERE _id = 100"));
        String color = queryString(migrated, "SELECT Color FROM ref_Projects WHERE _id = 100");
        assertTrue("Color: " + color, color != null && color.matches("#[0-9A-F]{6}"));
        assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Departments WHERE _id = 100"));
        assertEquals("Department A", queryString(migrated, "SELECT FullName FROM ref_Departments WHERE _id = 100"));
        assertEquals(-1, queryLong(migrated, "SELECT ParentID FROM ref_Locations WHERE _id = 100"));
        assertEquals("Place A", queryString(migrated, "SELECT FullName FROM ref_Locations WHERE _id = 100"));
        assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM ref_Locations WHERE _id = 100"), 0.0001);
        assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM ref_Locations WHERE _id = 100"), 0.0001);

        assertEquals("template", queryString(migrated, "SELECT SearchString FROM log_Templates WHERE _id = 100"));
        assertEquals("", queryString(migrated, "SELECT Comment FROM log_Templates WHERE _id = 100"));

        // update25: debt with transactions gets the currency of the account, debt without transactions is deleted
        assertEquals(100, queryLong(migrated, "SELECT Currency FROM ref_SimpleDebts WHERE _id = 100"));
        assertEquals(0, queryDouble(migrated, "SELECT StartAmount FROM ref_SimpleDebts WHERE _id = 100"), 0.0001);
        assertEquals("debt with tx", queryString(migrated, "SELECT SearchString FROM ref_SimpleDebts WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_SimpleDebts"));

        // debts and budgets are not touched by the steps
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Debts"));
        assertEquals(101, queryLong(migrated, "SELECT Account FROM ref_Debts WHERE _id = 100"));
        assertEquals(100, queryLong(migrated, "SELECT Payee FROM ref_Debts WHERE _id = 100"));
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_budget"));
        assertEquals(300, queryDouble(migrated, "SELECT Amount FROM ref_budget WHERE _id = 100"), 0.0001);
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_budget_debts"));
        assertEquals(50, queryDouble(migrated, "SELECT Amount FROM ref_budget_debts WHERE _id = 100"), 0.0001);

        // transactions: dates, update22: 0 -> -1 for Project/Department/Location, update24: NULL Lat/Lon/Accuracy/SimpleDebt
        assertEquals(date100, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id100));
        assertEquals(date101, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id101));
        assertEquals(date102, queryLong(migrated, "SELECT DateTime FROM log_Transactions WHERE _id = " + id102));
        assertEquals(-100, queryDouble(migrated, "SELECT Amount FROM log_Transactions WHERE _id = " + id100), 0.0001);
        assertEquals(250, queryDouble(migrated, "SELECT Amount FROM log_Transactions WHERE _id = " + id101), 0.0001);
        assertEquals(101, queryLong(migrated, "SELECT DestAccount FROM log_Transactions WHERE _id = " + id102));
        assertEquals(-1, queryLong(migrated, "SELECT Project FROM log_Transactions WHERE _id = " + id100));
        assertEquals(-1, queryLong(migrated, "SELECT Department FROM log_Transactions WHERE _id = " + id100));
        assertEquals(-1, queryLong(migrated, "SELECT Location FROM log_Transactions WHERE _id = " + id100));
        assertEquals(100, queryLong(migrated, "SELECT Project FROM log_Transactions WHERE _id = " + id101));
        assertEquals(100, queryLong(migrated, "SELECT Department FROM log_Transactions WHERE _id = " + id101));
        assertEquals(100, queryLong(migrated, "SELECT Location FROM log_Transactions WHERE _id = " + id101));
        assertEquals(-1, queryLong(migrated, "SELECT Project FROM log_Transactions WHERE _id = " + id102));
        assertEquals(0, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = " + id100), 0.0001);
        assertEquals(0, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = " + id100), 0.0001);
        assertEquals(-1, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = " + id100));
        assertEquals(100, queryLong(migrated, "SELECT SimpleDebt FROM log_Transactions WHERE _id = " + id100));
        assertEquals(1.5, queryDouble(migrated, "SELECT Lat FROM log_Transactions WHERE _id = " + id101), 0.0001);
        assertEquals(2.5, queryDouble(migrated, "SELECT Lon FROM log_Transactions WHERE _id = " + id101), 0.0001);
        assertEquals(10, queryLong(migrated, "SELECT Accuracy FROM log_Transactions WHERE _id = " + id101));
        assertEquals(-1, queryLong(migrated, "SELECT SimpleDebt FROM log_Transactions WHERE _id = " + id101));
        assertEquals("Кофе", queryString(migrated, "SELECT Comment FROM log_Transactions WHERE _id = " + id100));
        assertEquals("kofe", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id100));
        assertEquals("salary", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id101));
        assertEquals("transfer", queryString(migrated, "SELECT SearchString FROM log_Transactions WHERE _id = " + id102));
        assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Transactions WHERE _id IN (" + id100 + ", " + id101 + ", " + id102 + ") AND FN = 0 AND FD = 0 AND FP = 0 AND Split = 0"));

        // update32: one product per not deleted transaction, price equals the amount
        assertEquals(1, queryLong(migrated, "SELECT COUNT(*) FROM ref_Products"));
        assertProducts(migrated, id100, -100);
        assertProducts(migrated, id101, 250);
        assertProducts(migrated, id102, -40);
        assertEquals(3, queryLong(migrated, "SELECT COUNT(*) FROM log_Products"));

        // update27/28: running balance of not deleted transactions, transfer adds a row for the destination account
        assertEquals(4, queryLong(migrated, "SELECT COUNT(*) FROM log_Running_Balance"));
        assertBalance(migrated, 100, id100, 0, -100);
        assertBalance(migrated, 100, id101, 250, -100);
        assertBalance(migrated, 100, id102, 250, -140);
        assertBalance(migrated, 101, id102, 40, 0);
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
            "INSERT INTO log_Incoming_SMS (_id, Deleted, DateTime, Sender, Body) VALUES (100, 0, '2020-01-01 10:00:00', 100, 'sms body')",
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
            "INSERT INTO log_Incoming_SMS (_id, DateTime, Sender, Body) VALUES (100, '2016-05-17 09:00:00', '+1000', 'sms body')",
            "INSERT INTO ref_Sms_Parser_Patterns (_id, Type, Object, Pattern) VALUES (100, 1, 'object', 'pattern')",
            "INSERT INTO ref_Debts (_id, Account, Payee, Category, Closed) VALUES (100, 101, 100, 100, 0)",
            "INSERT INTO ref_budget (_id, Year, Month, Category, Amount, Currency) VALUES (100, 2016, 5, 100, 300, 100)",
            "INSERT INTO ref_budget_debts (_id, Year, Month, Debt, Amount) VALUES (100, 2016, 5, 100, 50)",
            TRANSACTION_V16 + " VALUES (100, '2016-05-17 10:30:00', 100, NULL, NULL, -100, 0, 0, 'Кофе', NULL, -1, 1, 0, NULL, NULL, NULL, 0, 100)",
            TRANSACTION_V16 + " VALUES (101, '2016-05-18 11:00:00', 100, 100, 101, 250, 100, 100, 'Salary', NULL, -1, 1, 0, 1.5, 2.5, 10, 100, NULL)",
            TRANSACTION_V16 + " VALUES (102, '2016-05-19 12:00:00', 100, NULL, NULL, -40, NULL, NULL, 'Transfer', NULL, 101, 1, 0, 0, 0, -1, NULL, NULL)"
    };

    private static void assertMatchesCleanSchema(SQLiteDatabase clean, SQLiteDatabase migrated) {
        assertEquals("tables and columns", MigrationTestHelper.dumpColumnsIgnoreCase(clean),
                MigrationTestHelper.dumpColumnsIgnoreCase(migrated));
        assertEquals("indexes", MigrationTestHelper.dumpIndexes(clean), MigrationTestHelper.dumpIndexes(migrated));
    }

    private static void assertProducts(SQLiteDatabase db, long transactionId, double amount) {
        try (Cursor c = db.rawQuery("SELECT ProductID, CategoryID, ProjectID, Price, Quantity FROM log_Products "
                + "WHERE TransactionID = " + transactionId, null)) {
            assertEquals("products of transaction " + transactionId, 1, c.getCount());
            assertTrue(c.moveToFirst());
            assertEquals(0, c.getLong(0));
            assertEquals(-1, c.getLong(1));
            assertEquals(-1, c.getLong(2));
            assertEquals(amount, c.getDouble(3), 0.0001);
            assertEquals(1, c.getDouble(4), 0.0001);
        }
    }

    private static void assertBalance(SQLiteDatabase db, long accountId, long transactionId, double income, double expense) {
        try (Cursor c = db.rawQuery("SELECT Income, Expense FROM log_Running_Balance WHERE AccountID = "
                + accountId + " AND TransactionID = " + transactionId, null)) {
            assertTrue("running balance " + accountId + "/" + transactionId, c.moveToFirst());
            assertEquals(income, c.getDouble(0), 0.0001);
            assertEquals(expense, c.getDouble(1), 0.0001);
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
}
