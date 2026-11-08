package com.yoshione.fingen;

import android.content.ContentValues;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.classes.ListSumsByCabbage;
import com.yoshione.fingen.classes.SumsByCabbage;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.filters.FilterListHelper;
import com.yoshione.fingen.model.Category;
import com.yoshione.fingen.model.Transaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Deleted rows of log_Products must not affect sums, plan/fact and search. A transaction whose product rows
 * are all deleted is counted with its own amount.
 */
@RunWith(AndroidJUnit4.class)
public class DeletedProductsTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private static final long CURRENCY_USD = 2;

    private Context mContext;
    private DbSnapshot mSnapshot;
    private long mAccountId;
    private long mCategoryLive;
    private long mCategoryDead;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        android.database.sqlite.SQLiteDatabase db = DBHelper.getInstance(mContext).getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("Name", "DelProd account " + System.nanoTime());
        cv.put("Type", 0);
        cv.put("Currency", CURRENCY_USD);
        cv.put("StartBalance", 0.0);
        cv.put("IsClosed", 0);
        cv.put("CustomOrder", 0);
        cv.put("Deleted", 0);
        cv.put("Dirty", 0);
        mAccountId = db.insertOrThrow("ref_Accounts", null, cv);
        mCategoryLive = insertCategory("zzlive" + System.nanoTime());
        mCategoryDead = insertCategory("zzdead" + System.nanoTime());
    }

    @After
    public void tearDown() {
        // DbSnapshot does not clean log_Running_Balance (no _id column)
        DBHelper.getInstance(mContext).getWritableDatabase()
                .delete("log_Running_Balance", "AccountID = ?", new String[]{String.valueOf(mAccountId)});
        mSnapshot.restore(mContext);
    }

    private long insertCategory(String name) {
        ContentValues cv = new ContentValues();
        cv.put("FBID", "");
        cv.put("TS", -1);
        cv.put("Deleted", 0);
        cv.put("Dirty", 0);
        cv.put("LastEdited", "");
        cv.put("Name", name);
        cv.put("ParentID", -1);
        cv.put("OrderNumber", 0);
        cv.put("Sign", 0);
        cv.put("FullName", name);
        cv.put("SearchString", name);
        return DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("ref_Categories", null, cv);
    }

    private long insertTransaction(double amount, long category, boolean split) {
        ContentValues cv = new ContentValues();
        cv.put("FBID", "");
        cv.put("TS", -1);
        cv.put("Deleted", 0);
        cv.put("Dirty", 0);
        cv.put("LastEdited", "");
        cv.put("DateTime", new Date().getTime());
        cv.put("SrcAccount", mAccountId);
        cv.put("DestAccount", -1);
        cv.put("Amount", amount);
        cv.put("Category", category);
        cv.put("Split", split ? 1 : 0);
        cv.put("ExchangeRate", 1.0);
        cv.put("AutoCreated", 0);
        return DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("log_Transactions", null, cv);
    }

    private void insertProduct(long transactionId, long categoryId, double price, boolean deleted) {
        ContentValues cv = new ContentValues();
        cv.put("FBID", "");
        cv.put("TS", -1);
        cv.put("Deleted", deleted ? 1 : 0);
        cv.put("Dirty", 0);
        cv.put("LastEdited", "");
        cv.put("TransactionID", transactionId);
        cv.put("CategoryID", categoryId);
        cv.put("ProjectID", -1);
        cv.put("Price", price);
        cv.put("Quantity", 1.0);
        DBHelper.getInstance(mContext).getWritableDatabase().insertOrThrow("log_Products", null, cv);
    }

    private BigDecimal outSum() {
        FilterListHelper helper = new FilterListHelper(new ArrayList<AbstractFilter>(), "", mContext);
        ListSumsByCabbage sums = TransactionsDAO.getInstance(mContext).getGroupedSums(helper, false, null, mContext);
        SumsByCabbage usd = sums.getSumsByCabbageId(CURRENCY_USD);
        return usd.getOutTrSum();
    }

    private BigDecimal categoryFact(long categoryId) {
        Calendar c = Calendar.getInstance();
        List<Category> categories = CategoriesDAO.getInstance(mContext)
                .getAllCategoriesWithPlanFact(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
        for (Category category : categories) {
            if (category.getID() == categoryId) {
                SumsByCabbage sums = category.getBudget().getmSums().getSumsByCabbageId(CURRENCY_USD);
                return sums.getOutTrSum();
            }
        }
        throw new AssertionError("category not found: " + categoryId);
    }

    private List<Transaction> search(String text) {
        FilterListHelper helper = new FilterListHelper(new ArrayList<AbstractFilter>(), text, mContext);
        return TransactionsDAO.getInstance(mContext).getRangeTransactionsRx(0, 100, helper, mContext).blockingGet();
    }

    @Test
    public void splitWithDeletedLine_sumsAndPlanFactIgnoreDeletedLine() {
        BigDecimal before = outSum();
        long id = insertTransaction(-100.0, -1, true);
        insertProduct(id, mCategoryLive, -60.0, false);
        insertProduct(id, mCategoryDead, -40.0, true);

        assertEquals(0, outSum().subtract(before).compareTo(new BigDecimal("-60")));
        assertEquals(0, categoryFact(mCategoryLive).compareTo(new BigDecimal("-60")));
        assertEquals(0, categoryFact(mCategoryDead).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void splitWithTwoLiveLines_sumIsNotDuplicated() {
        BigDecimal before = outSum();
        long id = insertTransaction(-100.0, -1, true);
        insertProduct(id, mCategoryLive, -60.0, false);
        insertProduct(id, mCategoryLive, -40.0, false);

        assertEquals(0, outSum().subtract(before).compareTo(new BigDecimal("-100")));
        assertEquals(0, categoryFact(mCategoryLive).compareTo(new BigDecimal("-100")));
    }

    @Test
    public void allLinesDeleted_transactionCountedWithOwnAmount() {
        BigDecimal before = outSum();
        long id = insertTransaction(-70.0, mCategoryLive, true);
        insertProduct(id, mCategoryDead, -70.0, true);

        assertEquals(0, outSum().subtract(before).compareTo(new BigDecimal("-70")));
        assertEquals(0, categoryFact(mCategoryLive).compareTo(new BigDecimal("-70")));
        assertEquals(0, categoryFact(mCategoryDead).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void search_ignoresCategoryOfDeletedLine() {
        long id = insertTransaction(-100.0, -1, true);
        insertProduct(id, mCategoryLive, -60.0, false);
        insertProduct(id, mCategoryDead, -40.0, true);

        List<Transaction> byDead = search("zzdead");
        List<Transaction> byLive = search("zzlive");

        for (Transaction t : byDead) {
            assertTrue("deleted line category must not match", t.getID() != id);
        }
        boolean found = false;
        for (Transaction t : byLive) {
            found |= t.getID() == id;
        }
        assertTrue("live line category must match", found);
    }
}
