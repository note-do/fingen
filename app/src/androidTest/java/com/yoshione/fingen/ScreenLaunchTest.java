package com.yoshione.fingen;

import android.app.Activity;
import android.content.Intent;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.calc.CalculatorActivity;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Category;
import com.yoshione.fingen.model.Credit;
import com.yoshione.fingen.model.Location;
import com.yoshione.fingen.utils.RequestCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Smoke tests: each Activity touched by the ViewBinding migration starts and does not crash.
 */
@RunWith(AndroidJUnit4.class)
public class ScreenLaunchTest {

    @Rule
    public final SystemDialogsRule dialogs = new SystemDialogsRule();

    private Activity mActivity;

    @Before
    public void setUp() {
        mActivity = null;
    }

    @After
    public void tearDown() {
        ScreenTestUtils.finish(mActivity);
    }

    private void check(Class<? extends Activity> cls, Intent extras) throws Exception {
        mActivity = ScreenTestUtils.launch(cls, extras);
        ScreenTestUtils.assertContentDisplayed(mActivity);
    }

    private Intent modelIntent(Object model) {
        Intent intent = new Intent();
        intent.putExtra("showHomeButton", false);
        intent.putExtra("model", (android.os.Parcelable) model);
        intent.putExtra("requestCode", RequestCodes.REQUEST_CODE_VIEW_MODELS);
        return intent;
    }

    @Test
    public void main() throws Exception {
        check(ActivityMain.class, null);
    }

    @Test
    public void about() throws Exception {
        check(ActivityAbout.class, null);
    }

    @Test
    public void additional() throws Exception {
        check(ActivityAdditional.class, null);
    }

    @Test
    public void backup() throws Exception {
        check(ActivityBackup.class, null);
    }

    @Test
    public void budget() throws Exception {
        check(ActivityBudget.class, null);
    }

    @Test
    public void debtsAndCredits() throws Exception {
        check(ActivityDebtsAndCredits.class, null);
    }

    @Test
    public void editAccount() throws Exception {
        check(ActivityEditAccount.class, new Intent().putExtra("account", new Account()));
    }

    @Test
    public void editCredit() throws Exception {
        check(ActivityEditCredit.class, new Intent().putExtra("credit", new Credit()));
    }

    @Test
    public void editLocation() throws Exception {
        check(ActivityEditLocation2.class, new Intent().putExtra("location", new Location()));
    }

    @Test
    public void editTransactionNew() throws Exception {
        check(ActivityEditTransaction.class, new Intent(FgConst.ACT_NEW_EXPENSE));
    }

    @Test
    public void exportCsv() throws Exception {
        check(ActivityExportCSV.class, null);
    }

    @Test
    public void importCsv() throws Exception {
        check(ActivityImportCSV.class, new Intent().putExtra("type", "fingen"));
    }

    @Test
    public void importCsvAdvanced() throws Exception {
        check(ActivityImportCSVAdvanced.class, null);
    }

    @Test
    public void importSms() throws Exception {
        check(ActivityImportSms.class, null);
    }

    @Test
    public void list() throws Exception {
        check(ActivityList.class, modelIntent(new Category()));
    }

    @Test
    public void modelList() throws Exception {
        check(ActivityModelList.class, modelIntent(new Category()));
    }

    @Test
    public void references() throws Exception {
        check(ActivityReferences.class, null);
    }

    @Test
    public void reports() throws Exception {
        check(ActivityReports.class,
                new Intent().putParcelableArrayListExtra("filter_list", new ArrayList<AbstractFilter>()));
    }

    @Test
    public void calculator() throws Exception {
        check(CalculatorActivity.class, null);
    }

    @Test
    public void accounts() throws Exception {
        check(ActivityAccounts.class, null);
    }

    @Test
    public void transactions() throws Exception {
        check(ActivityTransactions.class, null);
    }

    @Test
    public void smsList() throws Exception {
        check(ActivitySmsList.class, null);
    }
}
