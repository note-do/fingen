package com.yoshione.fingen.db.migrations;

/**
 * Шаг миграции: выполняется, если oldVersion &lt; belowVersion.
 * Условие сохраняется как было в DBHelper.onUpgrade (в том числе повторные и нестандартные условия).
 */
public final class MigrationStep {
    public final int belowVersion;
    public final DbMigration migration;

    public MigrationStep(int belowVersion, DbMigration migration) {
        this.belowVersion = belowVersion;
        this.migration = migration;
    }
}
