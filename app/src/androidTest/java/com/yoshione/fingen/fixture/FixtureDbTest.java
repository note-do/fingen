package com.yoshione.fingen.fixture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.classes.ListSumsByCabbage;
import com.yoshione.fingen.classes.SumsByCabbage;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.filters.DateRangeFilter;
import com.yoshione.fingen.filters.FilterListHelper;
import com.yoshione.fingen.model.SummaryItem;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests of the fixture DB. Every test replaces the real fingen.db of the app under test, the data
 * is not restored afterwards. Not part of the main run (package com.yoshione.fingen.fixture, see
 * CLAUDE.md, "Тестовые фикстуры").
 */
@RunWith(AndroidJUnit4.class)
public class FixtureDbTest {

    @Test
    public void copyFixture_allVariants_matchTheirSpec() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        for (String name : FixtureDb.VARIANTS) {
            FixtureDb.copyFixture(context, name);
            SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
            assertEquals(name, DBHelper.DATABASE_VERSION, db.getVersion());
            // counts, links and products; the dates are those of the day the variant was built
            FixtureDb.verify(context, FixtureDb.variantSpec(name));
        }
    }

    @Test
    public void copyFixture_summary_periodsHaveDataOnTheDayItWasBuilt() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.copyFixture(context, FixtureDb.VARIANT_SUMMARY);
        // the newest transaction of the variant is "today" of the build day
        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        long newest = count(db, "SELECT MAX(DateTime) FROM log_Transactions");
        Calendar built = Calendar.getInstance();
        built.setTimeInMillis(newest);
        Calendar now = Calendar.getInstance();
        Assume.assumeTrue("the variant was built on another day, periods are relative to the build day",
                built.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                        && built.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR));
        FixtureDb.verifySummaryPeriods(context);
    }

    @Test
    public void build_summarySpec_everySummaryPeriodHasIncomeAndExpense() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.summary();
        FixtureDb.build(context, spec); // also runs verify and verifySummaryPeriods

        long[][] counts = FixtureDb.summaryPeriodCounts(context);
        assertEquals(FixtureDb.SUMMARY_PERIOD_NAMES.length, counts.length);
        for (int p = 0; p < counts.length; p++) {
            assertTrue(FixtureDb.SUMMARY_PERIOD_NAMES[p] + " incomes", counts[p][0] > 0);
            assertTrue(FixtureDb.SUMMARY_PERIOD_NAMES[p] + " expenses", counts[p][1] > 0);
        }

        // the same through the app: the sums that FragmentSummary shows
        int[][] periods = {
                {DateRangeFilter.DATE_RANGE_DAY, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
                {DateRangeFilter.DATE_RANGE_DAY, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
                {DateRangeFilter.DATE_RANGE_WEEK, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
                {DateRangeFilter.DATE_RANGE_WEEK, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
                {DateRangeFilter.DATE_RANGE_MONTH, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
                {DateRangeFilter.DATE_RANGE_MONTH, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
                {DateRangeFilter.DATE_RANGE_YEAR, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
                {DateRangeFilter.DATE_RANGE_YEAR, DateRangeFilter.DATE_RANGE_MODIFIER_LAST}};
        List<SummaryItem> items = new ArrayList<>();
        for (int p = 0; p < periods.length; p++) {
            items.add(new SummaryItem(p + 1, periods[p][0], periods[p][1], new ListSumsByCabbage(),
                    FixtureDb.SUMMARY_PERIOD_NAMES[p]));
        }
        TransactionsDAO.getInstance(context).getSummaryGroupedSumsRx(items,
                new FilterListHelper(new ArrayList<AbstractFilter>(), "", context), context).blockingGet();
        for (SummaryItem item : items) {
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (SumsByCabbage sums : item.getListSumsByCabbage().getmList()) {
                income = income.add(sums.getInTrSum());
                expense = expense.add(sums.getOutTrSum());
            }
            assertTrue(item.getName() + " income " + income, income.signum() > 0);
            assertTrue(item.getName() + " expense " + expense, expense.signum() < 0);
        }
    }

    @Test
    public void build_datesGoBackFromTheCurrentDay() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        FixtureDb.build(context, spec);

        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(spec.anchorMillis);
        day.set(Calendar.HOUR_OF_DAY, 23);
        day.set(Calendar.MINUTE, 59);
        long endOfAnchorDay = day.getTimeInMillis();
        day.add(Calendar.DAY_OF_MONTH, -spec.spanDays - 1);
        long beforeSpan = day.getTimeInMillis();

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(0, count(db, "SELECT COUNT(*) FROM log_Transactions WHERE DateTime > " + endOfAnchorDay
                + " OR DateTime < " + beforeSpan));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM log_Incoming_SMS WHERE DateTime > " + endOfAnchorDay));
    }

    @Test
    public void build_defaultSpec_countsMatchAndVerifyPasses() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = new FixtureDb.Spec();
        FixtureDb.build(context, spec);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(DBHelper.DATABASE_VERSION, db.getVersion());
        assertEquals(spec.accounts, count(db, "SELECT COUNT(*) FROM ref_Accounts"));
        assertEquals(spec.payees, count(db, "SELECT COUNT(*) FROM ref_Payees"));
        assertEquals(spec.transactions, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(spec.deletedTransactions, count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted > 0"));
        assertEquals(spec.budgets, count(db, "SELECT COUNT(*) FROM ref_budget"));
        assertEquals(spec.templates, count(db, "SELECT COUNT(*) FROM log_Templates"));
        assertEquals(spec.markers, count(db, "SELECT COUNT(*) FROM ref_Sms_Parser_Patterns"));
        assertEquals(spec.incomingSms, count(db, "SELECT COUNT(*) FROM log_Incoming_SMS"));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM log_Transactions t WHERE NOT EXISTS ("
                + "SELECT 1 FROM log_Products p WHERE p.TransactionID = t._id AND p.ProductID = 0)"));
        assertTrue(count(db, "SELECT COUNT(*) FROM log_Running_Balance") > 0);
        FixtureDb.verify(context, spec);
    }

    @Test
    public void build_smallSpec_countIsConfigurable() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        assertEquals(5, spec.transactions);
        assertEquals(2, spec.accounts);
        FixtureDb.build(context, spec);

        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        assertEquals(2, count(db, "SELECT COUNT(*) FROM ref_Accounts"));
        assertEquals(5, count(db, "SELECT COUNT(*) FROM log_Transactions"));
        assertEquals(1, count(db, "SELECT COUNT(*) FROM log_Transactions WHERE Deleted > 0"));
        FixtureDb.verify(context, spec);
    }

    @Test
    public void build_sameSeed_sameData_differentSeed_differentData() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        FixtureDb.build(context, spec);
        String first = signature(context);
        FixtureDb.build(context, spec);
        assertEquals(first, signature(context));
        spec.seed = 99;
        FixtureDb.build(context, spec);
        assertTrue(!first.equals(signature(context)));
    }

    @Test
    public void verify_detectsCountMismatch() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        FixtureDb.Spec spec = FixtureDb.Spec.small();
        FixtureDb.build(context, spec);
        spec.transactions = 6;
        try {
            FixtureDb.verify(context, spec);
            fail("verify must fail when counts differ from the spec");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("log_Transactions"));
        }
    }

    private static String signature(Context context) {
        SQLiteDatabase db = DBHelper.getInstance(context).getReadableDatabase();
        StringBuilder sb = new StringBuilder();
        try (Cursor cursor = db.rawQuery("SELECT DateTime, SrcAccount, Amount, Payee, Category, Comment "
                + "FROM log_Transactions ORDER BY _id", null)) {
            while (cursor.moveToNext()) {
                for (int i = 0; i < 6; i++) {
                    sb.append(cursor.getString(i)).append('|');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }
}
