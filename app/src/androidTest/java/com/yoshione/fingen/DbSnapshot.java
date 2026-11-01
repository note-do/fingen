package com.yoshione.fingen;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers the highest _id of every table before a test and deletes everything inserted after it.
 * Rows are removed physically: the DAOs only mark rows as deleted.
 */
public final class DbSnapshot {

    private final Map<String, Long> mMaxIds = new HashMap<>();

    public static DbSnapshot take(Context context) {
        SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
        DbSnapshot snapshot = new DbSnapshot();
        for (String table : tablesWithId(db)) {
            snapshot.mMaxIds.put(table, maxId(db, table));
        }
        return snapshot;
    }

    public void restore(Context context) {
        SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
        // Roll back a transaction left open by the code under test, so its rows are not kept.
        if (db.inTransaction()) {
            db.endTransaction();
        }
        for (Map.Entry<String, Long> entry : mMaxIds.entrySet()) {
            db.delete("\"" + entry.getKey() + "\"", "_id > ?", new String[]{String.valueOf(entry.getValue())});
        }
    }

    private static List<String> tablesWithId(SQLiteDatabase db) {
        List<String> tables = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'", null);
        try {
            while (cursor.moveToNext()) {
                String table = cursor.getString(0);
                if (hasIdColumn(db, table)) {
                    tables.add(table);
                }
            }
        } finally {
            cursor.close();
        }
        return tables;
    }

    private static boolean hasIdColumn(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null);
        try {
            while (cursor.moveToNext()) {
                if ("_id".equals(cursor.getString(1))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private static long maxId(SQLiteDatabase db, String table) {
        Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(_id), 0) FROM \"" + table + "\"", null);
        try {
            cursor.moveToFirst();
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }
}
