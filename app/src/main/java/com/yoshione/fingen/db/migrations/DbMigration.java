package com.yoshione.fingen.db.migrations;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;

/**
 * Одна миграция схемы БД. Одна реализация — один файл (Migration{N}.java).
 * Вызывается внутри транзакции onUpgrade: внешние ключи и PRAGMA здесь менять нельзя.
 */
public interface DbMigration {
    void migrate(SQLiteDatabase db, Context context);
}
