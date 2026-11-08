package com.yoshione.fingen;

import com.yoshione.fingen.databinding.ActivityBackupBinding;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.activity.OnBackPressedCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import com.yoshione.fingen.backup.BackupPassword;
import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.dropbox.UploadTask;
import com.yoshione.fingen.dropbox.UserAccountTask;
import com.yoshione.fingen.utils.BackupFolder;
import com.yoshione.fingen.utils.DebouncingOnClickListener;
import com.yoshione.fingen.interfaces.IOnComplete;
import com.yoshione.fingen.utils.DateTimeFormatter;
import com.yoshione.fingen.utils.FabMenuController;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class ActivityBackup extends ToolbarActivity {

    private static final String TAG = "ActivityBackup";
    private static final List<String> DROPBOX_SCOPES = Arrays.asList(
            "account_info.read", "files.metadata.read", "files.content.read", "files.content.write");
    private boolean mAuthInProgress = false;

    private static final int MSG_SHOW_DIALOG = 0;
    private static final int MSG_DOWNLOAD_FILE = 1;

    private static final int REQUEST_PICK_FOLDER = 1;
    private static final int REQUEST_PICK_BACKUP_FILE = 2;
    private static final String RESTORE_CACHE_PREFIX = "restore_";

    private ActivityBackupBinding mBinding;
    FloatingActionButton fabBackup;
    FloatingActionButton fabRestore;
    FloatingActionButton fabRestoreFromDropbox;
    FloatingActionButton fabMenuButtonRoot;
    TextView mTextViewBackupFolder;
    Button mButtonSelectBackupFolder;
    SwitchCompat mSwitchCompatEnablePasswordProtection;
    EditText mEditTextPassword;
    private static final long PASSWORD_SAVE_DELAY_MS = 1000;
    private boolean mPasswordDirty;
    private final Runnable mSavePasswordRunnable = this::savePassword;
    EditText mEditTextDropboxAccount;
    TextInputLayout mTextInputLayoutDropboxAccount;
    TextView mTextViewLastBackupToDropbox;
    Button mButtonLogoutFromDropbox;
    View fabBGLayout;
    LinearLayout mFabBackupLayout;
    LinearLayout mFabRestoreLayout;
    LinearLayout mFabRestoreFromDropboxLayout;

    private SharedPreferences prefs;
    private UpdateRwHandler mHandler;
    private FabMenuController mFabMenuController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) {
            // Redirected to the DB upgrade screen
            return;
        }
        mBinding = ActivityBackupBinding.bind(getContentRoot());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mFabMenuController.isFABOpen()) {
                    mFabMenuController.closeFABMenu();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        fabBackup = mBinding.fabBackup;
        fabRestore = mBinding.fabRestore;
        fabRestoreFromDropbox = mBinding.fabRestoreFromDropbox;
        fabMenuButtonRoot = mBinding.fabMenuButtonRoot;
        mTextViewBackupFolder = mBinding.textViewBackupFolder;
        mButtonSelectBackupFolder = mBinding.buttonSelectBackupFolder;
        mSwitchCompatEnablePasswordProtection = mBinding.switchCompatEnablePasswordProtection;
        mEditTextPassword = mBinding.editTextPassword;
        mEditTextDropboxAccount = mBinding.editTextDropboxAccount;
        mTextInputLayoutDropboxAccount = mBinding.textInputLayoutDropboxAccount;
        mTextViewLastBackupToDropbox = mBinding.textViewLastBackupToDropbox;
        mButtonLogoutFromDropbox = mBinding.buttonLogoutFromDropbox;
        fabBGLayout = mBinding.fabBGLayout;
        mFabBackupLayout = mBinding.fabBackupLayout;
        mFabRestoreLayout = mBinding.fabRestoreLayout;
        mFabRestoreFromDropboxLayout = mBinding.fabRestoreFromDropboxLayout;
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initFabMenu();
        initBackupFolder();
        initPasswordProtection();
        initDropbox();
        mHandler = new UpdateRwHandler(this);
        deleteRestoreTempFiles();
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_backup;
    }

    @Override
    protected String getLayoutTitle() {
        return getString(R.string.ent_backup_data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.findItem(R.id.action_go_home).setVisible(true);
        return true;

    }

    private void initBackupFolder() {
        mButtonSelectBackupFolder.setOnClickListener(new DebouncingOnClickListener(view ->
                startActivityForResult(BackupFolder.createPickFolderIntent(), REQUEST_PICK_FOLDER)));
        updateBackupFolderField();
    }

    private void updateBackupFolderField() {
        String name = BackupFolder.getFolderName(this);
        if (name.isEmpty()) {
            mTextViewBackupFolder.setText(R.string.ttl_backup_folder_not_selected);
        } else {
            mTextViewBackupFolder.setText(getString(R.string.ttl_backup_folder, name));
        }
    }

    private void deleteRestoreTempFiles() {
        File[] files = getCacheDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(RESTORE_CACHE_PREFIX)) {
                    file.delete();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_FOLDER) {
            if (BackupFolder.setFolder(this, data.getData())) {
                updateBackupFolderField();
            } else {
                Toast.makeText(this, R.string.msg_backup_folder_selection_failed, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_PICK_BACKUP_FILE) {
            restoreFromUri(data.getData());
        }
    }

    /**
     * Copies the document to the cache and goes on with the usual restore from a local file.
     */
    private void restoreFromUri(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File file = FileUtils.copyUriToCache(ActivityBackup.this, uri, RESTORE_CACHE_PREFIX, ".zip");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                DBHelper.getInstance(ActivityBackup.this).showRestoreDialog(file.getAbsolutePath(), ActivityBackup.this);
                            }
                        }
                    });
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG, "Can't read backup: " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ActivityBackup.this, R.string.msg_file_not_exist, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void initPasswordProtection() {
        mSwitchCompatEnablePasswordProtection.setChecked(BackupPassword.isProtectionEnabled(this));
        mSwitchCompatEnablePasswordProtection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked && !mEditTextPassword.getText().toString().isEmpty()) {
                    // The password is saved with a delay: save it now so it is not lost for the check below
                    mPasswordDirty = true;
                    savePassword();
                    if (!compoundButton.isChecked()) {
                        // Keystore error: savePassword already turned the switch off
                        return;
                    }
                }
                String password = BackupPassword.get(ActivityBackup.this);
                boolean hasPassword = password != null && !password.isEmpty();
                if (isChecked && !hasPassword) {
                    // protection can't be enabled without a password: keep the switch and the pref in sync
                    compoundButton.setChecked(false);
                    Toast.makeText(ActivityBackup.this, R.string.msg_backup_password_required, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, isChecked && hasPassword).apply();
            }
        });
        String savedPassword = BackupPassword.get(this);
        mEditTextPassword.setText(savedPassword == null ? "" : savedPassword);
        mEditTextPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Encrypting on every key press is wasteful: save after a pause or on leaving the screen
                mPasswordDirty = true;
                mEditTextPassword.removeCallbacks(mSavePasswordRunnable);
                mEditTextPassword.postDelayed(mSavePasswordRunnable, PASSWORD_SAVE_DELAY_MS);
            }
        });
        showPasswordWarning();
    }

    private void savePassword() {
        mEditTextPassword.removeCallbacks(mSavePasswordRunnable);
        if (!mPasswordDirty) {
            return;
        }
        mPasswordDirty = false;
        String password = mEditTextPassword.getText().toString();
        if (BackupPassword.set(this, password)) {
            if (password.isEmpty() && mSwitchCompatEnablePasswordProtection.isChecked()) {
                // the password was cleared: protection can't stay on (the listener turns the pref off)
                mSwitchCompatEnablePasswordProtection.setChecked(false);
            }
            prefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD,
                    mSwitchCompatEnablePasswordProtection.isChecked() && !password.isEmpty()).apply();
            mEditTextPassword.setError(null);
        } else {
            // Keystore error: the password is not stored, so protection can't be enabled
            prefs.edit().putBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, false).apply();
            if (mSwitchCompatEnablePasswordProtection.isChecked()) {
                mSwitchCompatEnablePasswordProtection.setChecked(false);
            }
            Toast.makeText(this, R.string.msg_backup_password_not_saved, Toast.LENGTH_LONG).show();
        }
    }

    private void showPasswordWarning() {
        if (BackupPassword.hasWarning(this)) {
            mEditTextPassword.setError(getString(R.string.msg_backup_password_lost));
            Toast.makeText(this, R.string.msg_backup_password_lost, Toast.LENGTH_LONG).show();
        }
    }

    private void initDropbox() {
        getUserAccount();
        final boolean linked = DropboxClient.isLinked(this);
        mEditTextDropboxAccount.setOnClickListener(new DebouncingOnClickListener(view -> {
            if (!linked) {
                mAuthInProgress = true;
                Auth.startOAuth2PKCE(ActivityBackup.this, getString(R.string.DROPBOX_APP_KEY),
                        DropboxClient.getRequestConfig(), DROPBOX_SCOPES);
            }
        }));
        mButtonLogoutFromDropbox.setVisibility(linked ? View.VISIBLE : View.GONE);
        mButtonLogoutFromDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final DbxClientV2 client = DropboxClient.getClient(ActivityBackup.this);
                if (client != null) {
                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                client.auth().tokenRevoke();
                            } catch (DbxException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                DropboxClient.unlink(ActivityBackup.this);
                initDropbox();
            }
        });
        initLastDropboxBackupField();
    }

    private void initLastDropboxBackupField() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        Long dateLong = preferences.getLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, 0);
        String title = getString(R.string.ttl_last_backup_to_dropbox);
        if (dateLong == 0) {
            title = String.format("%s -", title);
        } else {
            Date date = new Date(dateLong);
            DateTimeFormatter dtf = DateTimeFormatter.getInstance(this);
            title = String.format("%s %s %s", title, dtf.getDateMediumString(date), dtf.getTimeShortString(date));
        }
        mTextViewLastBackupToDropbox.setText(title);
    }

    public void saveCredential() {
        if (!mAuthInProgress) {
            return;
        }
        mAuthInProgress = false;
        DbxCredential credential = Auth.getDbxCredential(); //credential with refresh token
        if (credential != null) {
            DropboxClient.saveCredential(this, credential);
        }
    }

    @Override
    protected void onPause() {
        savePassword();
        super.onPause();
    }

    @Override
    public void onResume() {
        saveCredential();
        initDropbox();
        super.onResume();
    }

    protected void getUserAccount() {
        final SharedPreferences dropboxPrefs = DropboxClient.getPrefs(this);
        mEditTextDropboxAccount.setText(dropboxPrefs.getString(FgConst.PREF_DROPBOX_ACCOUNT, ""));
        DbxClientV2 client = DropboxClient.getClient(this);
        if (client == null) return;
        new UserAccountTask(client, new UserAccountTask.TaskDelegate() {
            @Override
            public void onAccountReceived(FullAccount account) {
                //Print account's info
                mEditTextDropboxAccount.setText(account.getEmail());
                dropboxPrefs.edit().putString(FgConst.PREF_DROPBOX_ACCOUNT, account.getEmail()).apply();
            }

            @Override
            public void onError(Exception error) {
                Log.d("User", "Error receiving account details.");
            }
        }).execute();
    }

    private void initFabMenu() {
        mFabMenuController = new FabMenuController(fabMenuButtonRoot, fabBGLayout, this, mFabBackupLayout, mFabRestoreLayout, mFabRestoreFromDropboxLayout);
        fabBackup.setOnClickListener(new DebouncingOnClickListener(v -> {
            backupDB();
            mFabMenuController.closeFABMenu();
        }));
        fabRestore.setOnClickListener(new DebouncingOnClickListener(v -> {
            restoreDB();
            mFabMenuController.closeFABMenu();
        }));
        fabRestoreFromDropbox.setOnClickListener(new DebouncingOnClickListener(v -> {
            restoreDBFromDropbox();
            mFabMenuController.closeFABMenu();
        }));
    }

    private void backupDB() {
        savePassword();
        if (BackupPassword.isBackupBlocked(this)) {
            BackupPassword.markWarning(this);
            showPasswordWarning();
            return;
        }
        DbxClientV2 client = DropboxClient.getClient(this);
        boolean folderAvailable = BackupFolder.isAvailable(this);
        if (!folderAvailable) {
            Toast.makeText(this, R.string.msg_backup_folder_unavailable, Toast.LENGTH_LONG).show();
            if (client == null) {
                return;
            }
        }
        try {
            final File zip = DBHelper.getInstance(getApplicationContext()).backupDB(true, folderAvailable);
            if (folderAvailable) {
                if (prefs.getBoolean(FgConst.PREF_BACKUP_FOLDER_UNAVAILABLE, false)) {
                    Toast.makeText(this, R.string.msg_backup_folder_unavailable, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.msg_backup_saved, Toast.LENGTH_SHORT).show();
                }
            }
            if (client != null && zip != null) {
                new UploadTask(ActivityBackup.this, client, zip, new IOnComplete() {
                    @Override
                    public void onComplete() {
                        Toast.makeText(ActivityBackup.this, "File uploaded successfully", Toast.LENGTH_SHORT).show();
                        SharedPreferences preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ActivityBackup.this);
                        preferences.edit().putLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, new Date().getTime()).apply();
                        initLastDropboxBackupField();
                        BackupFolder.cleanCache(ActivityBackup.this, zip);
                    }
                }).execute();
            } else if (zip != null) {
                BackupFolder.cleanCache(this, zip);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void restoreDB() {
        new FileSelectDialog(this).showSelectBackupDialog();
    }

    private void restoreDBFromDropbox() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                DbxClientV2 client = DropboxClient.getClient(getApplicationContext());
                List<Metadata> metadataList;
                List<MetadataItem> items = new ArrayList<>();
                try {
                    metadataList = DropboxClient.getListFiles(client);
                    for (int i = metadataList.size() - 1; i >= 0; i--) {
                        if (metadataList.get(i).getName().toLowerCase().contains(".zip")) {
                            items.add(new MetadataItem((FileMetadata) metadataList.get(i)));
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error read list of files from Dropbox");
                }
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_DIALOG, items));
            }
        });
        t.start();
    }

    private static class UpdateRwHandler extends Handler {
        WeakReference<ActivityBackup> mActivity;

        UpdateRwHandler(ActivityBackup activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ActivityBackup activity = mActivity.get();
            if (activity == null || activity.isFinishing()) return;
            switch (msg.what) {
                case MSG_SHOW_DIALOG:
                    AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
                    builderSingle.setTitle(activity.getResources().getString(R.string.ttl_select_db_file));

                    final ArrayAdapter<MetadataItem> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
                    arrayAdapter.addAll((List<MetadataItem>) msg.obj);

                    builderSingle.setNegativeButton(
                            activity.getResources().getString(android.R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                    builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ListView lw = ((AlertDialog) dialog).getListView();
                            final MetadataItem item = (MetadataItem) lw.getAdapter().getItem(which);
                            Thread thread = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    File file = new File(activity.getCacheDir(), RESTORE_CACHE_PREFIX + item.mMetadata.getName());
                                    DbxClientV2 dbxClient = DropboxClient.getClient(activity);
                                    if (dbxClient == null) {
                                        return;
                                    }
                                    try (OutputStream outputStream = new FileOutputStream(file)) {
                                        dbxClient.files().download(item.mMetadata.getPathLower(), item.mMetadata.getRev())
                                                .download(outputStream);
                                        activity.mHandler.sendMessage(activity.mHandler.obtainMessage(MSG_DOWNLOAD_FILE, file));
                                    } catch (DbxException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                            thread.start();
                        }
                    });
                    builderSingle.show();
                    break;
                case MSG_DOWNLOAD_FILE:
                    try {
                        DBHelper.getInstance(activity).showRestoreDialog(((File) msg.obj).getCanonicalPath(), activity);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }

        }
    }

    private class MetadataItem {
        private FileMetadata mMetadata;

        public MetadataItem(FileMetadata metadata) {
            mMetadata = metadata;
        }

        public String toString() {
            return mMetadata.getName();
        }
    }

    private class FileSelectDialog {
        final AppCompatActivity activity;

        FileSelectDialog(AppCompatActivity activity) {
            this.activity = activity;
        }

        void showSelectBackupDialog() {
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
            builderSingle.setTitle(activity.getResources().getString(R.string.ttl_select_db_file));

            final List<BackupFolder.Item> files = BackupFolder.listBackups(getApplicationContext());
            if (BackupFolder.getFolderUri(getApplicationContext()) != null && files.isEmpty()
                    && !BackupFolder.isAvailable(getApplicationContext())) {
                Toast.makeText(activity, R.string.msg_backup_folder_unavailable, Toast.LENGTH_LONG).show();
            }
            List<String> names = new ArrayList<>();
            for (BackupFolder.Item item : files) {
                names.add(item.name);
            }
            names.add(activity.getString(R.string.act_restore_other_file));

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
            arrayAdapter.addAll(names);

            builderSingle.setNegativeButton(
                    activity.getResources().getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });

            builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which < files.size()) {
                        restoreFromUri(files.get(which).uri);
                    } else {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
                        startActivityForResult(intent, REQUEST_PICK_BACKUP_FILE);
                    }
                }
            });
            builderSingle.show();
        }
    }
}
