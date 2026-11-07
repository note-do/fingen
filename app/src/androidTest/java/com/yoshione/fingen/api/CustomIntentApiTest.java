package com.yoshione.fingen.api;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.DbSnapshot;
import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Category;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * External transaction API (CustomIntentReceiver) is declared only in the debug manifest.
 * The test sends an explicit-package broadcast and checks the database.
 */
@RunWith(AndroidJUnit4.class)
public class CustomIntentApiTest {

    private static final String ACTION_CREATE_TRANSACTION = "com.yoshione.fingen.intent.action.CREATE_TRANSACTION";
    private static final long CURRENCY_USD = 2;
    private static final long WAIT_MS = 5000;
    private static final long SETTLE_MS = 1500;

    private Context mContext;
    private DbSnapshot mSnapshot;
    private String mAccountName;
    private String mCategoryName;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        mAccountName = "Api account " + System.nanoTime();
        mCategoryName = "Api category " + System.nanoTime();

        Account account = new Account();
        account.setName(mAccountName);
        account.setCabbageId(CURRENCY_USD);
        AccountsDAO.getInstance(mContext).createModel(account);

        Category category = new Category();
        category.setName(mCategoryName);
        CategoriesDAO.getInstance(mContext).createModel(category);
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
    }

    private void sendCreateTransaction(String comment) {
        Intent intent = new Intent(ACTION_CREATE_TRANSACTION);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra("account", mAccountName);
        intent.putExtra("type", "2");
        intent.putExtra("amount", "12.34");
        intent.putExtra("payee", "Api payee");
        intent.putExtra("category", mCategoryName);
        intent.putExtra("comment", comment);
        mContext.sendBroadcast(intent);
    }

    private int countByComment(String comment) {
        Cursor cursor = DBHelper.getInstance(mContext).getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + DBHelper.T_LOG_TRANSACTIONS
                        + " WHERE " + DBHelper.C_LOG_TRANSACTIONS_COMMENT + " = ?", new String[]{comment});
        try {
            cursor.moveToFirst();
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    private int waitForCount(String comment, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        int count = countByComment(comment);
        while (count == 0 && System.currentTimeMillis() < end) {
            Thread.sleep(100);
            count = countByComment(comment);
        }
        return count;
    }

    @Test
    public void createTransactionBroadcast_createsTransaction() throws Exception {
        String comment = "api-create " + System.nanoTime();

        sendCreateTransaction(comment);

        assertEquals(1, waitForCount(comment, WAIT_MS));
    }

    @Test
    public void createTransactionBroadcast_createsExactlyOneTransaction() throws Exception {
        String comment = "api-once " + System.nanoTime();

        sendCreateTransaction(comment);

        assertEquals(1, waitForCount(comment, WAIT_MS));
        // A second receiver for the same action would deliver the broadcast again.
        Thread.sleep(SETTLE_MS);
        assertEquals(1, countByComment(comment));
    }
}
