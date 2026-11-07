package com.yoshione.fingen.dropbox;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.yoshione.fingen.R;
import com.yoshione.fingen.interfaces.IOnComplete;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by slv on 01.11.2016.
 * a
 */

public class UploadTask extends AsyncTask {

    private DbxClientV2 dbxClient;
    private File file;
    private IOnComplete mOnComplete;
    private Context mContext;
    private Exception mError;

    public UploadTask(Context context, DbxClientV2 dbxClient, File file, IOnComplete onComplete) {
        mContext = context.getApplicationContext();
        this.dbxClient = dbxClient;
        this.file = file;
        mOnComplete = onComplete;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        // Upload to Dropbox
        try (InputStream inputStream = new FileInputStream(file)) {
            dbxClient.files().uploadBuilder("/" + file.getName()) //Path in the user's Dropbox to save the file.
                    .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                    .uploadAndFinish(inputStream);
            Log.d("Upload Status", "Success");
        } catch (DbxException | IOException e) {
            e.printStackTrace();
            mError = e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);
        if (mError != null) {
            String message = mError.getMessage() != null ? mError.getMessage() : mError.toString();
            Toast.makeText(mContext, mContext.getString(R.string.msg_dropbox_upload_failed, message), Toast.LENGTH_LONG).show();
        } else if (mOnComplete != null) {
            mOnComplete.onComplete();
        }
    }
}
