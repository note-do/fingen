package com.yoshione.fingen.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.yoshione.fingen.BuildConfig;
import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.utils.BackupFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Daily backup: zip to the backup folder chosen by the user (if available) and upload to Dropbox.
 * Runs in the 1-6 AM window (local time). Each run schedules the next one (chain of OneTime requests).
 */
public class BackupWorker extends Worker {

    private static final String TAG = "BackupWorker";
    public static final String UNIQUE_NAME = "work_backup_daily";

    private static final int WINDOW_START_HOUR = 1;
    private static final int WINDOW_END_HOUR = 6;
    private static final int MAX_ATTEMPTS = 3;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Called on app start. Keeps the existing chain, so the schedule is not reset.
     */
    public static void schedule(Context context) {
        enqueue(context, false, ExistingWorkPolicy.KEEP);
    }

    private static void scheduleNext(Context context) {
        // The current work is still running, so append the next one after it
        enqueue(context, true, ExistingWorkPolicy.APPEND_OR_REPLACE);
    }

    private static void enqueue(Context context, boolean nextDay, ExistingWorkPolicy policy) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInitialDelay(calcDelay(System.currentTimeMillis(), nextDay), TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request);
    }

    /**
     * Delay until a random moment inside the 1-6 AM window.
     * nextDay = false: today's window if it is not over yet (from now or from 1 AM), otherwise tomorrow's.
     * nextDay = true: always tomorrow's window (called after a run, possibly inside today's window).
     */
    static long calcDelay(long now, boolean nextDay) {
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(now);
        start.set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.set(Calendar.HOUR_OF_DAY, WINDOW_END_HOUR);

        if (nextDay || now >= end.getTimeInMillis()) {
            start.add(Calendar.DAY_OF_YEAR, 1);
            end.add(Calendar.DAY_OF_YEAR, 1);
        }
        long from = Math.max(now, start.getTimeInMillis());
        long span = end.getTimeInMillis() - from;
        return from - now + (long) (new Random().nextDouble() * span);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            return doBackup(context);
        } finally {
            // The chain must go on after any end of the run, including unchecked exceptions
            scheduleNext(context);
        }
    }

    private Result doBackup(Context context) {
        if (!BuildConfig.FLAVOR.equals("nd")) {
            DbxClientV2 dbxClient = DropboxClient.getClient(context);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean folderAvailable = BackupFolder.isAvailable(context);
            if (!folderAvailable) {
                // No folder or access revoked: skip the local copy, Dropbox goes on as usual
                Log.w(TAG, "Backup folder is not available, local backup skipped");
                prefs.edit().putBoolean(FgConst.PREF_BACKUP_FOLDER_UNAVAILABLE, true).apply();
            }
            if (folderAvailable || dbxClient != null) {
                try {
                    File zip = DBHelper.getInstance(context).backupDB(true, folderAvailable && getRunAttemptCount() == 0);
                    if (dbxClient != null && zip != null) {
                        // Upload to Dropbox
                        try (InputStream inputStream = new FileInputStream(zip)) {
                            dbxClient.files().uploadBuilder("/" + zip.getName()) //Path in the user's Dropbox to save the file.
                                    .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                                    .uploadAndFinish(inputStream);
                            prefs.edit().putLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, new Date().getTime()).apply();
                        } catch (DbxException e) {
                            e.printStackTrace();
                            // getRunAttemptCount() starts at 0: MAX_ATTEMPTS runs in total
                            if (getRunAttemptCount() < MAX_ATTEMPTS - 1) {
                                return Result.retry();
                            }
                        }
                    }
                    BackupFolder.cleanCache(context, zip);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return Result.success();
    }
}
