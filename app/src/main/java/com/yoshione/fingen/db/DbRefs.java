package com.yoshione.fingen.db;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Граница между БД и моделями для необязательных ссылок: в БД пустая ссылка - NULL,
 * в моделях и остальном коде - -1.
 */
public final class DbRefs {

    private DbRefs() {
    }

    /** id &lt; 0 пишется как NULL. */
    public static void putRef(ContentValues values, String column, long id) {
        if (id < 0) {
            values.putNull(column);
        } else {
            values.put(column, id);
        }
    }

    /** NULL читается как -1. */
    public static long getRef(Cursor cursor, int index) {
        return cursor.isNull(index) ? -1 : cursor.getLong(index);
    }
}
