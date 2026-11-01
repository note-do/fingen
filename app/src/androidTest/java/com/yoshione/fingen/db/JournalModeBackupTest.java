package com.yoshione.fingen.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Journal mode of a SQLiteOpenHelper configured like DBHelper (super(context, name, null, version), no
 * setWriteAheadLoggingEnabled, no PRAGMA journal_mode) and completeness of a backup that copies only the main
 * file, as DBHelper.backupDB does (zip of getDbPath() without -wal/-shm).
 * <p>
 * The helper uses its own database name, the real fingen.db is not touched. The actual journal_mode and file
 * state are written to the log (tag JournalModeBackup). If the mode is WAL and -wal holds the row, a copy of the
 * main file without it is a known defect of backupDB, fixed here as current behaviour (the code is not changed).
 * Restore (DBHelper.restoreDB) is not tested: it calls System.exit.
 */
@RunWith(AndroidJUnit4.class)
public class JournalModeBackupTest {

    private static final String TAG = "JournalModeBackup";
    private static final String DB_NAME = "journal_mode_test.db";
    private static final String COPY_NAME = "journal_mode_test_copy.db";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        cleanUp();
    }

    @After
    public void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (String name : new String[]{DB_NAME, COPY_NAME}) {
            mContext.deleteDatabase(name);
            File db = mContext.getDatabasePath(name);
            for (String suffix : new String[]{"-wal", "-shm", "-journal"}) {
                new File(db.getPath() + suffix).delete();
            }
        }
    }

    private static String queryString(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    private static void copyFile(File from, File to) throws IOException {
        try (FileInputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    @Test
    public void backupOfMainFileOnly_containsRecentRow() throws Exception {
        SQLiteOpenHelper helper = new SQLiteOpenHelper(mContext, DB_NAME, null, 1) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE ref_Currencies (_id INTEGER PRIMARY KEY AUTOINCREMENT, Name TEXT)");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            }
        };
        File dbFile = mContext.getDatabasePath(DB_NAME);
        File walFile = new File(dbFile.getPath() + "-wal");
        File copy = mContext.getDatabasePath(COPY_NAME);
        String mode;
        boolean walExists;
        long walSize;
        boolean rowInCopy;
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            mode = queryString(db, "PRAGMA journal_mode");
            db.execSQL("INSERT INTO ref_Currencies (Name) VALUES ('journal_mode_test_row')");
            walExists = walFile.exists();
            walSize = walExists ? walFile.length() : 0;
            // like backupDB: main file only, database still open
            copyFile(dbFile, copy);
            SQLiteDatabase copyDb = SQLiteDatabase.openDatabase(copy.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try {
                rowInCopy = Integer.parseInt(queryString(copyDb,
                        "SELECT COUNT(*) FROM ref_Currencies WHERE Name = 'journal_mode_test_row'")) == 1;
            } finally {
                copyDb.close();
            }
        } finally {
            helper.close();
        }
        Log.i(TAG, "journal_mode=" + mode + ", wal exists=" + walExists + ", wal size=" + walSize
                + ", row in main-file copy=" + rowInCopy);

        if ("wal".equalsIgnoreCase(mode)) {
            assertTrue("WAL mode: -wal must exist after write", walExists && walSize > 0);
            assertTrue("KNOWN DEFECT: WAL mode, backupDB copies only the main file, the row is in -wal; "
                    + "if the row is now present, the backup became complete (update the test and CLAUDE.md)", !rowInCopy);
        } else {
            // Android framework default without WAL is TRUNCATE (not DELETE)
            assertTrue("Not WAL: expected rollback journal, got " + mode,
                    "truncate".equalsIgnoreCase(mode) || "delete".equalsIgnoreCase(mode));
            assertTrue("Rollback journal: row must be in the main file, backup is complete", rowInCopy);
        }
    }
}
