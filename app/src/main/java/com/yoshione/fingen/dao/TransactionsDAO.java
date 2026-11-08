package com.yoshione.fingen.dao;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.yoshione.fingen.BuildConfig;
import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.db.DbRefs;
import com.yoshione.fingen.classes.ListSumsByCabbage;
import com.yoshione.fingen.classes.SumsByCabbage;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.filters.DateRangeFilter;
import com.yoshione.fingen.filters.FilterListHelper;
import com.yoshione.fingen.interfaces.IAbstractModel;
import com.yoshione.fingen.interfaces.IDaoInheritor;
import com.yoshione.fingen.managers.CurrencyScale;
import com.yoshione.fingen.managers.FilterManager;
import com.yoshione.fingen.managers.TransferManager;
import com.yoshione.fingen.model.DateEntry;
import com.yoshione.fingen.model.ProductEntry;
import com.yoshione.fingen.model.SimpleDebt;
import com.yoshione.fingen.model.SummaryItem;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.LocaleUtils;
import com.yoshione.fingen.utils.Translit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import io.reactivex.rxjava3.core.Single;

import static com.yoshione.fingen.DBHelper.C_ID;
import static com.yoshione.fingen.DBHelper.T_LOG_TRANSACTIONS;

/**
 * Created by slv on 14.08.2015.
 * 1
 */
public class TransactionsDAO extends BaseDAO implements AbstractDAO, IDaoInheritor {
    public static final String TAG = "TransactionsDAO";
    private static final String SQLTAG = "SQLLOG";
    private static TransactionsDAO sInstance;
    private ProductEntrysDAO mProductEntrysDAO;

    // Категория и проект строки товара (если заданы) заменяют категорию и проект транзакции
    private static final String CAT_EXPR = "CASE WHEN prod.CategoryId IS NULL THEN t.CategoryId ELSE prod.CategoryId END";
    private static final String PRJ_EXPR = "CASE WHEN prod.ProjectId IS NULL THEN t.ProjectId ELSE prod.ProjectId END";

    /**
     * В запросах с log_Products имена CategoryId и ProjectId из фильтров становятся выражениями с учетом товара.
     */
    private static String replaceProductFields(String filter) {
        return filter
                .replaceAll("\\bCategoryId\\b", CAT_EXPR)
                .replaceAll("\\bProjectId\\b", PRJ_EXPR);
    }

    private TransactionsDAO(Context context) {
        super(context, T_LOG_TRANSACTIONS, IAbstractModel.MODEL_TYPE_TRANSACTION, DBHelper.T_LOG_TRANSACTIONS_ALL_COLUMNS);
        super.setDaoInheritor(this);
        mProductEntrysDAO = ProductEntrysDAO.getInstance(context);
    }

    public synchronized static TransactionsDAO getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TransactionsDAO(context);
        }
        return sInstance;
    }

    @Override
    public IAbstractModel cursorToModel(Cursor cursor) {
        return cursorToTransaction(cursor);
    }

    private Transaction cursorToTransaction(Cursor cursor) {
        Transaction transaction = new Transaction(-1);

        transaction.setID(cursor.getLong(mColumnIndexes.get(C_ID)));
        transaction.setDateTime(new Date(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_DATETIME))));
        long srcAccountID = cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_SRCACCOUNT));
        int digits = CurrencyScale.forAccount(mDatabase, srcAccountID);
        transaction.setAccountID(srcAccountID);
        transaction.setPayeeID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_PAYEE)));
        transaction.setCategoryID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_CATEGORY)));
        transaction.setAmount(CurrencyScale.fromMinor(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_AMOUNT)), digits), Transaction.TRANSACTION_TYPE_UNDEFINED);
        transaction.setProjectID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_PROJECT)));
        transaction.setDepartmentID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_DEPARTMENT)));
        transaction.setLocationID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_LOCATION)));
        transaction.setComment(cursor.getString(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_COMMENT)));
        transaction.setFile(cursor.getString(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_FILE)));
        transaction.setDestAccountID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_DESTACCOUNT)));
        transaction.setExchangeRate(CurrencyScale.fromRate(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_EXCHANGERATE))));
        transaction.setAutoCreated(cursor.getInt(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_AUTOCREATED)) == 1);
        transaction.setLat(cursor.getDouble(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_LAT)));
        transaction.setLon(cursor.getDouble(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_LON)));
        transaction.setAccuracy(cursor.getInt(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_ACCURACY)));
        transaction.setSimpleDebtID(DbRefs.getRef(cursor, mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_SIMPLEDEBT)));
        transaction.setFN(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_FN)));
        transaction.setFD(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_FD)));
        transaction.setFP(cursor.getLong(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_FP)));

        if (cursor.getInt(mColumnIndexes.get(DBHelper.C_LOG_TRANSACTIONS_SPLIT)) == 1) {
            List<ProductEntry> entries = new ArrayList<>();
            for (IAbstractModel entry : mProductEntrysDAO.getAllEntriesOfTransaction(transaction.getID(), false)) {
                entries.add((ProductEntry) entry);
            }
            transaction.setProductEntries(entries);
        }

        int fromBalanceIndex = cursor.getColumnIndex("FromBalance");
        int toBalanceIndex = cursor.getColumnIndex("ToBalance");
        if (fromBalanceIndex >= 0 && toBalanceIndex >= 0) {
            transaction.setFromAccountBalance(CurrencyScale.fromMinor(cursor.getLong(fromBalanceIndex), digits));
            transaction.setToAccountBalance(CurrencyScale.fromMinor(cursor.getLong(toBalanceIndex),
                    CurrencyScale.forAccount(mDatabase, transaction.getDestAccountID())));
        }

        if (transaction.getDestAccountID() >= 0) {
            transaction.setTransactionType(Transaction.TRANSACTION_TYPE_TRANSFER);
        }


        return transaction;
    }

    private void updateBalanceForTransaction(Transaction t, int mult, boolean revert) {
        updateBalance(revert, CurrencyScale.toMinor(t.getAmount(), CurrencyScale.forAccount(mDatabase, t.getAccountID())),
                mult, t.getAccountID(), t.getID(), t.getDateTime().getTime());
        if (t.getDestAccountID() >= 0) {
            updateBalance(revert, CurrencyScale.toMinor(TransferManager.getDestAmount(t), CurrencyScale.forAccount(mDatabase, t.getDestAccountID())),
                    mult, t.getDestAccountID(), t.getID(), t.getDateTime().getTime());
        }
    }

    @Override
    public IAbstractModel createModel(IAbstractModel model) throws Exception {
        mDatabase.beginTransaction();
        Transaction transaction;
        try {
            transaction = (Transaction) model;

            //если это перевод, то удаляем все его покупки (вдруг это раньше был расход со сплитом)
            if (transaction.getTransactionType() == Transaction.TRANSACTION_TYPE_TRANSFER) {
                transaction.getProductEntries().clear();
            }

            List<IAbstractModel> oldEntries = mProductEntrysDAO.getAllEntriesOfTransaction(transaction.getID(), true);
            ProductEntry defaultEntry = new ProductEntry();
            List<ProductEntry> newEntries = transaction.getProductEntries();

            transaction = (Transaction) super.createModel(model);

//        if (newEntries.isEmpty()) {
//            newEntries.add(new ProductEntry(-1, 0, BigDecimal.ONE, BigDecimal.ZERO, -1, -1, transaction.getID()));
//        }

            List<IAbstractModel> entriesToDelete = new ArrayList<>();
            List<ProductEntry> entriesCreated = new ArrayList<>();

            boolean entryExist;
            for (IAbstractModel oldEntry : oldEntries) {
                if (((ProductEntry) oldEntry).getProductID() == 0) {
                    defaultEntry = (ProductEntry) oldEntry;
                } else {
                    entryExist = false;
                    for (ProductEntry newEntry : newEntries) {
                        entryExist = oldEntry.getID() == newEntry.getID();
                        if (entryExist) break;
                    }
                    if (!entryExist) {
                        entriesToDelete.add(oldEntry);
                    }
                }
            }

            mProductEntrysDAO.bulkDeleteModel(entriesToDelete, true);

            int digits = CurrencyScale.forAccount(mDatabase, transaction.getAccountID());
            defaultEntry.setPrice(transaction.getAmount().setScale(digits, RoundingMode.HALF_EVEN));
            defaultEntry.setTransactionID(transaction.getID());
            defaultEntry.setProductID(0);

            for (ProductEntry newEntry : newEntries) {
                newEntry.setTransactionID(transaction.getID());
                entriesCreated.add((ProductEntry) mProductEntrysDAO.createModel(newEntry));
                defaultEntry.setPrice(defaultEntry.getPrice().subtract(newEntry.getPrice().multiply(newEntry.getQuantity())).setScale(digits, RoundingMode.HALF_EVEN));
            }

            mProductEntrysDAO.createModel(defaultEntry);

            transaction.setProductEntries(entriesCreated);

            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }
        return transaction;
    }

    @Override
    public synchronized void deleteModel(IAbstractModel model, boolean resetTS, Context context) {
        Transaction transaction = (Transaction) model;
        updateBalanceForTransaction(transaction, -1, true);
        mProductEntrysDAO.bulkDeleteModel(mProductEntrysDAO.getAllEntriesOfTransaction(transaction.getID(), true), true);
        super.deleteModel(model, resetTS, context);
    }

    @Override
    public List<IAbstractModel> bulkDeleteModel(List<IAbstractModel> models, boolean resetTS) {
        mDatabase.beginTransaction();

        for (IAbstractModel model : models) {
            updateBalanceForTransaction((Transaction) model, -1, true);
        }

        HashSet<Long> tids = new HashSet<>();

        for (IAbstractModel model : models) {
            if (!tids.contains(model.getID())) {
                tids.add(model.getID());
            }
        }

        if (!models.isEmpty()) {
            String where = String.format("%s IN (%s)", DBHelper.C_LOG_PRODUCTS_TRANSACTIONID, TextUtils.join(",", tids));
            mProductEntrysDAO.bulkDeleteModel(mProductEntrysDAO.getModels(where), true);
        }

        List<IAbstractModel> deletedTransactions = super.bulkDeleteModel(models, resetTS, true);

        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();

        return deletedTransactions;
    }

    public void bulkUpdateEntity(ArrayList<String> ids, IAbstractModel entity, boolean resetTS) throws Exception {
        String field = entity.getLogTransactionsField();
        if (field == null) return;
        mDatabase.beginTransaction();

        Transaction transaction;
        for (String id : ids) {
            transaction = getTransactionByID(Long.valueOf(id));
            switch (entity.getModelType()) {
                case IAbstractModel.MODEL_TYPE_ACCOUNT:
                    transaction.setAccountID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_PAYEE:
                    transaction.setPayeeID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_CATEGORY:
                    transaction.setCategoryID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_PROJECT:
                    transaction.setProjectID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_LOCATION:
                    transaction.setLocationID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_DEPARTMENT:
                    transaction.setDepartmentID(entity.getID());
                    break;
                case IAbstractModel.MODEL_TYPE_SIMPLEDEBT:
                    transaction.setSimpleDebtID(entity.getID());
                    break;
            }
            createModel(transaction);
        }

        mDatabase.setTransactionSuccessful();
        mDatabase.endTransaction();
    }

    @Override
    public void deleteAllModels() {
        mDatabase.execSQL("DELETE FROM log_Running_Balance");
        super.deleteAllModels();
    }

    @SuppressWarnings("unchecked")
    public synchronized Single<List<Transaction>> getRangeTransactionsRx(int first, int count, FilterListHelper filterListHelper, Context context) {
        return Single.fromCallable(() -> getRangeTransactions(first, count, filterListHelper, context));
    }

    @SuppressWarnings("unchecked")
    private synchronized List<Transaction> getRangeTransactions(int first, int count, FilterListHelper filterListHelper, Context context) {

            List < AbstractFilter > filterList = filterListHelper.getFilters();

            String tableName;

            if (filterListHelper.getSearchString().isEmpty()) {
                tableName = T_LOG_TRANSACTIONS;
            } else {
                createSearchTransactionsTable(Translit.toTranslit(filterListHelper.getSearchString().toLowerCase()).replaceAll("'", "''"));
                tableName = DBHelper.T_SEARCH_TRANSACTIONS;
            }

            String selection = FilterManager.createSelectionString(filterList, AccountsDAO.getInstance(context).getAllModelsIDs(), true,true,true, context);
            String where;
            if (selection != null && !selection.isEmpty()) {
                where = " AND (" + selection + ") ";
            } else {
                where = "";
            }

            // Колонки RB выбираются без DateTime: фильтры используют DateTime без префикса
            String sql = "SELECT DISTINCT lt.*, (frb.Income + frb.Expense + sa.StartBalance) as FromBalance, (trb.Income + trb.Expense + da.StartBalance) as ToBalance\n" +
                    "FROM " + tableName + " as lt\n" +
                    "LEFT OUTER JOIN ref_Accounts sa ON sa._id = lt.SrcAccountId\n" +
                    "LEFT OUTER JOIN ref_Accounts da ON da._id = lt.DestAccountId\n" +
                    "LEFT OUTER JOIN (SELECT AccountId, TransactionId, Income, Expense FROM log_Running_Balance) AS frb ON frb.TransactionId = lt._id AND frb.AccountId = lt.SrcAccountId\n" +
                    "LEFT OUTER JOIN (SELECT AccountId, TransactionId, Income, Expense FROM log_Running_Balance) AS trb ON trb.TransactionId = lt._id AND trb.AccountId = lt.DestAccountId\n" +
                    "WHERE lt.Deleted = 0" + where + "\n" +
                    "ORDER BY lt.DateTime DESC\n" +
                    "LIMIT " + String.valueOf(first) + ", " + String.valueOf(count);

            if (BuildConfig.DEBUG) {
                Log.d(SQLTAG, sql);
            }

            Cursor cursor;
            List<Transaction> transactions = new ArrayList<>();
            try {
                cursor = mDatabase.rawQuery(sql, null);
            } catch (Exception e) {
                return transactions;
            }
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        while (!cursor.isAfterLast()) {
                            transactions.add(cursorToTransaction(cursor));
                            cursor.moveToNext();
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            return transactions;
    }

    public List<Transaction> getTransactionsByQR(Transaction transaction, Context context) {
        Calendar c = Calendar.getInstance(LocaleUtils.getLocale(context));
        c.setTime(transaction.getDateTime());
        c.set(Calendar.HOUR_OF_DAY, 0); //anything 0 - 23
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();
        c.set(Calendar.HOUR_OF_DAY, 23); //anything 0 - 23
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        Date end = c.getTime();
        String select = String.format(
                        "%s = %s AND\n" +
                        "%s > '%s' AND \n" +
                        "%s < '%s' AND \n" +
                        "Deleted = 0",
                DBHelper.C_LOG_TRANSACTIONS_AMOUNT, String.valueOf(CurrencyScale.toMinor(transaction.getAmount(), CurrencyScale.forAccount(mDatabase, transaction.getAccountID()))),
                DBHelper.C_LOG_TRANSACTIONS_DATETIME, String.valueOf(start.getTime()),
                DBHelper.C_LOG_TRANSACTIONS_DATETIME, String.valueOf(end.getTime()));

//        Log.d(TAG, select);

        Cursor cursor = mDatabase.query(T_LOG_TRANSACTIONS, null,
                select, null,
                null, null, null);

        List<Transaction> transactions = new ArrayList<>();
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        transactions.add(cursorToTransaction(cursor));
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return transactions;
    }

    @Override
    public List<?> getAllModels() throws Exception {
        return getAllTransactions();
    }

    @SuppressWarnings("unchecked")
    public List<Transaction> getAllTransactions() throws Exception {

        return (List<Transaction>) getItems(getTableName(), null,
                null, null,
                String.format("%s %s", DBHelper.C_LOG_TRANSACTIONS_DATETIME, "desc"),
                null);
    }

    public Transaction getTransactionByID(long id) {
        return (Transaction) getModelById(id);
    }

    /**
     * Создает временную таблицу транзакций на основе log_Transactions  с учетом фильтров
     */
    private synchronized void createTempTransactionsTable(List<AbstractFilter> filterList, Context context) {
        HashSet<Long> allAccountsIDs = AccountsDAO.getInstance(context).getAllModelsIDs();
        //Генерируем условие для SQL запроса
        String filterSelectionSrc = FilterManager.createSelectionString(filterList, allAccountsIDs, false, true, false, context);
        String filterSelectionDst = FilterManager.createSelectionString(filterList, allAccountsIDs, false, false, false, context);
        String filterSrc = filterSelectionSrc.isEmpty() ? "" : "AND " + filterSelectionSrc;
        String filterDst = filterSelectionDst.isEmpty() ? "" : "AND " + filterSelectionDst;
        String filteredAccountsString = TextUtils.join(",", FilterManager.getAccountIDsFromFilters(filterList, allAccountsIDs));

        String sql = "DROP TABLE IF EXISTS temp_all_Transactions;";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);

        //<editor-fold desc="Mega SQL" defaultstate = "collapsed">
        /*
         * Запрос состоит из трех частей:
         * 1. Объединяем с таблицей log_Products, и все транзакции которые имеют товары дублируются по количеству товаров.
         *  Если у товара есть категория то такая транзакция будет иметь категорию товара, если нет,
         *  то будет иметь свою исходную категорию. Сумма транзакции равна цене товара умноженной на количество
         * 2. Транзакции без товаров (нет строки в log_Products) берутся из того же запроса через LEFT JOIN и учитываются со своей суммой
         * 3. Входящие переводы
         */
        sql = "CREATE TEMP TABLE temp_all_Transactions AS \n" +
                "SELECT " +
                "   t._id,\n" +
                "   t.SrcAccountId AS Account,\n" +
                "   ROUND(IFNULL(prod.Price*prod.Quantity/1000.0, t.Amount) / " + CurrencyScale.divisorSql("ca.DecimalCount") + ", 8) AS Amount,\n" +
                "   t.DateTime,\n" +
                "   t.PayeeId,\n" +
                "   " + CAT_EXPR + " AS CategoryId,\n" +
                "   " + PRJ_EXPR + " AS ProjectId,\n" +
                "   t.SimpleDebtId,\n" +
                "   t.DepartmentId,\n" +
                "   t.LocationId,\n" +
                "   a.CurrencyId AS Currency,\n" +
                "   t.Amount > 0 AS Income,\n " +
                "   IFNULL(a.CurrencyId = b.CurrencyId AND t.DestAccountId IN(" + filteredAccountsString + "), 0) AS ExcludeTransfer\n" +
                "FROM log_Transactions AS t\n" +
                "   LEFT JOIN log_Products prod ON t._id = prod.TransactionId AND prod.Deleted = 0 \n" +
                "   LEFT OUTER JOIN ref_Accounts AS a ON t.SrcAccountId = a._id\n" +
                "   LEFT OUTER JOIN ref_Currencies AS ca ON a.CurrencyId = ca._id\n" +
                "   LEFT OUTER JOIN ref_Accounts AS b ON t.DestAccountId = b._id\n" +
                "WHERE\n" +
                "   t.Deleted = 0\n" +
                "   AND (t.DestAccountId IS NULL OR t.SrcAccountId != t.DestAccountId) " + replaceProductFields(filterSrc)
                + "\n" +

                "UNION ALL\n" +

                "SELECT " +
                "   t._id,\n" +
                "   t.DestAccountId AS Account,\n" +
                "   ROUND(t.Amount * -1.0 / " + CurrencyScale.divisorSql("cb.DecimalCount") + " * (t.ExchangeRate / 1000000000000.0), 8),\n" +
                "   t.DateTime,\n" +
                "   t.PayeeId,\n" +
                "   t.CategoryId,\n" +
                "   t.ProjectId,\n" +
                "   t.SimpleDebtId,\n" +
                "   t.DepartmentId,\n" +
                "   t.LocationId,\n" +
                "   a.CurrencyId AS Currency,\n" +
                "   1 AS Income,\n " +
                "   IFNULL(a.CurrencyId = b.CurrencyId AND t.SrcAccountId IN(" + filteredAccountsString + "), 0) AS ExcludeTransfer\n" +
                "FROM\n" +
                "   log_Transactions AS t\n" +
                "   LEFT OUTER JOIN ref_Accounts AS a ON t.DestAccountId = a._id\n" +
                "   LEFT OUTER JOIN ref_Accounts AS b ON t.SrcAccountId = b._id\n" +
                "   LEFT OUTER JOIN ref_Currencies AS cb ON b.CurrencyId = cb._id\n" +
                "WHERE " +
                "   t.Deleted = 0\n" +
                "   AND t.DestAccountId IS NOT NULL\n" +
                "   AND (t.SrcAccountId != t.DestAccountId) " + filterDst+";";
        //</editor-fold>

        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);
        sql = "CREATE INDEX [temp_all_Transactions_Income] ON [temp_all_Transactions] (Income);";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);
    }

    /**
     * Создает временную таблицу транзакций на основе log_Transactions  с учетом фильтров и поискового запроса
     */
    private synchronized void createTempTransactionsTable(List<AbstractFilter> filterList, String searchString, Context context) {
        HashSet<Long> allAccountsIDs = AccountsDAO.getInstance(context).getAllModelsIDs();
        //Генерируем условие для SQL запроса
        String filterSelectionSrc = FilterManager.createSelectionString(filterList, allAccountsIDs, false, true, false, context);
        String filterSelectionDst = FilterManager.createSelectionString(filterList, allAccountsIDs, false, false, false, context);
        String filterSrc = filterSelectionSrc.isEmpty() ? "" : "AND " + filterSelectionSrc;
        String filterDst = filterSelectionDst.isEmpty() ? "" : "AND " + filterSelectionDst;
        String filteredAccountsString = TextUtils.join(",", FilterManager.getAccountIDsFromFilters(filterList, allAccountsIDs));

        String sql = "DROP TABLE IF EXISTS temp_all_Transactions;";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);

        //<editor-fold desc="Mega SQL" defaultstate = "collapsed">
        sql = "CREATE TEMP TABLE temp_all_Transactions AS \n" +

                "SELECT t._id,\n" +
                "   t.SrcAccountId     AS AccountID,\n" +
                "   ROUND(IFNULL(prod.Price*prod.Quantity/1000.0, t.Amount) / " + CurrencyScale.divisorSql("ca.DecimalCount") + ", 8) AS Amount, t.DateTime, a.CurrencyId AS Currency,\n" +
                "   IFNULL(prod.Price*prod.Quantity/1000.0, t.Amount) > 0       AS Income,\n" +
                "   IFNULL(a.CurrencyId = b.CurrencyId AND t.DestAccountId IN(" + filteredAccountsString + "), 0) AS ExcludeTransfer,\n" +
                "   a.Name           AS AccountName,\n" +
                "   s.Name           AS SimpleDebtName,\n" +
                "   a.SearchString   AS AccountName,\n" +
                "   s.SearchString   AS SimpleDebtName,\n" +
                "   p.SearchString   AS PayeeName,\n" +
                "   c.SearchString   AS CategoryName,\n" +
                "   pr.SearchString  AS ProjectName,\n" +
                "   d.SearchString   AS DepartmentName,\n" +
                "   l.SearchString   AS LocationName\n" +
                "FROM log_Transactions AS t\n" +
                "   LEFT JOIN log_Products prod ON t._id = prod.TransactionId AND prod.Deleted = 0 \n" +
                "   LEFT OUTER JOIN ref_Accounts AS a ON t.SrcAccountId = a._id\n" +
                "   LEFT OUTER JOIN ref_Currencies AS ca ON a.CurrencyId = ca._id\n" +
                "   LEFT OUTER JOIN ref_Accounts AS b ON t.DestAccountId = b._id\n" +
                "   LEFT OUTER JOIN ref_Categories AS c ON " + CAT_EXPR + " = c._id\n" +
                "   LEFT OUTER JOIN ref_Payees  AS p ON t.PayeeId = p._id\n" +
                "   LEFT OUTER JOIN ref_Locations AS l ON t.LocationId = l._id\n" +
                "   LEFT OUTER JOIN ref_Projects AS pr ON " + PRJ_EXPR + " = pr._id\n" +
                "   LEFT OUTER JOIN ref_SimpleDebts AS s ON t.SimpleDebtId = s._id\n" +
                "   LEFT OUTER JOIN ref_Departments AS d ON t.DepartmentId = d._id\n" +
                "WHERE t.Deleted = 0\n" +
                "   AND (t.DestAccountId IS NULL OR t.SrcAccountId != t.DestAccountId) " + replaceProductFields(filterSrc)
                            + "\n" +
                "   AND (AccountName    LIKE '%" + searchString + "%'\n" +
                "   OR SimpleDebtName   LIKE '%" + searchString + "%'\n" +
                "   OR PayeeName        LIKE '%" + searchString + "%'\n" +
                "   OR CategoryName     LIKE '%" + searchString + "%'\n" +
                "   OR PayeeName        LIKE '%" + searchString + "%'\n" +
                "   OR ProjectName      LIKE '%" + searchString + "%'\n" +
                "   OR DepartmentName   LIKE '%" + searchString + "%'\n" +
                "   OR LocationName     LIKE '%" + searchString + "%')\n" +

                "UNION ALL\n" +

                "SELECT t._id,\n" +
                "   t.DestAccountId   AS AccountID,\n" +
                "   ROUND(t.Amount * -1.0 / " + CurrencyScale.divisorSql("cb.DecimalCount") + " * (t.ExchangeRate / 1000000000000.0), 8) AS Amount1, t.DateTime, a.CurrencyId AS Currency,\n" +
                "   1 > 0           AS Income,\n" +
                "   IFNULL(a.CurrencyId = b.CurrencyId AND t.SrcAccountId IN(" + filteredAccountsString + "), 0) AS ExcludeTransfer,\n" +
                "   a.Name          AS AccountName,\n" +
                "   s.Name          AS SimpleDebtName,\n" +
                "   a.SearchString  AS AccountName,\n" +
                "   s.SearchString  AS SimpleDebtName,\n" +
                "   p.SearchString  AS PayeeName,\n" +
                "   c.SearchString  AS CategoryName,\n" +
                "   pr.SearchString AS ProjectName,\n" +
                "   d.SearchString  AS DepartmentName,\n" +
                "   l.SearchString  AS LocationName\n" +
                "FROM log_Transactions AS t " +
                "   LEFT OUTER JOIN ref_Accounts    AS a  ON t.DestAccountId  = a._id\n" +
                "   LEFT OUTER JOIN ref_Accounts    AS b  ON t.SrcAccountId   = b._id\n" +
                "   LEFT OUTER JOIN ref_Currencies  AS cb ON b.CurrencyId   = cb._id\n" +
                "   LEFT OUTER JOIN ref_Categories  AS c  ON t.CategoryId     = c._id\n" +
                "   LEFT OUTER JOIN ref_Payees      AS p  ON t.PayeeId        = p._id\n" +
                "   LEFT OUTER JOIN ref_Locations   AS l  ON t.LocationId     = l._id\n" +
                "   LEFT OUTER JOIN ref_Projects    AS pr ON t.ProjectId      = pr._id\n" +
                "   LEFT OUTER JOIN ref_SimpleDebts AS s  ON t.SimpleDebtId   = s._id\n" +
                "   LEFT OUTER JOIN ref_Departments AS d  ON t.DepartmentId   = d._id\n" +
                "WHERE t.Deleted = 0 " +
                "   AND t.DestAccountId IS NOT NULL " +
                "   AND (t.SrcAccountId != t.DestAccountId) " + filterDst + "\n" +
                "   AND (AccountName    LIKE '%" + searchString + "%'\n" +
                "   OR SimpleDebtName   LIKE '%" + searchString + "%'\n" +
                "   OR PayeeName        LIKE '%" + searchString + "%'\n" +
                "   OR CategoryName     LIKE '%" + searchString + "%'\n" +
                "   OR PayeeName        LIKE '%" + searchString + "%'\n" +
                "   OR ProjectName      LIKE '%" + searchString + "%'\n" +
                "   OR DepartmentName   LIKE '%" + searchString + "%'\n" +
                "   OR LocationName     LIKE '%" + searchString + "%');";
        //</editor-fold>

        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);
        sql = "CREATE INDEX [temp_all_Transactions_Income] ON [temp_all_Transactions] (Income);";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);
    }

    private synchronized void createSearchTransactionsTable(String searchString) {
//        if (searchString.equals(mLastSearchString)) return;
        String sql = "DROP TABLE IF EXISTS " + DBHelper.T_SEARCH_TRANSACTIONS + ";";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);

        //<editor-fold desc="SQL"  defaultstate = "collapsed">
        sql = "CREATE TEMP TABLE " + DBHelper.T_SEARCH_TRANSACTIONS + " AS \n" +
                "SELECT t.*\n" +
                "FROM log_Transactions AS t " +
                "   LEFT JOIN log_Products prod ON t._id = prod.TransactionId AND prod.Deleted = 0 \n" +
                "   LEFT OUTER JOIN ref_Accounts    AS a  ON t.SrcAccountId   = a._id\n" +
                "   LEFT OUTER JOIN ref_Categories AS c ON " + CAT_EXPR + " = c._id\n" +
                "   LEFT OUTER JOIN ref_Payees      AS p  ON t.PayeeId        = p._id\n" +
                "   LEFT OUTER JOIN ref_Locations   AS l  ON t.LocationId     = l._id\n" +
                "   LEFT OUTER JOIN ref_Projects AS pr ON " + PRJ_EXPR + " = pr._id\n" +
                "   LEFT OUTER JOIN ref_SimpleDebts AS s  ON t.SimpleDebtId   = s._id\n" +
                "   LEFT OUTER JOIN ref_Departments AS d  ON t.DepartmentId   = d._id\n" +
                "WHERE t.Deleted = 0 \n" +
                "    AND (lower(t.SearchString) LIKE '%" + searchString + "%'\n" +
                "    OR lower(a.SearchString)   LIKE '%" + searchString + "%'\n" +
                "    OR lower(s.SearchString)   LIKE '%" + searchString + "%'\n" +
                "    OR lower(p.SearchString)   LIKE '%" + searchString + "%'\n" +
                "    OR lower(c.SearchString)   LIKE '%" + searchString + "%'\n" +
                "    OR lower(pr.SearchString)  LIKE '%" + searchString + "%'\n" +
                "    OR lower(d.SearchString)   LIKE '%" + searchString + "%'\n" +
                "    OR lower(l.SearchString)   LIKE '%" + searchString + "%');";
        //</editor-fold>
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }
        mDatabase.execSQL(sql);
    }

    public void getSumForSimpleDebt(SimpleDebt simpleDebt) {

        String divisor = CurrencyScale.divisorSql("c.DecimalCount");
        String sql = "SELECT TOTAL(t.Amount) / " + divisor + " AS Sum,\n" +
                "SUM ( CASE WHEN t.Amount < 0 THEN t.Amount ELSE 0 END) / " + divisor + " AS OweMe,\n" +
                "SUM ( CASE WHEN t.Amount > 0 THEN t.Amount ELSE 0 END) / " + divisor + " AS IOwe\n" +
                "FROM ref_Accounts a JOIN ref_Currencies c ON c._id = a.CurrencyId\n" +
                "     JOIN (SELECT * FROM log_Transactions WHERE SimpleDebtId = " + String.valueOf(simpleDebt.getID()) + " AND DestAccountId IS NULL AND Deleted = 0) t ON a._id = t.SrcAccountId\n" +
                "WHERE a.CurrencyId = " + String.valueOf(simpleDebt.getCabbageID());

        Cursor cursor = mDatabase.rawQuery(sql, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    simpleDebt.setAmount(new BigDecimal(cursor.getDouble(0)));
                    simpleDebt.setOweMe(new BigDecimal(cursor.getDouble(1)));
                    simpleDebt.setIOwe(new BigDecimal(cursor.getDouble(2)));
                } finally {
                    cursor.close();
                }
            }
        }
    }

    public synchronized Single<ListSumsByCabbage> getGroupedSumsRx(FilterListHelper filterListHelper,
                                                         boolean takeSearchString,
                                                         ArrayList<Long> selectedIDs,
                                                         Context context) {
        return Single.fromCallable(() -> getGroupedSums(filterListHelper, takeSearchString, selectedIDs, context));
    }

    /**
     * @param filterListHelper - автоматически добавляет фильтр исключающий закрытые счета
     */
    @SuppressWarnings("unchecked")
    public synchronized ListSumsByCabbage getGroupedSums(FilterListHelper filterListHelper,
                                                         boolean takeSearchString,
                                                         ArrayList<Long> selectedIDs,
                                                         Context context) {
        //Создаем экземпляр результирующей записи "валюта - сумма"
        ListSumsByCabbage listSumsByCabbage = new ListSumsByCabbage();

        //Получаем актуальный список фильтров
        List<AbstractFilter> filterList = filterListHelper.getFilters();

        if (filterListHelper.getSearchString().isEmpty() || !takeSearchString) {
            createTempTransactionsTable(filterList, context);
        } else {
            createTempTransactionsTable(filterList, Translit.toTranslit(filterListHelper.getSearchString().toLowerCase()).replaceAll("'", "''"), context);
        }

        HashSet<Long> accIDs = FilterManager.getAccountIDsFromFilters(filterList, AccountsDAO.getInstance(context).getAllModelsIDs());
        String accIDsString = TextUtils.join(",", accIDs);

        String selectedTransactionsTable = "";
        if (selectedIDs != null && !selectedIDs.isEmpty()) {
            mDatabase.execSQL("DROP TABLE IF EXISTS temp_sel_Transactions;");
            mDatabase.execSQL("CREATE TEMP TABLE temp_sel_Transactions (_id INTEGER NOT NULL PRIMARY KEY)");
            mDatabase.beginTransaction();
            ContentValues cv = new ContentValues();
            for (Long id : selectedIDs) {
                cv.put("_id", id);
                mDatabase.insert("temp_sel_Transactions", null, cv);
            }
            mDatabase.setTransactionSuccessful();
            mDatabase.endTransaction();
            selectedTransactionsTable = " AS t INNER JOIN temp_sel_Transactions AS st ON t._id = st._id ";
        }

        String sql = "SELECT\n" +
                "c._id,\n" +
                "IFNULL((SELECT TOTAL(CASE WHEN Income = 1 THEN Amount ELSE 0 END) FROM temp_all_Transactions" + selectedTransactionsTable + " WHERE c._id = Currency AND NOT (ExcludeTransfer = 1)), 0) AS InAmountSum,\n" +
                "IFNULL((SELECT TOTAL(CASE WHEN Income = 0 THEN Amount ELSE 0 END) FROM temp_all_Transactions" + selectedTransactionsTable + " WHERE c._id = Currency AND NOT (ExcludeTransfer = 1)), 0) AS OutAmountSum,\n" +
                "IFNULL((SELECT TOTAL(StartBalance) FROM ref_Accounts WHERE CurrencyId = c._id AND _id IN (" + accIDsString + ")), 0) / " + CurrencyScale.divisorSql("c.DecimalCount") + " AS StartBalance\n" +
                "FROM ref_Currencies c\n" +
                "ORDER BY c.OrderNumber ASC";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }

        Cursor cursor = mDatabase.rawQuery(sql, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    SumsByCabbage sumsByCabbage;
                    while (!cursor.isAfterLast()) {
                        sumsByCabbage = new SumsByCabbage(cursor.getLong(0),
                                new BigDecimal(cursor.getDouble(1)),
                                new BigDecimal(cursor.getDouble(2)));
                        sumsByCabbage.setStartBalance(new BigDecimal(cursor.getDouble(3)));
                        listSumsByCabbage.getmList().add(sumsByCabbage);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return listSumsByCabbage;
    }

    public synchronized Single<List<SummaryItem>> getSummaryGroupedSumsRx(@NonNull List<SummaryItem> intervals,
                                                   FilterListHelper filterListHelper,
                                                   @NonNull Context context){
        return Single.fromCallable(() -> getSummaryGroupedSums(intervals, filterListHelper, context));
    }

    @SuppressWarnings("unchecked")
    private synchronized List<SummaryItem> getSummaryGroupedSums(@NonNull List<SummaryItem> intervals,
                                                                 FilterListHelper filterListHelper,
                                                                 @NonNull Context context) {
        SummaryItem summaryItem;

        //Получаем список фильтров
        List<AbstractFilter> filterList = filterListHelper.getFilters();

        createTempTransactionsTable(filterList, context);

        int intervalStart;
        int intervalEnd;
        Date minDate;
        Date maxDate;
        SumsByCabbage sumsByCabbage;
        Cursor cursor;

        for (int j = 0; j < intervals.size(); j++) {
            summaryItem = intervals.get(j);
            intervalStart = intervals.get(j).getIntervalFirst();
            intervalEnd = intervals.get(j).getIntervalSecond();
            minDate = DateRangeFilter.getRangeStart(intervalStart, intervalEnd, context);
            maxDate = DateRangeFilter.getRangeEnd(intervalStart, intervalEnd, context);
            cursor = mDatabase.rawQuery("SELECT\n" +
                    "Currency,\n" +
                    "TOTAL(CASE WHEN Income = 1 THEN Amount ELSE 0 END) AS InAmountSum,\n" +
                    "TOTAL(CASE WHEN Income = 0 THEN Amount ELSE 0 END) AS OutAmountSum\n" +
                    "FROM temp_all_Transactions\n" +
                    "WHERE DateTime >= " + String.valueOf(minDate.getTime()) + " AND DateTime <= " + String.valueOf(maxDate.getTime()) + " AND NOT (ExcludeTransfer = 1)\n" +
                    "GROUP BY Currency;", null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        while (!cursor.isAfterLast()) {
                            sumsByCabbage = new SumsByCabbage(cursor.getLong(0), new BigDecimal(cursor.getDouble(1)), new BigDecimal(cursor.getDouble(2)));
                            sumsByCabbage.setStartBalance(new BigDecimal(cursor.getDouble(0)));
                            summaryItem.getListSumsByCabbage().getmList().add(sumsByCabbage);
                            cursor.moveToNext();
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return intervals;
    }

    private String getEntityField(int modelType) throws Exception {
        switch (modelType) {
            case IAbstractModel.MODEL_TYPE_ACCOUNT:
                return "Account";
            case IAbstractModel.MODEL_TYPE_CATEGORY:
                return DBHelper.C_LOG_TRANSACTIONS_CATEGORY;
            case IAbstractModel.MODEL_TYPE_PROJECT:
                return DBHelper.C_LOG_TRANSACTIONS_PROJECT;
            case IAbstractModel.MODEL_TYPE_SIMPLEDEBT:
                return DBHelper.C_LOG_TRANSACTIONS_SIMPLEDEBT;
            case IAbstractModel.MODEL_TYPE_PAYEE:
                return DBHelper.C_LOG_TRANSACTIONS_PAYEE;
            case IAbstractModel.MODEL_TYPE_LOCATION:
                return DBHelper.C_LOG_TRANSACTIONS_LOCATION;
            case IAbstractModel.MODEL_TYPE_DEPARTMENT:
                return DBHelper.C_LOG_TRANSACTIONS_DEPARTMENT;
            default:
                throw new Exception();
        }
    }

    public synchronized LongSparseArray<LongSparseArray<IAbstractModel>> getEntityReport(int modelType, FilterListHelper filterListHelper, Context context) throws Exception {
        LongSparseArray<LongSparseArray<IAbstractModel>> map = new LongSparseArray<>();
        AbstractDAO abstractDAO = BaseDAO.getDAO(modelType, context);
        if (abstractDAO == null) return map;
        List<AbstractFilter> filterList = filterListHelper.getFilters();
        long cabbageID;
        long modelID;
        IAbstractModel model;
        LongSparseArray<IAbstractModel> models;
        String entityField = getEntityField(modelType);
        String excludeTransfers = modelType == IAbstractModel.MODEL_TYPE_ACCOUNT ? "" : "WHERE  ExcludeTransfer != 1 ";

        createTempTransactionsTable(filterList, context);

        String sql = "SELECT\n" +
                "Currency, " + entityField + ",\n" +
                "TOTAL(CASE WHEN Income = 1 THEN Amount ELSE 0 END) AS InAmountSum,\n" +
                "TOTAL(CASE WHEN Income = 0 THEN Amount ELSE 0 END) AS OutAmountSum\n" +
                "FROM temp_all_Transactions\n" +
                excludeTransfers +
                "GROUP BY Currency, " + entityField + ";";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }

        Cursor cursor = mDatabase.rawQuery(sql, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        cabbageID = cursor.getLong(0);
                        modelID = cursor.getLong(1);
                        if (modelID > 0) {
                            model = abstractDAO.getModelById(modelID);
                        } else {
                            model = abstractDAO.createEmptyModel();
                        }
                        model.setIncome(new BigDecimal(cursor.getDouble(2)).abs());
                        model.setExpense(new BigDecimal(cursor.getDouble(3)).abs());
                        if (map.indexOfKey(cabbageID) < 0) {
                            map.put(cabbageID, new LongSparseArray<>());
                        }
                        models = map.get(cabbageID);
                        models.put(model.getID(), model);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return map;
    }

    public synchronized LinkedHashMap<Long, List<DateEntry>> getCommonDateReport(String datePattern, String dateFormat, FilterListHelper filterListHelper, Context context) throws Exception {
        LinkedHashMap<Long, List<DateEntry>> map = new LinkedHashMap<>();
        List<AbstractFilter> filterList = filterListHelper.getFilters();
        Date date;
        BigDecimal income;
        BigDecimal expense;
        long cabbageID;
        List<DateEntry> entries;
        @SuppressLint("SimpleDateFormat") DateFormat df = new SimpleDateFormat(dateFormat);

        createTempTransactionsTable(filterList, context);

        String sql = "SELECT \n" +
                "Currency, \n" +
                "strftime('" + datePattern + "', DateTime/1000, 'unixepoch', 'localtime') AS Date,\n" +
                "TOTAL(CASE WHEN Income = 1 THEN Amount ELSE 0 END) AS InAmountSum,\n" +
                "TOTAL(CASE WHEN Income = 0 THEN Amount ELSE 0 END) AS OutAmountSum\n" +
                "FROM temp_all_Transactions\n" +
                "WHERE  ExcludeTransfer != 1\n" +
                "GROUP BY Currency, strftime('" + datePattern + "', DateTime/1000, 'unixepoch', 'localtime');";
        if (BuildConfig.DEBUG) {
            Log.d(SQLTAG, sql);
        }

        Cursor cursor = mDatabase.rawQuery(sql, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        cabbageID = cursor.getLong(0);
                        date = df.parse(cursor.getString(1));
                        income = new BigDecimal(cursor.getDouble(2)).abs();
                        expense = new BigDecimal(cursor.getDouble(3)).abs();
                        if (!map.containsKey(cabbageID)) {
                            map.put(cabbageID, new ArrayList<>());
                        }
                        entries = map.get(cabbageID);
                        entries.add(new DateEntry(date, income, expense));
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return map;
    }

    public Transaction hasDuplicates(Transaction transaction) {
        /*
        SELECT *
        FROM log_Transactions
        WHERE
            SrcAccount = 1 AND
            ROUND(Amount,2) = -134 AND
            DATETIME(DateTime) > DATETIME('2016-02-22 00:00:00') AND
            DATETIME(DateTime) < DATETIME('2016-02-22 20:00:00')
        */
//        DateTimeFormatter df = DateTimeFormatter.getInstance(context);
        String select = String.format(/*"SELECT * \n" +
                        "FROM %s \n" +
                        "WHERE \n" +*/
                "    %s = %s AND\n" +
                        "    %s = %s AND\n" +
                        "    %s > '%s' AND \n" +
                        "    %s < '%s' AND \n" +
                        "    Deleted = 0", /*DBHelper.T_LOG_TRANSACTIONS,*/
                DBHelper.C_LOG_TRANSACTIONS_SRCACCOUNT, String.valueOf(transaction.getAccountID()),
                DBHelper.C_LOG_TRANSACTIONS_AMOUNT, String.valueOf(CurrencyScale.toMinor(transaction.getAmount(), CurrencyScale.forAccount(mDatabase, transaction.getAccountID()))),
                DBHelper.C_LOG_TRANSACTIONS_DATETIME, String.valueOf(transaction.getDateTime().getTime() - 60000 * 30),
                DBHelper.C_LOG_TRANSACTIONS_DATETIME, String.valueOf(transaction.getDateTime().getTime() + 60000 * 30));

//        Log.d(TAG, select);


        Cursor cursor = mDatabase.query(T_LOG_TRANSACTIONS, null,
                select, null,
                null, null, null);

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            Transaction existsTransaction = cursorToTransaction(cursor);
            cursor.close();

            return existsTransaction;
        } else {
            cursor.close();

            return new Transaction(-1);
        }
    }

    public Transaction getLastTransactionForFN(Transaction transaction) {
        Transaction lastTransaction = new Transaction(-1);
        String sql = "SELECT * FROM log_Transactions "+
                "WHERE Deleted = 0 AND FN =" + String.valueOf(transaction.getFN()) + "\n" +
                "ORDER BY DateTime DESC\n" +
                "LIMIT 1";
        Cursor cursor;
        try {
            cursor = mDatabase.rawQuery(sql, null);
        } catch (Exception e) {
            return lastTransaction;
        }
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        lastTransaction = cursorToTransaction(cursor);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return lastTransaction;
    }

    public boolean isTransactionLastForAccount(Transaction transaction) {
        /*
        SELECT *
        FROM log_Transactions
        WHERE
            SrcAccount = 1 AND
            DateTime > 123456789)
        */
        String select = String.format("%s = %s AND %s > '%s' AND Deleted = 0",
                DBHelper.C_LOG_TRANSACTIONS_SRCACCOUNT, String.valueOf(transaction.getAccountID()),
                DBHelper.C_LOG_TRANSACTIONS_DATETIME, String.valueOf(transaction.getDateTime()));


        Cursor cursor = mDatabase.query(T_LOG_TRANSACTIONS, null,
                select, null,
                null, null, null);

        boolean result = cursor.getCount() == 0;
        cursor.close();

        return result;
    }

    public Pair<Date, Date> getFullDateRange() {
        Date first = new Date();
        Date last = new Date();
        String minCol = String.format("MIN(%s) AS minDate", DBHelper.C_LOG_TRANSACTIONS_DATETIME);
        String maxCol = String.format("MAX(%s) AS maxDate", DBHelper.C_LOG_TRANSACTIONS_DATETIME);

        Cursor cursor = mDatabase.query(T_LOG_TRANSACTIONS, new String[]{minCol, maxCol},
                "Deleted = 0", null, null, null, null);
        if (cursor.getCount() == 0) {
            cursor.close();
            return new Pair<>(first, last);
        }
        cursor.moveToFirst();
        first = new Date(cursor.getLong(cursor.getColumnIndexOrThrow("minDate")));
        last = new Date(cursor.getLong(cursor.getColumnIndexOrThrow("maxDate")));
        cursor.close();

        return new Pair<>(first, last);
    }
}
