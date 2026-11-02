package com.yoshione.fingen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Category;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.RequestCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Screens that show lists must not crash when the database has data: an account, a category with
 * a color and a transaction. Rows are removed in tearDown by DbSnapshot.
 */
@RunWith(AndroidJUnit4.class)
public class ScreenLaunchWithDataTest {

    private static final long CURRENCY_USD = 2;

    @Rule
    public final SystemDialogsRule dialogs = new SystemDialogsRule();

    private Context mContext;
    private DbSnapshot mSnapshot;
    private Activity mActivity;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        mActivity = null;

        Account account = new Account();
        account.setName("Data account " + System.nanoTime());
        account.setCabbageId(CURRENCY_USD);
        account = (Account) AccountsDAO.getInstance(mContext).createModel(account);

        Category category = new Category();
        category.setName("Data category " + System.nanoTime());
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
        ScreenTestUtils.finish(mActivity);
        mSnapshot.restore(mContext);
    }

    private void check(Class<? extends Activity> cls, Intent extras) throws Exception {
        mActivity = ScreenTestUtils.launch(cls, extras);
        ScreenTestUtils.assertContentDisplayed(mActivity);
        // List data is loaded asynchronously after the screen is resumed.
        Thread.sleep(1500);
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
    public void accounts() throws Exception {
        check(ActivityAccounts.class, null);
    }

    @Test
    public void transactions() throws Exception {
        check(ActivityTransactions.class, null);
    }

    @Test
    public void list() throws Exception {
        check(ActivityList.class, modelIntent(new Category()));
    }

    @Test
    public void references() throws Exception {
        check(ActivityReferences.class, null);
    }
}
