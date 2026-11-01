package com.yoshione.fingen;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.DBHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class TransactionsDAOTest {

    private static final long CURRENCY_USD = 2;

    private Context mContext;
    private TransactionsDAO mDao;
    private long mAccountId;

    private DbSnapshot mSnapshot;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        mDao = TransactionsDAO.getInstance(mContext);
        mAccountId = insertAccount();
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
    }

    /**
     * Inserts an account directly, so transaction tests do not depend on account creation.
     */
    private long insertAccount() {
        ContentValues cv = new ContentValues();
        cv.put("Name", "Tx account " + System.nanoTime());
        cv.put("Type", 0);
        cv.put("Currency", CURRENCY_USD);
        cv.put("StartBalance", 0.0);
        cv.put("IsClosed", 0);
        cv.put("CustomOrder", 0);
        cv.put("Deleted", 0);
        cv.put("Dirty", 0);
        return DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("ref_Accounts", null, cv);
    }

    private Transaction newExpense(BigDecimal amount) {
        Transaction transaction = new Transaction(-1);
        transaction.setTransactionType(Transaction.TRANSACTION_TYPE_EXPENSE);
        transaction.setAccountID(mAccountId);
        transaction.setAmount(amount, Transaction.TRANSACTION_TYPE_EXPENSE);
        transaction.setDateTime(new Date());
        return transaction;
    }

    @Test
    public void createExpense_isReadBackById() throws Exception {
        Transaction created = (Transaction) mDao.createModel(newExpense(new BigDecimal("150.00")));

        Transaction stored = mDao.getTransactionByID(created.getID());
        assertNotNull(stored);
        // Expenses are returned with a negative sign, see Transaction.getAmount().
        assertEquals(0, stored.getAmount().compareTo(new BigDecimal("-150.00")));
        assertEquals(mAccountId, stored.getAccountID());
    }

    @Test
    public void createExpense_isListed() throws Exception {
        Transaction created = (Transaction) mDao.createModel(newExpense(new BigDecimal("20.00")));

        List<Transaction> transactions = mDao.getAllTransactions();
        boolean found = false;
        for (Transaction t : transactions) {
            if (t.getID() == created.getID()) {
                found = true;
            }
        }
        assertTrue("transaction must be in the list", found);
    }

    @Test
    public void deleteExpense_isRemoved() throws Exception {
        Transaction created = (Transaction) mDao.createModel(newExpense(new BigDecimal("30.00")));

        mDao.deleteModel(created, false, mContext);

        Transaction stored = mDao.getTransactionByID(created.getID());
        assertTrue("deleted transaction must not be found", stored == null || stored.getID() < 0);
    }
}
