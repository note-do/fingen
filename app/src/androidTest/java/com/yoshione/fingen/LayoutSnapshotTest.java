package com.yoshione.fingen;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Category;
import com.yoshione.fingen.model.Template;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.RequestCodes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Snapshots (PNG + view geometry JSON) of screens that contain layouts flagged by lint MissingConstraints:
 * activity_backup, activity_edit_transaction, fragment_date_filter_settings, fragment_model_list,
 * fragment_tree_model_list, list_item_menu, list_item_summary.
 * list_item_transactions_split is not inflated by any code and has no screen.
 *
 * Tag: -Pandroid.testInstrumentationRunnerArguments.snapTag=before (default "default").
 * Output: /storage/emulated/0/Download/fingen_snapshots/<tag>/ (MediaStore, survives uninstall).
 * Pull: adb pull /storage/emulated/0/Download/fingen_snapshots/<tag> <dir>.
 */
@RunWith(AndroidJUnit4.class)
public class LayoutSnapshotTest {

    private static final String ARG_SNAP_TAG = "snapTag";
    private static final String DEFAULT_TAG = "default";
    private static final String DOWNLOAD_ROOT = "Download/fingen_snapshots/";
    private static final String DEVICE_ROOT = "/storage/emulated/0/" + DOWNLOAD_ROOT;
    private static final long CURRENCY_USD = 2;
    private static final long SETTLE_MS = 1500;

    @Rule
    public final SystemDialogsRule dialogs = new SystemDialogsRule();

    private Context mContext;
    private DbSnapshot mSnapshot;
    private String mTag;
    private Activity mActivity;
    private android.app.DialogFragment mDialog;

    @BeforeClass
    public static void clearPreviousRun() throws Exception {
        // Previous run with the same tag is replaced by this one (once per class, not per test).
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("rm -rf " + DEVICE_ROOT + resolveTag());
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mTag = resolveTag();
        mActivity = null;
        mDialog = null;

        mSnapshot = DbSnapshot.take(mContext);

        Account account = new Account();
        account.setName("Snapshot account " + System.nanoTime());
        account.setCabbageId(CURRENCY_USD);
        account = (Account) AccountsDAO.getInstance(mContext).createModel(account);

        Category category = new Category();
        category.setName("Snapshot category " + System.nanoTime());
        category.setColor(0xFF2196F3);
        category = (Category) CategoriesDAO.getInstance(mContext).createModel(category);

        Transaction transaction = new Transaction(-1);
        transaction.setTransactionType(Transaction.TRANSACTION_TYPE_EXPENSE);
        transaction.setAccountID(account.getID());
        transaction.setCategoryID(category.getID());
        transaction.setAmount(new BigDecimal("12.34"), Transaction.TRANSACTION_TYPE_EXPENSE);
        transaction.setDateTime(new Date());
        TransactionsDAO.getInstance(mContext).createModel(transaction);
    }

    @After
    public void tearDown() {
        final android.app.DialogFragment dialog = mDialog;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (dialog != null) {
                    dialog.dismissAllowingStateLoss();
                }
            }
        });
        ScreenTestUtils.finish(mActivity);
        mSnapshot.restore(mContext);
    }

    @Test
    public void mainSummary() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityMain.class, null);
        closeStartupChangelog((AppCompatActivity) mActivity);
        capture("main_summary", null);
    }

    @Test
    public void backup() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityBackup.class, null);
        capture("backup", null);
    }

    @Test
    public void editTransactionNew() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityEditTransaction.class, new Intent(FgConst.ACT_NEW_EXPENSE));
        capture("edit_transaction_new", null);
    }

    @Test
    public void dateFilterDialog() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityMain.class, null);
        final AppCompatActivity host = (AppCompatActivity) mActivity;
        closeStartupChangelog(host);
        final android.app.DialogFragment dialog = FragmentDateFilterEdit.newInstance(0, 0);
        mDialog = dialog;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                dialog.show(host.getFragmentManager(), "snapshot_dialog");
                host.getFragmentManager().executePendingTransactions();
            }
        });
        capture("date_filter_dialog", dialog);
    }

    @Test
    public void modelListTemplates() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityModelList.class, modelIntent(new Template()));
        capture("model_list_templates", null);
    }

    @Test
    public void debtsAndCredits() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityDebtsAndCredits.class, null);
        capture("debts_and_credits", null);
    }

    @Test
    public void treeListCategories() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityList.class, modelIntent(new Category()));
        capture("tree_list_categories", null);
    }

    @Test
    public void additionalMenu() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityAdditional.class, null);
        capture("additional_menu", null);
    }

    @Test
    public void referencesMenu() throws Exception {
        mActivity = ScreenTestUtils.launch(ActivityReferences.class, null);
        capture("references_menu", null);
    }

    /** ActivityMain shows the changelog dialog after start; it would cover the dialog under test. */
    private void closeStartupChangelog(final AppCompatActivity host) throws InterruptedException {
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                host.getSupportFragmentManager().executePendingTransactions();
                Fragment changelog = host.getSupportFragmentManager().findFragmentByTag("changelogdemo_dialog");
                if (changelog instanceof DialogFragment) {
                    ((DialogFragment) changelog).dismissAllowingStateLoss();
                }
                host.getSupportFragmentManager().executePendingTransactions();
            }
        });
        Thread.sleep(500);
    }

    private static String resolveTag() {
        Bundle args = InstrumentationRegistry.getArguments();
        String tag = args.getString(ARG_SNAP_TAG, DEFAULT_TAG);
        return tag.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private Intent modelIntent(Object model) {
        Intent intent = new Intent();
        intent.putExtra("showHomeButton", false);
        intent.putExtra("model", (android.os.Parcelable) model);
        intent.putExtra("requestCode", RequestCodes.REQUEST_CODE_VIEW_MODELS);
        return intent;
    }

    /**
     * Waits for data and drawing, saves screenshot and geometry of the activity window
     * (or of the dialog window when dialog != null) under <tag>_<screen>.png / .json.
     */
    private void capture(String screen, android.app.DialogFragment dialog) throws Exception {
        Thread.sleep(SETTLE_MS);
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.waitForIdle();

        final String geometry = buildGeometry(screen, dialog);
        assertNotNull("geometry is empty for " + screen, geometry);

        String baseName = mTag + "_" + screen;
        File dir = new File(mContext.getCacheDir(), "snapshots");
        dir.mkdirs();

        File png = new File(dir, baseName + ".png");
        assertTrue("takeScreenshot failed for " + screen, device.takeScreenshot(png));

        File json = new File(dir, baseName + ".json");
        writeText(json, geometry);

        publish(png, baseName + ".png", "image/png");
        publish(json, baseName + ".json", "application/json");
    }

    private String buildGeometry(final String screen, final android.app.DialogFragment dialog) {
        final String[] result = {null};
        final String[] error = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    View root = dialog != null
                            ? dialog.getDialog().getWindow().getDecorView()
                            : mActivity.getWindow().getDecorView();
                    Resources res = mActivity.getResources();
                    JSONArray views = new JSONArray();
                    collect(res, root, 0, views);

                    JSONObject doc = new JSONObject();
                    doc.put("tag", mTag);
                    doc.put("screen", screen);
                    doc.put("rootClass", root.getClass().getName());
                    doc.put("density", res.getDisplayMetrics().density);
                    doc.put("screenWidth", res.getDisplayMetrics().widthPixels);
                    doc.put("screenHeight", res.getDisplayMetrics().heightPixels);
                    doc.put("views", views);
                    result[0] = doc.toString(2);
                } catch (JSONException e) {
                    error[0] = e.toString();
                }
            }
        });
        assertTrue("geometry failed for " + screen + ": " + error[0], error[0] == null);
        return result[0];
    }

    /** Depth-first walk; only views with an id are written. */
    private static void collect(Resources res, View view, int depth, JSONArray out) throws JSONException {
        if (view.getId() != View.NO_ID) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            JSONObject item = new JSONObject();
            item.put("id", resourceName(res, view.getId()));
            item.put("idValue", String.format("0x%08x", view.getId()));
            item.put("class", view.getClass().getName());
            item.put("depth", depth);
            item.put("x", location[0]);
            item.put("y", location[1]);
            item.put("width", view.getWidth());
            item.put("height", view.getHeight());
            item.put("visibility", visibilityName(view.getVisibility()));
            out.put(item);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(res, group.getChildAt(i), depth + 1, out);
            }
        }
    }

    private static String resourceName(Resources res, int id) {
        try {
            return res.getResourceName(id);
        } catch (Resources.NotFoundException e) {
            return String.format("0x%08x", id);
        }
    }

    private static String visibilityName(int visibility) {
        switch (visibility) {
            case View.VISIBLE:
                return "VISIBLE";
            case View.INVISIBLE:
                return "INVISIBLE";
            default:
                return "GONE";
        }
    }

    private static void writeText(File file, String text) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    /** Copies a cache file to shared Downloads: the test process cannot write there directly. */
    private void publish(File src, String displayName, String mimeType) throws IOException {
        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_ROOT + mTag);
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull("MediaStore insert failed: " + displayName, uri);

        OutputStream out = resolver.openOutputStream(uri);
        assertNotNull("openOutputStream failed: " + displayName, out);
        InputStream in = new FileInputStream(src);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }

        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
    }
}
