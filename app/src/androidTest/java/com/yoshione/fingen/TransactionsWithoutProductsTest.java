package com.yoshione.fingen;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.classes.ListSumsByCabbage;
import com.yoshione.fingen.classes.SumsByCabbage;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.filters.FilterListHelper;
import com.yoshione.fingen.model.Transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * A transaction without a row in log_Products (inserted by SQL, not by the app) must be counted in sums
 * with its own amount.
 */
@RunWith(AndroidJUnit4.class)
public class TransactionsWithoutProductsTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private static final long CURRENCY_USD = 2;

    private Context mContext;
    private DbSnapshot mSnapshot;
    private long mAccountId;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        ContentValues cv = new ContentValues();
        cv.put("Name", "NoProducts account " + System.nanoTime());
        cv.put("Type", 0);
        cv.put("CurrencyId", CURRENCY_USD);
        cv.put("StartBalance", 0);
        cv.put("IsClosed", 0);
        cv.put("OrderNumber", 0);
        cv.put("Deleted", 0);
        mAccountId = DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("ref_Accounts", null, cv);
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
    }

    private BigDecimal outSum() {
        FilterListHelper helper = new FilterListHelper(new ArrayList<AbstractFilter>(), "", mContext);
        ListSumsByCabbage sums = TransactionsDAO.getInstance(mContext).getGroupedSums(helper, false, null, mContext);
        SumsByCabbage usd = sums.getSumsByCabbageId(CURRENCY_USD);
        return usd.getOutTrSum();
    }

    private long insertTransactionWithoutProducts(double amount) {
        ContentValues cv = new ContentValues();
        cv.put("Deleted", 0);
        cv.put("DateTime", new Date().getTime());
        cv.put("SrcAccountId", mAccountId);
        cv.putNull("DestAccountId");
        // amounts are minor units of the account currency (2 digits)
        cv.put("Amount", Math.round(amount * 100));
        cv.put("ExchangeRate", 1000000);
        cv.put("IsAutoCreated", 0);
        return DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("log_Transactions", null, cv);
    }

    @Test
    public void transactionWithoutProducts_isCountedInSums() throws Exception {
        BigDecimal before = outSum();
        long id = insertTransactionWithoutProducts(-70.0);

        BigDecimal after = outSum();

        assertEquals("no product row expected", 0, countProducts(id));
        assertEquals(0, after.subtract(before).compareTo(new BigDecimal("-70")));
    }

    @Test
    public void transactionWithAndWithoutProducts_bothCounted() throws Exception {
        BigDecimal before = outSum();
        Transaction t = new Transaction(-1);
        t.setTransactionType(Transaction.TRANSACTION_TYPE_EXPENSE);
        t.setAccountID(mAccountId);
        t.setAmount(new BigDecimal("150.00"), Transaction.TRANSACTION_TYPE_EXPENSE);
        t.setDateTime(new Date());
        TransactionsDAO.getInstance(mContext).createModel(t);
        insertTransactionWithoutProducts(-70.0);

        BigDecimal after = outSum();

        assertEquals(0, after.subtract(before).compareTo(new BigDecimal("-220")));
    }

    private int countProducts(long transactionId) {
        android.database.Cursor c = DBHelper.getInstance(mContext).getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM log_Products WHERE TransactionId = ?", new String[]{String.valueOf(transactionId)});
        try {
            c.moveToFirst();
            return c.getInt(0);
        } finally {
            c.close();
        }
    }
}
