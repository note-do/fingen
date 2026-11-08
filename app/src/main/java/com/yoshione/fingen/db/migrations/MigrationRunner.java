package com.yoshione.fingen.db.migrations;

import android.content.Context;

import android.database.sqlite.SQLiteDatabase;

import java.util.List;

/**
 * Выполняет шаги миграций по порядку списка.
 * Шаги могут ссылаться как на legacy-методы UpdateHelper, так и на отдельные классы Migration{N}:
 * порядок и условия задаются только списком в DBHelper.
 */
public final class MigrationRunner {

    private MigrationRunner() {
    }

    public static void run(SQLiteDatabase db, Context context, int oldVersion, List<MigrationStep> steps) {
        for (MigrationStep step : steps) {
            if (oldVersion < step.belowVersion) {
                step.migration.migrate(db, context);
            }
        }
    }
}
