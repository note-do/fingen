package com.yoshione.fingen;

import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.databinding.ActivityExportCsvBinding;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import com.google.android.material.textfield.TextInputLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.yoshione.fingen.csv.CsvImporter;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.interfaces.IProgressEventsListener;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActivityExportCSV extends ToolbarActivity implements IProgressEventsListener {

    private static final int REQUEST_CREATE_CSV = 1;
    private final static int HANDLER_OPERATION_HIDE = 0;
    private final static int HANDLER_OPERATION_SHOW = 1;
    private final static int HANDLER_OPERATION_UPDATE = 2;
    private final static int HANDLER_OPERATION_TOAST = 3;
    private static final ProgressBar[] numberProgressBarArr = new ProgressBar[]{null};
    private static final Activity[] activityArr = new Activity[]{null};
    private ActivityExportCsvBinding mBinding;
    ProgressBar progressbar;
    UpdateProgressHandler handler;
    EditText mEditTextFileName;
    TextInputLayout mTextInputLayoutFileName;
    CheckBox mCheckBoxExportProducts;
    private int mCurrentProgress;

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_export_csv;
    }

    @Override
    protected String getLayoutTitle() {
        return getString(R.string.ent_export_data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityExportCsvBinding.bind(getContentRoot());
        progressbar = mBinding.progressbar;
        mEditTextFileName = mBinding.editTextFileName;
        mTextInputLayoutFileName = mBinding.textInputLayoutFileName;
        mCheckBoxExportProducts = mBinding.checkBoxExportProducts;

        progressbar.setProgress(0);
        numberProgressBarArr[0] = progressbar;
        activityArr[0] = this;
        mCurrentProgress = 0;
        handler = new UpdateProgressHandler();

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ActivityExportCSV.this);

        mCheckBoxExportProducts.setChecked(preferences.getBoolean("export_products", false));
        mCheckBoxExportProducts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                preferences.edit().putBoolean("export_products", b).apply();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_export, menu);
        menu.findItem(R.id.action_go_home).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_export:
                selectExportFile();
                return true;
            default:
                return false;
        }
    }

    void selectExportFile() {
        String fn = mEditTextFileName.getText().toString().trim();

        if (fn.isEmpty()) {
            mTextInputLayoutFileName.setError(getString(R.string.err_set_file_name));
            return;
        }

        if (!fn.toLowerCase().contains(".csv")) {
            fn = fn + ".csv";
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, fn);
        startActivityForResult(intent, REQUEST_CREATE_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CREATE_CSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            exportCSV(data.getData());
        }
    }

    void exportCSV(final Uri uri) {
        final File tmp;
        try {
            tmp = File.createTempFile("csv_export", ".csv", getCacheDir());
        } catch (IOException e) {
            Log.e("csv", "temp file failed: " + e.getMessage());
            Toast.makeText(this, getString(R.string.msg_export_complete_error_1), Toast.LENGTH_SHORT).show();
            return;
        }

        final CsvImporter csvImporter = new CsvImporter(this, tmp.getPath(), 0, true);

        // Completion is reported only after the temp file is copied to the chosen document
        final int[] resultCode = new int[]{IProgressEventsListener.CODE_OK};
        csvImporter.setmCsvImportProgressChangeListener(new IProgressEventsListener() {
            @Override
            public void onProgressChange(int progress) {
                ActivityExportCSV.this.onProgressChange(progress);
            }

            @Override
            public void onOperationComplete(int code) {
                if (code != IProgressEventsListener.CODE_OK) {
                    resultCode[0] = code;
                }
            }

            @Override
            public void onOperationComplete(int code, int[] stats) {
                onOperationComplete(code);
            }
        });

        final boolean exportProducts = mCheckBoxExportProducts.isChecked();
        handler.sendMessage(handler.obtainMessage(HANDLER_OPERATION_SHOW, 0, 0));
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                List<Transaction> transactions;
                try {
                    transactions = getIntent().getParcelableArrayListExtra("transactions");
                    if (transactions == null) {
                        transactions = TransactionsDAO.getInstance(ActivityExportCSV.this).getAllTransactions();
                    }
                } catch (Exception e) {
                    transactions = new ArrayList<>();
                }
                try {
                    csvImporter.saveCSV(transactions, exportProducts);
                    if (resultCode[0] == IProgressEventsListener.CODE_OK) {
                        FileUtils.copyFileToUri(ActivityExportCSV.this, tmp, uri);
                    }
                } catch (IOException | SecurityException e) {
                    Log.e("csv", "copy failed: " + e.getMessage());
                    resultCode[0] = IProgressEventsListener.CODE_ERROR;
                } finally {
                    tmp.delete();
                }
                ActivityExportCSV.this.onOperationComplete(resultCode[0]);
            }
        });
        t.start();
    }

    @Override
    public void onProgressChange(int progress) {
//        Log.d("csv", String.format("enter listener progress = %d", progress));
        if (progress != mCurrentProgress) {
//            Log.d("csv", "progress != mCurrentProgress");
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
                            Toast.makeText(activityArr[0], activityArr[0].getString(R.string.msg_export_complete_ok), Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            Toast.makeText(activityArr[0], activityArr[0].getString(R.string.msg_export_complete_error_1), Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
            }
        }
    }
}
