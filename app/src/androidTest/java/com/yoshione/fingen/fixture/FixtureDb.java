package com.yoshione.fingen.fixture;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import androidx.test.InstrumentationRegistry;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.AccountsSetsLogDAO;
import com.yoshione.fingen.dao.AccountsSetsRefDAO;
import com.yoshione.fingen.dao.BudgetCreditsDAO;
import com.yoshione.fingen.dao.BudgetDAO;
import com.yoshione.fingen.dao.CabbagesDAO;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.dao.CreditsDAO;
import com.yoshione.fingen.dao.DepartmentsDAO;
import com.yoshione.fingen.dao.LocationsDAO;
import com.yoshione.fingen.dao.PayeesDAO;
import com.yoshione.fingen.dao.ProductEntrysDAO;
import com.yoshione.fingen.dao.ProductsDAO;
import com.yoshione.fingen.dao.ProjectsDAO;
import com.yoshione.fingen.dao.SendersDAO;
import com.yoshione.fingen.dao.SimpleDebtsDAO;
import com.yoshione.fingen.dao.SmsDAO;
import com.yoshione.fingen.dao.SmsMarkersDAO;
import com.yoshione.fingen.dao.TemplatesDAO;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.filters.DateRangeFilter;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Category;
import com.yoshione.fingen.model.Credit;
import com.yoshione.fingen.model.Department;
import com.yoshione.fingen.model.Location;
import com.yoshione.fingen.model.Payee;
import com.yoshione.fingen.model.Product;
import com.yoshione.fingen.model.ProductEntry;
import com.yoshione.fingen.model.Project;
import com.yoshione.fingen.model.Sender;
import com.yoshione.fingen.model.SimpleDebt;
import com.yoshione.fingen.model.Sms;
import com.yoshione.fingen.model.SmsMarker;
import com.yoshione.fingen.model.Template;
import com.yoshione.fingen.model.Transaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Test database with a configurable amount of data. One generator: {@link #build(Context, Spec)}
 * resets fingen.db to a clean schema created by DBHelper.onCreate and fills it through the app's
 * own DAOs (createModel of AccountsDAO, TransactionsDAO, ...), so related rows (log_Products default
 * entry, running balance, search strings, full names) are produced exactly as when a user works
 * with the app. Then {@link #verify(Context, Spec)} checks counts, foreign keys and products.
 * <p>
 * Dates are always relative to the current date of the device (local time zone): the "anchor" is
 * {@link Spec#anchorMillis} (default: now), transactions go back from the anchor day. The same seed
 * and the same anchor day give the same data; compare dates against the anchor day, not a fixed date.
 * <p>
 * Ready databases (variants) are produced by FixtureVariantsExportTest from {@link #variantSpec} and
 * stored in assets/fixtures/&lt;name&gt;.db; tests take them with {@link #copyFixture(Context, String)}
 * without generating anything. Dates in a stored variant are "old" (relative to the day it was
 * built), see CLAUDE.md, section "Тестовые фикстуры".
 * <p>
 * Both ways replace the real fingen.db of the app under test. The previous content is lost: take a
 * backup first if the device holds data that is needed (tests run on a test device, see CLAUDE.md).
 */
public final class FixtureDb {

    public static final String VARIANT_SUMMARY = "summary";
    public static final String VARIANT_DEFAULT100 = "default100";
    public static final String VARIANT_SMALL = "small";
    public static final String[] VARIANTS = {VARIANT_SUMMARY, VARIANT_DEFAULT100, VARIANT_SMALL};

    /** Periods of the Summary tab (FragmentSummary): DateRangeFilter range and modifier, name. */
    private static final int[][] SUMMARY_PERIODS = {
            {DateRangeFilter.DATE_RANGE_DAY, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
            {DateRangeFilter.DATE_RANGE_DAY, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
            {DateRangeFilter.DATE_RANGE_WEEK, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
            {DateRangeFilter.DATE_RANGE_WEEK, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
            {DateRangeFilter.DATE_RANGE_MONTH, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
            {DateRangeFilter.DATE_RANGE_MONTH, DateRangeFilter.DATE_RANGE_MODIFIER_LAST},
            {DateRangeFilter.DATE_RANGE_YEAR, DateRangeFilter.DATE_RANGE_MODIFIER_CURRENT},
            {DateRangeFilter.DATE_RANGE_YEAR, DateRangeFilter.DATE_RANGE_MODIFIER_LAST}
    };
    public static final String[] SUMMARY_PERIOD_NAMES = {"today", "yesterday", "this_week", "last_week",
            "this_month", "last_month", "this_year", "last_year"};

    private static final String DB_NAME = "fingen.db";

    private static final Class<?>[] DAO_CLASSES = {
            AccountsDAO.class, AccountsSetsLogDAO.class, AccountsSetsRefDAO.class, BudgetCreditsDAO.class,
            BudgetDAO.class, CabbagesDAO.class, CategoriesDAO.class, CreditsDAO.class, DepartmentsDAO.class,
            LocationsDAO.class, PayeesDAO.class, ProductEntrysDAO.class, ProductsDAO.class, ProjectsDAO.class,
            SendersDAO.class, SimpleDebtsDAO.class, SmsDAO.class, SmsMarkersDAO.class, TemplatesDAO.class,
            TransactionsDAO.class
    };

    private static final String[] ACCOUNT_TYPE_NAMES = {"Наличные", "Банк", "Дебетовая", "Кредитная", "Актив",
            "Долг", "Другое"};
    private static final String[] ROOT_CATEGORIES = {"Продукты", "Транспорт", "Жильё", "Здоровье", "Развлечения",
            "Одежда", "Связь", "Образование", "Зарплата", "Подарки", "Проценты", "Прочие доходы"};
    private static final String[] COLORS = {"#E53935", "#8E24AA", "#3949AB", "#039BE5", "#00897B", "#7CB342",
            "#FDD835", "#FB8C00", "#43A047", "#00ACC1", "#6D4C41", "#546E7A"};
    private static final String[] COMMENT_WORDS = {"обед", "бензин", "кофе", "аптека", "такси", "подписка", "кино",
            "ремонт", "подарок", "аванс"};
    // Marker types in SmsParser: 0 account, 1 currency, 2 transaction type, 3 payee, 4 ignore, 6 dest account.
    // Share of each type in every 100 markers (30/12/12/24/10/12).
    private static final int[] MARKER_TYPES = {0, 1, 2, 3, 4, 6};
    private static final int[] MARKER_SHARE = {30, 12, 12, 24, 10, 12};

    /** Amounts of data for {@link #build}. All fields are plain ints, defaults are about 100 rows per table. */
    public static final class Spec {
        public int accounts = 24;
        /** Accounts with IsClosed = 1: accounts with index 6, 13, 20, ... (not used for transactions). */
        public int closedAccounts = 3;
        /** Total, about 40% of them are children of expense root categories. */
        public int categories = 20;
        /** The last payees (up to 5) are nested under the first ones. */
        public int payees = 100;
        public int projects = 10;
        public int departments = 10;
        public int locations = 10;
        public int simpleDebts = 10;
        public int transactions = 100;
        /** Transactions deleted through TransactionsDAO.deleteModel (Deleted = 1), evenly spread. */
        public int deletedTransactions = 8;
        /** Expense transactions (not deleted) with 2 extra product entries. */
        public int splitTransactions = 8;
        /** Rows of ref_Products besides the default product (_id 0). */
        public int products = 10;
        /** Rows of ref_budget (distinct month x category x currency), at most budgetMonths * categories * 2. */
        public int budgets = 100;
        public int budgetMonths = 12;
        public int templates = 30;
        /**
         * Rows of ref_Debts (credits), each gets debtBudgetMonths rows in ref_budget_debts. One per account
         * (unique Account + Deleted), on open accounts of type 3..6, or any open account if there are none.
         */
        public int debts = 9;
        public int debtBudgetMonths = 3;
        public int senders = 10;
        public int incomingSms = 100;
        /** Rows of ref_Sms_Parser_Patterns. */
        public int markers = 100;
        public long seed = 35;
        /**
         * "Now" of the data: the current time of the device (local time zone). Dates of transactions,
         * SMS and budgets go back from the day of this moment.
         */
        public long anchorMillis = System.currentTimeMillis();
        /**
         * Put 2 transactions (one income, one expense, not deleted, not transfers) into every period of
         * the Summary tab: today, yesterday, this/last week, this/last month, this/last year (periods
         * as in DateRangeFilter, with the first day of week from the settings, Monday by default).
         * They take the first 16 transactions. Logically empty periods are not forced: "yesterday" is
         * on the previous day, so with the anchor on Monday "this week" is only today, with the anchor on
         * the 1st "this month" is only today, on January 1st "this year" is only today.
         */
        public boolean coverSummaryPeriods = false;
        /** Transactions are spread over this many days back from the anchor. */
        public int spanDays = 360;

        /** Defaults: about 100 rows per table (variant default100). */
        public Spec() {
        }

        /** Variant summary: 90 transactions (16 cover the Summary periods), the rest as default. */
        public static Spec summary() {
            Spec s = new Spec();
            s.transactions = 90;
            s.deletedTransactions = 6;
            s.splitTransactions = 6;
            s.coverSummaryPeriods = true;
            return s;
        }

        /** Few rows of everything: 2 accounts, 5 transactions (variant small). */
        public static Spec small() {
            Spec s = new Spec();
            s.accounts = 2;
            s.closedAccounts = 0;
            s.categories = 5;
            s.payees = 3;
            s.projects = 1;
            s.departments = 1;
            s.locations = 1;
            s.simpleDebts = 1;
            s.transactions = 5;
            s.deletedTransactions = 1;
            s.splitTransactions = 1;
            s.products = 2;
            s.budgets = 4;
            s.budgetMonths = 2;
            s.templates = 2;
            s.debts = 1;
            s.debtBudgetMonths = 1;
            s.senders = 1;
            s.incomingSms = 3;
            s.markers = 6;
            s.spanDays = 30;
            return s;
        }

        void validate() {
            if (accounts - closedAccounts() < 1 && transactions + templates + debts > 0) {
                throw new IllegalArgumentException("Spec: need at least one open account");
            }
            if (deletedTransactions > transactions) {
                throw new IllegalArgumentException("Spec: deletedTransactions > transactions");
            }
            if (budgets > 0 && (categories < 1 || budgetMonths < 1
                    || budgets > budgetMonths * budgetCategories(this) * 2)) {
                throw new IllegalArgumentException("Spec: budgets exceed budgetMonths * categories * 2");
            }
            if (incomingSms > 0 && senders < 1) {
                throw new IllegalArgumentException("Spec: incomingSms need senders");
            }
            if (debts > debtHolders()) {
                throw new IllegalArgumentException("Spec: debts > accounts that can hold a debt (" + debtHolders() + ")");
            }
            if (spanDays < 1) {
                throw new IllegalArgumentException("Spec: spanDays < 1");
            }
            if (coverSummaryPeriods && (transactions < COVER_COUNT + deletedTransactions || accounts - closedAccounts() < 1)) {
                throw new IllegalArgumentException("Spec: coverSummaryPeriods needs transactions >= "
                        + COVER_COUNT + " + deletedTransactions");
            }
        }

        /** Open accounts of type 3..6 (credit, active, passive, other), all open accounts if none. */
        int debtHolders() {
            int typed = 0;
            for (int i = 0; i < accounts; i++) {
                boolean closed = i % 7 == 6 && i / 7 < closedAccounts;
                if (i % 7 >= 3 && !closed) {
                    typed++;
                }
            }
            return typed > 0 ? typed : accounts - closedAccounts();
        }

        /** Number of closed accounts actually created (indexes 6, 13, 20, ... below accounts). */
        int closedAccounts() {
            int n = 0;
            while (n < closedAccounts && 6 + 7 * n < accounts) {
                n++;
            }
            return n;
        }
    }

    private FixtureDb() {
    }

    /** 2 transactions (expense, income) for each of the 8 Summary periods. */
    private static final int COVER_COUNT = 16;

    /** Spec of a ready variant by name, see {@link #VARIANTS}. */
    public static Spec variantSpec(String name) {
        switch (name) {
            case VARIANT_SUMMARY:
                return Spec.summary();
            case VARIANT_DEFAULT100:
                return new Spec();
            case VARIANT_SMALL:
                return Spec.small();
            default:
                throw new IllegalArgumentException("Unknown fixture variant: " + name);
        }
    }

    /** Asset path of a ready variant. */
    public static String assetPath(String name) {
        return "fixtures/" + name + ".db";
    }

    // Categories for a budget: expense categories plus up to 2 income roots.
    private static int budgetCategories(Spec spec) {
        int children = spec.categories * 2 / 5;
        int roots = spec.categories - children;
        int expenseRoots = expenseRoots(roots);
        return expenseRoots + children + Math.min(2, roots - expenseRoots);
    }

    private static int expenseRoots(int roots) {
        return roots <= 1 ? roots : (roots * 2 + 2) / 3;
    }

    /**
     * Fast path: resets the DB (see {@link #resetDb}) and copies the ready variant (name from
     * {@link #VARIANTS}) over fingen.db. The next DBHelper.getInstance(context) opens the fixture
     * (version 35, no migration). Dates are those of the day the variant was built.
     */
    public static void copyFixture(Context ctx, String name) throws IOException {
        File db = resetDb(ctx);
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = testContext.getAssets().open(assetPath(name));
             OutputStream out = new FileOutputStream(db, false)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }

    /**
     * Closes the DBHelper connection, drops the DBHelper and DAO singletons (they cache the open
     * database) and removes fingen.db with -journal/-wal/-shm. Returns the (now absent) DB file.
     */
    public static File resetDb(Context ctx) throws IOException {
        DBHelper.getInstance(ctx).close();
        resetSingletons();

        File db = ctx.getDatabasePath(DB_NAME);
        File dir = db.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Can't create " + dir.getPath());
        }
        db.delete();
        new File(db.getPath() + "-journal").delete();
        new File(db.getPath() + "-wal").delete();
        new File(db.getPath() + "-shm").delete();
        return db;
    }

    /**
     * Resets fingen.db to a clean schema (DBHelper.onCreate) and fills it through the DAOs as
     * described by the spec. Synchronous. At the end the running balance is rebuilt with the app's own
     * DBHelper.updateRunningBalance (the same script as after a migration), then {@link #verify} runs.
     */
    public static void build(Context ctx, Spec spec) throws Exception {
        spec.validate();
        resetDb(ctx);
        DBHelper helper = DBHelper.getInstance(ctx);
        SQLiteDatabase db = helper.getWritableDatabase();
        Random rnd = new Random(spec.seed);

        List<Long> currencies = new ArrayList<>(CabbagesDAO.getInstance(ctx).getAllModelsIDs());
        Collections.sort(currencies);

        // ---- accounts ----
        List<Long> accountIds = new ArrayList<>();
        List<Long> openAccounts = new ArrayList<>();
        List<Long> debtAccounts = new ArrayList<>();
        long[] accountCurrency = new long[spec.accounts];
        int[] startBalances = {0, 1000, 5000, 15000};
        for (int i = 0; i < spec.accounts; i++) {
            int type = i % 7;
            boolean closed = i % 7 == 6 && i / 7 < spec.closedAccounts;
            Account account = new Account();
            account.setAccountType(Account.AccountType.values()[type]);
            account.setName(String.format("Тест счёт %02d %s", i + 1, ACCOUNT_TYPE_NAMES[type]));
            account.setCabbageId(i % 3 != 2 ? currencies.get(0) : currencies.get((i / 3) % currencies.size()));
            account.setEmitent("Тест банк " + (i % 4 + 1));
            account.setLast4Digits(1000 + i * 37 % 9000);
            account.setComment("Тест комментарий " + (i + 1));
            account.setStartBalance(BigDecimal.valueOf(startBalances[rnd.nextInt(startBalances.length)]));
            account.setIsClosed(closed);
            account.setOrder(i + 1);
            if (type == 3) {
                account.setCreditLimit(BigDecimal.valueOf(rnd.nextBoolean() ? 50000 : 100000));
            }
            account = (Account) AccountsDAO.getInstance(ctx).createModel(account);
            accountIds.add(account.getID());
            accountCurrency[i] = account.getCabbageId();
            if (!closed) {
                openAccounts.add(account.getID());
                if (type >= 3) {
                    debtAccounts.add(account.getID());
                }
            }
        }

        // ---- categories: roots (expense first, then income), children of expense roots ----
        int childCount = spec.categories * 2 / 5;
        int rootCount = spec.categories - childCount;
        int expenseRootCount = expenseRoots(rootCount);
        List<Long> expenseCategories = new ArrayList<>();
        List<Long> incomeCategories = new ArrayList<>();
        List<Long> expenseRoots = new ArrayList<>();
        for (int i = 0; i < rootCount; i++) {
            Category category = new Category();
            category.setName("Тест " + ROOT_CATEGORIES[i % ROOT_CATEGORIES.length] + (i >= ROOT_CATEGORIES.length ? " " + i : ""));
            category.setColor(Color.parseColor(COLORS[i % COLORS.length]));
            category.setOrderNum(i + 1);
            long id = CategoriesDAO.getInstance(ctx).createModel(category).getID();
            if (i < expenseRootCount) {
                expenseRoots.add(id);
                expenseCategories.add(id);
            } else {
                incomeCategories.add(id);
            }
        }
        for (int k = 0; k < childCount; k++) {
            Category category = new Category();
            category.setName("Тест подкатегория " + (k + 1));
            category.setColor(Color.parseColor(COLORS[(k + 3) % COLORS.length]));
            category.setParentID(expenseRoots.get(k % expenseRoots.size()));
            category.setOrderNum(k + 1);
            expenseCategories.add(CategoriesDAO.getInstance(ctx).createModel(category).getID());
        }

        // ---- payees, the last up to 5 are children of the first ones ----
        List<Long> payeeIds = new ArrayList<>();
        int nested = Math.min(5, spec.payees / 2);
        for (int i = 0; i < spec.payees; i++) {
            Payee payee = new Payee();
            payee.setName(String.format("Тест получатель %03d", i + 1));
            payee.setDefCategoryID(pick(rnd, expenseCategories, true));
            payee.setOrderNum(i + 1);
            if (i >= spec.payees - nested) {
                payee.setParentID(payeeIds.get(i - (spec.payees - nested)));
            }
            payeeIds.add(PayeesDAO.getInstance(ctx).createModel(payee).getID());
        }

        // ---- projects, departments, locations, simple debts ----
        List<Long> projects = new ArrayList<>();
        for (int i = 1; i <= spec.projects; i++) {
            Project project = new Project();
            project.setName(String.format("Тест проект %02d", i));
            project.setColor(Color.parseColor(COLORS[i % COLORS.length]));
            project.setIsActive(i != 10);
            project.setOrderNum(i);
            projects.add(ProjectsDAO.getInstance(ctx).createModel(project).getID());
        }
        List<Long> departments = new ArrayList<>();
        for (int i = 1; i <= spec.departments; i++) {
            Department department = new Department();
            department.setName(String.format("Тест подразделение %02d", i));
            department.setIsActive(i != 10);
            department.setOrderNum(i);
            departments.add(DepartmentsDAO.getInstance(ctx).createModel(department).getID());
        }
        List<Long> locations = new ArrayList<>();
        for (int i = 1; i <= spec.locations; i++) {
            Location location = new Location();
            location.setName(String.format("Тест место %02d", i));
            location.setAddress("Тест адрес " + i);
            location.setLon(37.6 + i * 0.01);
            location.setLat(55.7 + i * 0.01);
            location.setOrderNum(i);
            locations.add(LocationsDAO.getInstance(ctx).createModel(location).getID());
        }
        List<Long> simpleDebts = new ArrayList<>();
        int[] debtStarts = {0, 500, 2000, -1500};
        for (int i = 1; i <= spec.simpleDebts; i++) {
            SimpleDebt debt = new SimpleDebt();
            debt.setName(String.format("Тест долг %02d", i));
            debt.setIsActive(i != 10);
            debt.setStartAmount(BigDecimal.valueOf(debtStarts[rnd.nextInt(debtStarts.length)]));
            debt.setCabbageID(currencies.get(i % 2 == 1 ? 0 : Math.min(1, currencies.size() - 1)));
            simpleDebts.add(SimpleDebtsDAO.getInstance(ctx).createModel(debt).getID());
        }

        // ---- products (the default product _id 0 exists in a clean DB) ----
        List<Long> products = new ArrayList<>();
        for (int i = 1; i <= spec.products; i++) {
            Product product = new Product();
            product.setName(String.format("Тест товар %02d", i));
            products.add(ProductsDAO.getInstance(ctx).createModel(product).getID());
        }

        // ---- transactions through TransactionsDAO.createModel: it creates the default log_Products entry ----
        // the first COVER_COUNT transactions (if covering the Summary periods) are never deleted
        int cover = spec.coverSummaryPeriods ? COVER_COUNT : 0;
        boolean[] deleted = new boolean[spec.transactions];
        for (int k = 0; k < spec.deletedTransactions; k++) {
            deleted[cover + (int) ((k + 0.5) * (spec.transactions - cover) / spec.deletedTransactions)] = true;
        }
        int splitLeft = products.isEmpty() ? 0 : spec.splitTransactions;
        int[] weights = {70, 18, 12};
        for (int i = 0; i < spec.transactions; i++) {
            int day = (int) ((long) i * (spec.spanDays - 4) / spec.transactions) + rnd.nextInt(4);
            if (i < 6 && i < spec.spanDays) {
                day = i; // a few within the last days (for "today" and "this week" filters)
            }
            int roll = rnd.nextInt(100);
            int kind = roll < weights[0] ? 0 : (roll < weights[0] + weights[1] ? 1 : 2); // 0 expense, 1 income, 2 transfer
            if (kind == 2 && openAccounts.size() < 2) {
                kind = 0;
            }
            long coverMillis = -1;
            if (i < cover) {
                // pair i/2 is the period i/2 of the Summary tab: expense first, then income, in the middle
                // of the first day of the period (periods as the app computes them)
                kind = i % 2;
                int[] period = SUMMARY_PERIODS[i / 2];
                coverMillis = DateRangeFilter.getRangeStart(period[0], period[1], ctx).getTime()
                        + (12 + i % 2) * 3_600_000L + rnd.nextInt(60) * 60_000L;
            }
            long src = openAccounts.get(rnd.nextInt(openAccounts.size()));
            Transaction t = new Transaction(-1);
            long dateMillis = dateMillis(spec, day, 8 + rnd.nextInt(14), rnd.nextInt(60));
            t.setDateTime(new Date(coverMillis >= 0 ? coverMillis : dateMillis));
            t.setAccountID(src);
            t.setComment(String.format("Тест %s %d", COMMENT_WORDS[rnd.nextInt(COMMENT_WORDS.length)], i + 1));
            t.setProjectID(pickOrNone(rnd, projects, 3));
            t.setDepartmentID(pickOrNone(rnd, departments, 2));
            t.setLocationID(pickOrNone(rnd, locations, 4));
            if (kind == 2) {
                long dst;
                do {
                    dst = openAccounts.get(rnd.nextInt(openAccounts.size()));
                } while (dst == src);
                int[] amounts = {500, 1000, 2500, 10000};
                t.setDestAccountID(dst);
                t.setAmount(BigDecimal.valueOf(amounts[rnd.nextInt(amounts.length)]), Transaction.TRANSACTION_TYPE_TRANSFER);
                t.setExchangeRate(BigDecimal.valueOf(rate(accountIds, accountCurrency, currencies, src, dst)));
            } else if (kind == 1) {
                int[] amounts = {1500, 20000, 45000, 70000};
                t.setPayeeID(pickOrNone(rnd, payeeIds, 0));
                t.setCategoryID(pickOrNone(rnd, incomeCategories, 0));
                t.setSimpleDebtID(pickOrNone(rnd, simpleDebts, 8));
                t.setAmount(BigDecimal.valueOf(amounts[rnd.nextInt(amounts.length)] * 100L + rnd.nextInt(100), 2),
                        Transaction.TRANSACTION_TYPE_INCOME);
            } else {
                t.setPayeeID(pickOrNone(rnd, payeeIds, 0));
                t.setCategoryID(pickOrNone(rnd, expenseCategories, 0));
                t.setSimpleDebtID(pickOrNone(rnd, simpleDebts, 8));
                t.setAmount(BigDecimal.valueOf((50 + rnd.nextInt(8951)) * 100L + rnd.nextInt(100), 2),
                        Transaction.TRANSACTION_TYPE_EXPENSE);
                if (splitLeft > 0 && !deleted[i]) {
                    splitLeft--;
                    List<ProductEntry> entries = new ArrayList<>();
                    for (int k = 0; k < 2; k++) {
                        entries.add(new ProductEntry(-1, products.get(rnd.nextInt(products.size())),
                                BigDecimal.valueOf(1 + rnd.nextInt(3)), BigDecimal.valueOf(-(1 + rnd.nextInt(4)) * 10),
                                -1, -1, -1));
                    }
                    t.setProductEntries(entries);
                }
            }
            t = (Transaction) TransactionsDAO.getInstance(ctx).createModel(t);
            if (deleted[i]) {
                TransactionsDAO.getInstance(ctx).deleteModel(t, true, ctx);
            }
        }

        // ---- budget by category: distinct month x category x currency ----
        List<long[]> combos = new ArrayList<>();
        List<Long> budgetCategories = new ArrayList<>(expenseCategories);
        budgetCategories.addAll(incomeCategories.subList(0, Math.min(2, incomeCategories.size())));
        for (int m = 0; m < spec.budgetMonths; m++) {
            for (long c : budgetCategories) {
                for (int cu = 0; cu < 2; cu++) {
                    combos.add(new long[]{m, c, currencies.get(Math.min(cu, currencies.size() - 1))});
                }
            }
        }
        Collections.shuffle(combos, rnd);
        int[] budgetAmounts = {3000, 5000, 8000, 12000, 20000};
        for (int i = 0; i < spec.budgets; i++) {
            long[] combo = combos.get(i);
            BudgetDAO.getInstance(ctx).createBudget(monthsBackYear(spec, (int) combo[0]),
                    monthsBackMonth(spec, (int) combo[0]), combo[1],
                    BigDecimal.valueOf(budgetAmounts[rnd.nextInt(budgetAmounts.length)]), combo[2]);
        }

        // ---- templates ----
        int[] templateTypes = {-1, -1, -1, 1, 0};
        for (int i = 0; i < spec.templates; i++) {
            int type = templateTypes[rnd.nextInt(templateTypes.length)];
            if (type == 0 && openAccounts.size() < 2) {
                type = -1;
            }
            long src = openAccounts.get(rnd.nextInt(openAccounts.size()));
            Template template = new Template();
            template.setName(String.format("Тест шаблон %02d", i + 1));
            template.setComment("Тест комментарий");
            template.setAccountID(src);
            template.setTrType(type);
            if (type == 0) {
                long dst;
                do {
                    dst = openAccounts.get(rnd.nextInt(openAccounts.size()));
                } while (dst == src);
                int[] amounts = {500, 1000, 3000};
                template.setDestAccountID(dst);
                template.setAmount(BigDecimal.valueOf(-amounts[rnd.nextInt(amounts.length)]));
                template.setExchangeRate(BigDecimal.valueOf(rate(accountIds, accountCurrency, currencies, src, dst)));
            } else {
                int[] amounts = {100, 300, 1500, 5000};
                template.setPayeeID(pickOrNone(rnd, payeeIds, 0));
                template.setCategoryID(pickOrNone(rnd, type > 0 ? incomeCategories : expenseCategories, 0));
                template.setProjectID(pickOrNone(rnd, projects, 1));
                template.setDepartmentID(pickOrNone(rnd, departments, 1));
                template.setLocationID(pickOrNone(rnd, locations, 1));
                template.setAmount(BigDecimal.valueOf(type * amounts[rnd.nextInt(amounts.length)]));
            }
            TemplatesDAO.getInstance(ctx).createModel(template);
        }

        // ---- debts (credits) and their budget ----
        List<Long> debtHolders = debtAccounts.isEmpty() ? openAccounts : debtAccounts;
        int[] debtAmounts = {1000, 5000, 10000};
        for (int k = 0; k < spec.debts; k++) {
            Credit credit = new Credit();
            credit.setAccountID(debtHolders.get(k));
            credit.setPayeeID(payeeIds.isEmpty() ? -1 : payeeIds.get(k % payeeIds.size()));
            credit.setCategoryID(expenseCategories.isEmpty() ? -1 : expenseCategories.get(k % expenseCategories.size()));
            credit.setClosed(k % 5 == 4);
            credit.setComment("Тест долг-комментарий " + (k + 1));
            long creditId = CreditsDAO.getInstance(ctx).createModel(credit).getID();
            for (int m = 0; m < spec.debtBudgetMonths; m++) {
                BudgetCreditsDAO.getInstance(ctx).createBudget(monthsBackYear(spec, m), monthsBackMonth(spec, m),
                        creditId, BigDecimal.valueOf(debtAmounts[rnd.nextInt(debtAmounts.length)]));
            }
        }

        // ---- senders and incoming SMS ----
        List<Long> senders = new ArrayList<>();
        for (int i = 1; i <= spec.senders; i++) {
            senders.add(SendersDAO.getInstance(ctx).createModel(new Sender(-1,
                    String.format("Тест отправитель %02d", i), true, String.format("TESTBANK%02d", i), 3, 5,
                    "dd.MM.yy HH:mm", false, false)).getID());
        }
        String[] smsBodies = {
                "Покупка %.2f RUB, карта *%04d, Тест магазин %d. Баланс: %.2f RUB",
                "Списание %.2f RUB, карта *%04d, Тест оплата %d. Остаток: %.2f RUB",
                "Зачисление %.2f RUB на карту *%04d, Тест перевод %d. Баланс: %.2f RUB",
                "Test purchase %.2f USD, card *%04d, Test shop %d. Balance: %.2f USD"};
        for (int i = 0; i < spec.incomingSms; i++) {
            long when = dateMillis(spec, (int) (i * 3.6), 9, 0) - rnd.nextInt(600) * 60_000L;
            String body = String.format(java.util.Locale.US, smsBodies[rnd.nextInt(smsBodies.length)],
                    rnd.nextInt(8951) + 50 + rnd.nextInt(100) / 100.0, 1000 + i * 37 % 9000, i + 1,
                    rnd.nextInt(89001) + 1000 + rnd.nextInt(100) / 100.0);
            SmsDAO.getInstance(ctx).createModel(new Sms(-1, new Date(when), senders.get(i % senders.size()), body));
        }

        // ---- SMS parser markers ----
        for (int i = 0; i < spec.markers; i++) {
            int slot = i % 100;
            int idx = 0;
            for (int acc = MARKER_SHARE[0]; slot >= acc && idx < MARKER_SHARE.length - 1; ) {
                idx++;
                acc += MARKER_SHARE[idx];
            }
            int type = MARKER_TYPES[idx];
            String object;
            String pattern;
            switch (type) {
                case 0:
                    object = String.valueOf(accountIds.get(i % accountIds.size()));
                    pattern = String.format("*%04d тест%d", 1000 + i * 37 % 9000, i);
                    break;
                case 1:
                    object = String.valueOf(currencies.get(i % currencies.size()));
                    pattern = new String[]{"RUB", "USD", "EUR", "UAH", "BYN", "KZT"}[i % 6] + " тест" + i;
                    break;
                case 2:
                    object = String.valueOf(new int[]{-1, 0, 1}[i % 3]);
                    pattern = new String[]{"покупка", "перевод", "зачисление"}[i % 3] + " тест" + i;
                    break;
                case 3:
                    object = String.valueOf(payeeIds.get(i % payeeIds.size()));
                    pattern = "Тест магазин " + (i + 1);
                    break;
                case 6:
                    object = String.valueOf(accountIds.get(i * 2 % accountIds.size()));
                    pattern = String.format("на карту *%04d тест%d", 2000 + i * 53 % 7000, i);
                    break;
                default:
                    object = "Ignore";
                    pattern = "Тест реклама " + (i + 1);
                    break;
            }
            SmsMarkersDAO.getInstance(ctx).createModel(new SmsMarker(-1, type, object, pattern));
        }

        // ---- running balance: rebuilt with the app's own script (same as after a migration) ----
        helper.updateRunningBalance(db);

        verify(ctx, spec);
        if (spec.coverSummaryPeriods) {
            verifySummaryPeriods(ctx);
        }
    }

    /**
     * Every period of the Summary tab has at least one income and one expense, periods counted from the
     * current moment. Not part of {@link #verify} (it does not depend on the date): a stored variant
     * satisfies it only on the day it was built.
     */
    public static void verifySummaryPeriods(Context ctx) {
        long[][] periods = summaryPeriodCounts(ctx);
        List<String> errors = new ArrayList<>();
        for (int p = 0; p < periods.length; p++) {
            if (periods[p][0] == 0 || periods[p][1] == 0) {
                errors.add("Summary period " + SUMMARY_PERIOD_NAMES[p] + ": " + periods[p][0] + " incomes, "
                        + periods[p][1] + " expenses, expected both > 0");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("FixtureDb.verifySummaryPeriods failed:\n" + join(errors));
        }
    }

    /**
     * Checks the current fingen.db against the spec: version, row counts, every transaction has the
     * default log_Products entry and the entries add up to the amount, no dangling foreign keys
     * (-1 means "no value"). Throws IllegalStateException listing all violations.
     */
    public static void verify(Context ctx, Spec spec) {
        SQLiteDatabase db = DBHelper.getInstance(ctx).getReadableDatabase();
        List<String> errors = new ArrayList<>();

        if (db.getVersion() != DBHelper.DATABASE_VERSION) {
            errors.add("version " + db.getVersion() + " != " + DBHelper.DATABASE_VERSION);
        }
        expectCount(db, errors, "ref_Accounts", "", spec.accounts);
        expectCount(db, errors, "ref_Accounts", "IsClosed = 1", spec.closedAccounts());
        expectCount(db, errors, "ref_Categories", "", spec.categories);
        expectCount(db, errors, "ref_Payees", "", spec.payees);
        expectCount(db, errors, "ref_Projects", "", spec.projects);
        expectCount(db, errors, "ref_Departments", "", spec.departments);
        expectCount(db, errors, "ref_Locations", "", spec.locations);
        expectCount(db, errors, "ref_SimpleDebts", "", spec.simpleDebts);
        expectCount(db, errors, "ref_Products", "_id > 0", spec.products);
        expectCount(db, errors, "log_Transactions", "", spec.transactions);
        expectCount(db, errors, "log_Transactions", "Deleted > 0", spec.deletedTransactions);
        expectCount(db, errors, "ref_budget", "", spec.budgets);
        expectCount(db, errors, "log_Templates", "", spec.templates);
        expectCount(db, errors, "ref_Debts", "", spec.debts);
        expectCount(db, errors, "ref_budget_debts", "", spec.debts * spec.debtBudgetMonths);
        expectCount(db, errors, "ref_Senders", "", spec.senders);
        expectCount(db, errors, "log_Incoming_SMS", "", spec.incomingSms);
        expectCount(db, errors, "ref_Sms_Parser_Patterns", "", spec.markers);

        long noProduct = count(db, "SELECT COUNT(*) FROM log_Transactions t WHERE NOT EXISTS ("
                + "SELECT 1 FROM log_Products p WHERE p.TransactionId = t._id AND p.ProductId = 0)");
        if (noProduct != 0) {
            errors.add(noProduct + " transactions have no default log_Products row (ProductId = 0)");
        }
        long badSum = count(db, "SELECT COUNT(*) FROM log_Transactions t WHERE t.Deleted = 0 AND ABS(t.Amount - "
                + "(SELECT IFNULL(SUM(p.Price * p.Quantity), 0) / 1000.0 FROM log_Products p "
                + "WHERE p.TransactionId = t._id AND p.Deleted = 0)) > 1.0");
        if (badSum != 0) {
            errors.add(badSum + " transactions where the sum of log_Products differs from Amount");
        }
        long badSplit = count(db, "SELECT COUNT(*) FROM log_Transactions t WHERE t.Deleted = 0 AND (t.IsSplit = 1) != "
                + "((SELECT COUNT(*) FROM log_Products p WHERE p.TransactionId = t._id AND p.Deleted = 0) > 1)");
        if (badSplit != 0) {
            errors.add(badSplit + " transactions where IsSplit does not match the number of log_Products rows");
        }

        String[][] links = {
                {"log_Transactions", "SrcAccountId", "ref_Accounts"}, {"log_Transactions", "DestAccountId", "ref_Accounts"},
                {"log_Transactions", "PayeeId", "ref_Payees"}, {"log_Transactions", "CategoryId", "ref_Categories"},
                {"log_Transactions", "ProjectId", "ref_Projects"}, {"log_Transactions", "SimpleDebtId", "ref_SimpleDebts"},
                {"log_Transactions", "DepartmentId", "ref_Departments"}, {"log_Transactions", "LocationId", "ref_Locations"},
                {"log_Products", "TransactionId", "log_Transactions"}, {"log_Products", "ProductId", "ref_Products"},
                {"log_Products", "CategoryId", "ref_Categories"}, {"log_Products", "ProjectId", "ref_Projects"},
                {"ref_Accounts", "CurrencyId", "ref_Currencies"}, {"ref_Categories", "ParentId", "ref_Categories"},
                {"ref_Payees", "ParentId", "ref_Payees"}, {"ref_Payees", "DefaultCategoryId", "ref_Categories"},
                {"ref_SimpleDebts", "CurrencyId", "ref_Currencies"},
                {"log_Templates", "SrcAccountId", "ref_Accounts"}, {"log_Templates", "DestAccountId", "ref_Accounts"},
                {"log_Templates", "PayeeId", "ref_Payees"}, {"log_Templates", "CategoryId", "ref_Categories"},
                {"log_Templates", "ProjectId", "ref_Projects"}, {"log_Templates", "DepartmentId", "ref_Departments"},
                {"log_Templates", "LocationId", "ref_Locations"},
                {"ref_Debts", "AccountId", "ref_Accounts"}, {"ref_Debts", "PayeeId", "ref_Payees"},
                {"ref_Debts", "CategoryId", "ref_Categories"},
                {"ref_budget", "CategoryId", "ref_Categories"}, {"ref_budget", "CurrencyId", "ref_Currencies"},
                {"ref_budget_debts", "DebtId", "ref_Debts"}, {"log_Incoming_SMS", "SenderId", "ref_Senders"}
        };
        for (String[] link : links) {
            long dangling = count(db, String.format("SELECT COUNT(*) FROM %1$s s WHERE s.%2$s IS NOT NULL AND NOT EXISTS ("
                    + "SELECT 1 FROM %3$s r WHERE r._id = s.%2$s)", link[0], link[1], link[2]));
            if (dangling != 0) {
                errors.add(dangling + " rows of " + link[0] + "." + link[1] + " point to missing " + link[2]);
            }
        }
        // Marker Object is text: type 0 and 6 account id, 1 currency id, 3 payee id
        String[][] markerLinks = {{"0", "ref_Accounts"}, {"6", "ref_Accounts"}, {"1", "ref_Currencies"},
                {"3", "ref_Payees"}};
        for (String[] link : markerLinks) {
            long dangling = count(db, String.format("SELECT COUNT(*) FROM ref_Sms_Parser_Patterns s WHERE s.Type = %s "
                    + "AND NOT EXISTS (SELECT 1 FROM %s r WHERE CAST(r._id AS TEXT) = s.Object)", link[0], link[1]));
            if (dangling != 0) {
                errors.add(dangling + " SMS markers of type " + link[0] + " point to missing " + link[1]);
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("FixtureDb.verify failed:\n" + join(errors));
        }
    }

    /**
     * Non-deleted incomes and expenses (not transfers) in each of the 8 periods of the Summary tab
     * (order of {@link #SUMMARY_PERIOD_NAMES}), as [period]{incomes, expenses}. Period bounds come
     * from DateRangeFilter for the current moment, as in the app.
     */
    public static long[][] summaryPeriodCounts(Context ctx) {
        SQLiteDatabase db = DBHelper.getInstance(ctx).getReadableDatabase();
        long[][] result = new long[SUMMARY_PERIODS.length][2];
        for (int p = 0; p < SUMMARY_PERIODS.length; p++) {
            long start = DateRangeFilter.getRangeStart(SUMMARY_PERIODS[p][0], SUMMARY_PERIODS[p][1], ctx).getTime();
            long end = DateRangeFilter.getRangeEnd(SUMMARY_PERIODS[p][0], SUMMARY_PERIODS[p][1], ctx).getTime();
            String where = " FROM log_Transactions WHERE Deleted = 0 AND DestAccountId IS NULL AND DateTime >= " + start
                    + " AND DateTime <= " + end;
            result[p][0] = count(db, "SELECT COUNT(*)" + where + " AND Amount > 0");
            result[p][1] = count(db, "SELECT COUNT(*)" + where + " AND Amount < 0");
        }
        return result;
    }

    private static void expectCount(SQLiteDatabase db, List<String> errors, String table, String where, int expected) {
        long actual = count(db, "SELECT COUNT(*) FROM " + table + (where.isEmpty() ? "" : " WHERE " + where));
        if (actual != expected) {
            errors.add(table + (where.isEmpty() ? "" : " [" + where + "]") + ": " + actual + " rows, expected " + expected);
        }
    }

    private static long count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            cursor.moveToFirst();
            return cursor.getLong(0);
        }
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("  - ").append(line).append('\n');
        }
        return sb.toString();
    }

    // random element; withNone adds one "no value" (-1) choice, -1 if there is nothing to choose
    private static long pick(Random rnd, List<Long> list, boolean withNone) {
        int n = list.size() + (withNone ? 1 : 0);
        if (n == 0) {
            return -1;
        }
        int i = rnd.nextInt(n);
        return i < list.size() ? list.get(i) : -1;
    }

    // random element; the list is extended with "noneWeight" extra "-1" entries (no value)
    private static long pickOrNone(Random rnd, List<Long> list, int noneWeight) {
        int n = list.size() + noneWeight;
        if (n == 0) {
            return -1;
        }
        int i = rnd.nextInt(n);
        return i < list.size() ? list.get(i) : -1;
    }

    private static double rate(List<Long> accountIds, long[] accountCurrency, List<Long> currencies, long src, long dst) {
        long a = accountCurrency[accountIds.indexOf(src)];
        long b = accountCurrency[accountIds.indexOf(dst)];
        if (a == b) {
            return 1.0;
        }
        return currencies.indexOf(a) < currencies.indexOf(b) ? 0.011 : 90.0;
    }

    // local time: the day of the anchor minus daysBack, at hour:minute
    private static long dateMillis(Spec spec, int daysBack, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(spec.anchorMillis);
        c.add(Calendar.DAY_OF_MONTH, -daysBack);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static Calendar monthsBack(Spec spec, int n) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(spec.anchorMillis);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, -n);
        return c;
    }

    private static int monthsBackYear(Spec spec, int n) {
        return monthsBack(spec, n).get(Calendar.YEAR);
    }

    // 0-based, as the app stores it (Calendar.MONTH)
    private static int monthsBackMonth(Spec spec, int n) {
        return monthsBack(spec, n).get(Calendar.MONTH);
    }

    // DBHelper and the DAOs keep a static instance with the opened database (restoreDB resets
    // DBHelper.mInstance the same way), a stale one would still point to the replaced file.
    private static void resetSingletons() {
        try {
            clearStaticInstances(DBHelper.class);
            for (Class<?> daoClass : DAO_CLASSES) {
                clearStaticInstances(daoClass);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clearStaticInstances(Class<?> clazz) throws IllegalAccessException {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && field.getType() == clazz) {
                field.setAccessible(true);
                field.set(null, null);
            }
        }
    }
}
