package com.yoshione.fingen;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class DbSnapshotTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    @Test
    public void restore_removesRowsInsertedAfterSnapshot() {
        Context context = InstrumentationRegistry.getTargetContext();
        SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
        long before = count(db, "ref_Accounts");

        DbSnapshot snapshot = DbSnapshot.take(context);
        ContentValues cv = new ContentValues();
        cv.put("Name", "Snapshot probe " + System.nanoTime());
        cv.put("Type", 0);
        cv.put("CurrencyId", 2);
        cv.put("StartBalance", 0);
        cv.put("IsClosed", 0);
        cv.put("OrderNumber", 0);
        cv.put("Deleted", 0);
        db.insertOrThrow("ref_Accounts", null, cv);

        snapshot.restore(context);

        assertEquals(before, count(db, "ref_Accounts"));
    }

    private static long count(SQLiteDatabase db, String table) {
        android.database.Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
        try {
            cursor.moveToFirst();
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }
}
