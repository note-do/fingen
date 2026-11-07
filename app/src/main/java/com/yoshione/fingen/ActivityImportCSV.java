package com.yoshione.fingen;

import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.databinding.ActivityImportCsvBinding;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.appcompat.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import android.util.Log;
import com.yoshione.fingen.interfaces.IProgressEventsListener;
import com.yoshione.fingen.csv.CsvImporter;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.IOException;

public class ActivityImportCSV extends ToolbarActivity implements IProgressEventsListener {

    private static final int REQUEST_PICK_CSV = 1;
    private static final String STATE_URI = "csv_uri";
    private final static int HANDLER_OPERATION_HIDE = 0;
    private final static int HANDLER_OPERATION_SHOW = 1;
    private final static int HANDLER_OPERATION_UPDATE = 2;
    private final static int HANDLER_OPERATION_TOAST = 3;
    private static final ProgressBar[] numberProgressBarArr = new ProgressBar[]{null};
    private static final Activity[] activityArr = new Activity[]{null};
    private ActivityImportCsvBinding mBinding;
    EditText editTextFileName;
    ProgressBar progressbar;
    SwitchCompat mSwitchCompatSkipDuplicates;
    UpdateProgressHandler handler;
    private int mCurrentProgress;
    private Uri mUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityImportCsvBinding.bind(getContentRoot());
        editTextFileName = findViewById(R.id.editTextFileName);
        progressbar = mBinding.progressbar;
        mSwitchCompatSkipDuplicates = mBinding.switchCompatSkipDuplicates;
        findViewById(R.id.editTextFileName).setOnClickListener(new DebouncingOnClickListener(v -> OnSelectFileClick()));

        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString(STATE_URI);
            if (uri != null) {
                mUri = Uri.parse(uri);
                editTextFileName.setText(FileUtils.getDisplayName(this, mUri));
            }
        }

        progressbar.setProgress(0);
        numberProgressBarArr[0] = progressbar;
        activityArr[0] = this;
        mCurrentProgress = 0;
        handler = new UpdateProgressHandler();

        mSwitchCompatSkipDuplicates.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("financisto_csv_skip_diplicates", false));
        mSwitchCompatSkipDuplicates.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("financisto_csv_skip_diplicates", b).apply();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mUri != null) {
            outState.putString(STATE_URI, mUri.toString());
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_import_csv;
    }

    @Override
    protected String getLayoutTitle() {
        if (getIntent().getStringExtra("type").equals("fingen")) {
            return getString(R.string.ent_import_csv_fingen);
        } else {
            return getString(R.string.ent_import_csv_financisto);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_import, menu);
        menu.findItem(R.id.action_go_home).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_import :
                importCSV();
                return true;
            default:
                return false;
        }
    }

    void OnSelectFileClick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "text/csv", "text/comma-separated-values"});
        startActivityForResult(intent, REQUEST_PICK_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_CSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            mUri = data.getData();
            editTextFileName.setText(FileUtils.getDisplayName(this, mUri));
        }
    }

    void importCSV() {
        if (mUri == null) {
            Toast.makeText(ActivityImportCSV.this, getString(R.string.msg_file_not_exist), Toast.LENGTH_SHORT).show();
            return;
        }
        final File tmp;
        try {
            tmp = FileUtils.copyUriToCache(this, mUri);
        } catch (IOException | SecurityException e) {
            Log.e("csv", "copy failed: " + e.getMessage());
            Toast.makeText(ActivityImportCSV.this, getString(R.string.msg_file_not_exist), Toast.LENGTH_SHORT).show();
            return;
        }

        final CsvImporter csvImporter = new CsvImporter(this, tmp.getPath(), 0, false);

        csvImporter.setmCsvImportProgressChangeListener(this);

        handler.sendMessage(handler.obtainMessage(HANDLER_OPERATION_SHOW, 0, 0));
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (getIntent().getStringExtra("type").equals("fingen")) {
                        csvImporter.loadFingenCSV();
                    } else {
                        csvImporter.loadFinancistoCSV();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    tmp.delete();
                }
            }
        });
        t.start();
    }

    @Override
    public void onProgressChange(int progress) {
//        Log.d("csv", String.format("enter listener progress = %d", progress));
        if (progress != mCurrentProgress) {
            Log.d("csv", "progress != mCurrentProgress");
            handler.sendMessage(handler.obtainMessage(HANDLER_OPERATION_UPDATE, progress, 0));
            mCurrentProgress = progress;
        }
    }

    @Override
    public void onOperationComplete(int code) {
        handler.sendMessage(handler.obtainMessage(HANDLER_OPERATION_TOAST, code, 0));
        Intent intent = new Intent(this, ActivityMain.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("action", ActivityMain.ACTION_OPEN_TRANSACTIONS_LIST);
        startActivity(intent);
    }

    @Override
    public void onOperationComplete(int code, int[] stats) {
        onOperationComplete(code);
    }

    private static class UpdateProgressHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_OPERATION_SHOW:
                    numberProgressBarArr[0].setVisibility(View.VISIBLE);
                    break;
                case HANDLER_OPERATION_UPDATE:
                    numberProgressBarArr[0].setProgress(msg.arg1);
                    break;
                case HANDLER_OPERATION_HIDE:
                    break;
                case HANDLER_OPERATION_TOAST:
                    switch (msg.arg1) {
                        case 0:
                            Toast.makeText(activityArr[0], activityArr[0].getString(R.string.msg_import_complete_ok), Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            Toast.makeText(activityArr[0], activityArr[0].getString(R.string.msg_import_complete_error_1), Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
            }
        }
    }
}
