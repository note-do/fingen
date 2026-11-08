package com.yoshione.fingen.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.yoshione.fingen.DBHelper;

import java.io.File;

/**
 * Gate for the DB upgrade at app start. {@link #needsUpgrade(Context)} reads PRAGMA user_version without DBHelper,
 * {@link #start(Context)} opens DBHelper (and so runs the migration) in a background thread.
 * The state lives in statics, so it survives recreation of the upgrade screen.
 */
public final class DbUpgradeGate {

    private static final String TAG = "DbUpgradeGate";

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    public static final int STAGE_PREPARE = 0;
    public static final int STAGE_CONVERT = 1;
    public static final int STAGE_BALANCE = 2;
    public static final int STAGE_VERIFY = 3;

    public interface Listener {
        /** Called on the main thread when the state or progress changes. */
        void onChanged();
    }

    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    private static volatile boolean sUpToDate = false;
    private static volatile int sState = IDLE;
    private static volatile int sPercent = 0;
    private static volatile int sStage = STAGE_PREPARE;
    private static volatile int sErrorCode = 0;
    private static volatile String sErrorMessage = null;
    private static volatile long sRequiredBytes = 0;
    private static volatile Listener sListener;

    private DbUpgradeGate() {
    }

    /**
     * True if the DB file exists and its version is lower than DBHelper.DATABASE_VERSION (or a migration is running).
     * A result "up to date" is cached for the process. No file means a fresh install: DBHelper creates the schema.
     */
    public static boolean needsUpgrade(Context context) {
        if (sUpToDate) {
            return false;
        }
        if (sState == RUNNING) {
            return true;
        }
        File file = context.getApplicationContext().getDatabasePath(DBHelper.DATABASE_NAME);
        if (!file.exists()) {
            return false;
        }
        SQLiteDatabase db = null;
        Cursor c = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = db.rawQuery("PRAGMA user_version", null);
            int version = c.moveToFirst() ? c.getInt(0) : 0;
            if (version >= DBHelper.DATABASE_VERSION) {
                sUpToDate = true;
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // Can't read (hot journal, corruption): let the upgrade screen open DBHelper and show the real error
            Log.w(TAG, "Can't read user_version", e);
            return true;
        } finally {
            if (c != null) {
                c.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }

    public static int getState() {
        return sState;
    }

    public static int getPercent() {
        return sPercent;
    }

    public static int getStage() {
        return sStage;
    }

    public static int getErrorCode() {
        return sErrorCode;
    }

    public static String getErrorMessage() {
        return sErrorMessage;
    }

    public static long getRequiredBytes() {
        return sRequiredBytes;
    }

    public static void setListener(Listener listener) {
        sListener = listener;
    }

    /** Progress from the migration (any thread). */
    public static void setProgress(int percent, int stage) {
        sPercent = percent;
        sStage = stage;
        notifyChanged();
    }

    /** Starts the migration in a background thread; does nothing while it is already running. */
    public static synchronized void start(Context context) {
        if (sState == RUNNING) {
            return;
        }
        final Context app = context.getApplicationContext();
        sState = RUNNING;
        sPercent = 0;
        sStage = STAGE_PREPARE;
        sErrorCode = 0;
        sErrorMessage = null;
        sRequiredBytes = 0;
        notifyChanged();
        new Thread(() -> {
            try {
                DBHelper.getInstance(app);
                sUpToDate = true;
                sState = DONE;
            } catch (DbUpgradeException e) {
                Log.e(TAG, "DB upgrade failed, code " + e.getCode(), e);
                fail(e.getCode(), e.getMessage(), e.getRequiredBytes());
            } catch (Exception e) {
                Log.e(TAG, "DB upgrade failed", e);
                fail(DbUpgradeException.CODE_FAILED, e.getMessage(), 0);
            }
            notifyChanged();
        }, "db-upgrade").start();
    }

    private static void fail(int code, String message, long requiredBytes) {
        sErrorCode = code;
        sErrorMessage = message;
        sRequiredBytes = requiredBytes;
        sState = FAILED;
    }

    private static void notifyChanged() {
        final Listener listener = sListener;
        if (listener != null) {
            HANDLER.post(listener::onChanged);
        }
    }
}
