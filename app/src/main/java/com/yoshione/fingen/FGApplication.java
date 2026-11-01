/*
 * Copyright (c) 2015.
 */
package com.yoshione.fingen;

import android.app.Activity;
import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import android.view.Gravity;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.backup.BackupWorker;
import com.yoshione.fingen.di.AppComponent;
import com.yoshione.fingen.di.DaggerAppComponent;
import com.yoshione.fingen.di.modules.ContextModule;
import com.yoshione.fingen.interfaces.ISyncAnimMethods;
import com.yoshione.fingen.lock.PinLockManager;

import java.lang.ref.WeakReference;

public class FGApplication extends Application implements ISyncAnimMethods {

    private static final String TAG = "FGApplication";
    public static final int MSG_SHOW_ANIM = 1;
    public static final int MSG_HIDE_ANIM = 2;
    public static final int MSG_SHOW_DIALOG = 3;
    public static final int MSG_HIDE_DIALOG = 4;

    boolean mBound = false;
    ServiceConnection sConn;
    Intent intent;

    // Popup to show persistent view
    public PopupWindow mPopupWindow;
    // View held by Popup
    public LinearLayout mLinearLayout;
    ImageView mImageView;

    public AlertDialog getDialog() {
        return mDialog;
    }

    AlertDialog mDialog;
    RotateAnimation mSpinAnim;

    public boolean mAppPaused = true;

    public UpdateUIHandler mUpdateUIHandler;

    private static AppComponent sAppComponent;

    private static FGApplication mContext;

    public static FGApplication getContext() {
        return mContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
//        Debug.startMethodTracing("Startup");
        super.onCreate();

        sAppComponent = DaggerAppComponent.builder()
                .contextModule(new ContextModule(this))
                .build();

        mContext = this;

        DropboxClient.migrateLegacyToken(this);

        cancelLegacyJobs();

        mUpdateUIHandler = new UpdateUIHandler(this);

        mAppPaused = true;

        PinLockManager.init(this);

        registerActivityLifecycleCallbacks(new FgActivityLifecycleCallbacks());

        BackupWorker.schedule(this);
    }

    // One-time cleanup of jobs left by the removed android-job library
    private void cancelLegacyJobs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(FgConst.PREF_ANDROID_JOB_MIGRATED, false)) {
            return;
        }
        JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            for (JobInfo job : scheduler.getAllPendingJobs()) {
                if (job.getService().getClassName().startsWith("com.evernote.android.job")) {
                    scheduler.cancel(job.getId());
                }
            }
        }
        prefs.edit().putBoolean(FgConst.PREF_ANDROID_JOB_MIGRATED, true).apply();
    }

    public static AppComponent getAppComponent() {
        return sAppComponent;
    }

    @Override
    public void onTerminate() {
        if (mBound) {
            unbindService(sConn);
            mBound = false;
        }
        stopService(intent);
        super.onTerminate();
    }

    // targetSdk 36 ignores screenOrientation on sw >= 600dp, so portrait is locked
    // here for phones only; tablets and foldables rotate freely
    private static void lockPortraitOnPhones(Activity activity) {
        if (activity instanceof ActivityEditTransaction
                || activity instanceof ActivityEditLocation2
                || activity instanceof ActivityShortcut
                || !activity.getClass().getName().startsWith("com.yoshione.fingen")) {
            return;
        }
        if (activity.getResources().getConfiguration().smallestScreenWidthDp < 600) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private final class FgActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {

        int numOfRunning = 0;

        @Override
        public void onActivityCreated(Activity arg0, Bundle arg1) {
            lockPortraitOnPhones(arg0);

            switch (Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("theme", "0"))) {
                case ActivityMain.THEME_LIGHT:
                    arg0.setTheme(R.style.AppThemeLight);
                    break;
                case ActivityMain.THEME_DARK:
                    arg0.setTheme(R.style.AppThemeDark);
                    break;
                default:
                    arg0.setTheme(R.style.AppThemeLight);
                    break;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(arg0)
//                    .setMessage(getString(R.string.msg_db_upgrade))
                    .setCancelable(false);
            mDialog = builder.create();
        }

        @Override
        public void onActivityDestroyed(Activity arg0) { }

        @Override
        public void onActivityPaused(Activity arg0) {

            // An activity has been paused
            // Decrement count, but wait for a certain
            // period of time, in case another activity
            // from this application is being launched
            numOfRunning--;

            // Delay: 100 ms
            // If no activity's onResumed() was called,
            // its safe to assume that the application
            // has been paused, in which case, dismiss
            // the popup
            new Handler().postDelayed(() -> {
                if (numOfRunning == 0) {
                    hideSyncAnim();
                    mAppPaused = true;
                }
            }, 100L);
        }

        @Override
        public void onActivityResumed(Activity arg0) {

            // If no activities were running, show the popup
            if (numOfRunning == 0) {
//                mPopupWindow.showAtLocation(mLinearLayout, Gravity.BOTTOM, 0, 0);
            }
            mAppPaused = false;
            // Now, one activity is running
            numOfRunning++;
        }

        @Override
        public void onActivitySaveInstanceState(Activity arg0, Bundle arg1) { }

        @Override
        public void onActivityStarted(Activity arg0) { }

        @Override
        public void onActivityStopped(Activity arg0) { }

    }

    public void showSyncAnim() {
        if (!mAppPaused) {
            mPopupWindow.showAtLocation(mLinearLayout, Gravity.BOTTOM, 0, 0);
            mImageView.startAnimation(mSpinAnim);
        }
    }

    public void hideSyncAnim() {
        if (mImageView != null) {
            mImageView.clearAnimation();
            mPopupWindow.dismiss();
        }
    }

    public static class UpdateUIHandler extends Handler {
        WeakReference<FGApplication> mReference;

        UpdateUIHandler(FGApplication activity) {
            mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final FGApplication app = mReference.get();

            switch (msg.what) {
                case MSG_SHOW_ANIM:
//                    app.showSyncAnim();
//                    Toast.makeText(app, "Testttttttttttttt", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_HIDE_ANIM:
//                    app.hideSyncAnim();
//                    Toast.makeText(activity, R.string.msg_db_rebuild_error, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SHOW_DIALOG:
//                    AlertDialog.Builder builder = new AlertDialog.Builder(app)
//                            .setMessage("UPGRADE")
//                            .setCancelable(false);
//                    app.mDialog = builder.create();
                    app.mDialog.show();
                    break;
                case MSG_HIDE_DIALOG:
                    app.mDialog.setMessage((CharSequence) msg.obj);
                    app.mDialog.dismiss();
                    break;
            }

        }
    }
}
