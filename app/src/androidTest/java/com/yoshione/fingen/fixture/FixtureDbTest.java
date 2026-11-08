package com.yoshione.fingen.fixture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests of the fixture DB. Every test replaces the real fingen.db of the app under test, the data
 * is not restored afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class FixtureDbTest {

    @Test
    public void copyFixture_hasExpectedTransactions() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.copyFixture(context);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(DBHelper.DATABASE_VERSION, db.getVersion());
        assertEquals(FixtureDb.TRANSACTIONS_TOTAL, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(FixtureDb.TRANSACTIONS_DELETED,
                count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted = 1"));
    }

    @Test
    public void build_defaultSpec_countsMatchAndVerifyPasses() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = new FixtureDb.Spec();
        FixtureDb.build(context, spec);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(DBHelper.DATABASE_VERSION, db.getVersion());
        assertEquals(spec.accounts, count(db, "SELECT COUNT(*) FROM ref_Accounts"));
        assertEquals(spec.payees, count(db, "SELECT COUNT(*) FROM ref_Payees"));
        assertEquals(spec.transactions, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(spec.deletedTransactions, count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted > 0"));
        assertEquals(spec.budgets, count(db, "SELECT COUNT(*) FROM ref_budget"));
        assertEquals(spec.templates, count(db, "SELECT COUNT(*) FROM log_Templates"));
        assertEquals(spec.markers, count(db, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns"));
        assertEquals(spec.incomingSms, count(db, "SELECT COUNT(*) FROM log_Incoming_SMS"));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM log_Transactions t WHERE NOT EXISTS ("
                + "SELECT 1 FROM log_Products p WHERE p.TransactionID = t._id AND p.ProductID = 0)"));
        assertTrue(count(db, "SELECT COUNT(*) FROM log_Running_Balance") > 0);
        FixtureDb.verify(context, spec);
    }

    @Test
    public void build_smallSpec_countIsConfigurable() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        assertEquals(5, spec.transactions);
        assertEquals(2, spec.accounts);
        FixtureDb.build(context, spec);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(2, count(db, "SELECT COUNT(*) FROM ref_Accounts"));
        assertEquals(5, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted > 0"));
        FixtureDb.verify(context, spec);
    }

    @Test
    public void build_sameSeed_sameData_differentSeed_differentData() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        FixtureDb.build(context, spec);
        String first = signature(context);
        FixtureDb.build(context, spec);
        assertEquals(first, signature(context));
        spec.seed = 99;
        FixtureDb.build(context, spec);
        assertTrue(!first.equals(signature(context)));
    }

    @Test
    public void verify_detectsCountMismatch() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        FixtureDb.build(context, spec);
        spec.transactions = 6;
        try {
            FixtureDb.verify(context, spec);
            fail("verify must fail when counts differ from the spec");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("log_Transactions"));
        }
    }

    private static String signature(Context context) {
        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        StringBuilder sb = new StringBuilder();
        try (Cursor cursor = db.rawQuery("SELECT DateTime, SrcAccount, Amount, Payee, Category, Comment "
                + "FROM log_Transactions ORDER BY _id", null)) {
            while (cursor.moveToNext()) {
                for (int i = 0; i < 6; i++) {
                    sb.append(cursor.getString(i)).append('|');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }
}
