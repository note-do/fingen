package com.yoshione.fingen;

import com.yoshione.fingen.databinding.ActivityAboutBinding;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.yoshione.fingen.widgets.ToolbarActivity;

import java.lang.ref.WeakReference;

import de.psdev.licensesdialog.LicensesDialog;

public class ActivityAbout extends ToolbarActivity {

    private static final int MSG_REBUILD_START = 0;
    private static final int MSG_REBUILD_ERROR = 1;
    private static final int MSG_REBUILD_SUCCESS = 2;
    private ActivityAboutBinding mBinding;
    TextView mTextViewVersion;
    TextView mTextViewLicense;
    TextView mButtonLicenses;
    TextView mButtonChangeLog;
    TextView mTextViewSQLiteVersion;
    Button mButtonRebuildDB;
    private UpdateUIHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityAboutBinding.bind(getContentRoot());
        mTextViewVersion = mBinding.textViewVersion;
        mTextViewLicense = mBinding.textViewLicense;
        mButtonLicenses = mBinding.buttonLicenses;
        mButtonChangeLog = mBinding.buttonChangeLog;
        mTextViewSQLiteVersion = mBinding.textViewSQLiteVersion;
        mButtonRebuildDB = mBinding.buttonRebuildDB;

        mHandler = new UpdateUIHandler(this);
        PackageInfo pInfo;
        String version;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            version = "N/A";
        }
        mTextViewVersion.setText(String.format("%s: %s", getString(R.string.ttl_app_version), version));
        String s = getString(R.string.ttl_sqlite_version) + DBHelper.getInstance(getApplicationContext()).getSqliteVersion();
        mTextViewSQLiteVersion.setText(s);
        mButtonLicenses.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new LicensesDialog.Builder(ActivityAbout.this)
                        .setNotices(R.raw.license)
                        .build()
                        .show();
            }
        });

        mButtonChangeLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentChangelog fragmentChangelog = new FragmentChangelog();
                FragmentManager fm = ActivityAbout.this.getSupportFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                Fragment prev = fm.findFragmentByTag("changelogdemo_dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                fragmentChangelog.show(ft, "changelogdemo_dialog");
            }
        });

        mButtonRebuildDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean error = false;
                        try {
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_REBUILD_START));
                            DBHelper.getInstance(getApplicationContext()).rebuildDB();
                        } catch (Exception e) {
                            error = true;
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_REBUILD_ERROR));
                        } finally {
                            if (!error) {
                                mHandler.sendMessage(mHandler.obtainMessage(MSG_REBUILD_SUCCESS));
                            }
                        }
                    }
                });
                thread.start();
            }
        });
    }

    private static class UpdateUIHandler extends Handler {
        WeakReference<ActivityAbout> mActivity;

        UpdateUIHandler(ActivityAbout activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ActivityAbout activity = mActivity.get();

            FGApplication app = (FGApplication) activity.getApplication();
            switch (msg.what) {
                case MSG_REBUILD_START:
                    app.getDialog().setMessage(app.getString(R.string.msg_db_upgrade));
                    app.getDialog().show();
                    break;
                case MSG_REBUILD_ERROR:
                    app.getDialog().hide();
                    Toast.makeText(activity, R.string.msg_db_rebuild_error, Toast.LENGTH_SHORT).show();
                    break;
                case MSG_REBUILD_SUCCESS:
                    app.getDialog().hide();
                    Toast.makeText(activity, R.string.msg_db_rebuild_success, Toast.LENGTH_SHORT).show();
                    break;
            }

        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_about;
    }

    @Override
    protected String getLayoutTitle() {
        return getString(R.string.ent_about);
    }
}
