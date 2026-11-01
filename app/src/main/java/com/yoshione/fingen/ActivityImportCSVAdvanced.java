package com.yoshione.fingen;

import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.databinding.ActivityImportCsvAdvancedBinding;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import com.google.android.material.textfield.TextInputLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SwitchCompat;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yoshione.fingen.adapter.AdapterColumnIndex;
import com.yoshione.fingen.interfaces.IProgressEventsListener;
import com.yoshione.fingen.model.EntityToFieldLink;
import com.yoshione.fingen.csv.CsvImporter;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.utils.IconGenerator;
import com.yoshione.fingen.utils.ImportParams;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ActivityImportCSVAdvanced extends ToolbarActivity implements IProgressEventsListener {

    private static final int REQUEST_PICK_CSV = 1;
    private static final String STATE_URI = "csv_uri";
    private final static int HANDLER_OPERATION_HIDE = 0;
    private final static int HANDLER_OPERATION_SHOW = 1;
    private final static int HANDLER_OPERATION_UPDATE = 2;
    private final static int HANDLER_OPERATION_TOAST = 3;
    private static final ProgressBar[] ProgressBarArr = new ProgressBar[]{null};
    private static final Activity[] activityArr = new Activity[]{null};
    private ActivityImportCsvAdvancedBinding mBinding;
    EditText editTextFileName;
    ProgressBar progressbar;
    RecyclerView mRecyclerView;
    TextView mTextView3;
    LinearLayout mLayoutRW;
    EditText mTextViewSkipLines;
    ImageButton mImageButtonDec;
    ImageButton mImageButtonInc;
    TextInputLayout mTextInputLayoutLines;
    SwitchCompat mSwitchCompatSkipDuplicates;
    UpdateProgressHandler handler;
    private int mCurrentProgress;
    private int mSkipLines = 0;

    private AdapterColumnIndex mAdapterColumnIndex;

    private Uri mUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityImportCsvAdvancedBinding.bind(getContentRoot());
        editTextFileName = findViewById(R.id.editTextFileName);
        progressbar = mBinding.progressbar;
        mRecyclerView = findViewById(R.id.recycler_view);
        mTextView3 = mBinding.textView3;
        mLayoutRW = mBinding.layoutRW;
        mTextViewSkipLines = mBinding.textViewSkipLines;
        mImageButtonDec = mBinding.imageButtonDec;
        mImageButtonInc = mBinding.imageButtonInc;
        mTextInputLayoutLines = mBinding.textInputLayoutLines;
        mSwitchCompatSkipDuplicates = mBinding.switchCompatSkipDuplicates;
        mBinding.imageButtonInc.setOnClickListener(new DebouncingOnClickListener(v -> OnChangeSkipLinesClick(v)));
        mBinding.imageButtonDec.setOnClickListener(new DebouncingOnClickListener(v -> OnChangeSkipLinesClick(v)));
        findViewById(R.id.editTextFileName).setOnClickListener(new DebouncingOnClickListener(v -> OnSelectFileClick()));

        progressbar.setProgress(0);
        mCurrentProgress = 0;
        handler = new UpdateProgressHandler();
        ProgressBarArr[0] = progressbar;
        activityArr[0] = this;

        editTextFileName.setInputType(InputType.TYPE_NULL);

        mSwitchCompatSkipDuplicates.setChecked(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("custom_csv_skip_diplicates", false));
        mSwitchCompatSkipDuplicates.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("custom_csv_skip_diplicates", b).apply();
            }
        });

        mImageButtonDec.setImageDrawable(IconGenerator.getExpandIndicatorIcon(true, this));
        mImageButtonInc.setImageDrawable(IconGenerator.getExpandIndicatorIcon(false, this));
        updateSkipLinesField();

        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString(STATE_URI);
            if (uri != null) {
                mUri = Uri.parse(uri);
                initColumns();
            }
        }
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
        return R.layout.activity_import_csv_advanced;
    }

    @Override
    protected String getLayoutTitle() {
        return getString(R.string.ent_import_csv_advanced);
    }

    private void updateSkipLinesField() {
        mTextViewSkipLines.setText(String.valueOf(mSkipLines));
    }

    void OnChangeSkipLinesClick(View view) {
        if (mUri == null) return;
        switch (view.getId()) {
            case R.id.imageButtonInc:
                mSkipLines++;
                break;
            case R.id.imageButtonDec:
                if (mSkipLines > 0) {
                    mSkipLines--;
                }
                break;
        }
        updateSkipLinesField();
        initColumns();
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
            initColumns();
        }
    }

    void initColumns() {
        if (mUri == null) return;
        editTextFileName.setText(FileUtils.getDisplayName(this, mUri));
        File tmp = null;
        List<String> columns = new ArrayList<>();
        columns.add(0, "-");
        try {
            tmp = FileUtils.copyUriToCache(this, mUri);
            CsvImporter csvImporter = new CsvImporter(getApplicationContext(), tmp.getPath(), mSkipLines, false);
            columns.addAll(csvImporter.loadColumnsFromCSV());
        } catch (IOException | SecurityException e) {
            Log.e("csv", "copy failed: " + e.getMessage());
            Toast.makeText(this, getString(R.string.msg_file_not_exist), Toast.LENGTH_SHORT).show();
            return;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        List<EntityToFieldLink> entityToFieldLinkList = new ArrayList<>();
        for (int i : EntityToFieldLink.ENTITY_TYPES) {
            entityToFieldLinkList.add(new EntityToFieldLink(i));
        }

        mAdapterColumnIndex = new AdapterColumnIndex(entityToFieldLinkList, columns, this);
        mRecyclerView.setAdapter(mAdapterColumnIndex);
        mAdapterColumnIndex.notifyDataSetChanged();
        mLayoutRW.setVisibility(View.VISIBLE);
    }

    void importCSV() {
        if (mUri == null || mAdapterColumnIndex == null) return;
        final File tmp;
        try {
            tmp = FileUtils.copyUriToCache(this, mUri);
        } catch (IOException | SecurityException e) {
            Log.e("csv", "copy failed: " + e.getMessage());
            Toast.makeText(ActivityImportCSVAdvanced.this, getString(R.string.msg_file_not_exist), Toast.LENGTH_SHORT).show();
            return;
        }
        final CsvImporter csvImporter = new CsvImporter(getApplicationContext(), tmp.getPath(), mSkipLines, false);

        csvImporter.setmCsvImportProgressChangeListener(this);

        final ImportParams importParams = mAdapterColumnIndex.getImportParams();

        importParams.setDateFormat(csvImporter.detectDateFormat(importParams.date), csvImporter.detectTimeFormat(importParams.time));

        handler.sendMessage(handler.obtainMessage(HANDLER_OPERATION_SHOW, 0, 0));
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    csvImporter.loadCustomCSV(importParams);
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
                    ProgressBarArr[0].setVisibility(View.VISIBLE);
                    break;
                case HANDLER_OPERATION_UPDATE:
                    ProgressBarArr[0].setProgress(msg.arg1);
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
