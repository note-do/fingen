package com.yoshione.fingen.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Smoke test of the fixture DB. Replaces the real fingen.db of the app under test with
 * assets/fixtures/fingen_v35_100.db, the data is not restored afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class FixtureDbTest {

    @Test
    public void copyTo_fixtureHasExpectedTransactions() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.copyTo(context);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(DBHelper.DATABASE_VERSION, db.getVersion());
        assertEquals(FixtureDb.TRANSACTIONS_TOTAL, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(FixtureDb.TRANSACTIONS_DELETED,
                count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted = 1"));
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }
}
