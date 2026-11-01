package com.yoshione.fingen.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Migration tests on separate database files. DBHelper.onUpgrade makes a zip backup of the real
 * fingen.db when the storage permission is granted; such files are removed after each test.
 * <p>
 * Fixtures of versions 23 and 30 (assets/migrations) are RECONSTRUCTED from UpdateHelper, not taken
 * from real databases, see the header of each fixture. For them the schema is compared with a clean
 * database by tables, columns (ignoring identifier case) and indexes, not by the SQL text of tables:
 * ALTER TABLE ADD COLUMN changes that text and the column order.
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
     * EXPECTED DEFECT, not fixed in this step: UpdateHelper.update29 (UpdateHelper.java:738) calls
     * cursor.getString(1).toLowerCase() and throws NPE for a not deleted transaction with NULL Comment.
     * The test describes the behaviour after a fix (migration completes), remove @Ignore then.
     */
    @Ignore("Known defect: UpdateHelper.update29 (UpdateHelper.java:738) throws NPE on NULL Comment of a not deleted transaction")
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
        } finally {
            migrated.close();
        }
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
