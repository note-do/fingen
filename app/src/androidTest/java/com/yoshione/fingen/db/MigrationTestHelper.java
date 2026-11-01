package com.yoshione.fingen.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.utils.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;

/**
 * Builds databases of an old version from SQL fixtures (assets/migrations) in separate files,
 * so the real fingen.db of the app is never touched by the schema work.
 */
final class MigrationTestHelper {

    private static final String PREFIX = "migration_test_";

    private MigrationTestHelper() {
    }

    static String dbName(String suffix) {
        return PREFIX + suffix + ".db";
    }

    /**
     * Creates database file migration_test_[version].db from assets/migrations/fingen_v[version].sql
     * and sets user_version. Optional seed statements are executed after the schema.
     */
    static File createFixture(Context context, int version, String... seedSql) throws IOException {
        context.deleteDatabase(dbName(String.valueOf(version)));
        File file = context.getDatabasePath(dbName(String.valueOf(version)));
        file.getParentFile().mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            for (String statement : readStatements("migrations/fingen_v" + version + ".sql")) {
                db.execSQL(statement);
            }
            for (String statement : seedSql) {
                db.execSQL(statement);
            }
            db.setVersion(version);
        } finally {
            db.close();
        }
        return file;
    }

    private static List<String> readStatements(String assetPath) throws IOException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        List<String> result = new ArrayList<>();
        try (InputStream in = testContext.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder current = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.toUpperCase().startsWith("PRAGMA")
                        || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            }
            if (current.toString().trim().length() > 0) {
                result.add(current.toString());
            }
        }
        return result;
    }

    /** Opens the file with DATABASE_VERSION, so SQLiteOpenHelper runs DBHelper.onUpgrade if needed. */
    static SQLiteDatabase openMigrated(Context context, String name) {
        return new MigrationOpenHelper(context, name, false).getWritableDatabase();
    }

    /** Creates a clean database with DBHelper.onCreate in a separate file. */
    static SQLiteDatabase openClean(Context context, String name) {
        context.deleteDatabase(name);
        return new MigrationOpenHelper(context, name, true).getWritableDatabase();
    }

    /** Normalized "type name" -> sql for tables and indexes. */
    static Map<String, String> dumpSchema(SQLiteDatabase db) {
        Map<String, String> result = new TreeMap<>();
        try (Cursor c = db.rawQuery("SELECT type, name, sql FROM sqlite_master WHERE type IN ('table','index') "
                + "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'", null)) {
            while (c.moveToNext()) {
                String sql = c.isNull(2) ? "" : c.getString(2).replaceAll("\\s+", " ").trim();
                result.put(c.getString(0) + " " + c.getString(1), sql);
            }
        }
        return result;
    }

    /** Table name -> set of "name:type" of its columns. */
    static Map<String, Set<String>> dumpColumns(SQLiteDatabase db) {
        Map<String, Set<String>> result = new TreeMap<>();
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                + "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'", null)) {
            while (c.moveToNext()) {
                tables.add(c.getString(0));
            }
        }
        for (String table : tables) {
            Set<String> columns = new HashSet<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(\"" + table + "\")", null)) {
                while (c.moveToNext()) {
                    columns.add(c.getString(1) + ":" + c.getString(2));
                }
            }
            result.put(table, columns);
        }
        return result;
    }

    /**
     * Lower-case "table" -> set of lower-case "column:type". Identifier case is ignored, because
     * old fixtures declare isActive and a clean database declares IsActive.
     */
    static Map<String, Set<String>> dumpColumnsIgnoreCase(SQLiteDatabase db) {
        Map<String, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : dumpColumns(db).entrySet()) {
            Set<String> columns = new HashSet<>();
            for (String column : entry.getValue()) {
                columns.add(column.toLowerCase());
            }
            result.put(entry.getKey().toLowerCase(), columns);
        }
        return result;
    }

    /**
     * Indexes of all tables as "table|origin|unique|columns" (index name is added for indexes
     * created by CREATE INDEX). Does not depend on the SQL text of the tables.
     */
    static Set<String> dumpIndexes(SQLiteDatabase db) {
        Set<String> result = new HashSet<>();
        List<String> tables = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' "
                + "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'", null)) {
            while (c.moveToNext()) {
                tables.add(c.getString(0));
            }
        }
        for (String table : tables) {
            List<String[]> indexes = new ArrayList<>();
            try (Cursor c = db.rawQuery("PRAGMA index_list(\"" + table + "\")", null)) {
                int originIdx = c.getColumnIndex("origin");
                while (c.moveToNext()) {
                    indexes.add(new String[]{c.getString(c.getColumnIndex("name")),
                            c.getString(c.getColumnIndex("unique")),
                            originIdx >= 0 ? c.getString(originIdx) : ""});
                }
            }
            for (String[] index : indexes) {
                StringBuilder columns = new StringBuilder();
                try (Cursor c = db.rawQuery("PRAGMA index_info(\"" + index[0] + "\")", null)) {
                    while (c.moveToNext()) {
                        columns.append(c.getString(c.getColumnIndex("name"))).append(',');
                    }
                }
                String name = "c".equals(index[2]) ? "|" + index[0] : "";
                result.add((table + "|" + index[2] + "|" + index[1] + "|" + columns + name).toLowerCase());
            }
        }
        return result;
    }

    /** Names of files in the external backup folder (DBHelper.onUpgrade writes a zip there). */
    static Set<String> listBackups() {
        Set<String> result = new HashSet<>();
        String path = FileUtils.getExtFingenBackupFolder();
        if (!path.isEmpty()) {
            String[] names = new File(path).list();
            if (names != null) {
                result.addAll(Arrays.asList(names));
            }
        }
        return result;
    }

    /** Deletes backup files that appeared after the "before" listing. */
    static void deleteNewBackups(Set<String> before) {
        String path = FileUtils.getExtFingenBackupFolder();
        if (path.isEmpty()) {
            return;
        }
        for (String name : listBackups()) {
            if (!before.contains(name)) {
                new File(path, name).delete();
            }
        }
    }

    static void deleteTestDatabases(Context context) {
        String[] names = context.databaseList();
        if (names != null) {
            for (String name : names) {
                if (name.startsWith(PREFIX)) {
                    context.deleteDatabase(name);
                }
            }
        }
    }

    private static final class MigrationOpenHelper extends SQLiteOpenHelper {
        private final Context mContext;
        private final boolean mAllowCreate;

        MigrationOpenHelper(Context context, String name, boolean allowCreate) {
            super(context, name, null, DBHelper.DATABASE_VERSION);
            mContext = context;
            mAllowCreate = allowCreate;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            if (!mAllowCreate) {
                throw new AssertionError("onCreate must not be called for a fixture database");
            }
            DBHelper.getInstance(mContext).onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            DBHelper.getInstance(mContext).onUpgrade(db, oldVersion, newVersion);
        }
    }
}
