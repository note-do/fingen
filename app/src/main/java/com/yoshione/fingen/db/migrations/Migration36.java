package com.yoshione.fingen.db.migrations;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteStatement;
import android.os.StatFs;
import android.util.Log;

import com.yoshione.fingen.db.DbUpgradeException;
import com.yoshione.fingen.db.DbUpgradeGate;
import com.yoshione.fingen.db.SchemaV36;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Миграция 35 -> 36: внешние ключи, целочисленные суммы, переименование колонок.
 * <ul>
 * <li>пустая ссылка -1 становится NULL; висящие ссылки очищаются;</li>
 * <li>суммы REAL переводятся в INTEGER в минорных единицах валюты счета (HALF_UP), курс - ×10^6, количество - ×10^3;</li>
 * <li>колонки FBID, TS, Dirty, LastEdited, ref_Categories.Sign, ref_Locations.Radius, ref_Debts.SrcAccount удаляются;</li>
 * <li>DateTime входящих SMS становится UTC-миллисекундами;</li>
 * <li>все таблицы пересобираются в порядке родители - дети (X_new, INSERT, DROP X, RENAME), _id и sqlite_sequence сохраняются;</li>
 * <li>log_Running_Balance пересчитывается.</li>
 * </ul>
 * Выполняется внутри транзакции onUpgrade при выключенных внешних ключах; любое исключение откатывает всю миграцию.
 * {@link #prepare(SQLiteDatabase)} (место на диске и копия fingen.db.pre36) вызывается из DBHelper.onUpgrade до первого шага цепочки.
 */
public class Migration36 implements DbMigration {

    private static final String TAG = "Migration36";
    public static final String PRE_COPY_SUFFIX = ".pre36";
    /** Копия pre36 (1x), новые таблицы и журнал при пересборке (около 2x), запас на рост файла (0.5x). */
    private static final double SPACE_FACTOR = 3.5;
    private static final long SPACE_RESERVE = 5L * 1024 * 1024;
    private static final int RATE_DIGITS = 12;
    private static final BigDecimal LONG_LIMIT = new BigDecimal("9000000000000000000");

    // Счетчики для сверки: сколько строк переведено в удаленные и сколько удалено физически
    private final Map<String, Long> mMarkedDeleted = new HashMap<>();
    private final Map<String, Long> mHardDeletedActive = new HashMap<>();
    private final Map<String, Long> mHardDeletedTotal = new HashMap<>();
    private final Map<String, Long> mCreated = new HashMap<>();
    /** Служебные строки, созданные миграцией с Deleted = 0 (входят и в total, и в active). */
    private final Map<String, Long> mCreatedActive = new HashMap<>();
    private final Set<String> mRenamedTables = new HashSet<>();

    private final Map<Long, Integer> mAccountDigits = new HashMap<>();
    private final Map<String, Long> mPlaceholders = new HashMap<>();
    private long mMigrationTime;

    //<editor-fold desc="prepare: free space and pre-copy">
    private static long requiredBytes(long dbSize) {
        return (long) (dbSize * SPACE_FACTOR) + SPACE_RESERVE;
    }

    /**
     * Проверяет свободное место (нужно 3.5 размера БД + запас: копия, рост файла и журнал) и копирует файл БД в fingen.db.pre36 рядом с БД.
     * Копия делается до первой записи миграции, поэтому содержит исходную версию.
     *
     * @throws DbUpgradeException CODE_NO_SPACE, если места не хватает (onUpgrade откатывается, БД не меняется)
     */
    public static void prepare(SQLiteDatabase db) {
        DbUpgradeGate.setProgress(0, DbUpgradeGate.STAGE_PREPARE);
        File dbFile = new File(db.getPath());
        long size = dbFile.length();
        long required = requiredBytes(size);
        File dir = dbFile.getParentFile();
        long available;
        try {
            available = new StatFs(dir.getPath()).getAvailableBytes();
        } catch (RuntimeException e) {
            throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Can't read free space of " + dir, e);
        }
        if (available < required) {
            throw new DbUpgradeException(DbUpgradeException.CODE_NO_SPACE,
                    "Not enough free space: need " + required + ", available " + available, required);
        }
        File copy = new File(dbFile.getPath() + PRE_COPY_SUFFIX);
        try (FileInputStream in = new FileInputStream(dbFile); FileOutputStream out = new FileOutputStream(copy)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            copy.delete();
            throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Can't copy database to " + copy.getName(), e);
        }
        Log.i(TAG, "Pre-migration copy " + copy.getPath() + " (" + copy.length() + " bytes)");
    }
    //</editor-fold>

    @Override
    public void migrate(SQLiteDatabase db, Context context) {
        mMigrationTime = System.currentTimeMillis();
        DbUpgradeGate.setProgress(10, DbUpgradeGate.STAGE_CONVERT);
        try {
            requireForeignKeysOff(db);
            Map<String, long[]> before = snapshotCounts(db);

            fixCurrencies(db);
            ensureDefaultProduct(db);
            cleanup(db);
            loadAccountDigits(db);
            Map<String, Map<Long, Long>> sumsBefore = new HashMap<>();
            Map<String, Map<Long, BigDecimal>> exactBefore = new HashMap<>();
            snapshotSums(db, sumsBefore, exactBefore);

            rebuildAll(db);

            DbUpgradeGate.setProgress(70, DbUpgradeGate.STAGE_BALANCE);
            Frozen36.rebuildRunningBalance(db);
            // Переименованные дубли: пересчитываем FullName и SearchString (только таблицы с этими колонками)
            for (String table : mRenamedTables) {
                boolean tree = table.equals("ref_Categories") || table.equals("ref_Payees") || table.equals("ref_Projects")
                        || table.equals("ref_Locations") || table.equals("ref_Departments");
                boolean plain = table.equals("ref_Accounts") || table.equals("ref_SimpleDebts") || table.equals("ref_Products");
                if (tree || plain) {
                    Frozen36.updateFullNames(table, tree, db);
                }
            }

            DbUpgradeGate.setProgress(85, DbUpgradeGate.STAGE_VERIFY);
            verify(db, before, sumsBefore, exactBefore);
        } catch (DbUpgradeException e) {
            throw e;
        } catch (SQLiteFullException e) {
            // Диск заполнился во время миграции: транзакция откатится, БД остается версии 35
            throw new DbUpgradeException(DbUpgradeException.CODE_NO_SPACE,
                    "Disk full during migration 35 -> 36: " + e.getMessage(), requiredBytes(new File(db.getPath()).length()));
        } catch (RuntimeException e) {
            throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Migration 35 -> 36 failed: " + e.getMessage(), e);
        }
    }

    private static void requireForeignKeysOff(SQLiteDatabase db) {
        Cursor c = db.rawQuery("PRAGMA foreign_keys", null);
        try {
            if (c.moveToFirst() && c.getInt(0) != 0) {
                // DROP TABLE с включенными ключами удаляет дочерние строки каскадом
                throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Foreign keys must be off during migration 36");
            }
        } finally {
            c.close();
        }
    }

    //<editor-fold desc="Utilities">
    private static long queryLong(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private static long update(SQLiteDatabase db, String sql) {
        SQLiteStatement statement = db.compileStatement(sql);
        try {
            return statement.executeUpdateDelete();
        } finally {
            statement.close();
        }
    }

    private static void add(Map<String, Long> map, String key, long value) {
        Long old = map.get(key);
        map.put(key, (old == null ? 0 : old) + value);
    }

    private static long get(Map<String, Long> map, String key) {
        Long value = map.get(key);
        return value == null ? 0 : value;
    }

    private static void log(String message) {
        Log.i(TAG, message);
    }

    private static long toMinor(double value, int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        BigDecimal result = BigDecimal.valueOf(value).movePointRight(digits).setScale(0, RoundingMode.HALF_UP);
        if (result.abs().compareTo(LONG_LIMIT) > 0) {
            throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Amount is out of range: " + value);
        }
        return result.longValue();
    }

    /** Курс REAL -> целое ×10^12 (HALF_UP); не помещается в long - ошибка миграции с понятным текстом. */
    private static long toRate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        BigDecimal result = BigDecimal.valueOf(value).movePointRight(RATE_DIGITS).setScale(0, RoundingMode.HALF_UP);
        if (result.abs().compareTo(LONG_LIMIT) > 0) {
            String text = "Exchange rate " + BigDecimal.valueOf(value).toPlainString() + " does not fit into INTEGER with scale 1e" + RATE_DIGITS;
            log(text);
            throw new DbUpgradeException(DbUpgradeException.CODE_VERIFY, text);
        }
        return result.longValue();
    }

    private static BigDecimal exactMinor(double value, int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).movePointRight(digits);
    }

    private int digitsOfAccount(long accountId) {
        Integer digits = mAccountDigits.get(accountId);
        return digits == null ? 2 : digits;
    }

    private static final String[] COUNT_TABLES = SchemaV36.TABLES;
    //</editor-fold>

    //<editor-fold desc="Snapshots">
    private Map<String, long[]> snapshotCounts(SQLiteDatabase db) {
        Map<String, long[]> counts = new HashMap<>();
        for (String table : COUNT_TABLES) {
            long total = queryLong(db, "SELECT COUNT(*) FROM " + table);
            long active = table.equals("log_Running_Balance") ? total : queryLong(db, "SELECT COUNT(*) FROM " + table + " WHERE IFNULL(Deleted, 0) = 0");
            counts.put(table, new long[]{total, active});
            log("Before: " + table + " total=" + total + " active=" + active);
        }
        return counts;
    }

    private void loadAccountDigits(SQLiteDatabase db) {
        mAccountDigits.clear();
        Cursor c = db.rawQuery("SELECT a._id, cur.DecimalCount FROM ref_Accounts a LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", null);
        try {
            while (c.moveToNext()) {
                mAccountDigits.put(c.getLong(0), c.isNull(1) ? 2 : c.getInt(1));
            }
        } finally {
            c.close();
        }
    }

    /**
     * Суммы до миграции в минорных единицах (HALF_UP) по валютам, отдельно по сущностям.
     * Ключ второй карты - id валюты (-1 - без валюты).
     */
    private void snapshotSums(SQLiteDatabase db, Map<String, Map<Long, Long>> sums, Map<String, Map<Long, BigDecimal>> exact) {
        sumQuery(db, "accounts.StartBalance", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), a.StartBalance FROM ref_Accounts a "
                + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "accounts.CreditLimit", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), IFNULL(a.CreditLimit, 0) FROM ref_Accounts a "
                + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "transactions.Amount", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), t.Amount FROM log_Transactions t "
                + "JOIN ref_Accounts a ON a._id = t.SrcAccount LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "templates.Amount", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), IFNULL(t.Amount, 0) FROM log_Templates t "
                + "JOIN ref_Accounts a ON a._id = t.SrcAccount LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "products.Price", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), p.Price FROM log_Products p "
                + "JOIN log_Transactions t ON t._id = p.TransactionID JOIN ref_Accounts a ON a._id = t.SrcAccount "
                + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "budget.Amount", "SELECT b.Currency, IFNULL(cur.DecimalCount, 2), b.Amount FROM ref_budget b "
                + "LEFT JOIN ref_Currencies cur ON cur._id = b.Currency", sums, exact);
        sumQuery(db, "budget_debts.Amount", "SELECT a.Currency, IFNULL(cur.DecimalCount, 2), b.Amount FROM ref_budget_debts b "
                + "JOIN ref_Debts d ON d._id = b.Debt JOIN ref_Accounts a ON a._id = d.Account "
                + "LEFT JOIN ref_Currencies cur ON cur._id = a.Currency", sums, exact);
        sumQuery(db, "simpledebts.StartAmount", "SELECT s.Currency, IFNULL(cur.DecimalCount, 2), s.StartAmount FROM ref_SimpleDebts s "
                + "LEFT JOIN ref_Currencies cur ON cur._id = s.Currency", sums, exact);
    }

    private void sumQuery(SQLiteDatabase db, String key, String sql, Map<String, Map<Long, Long>> sums, Map<String, Map<Long, BigDecimal>> exact) {
        Map<Long, Long> result = new HashMap<>();
        Map<Long, BigDecimal> exactResult = new HashMap<>();
        Cursor c = db.rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                long currency = c.isNull(0) ? -1 : c.getLong(0);
                int digits = c.getInt(1);
                double value = c.isNull(2) ? 0 : c.getDouble(2);
                Long old = result.get(currency);
                result.put(currency, (old == null ? 0 : old) + toMinor(value, digits));
                BigDecimal oldExact = exactResult.get(currency);
                exactResult.put(currency, (oldExact == null ? BigDecimal.ZERO : oldExact).add(exactMinor(value, digits)));
            }
        } finally {
            c.close();
        }
        sums.put(key, result);
        exact.put(key, exactResult);
    }

    private Map<Long, Long> sumsAfter(SQLiteDatabase db, String sql) {
        Map<Long, Long> result = new HashMap<>();
        Cursor c = db.rawQuery(sql, null);
        try {
            while (c.moveToNext()) {
                long currency = c.isNull(0) ? -1 : c.getLong(0);
                result.put(currency, c.getLong(1));
            }
        } finally {
            c.close();
        }
        return result;
    }
    //</editor-fold>

    //<editor-fold desc="Currencies and cleanup">
    private void fixCurrencies(SQLiteDatabase db) {
        long negative = update(db, "UPDATE ref_Currencies SET DecimalCount = 2 WHERE DecimalCount IS NULL OR DecimalCount < 0");
        long tooBig = update(db, "UPDATE ref_Currencies SET DecimalCount = 8 WHERE DecimalCount > 8");
        log("Currencies DecimalCount: " + negative + " set to 2 (was -1/NULL), " + tooBig + " set to 8 (was > 8)");
    }

    /** Строка ref_Products(_id = 0) нужна log_Products (ProductId = 0, RESTRICT); без нее cleanup удалил бы строки товаров. */
    private void ensureDefaultProduct(SQLiteDatabase db) {
        long inserted = queryLong(db, "SELECT COUNT(*) FROM ref_Products WHERE _id = 0") == 0 ? 1 : 0;
        update(db, "INSERT OR IGNORE INTO ref_Products (_id, Deleted, Name, SearchString) VALUES (0, 0, 'default_product', '')");
        add(mCreated, "ref_Products", inserted);
        add(mCreatedActive, "ref_Products", inserted);
        long revived = update(db, "UPDATE ref_Products SET Deleted = 0 WHERE _id = 0 AND IFNULL(Deleted, 0) != 0");
        add(mCreatedActive, "ref_Products", revived);
        log("ref_Products(_id=0): " + inserted + " created, " + revived + " restored from deleted");
    }

    private long maxDel(SQLiteDatabase db, String table) {
        return queryLong(db, "SELECT IFNULL(MAX(Deleted), 0) FROM " + table);
    }

    private void cleanup(SQLiteDatabase db) {
        // log_Products: неизвестный ProductID - строка удаляется, отрицательное количество становится 0
        long productsActive = queryLong(db, "SELECT COUNT(*) FROM log_Products WHERE IFNULL(Deleted, 0) = 0 AND (ProductID IS NULL OR ProductID NOT IN (SELECT _id FROM ref_Products))");
        long productsTotal = update(db, "DELETE FROM log_Products WHERE ProductID IS NULL OR ProductID NOT IN (SELECT _id FROM ref_Products)");
        add(mHardDeletedActive, "log_Products", productsActive);
        add(mHardDeletedTotal, "log_Products", productsTotal);
        log("log_Products: " + productsTotal + " rows with unknown ProductID deleted (" + productsActive + " active)");
        log("log_Products: " + update(db, "UPDATE log_Products SET Quantity = 0 WHERE Quantity IS NULL OR Quantity < 0") + " Quantity < 0 set to 0");
        update(db, "UPDATE log_Products SET Price = 0 WHERE Price IS NULL");

        // NOT NULL-ссылки: висящая ссылка - строка помечается удаленной и ссылается на служебную удаленную запись
        orphans(db, "log_Transactions", "SrcAccount", "ref_Accounts");
        logTemplatesWithoutAccount(db);
        orphans(db, "log_Templates", "SrcAccount", "ref_Accounts");
        orphans(db, "ref_Debts", "Account", "ref_Accounts");
        orphans(db, "log_Accounts_Sets", "AccountID", "ref_Accounts");
        orphans(db, "log_Accounts_Sets", "SetID", "ref_Accounts_Sets");
        orphans(db, "ref_budget", "Category", "ref_Categories");
        orphans(db, "ref_budget", "Currency", "ref_Currencies");
        orphans(db, "ref_budget_debts", "Debt", "ref_Debts");
        orphans(db, "log_Products", "TransactionID", "log_Transactions");
        attachSmsToUnknownSender(db);

        // Необязательные ссылки: -1 и висящие ссылки становятся NULL
        nullify(db, "ref_Accounts", "Currency", "ref_Currencies");
        nullify(db, "ref_SimpleDebts", "Currency", "ref_Currencies");
        nullifyParent(db, "ref_Categories");
        nullifyParent(db, "ref_Payees");
        nullifyParent(db, "ref_Projects");
        nullifyParent(db, "ref_Locations");
        nullifyParent(db, "ref_Departments");
        nullify(db, "ref_Payees", "DefCategory", "ref_Categories");
        nullify(db, "log_Transactions", "DestAccount", "ref_Accounts");
        nullify(db, "log_Transactions", "Payee", "ref_Payees");
        nullify(db, "log_Transactions", "Category", "ref_Categories");
        nullify(db, "log_Transactions", "Project", "ref_Projects");
        nullify(db, "log_Transactions", "SimpleDebt", "ref_SimpleDebts");
        nullify(db, "log_Transactions", "Department", "ref_Departments");
        nullify(db, "log_Transactions", "Location", "ref_Locations");
        nullify(db, "log_Templates", "DestAccount", "ref_Accounts");
        nullify(db, "log_Templates", "Payee", "ref_Payees");
        nullify(db, "log_Templates", "Category", "ref_Categories");
        nullify(db, "log_Templates", "Project", "ref_Projects");
        nullify(db, "log_Templates", "Department", "ref_Departments");
        nullify(db, "log_Templates", "Location", "ref_Locations");
        nullify(db, "log_Products", "CategoryID", "ref_Categories");
        nullify(db, "log_Products", "ProjectID", "ref_Projects");
        nullify(db, "ref_Debts", "Payee", "ref_Payees");
        nullify(db, "ref_Debts", "Category", "ref_Categories");

        // Дубли по новым уникальным индексам среди Deleted = 0
        dedupe(db, "ref_Currencies", "Code", null);
        assignDefaultCurrency(db);
        dedupe(db, "ref_Accounts", "Name", null);
        dedupe(db, "ref_Accounts_Sets", "Name", null);
        dedupe(db, "ref_Categories", "Name", "IFNULL(ParentID, 0)");
        dedupe(db, "ref_Payees", "Name", "IFNULL(ParentID, 0)");
        dedupe(db, "ref_Projects", "Name", "IFNULL(ParentID, 0)");
        dedupe(db, "ref_Locations", "Name", "IFNULL(ParentID, 0)");
        dedupe(db, "ref_Departments", "Name", "IFNULL(ParentID, 0)");
        dedupe(db, "ref_SimpleDebts", "Name", null);
        dedupe(db, "ref_Products", "Name", null);
        dedupe(db, "ref_Senders", "Name", null);
        dedupe(db, "ref_Senders", "PhoneNo", null);
        dedupeDebts(db);
    }

    /**
     * Счета без валюты (NULL, -1 или висящая ссылка) получают валюту по умолчанию: живая RUB, иначе первая живая по _id,
     * иначе создается RUB. Выполняется после очистки ссылок и дублей валют и до loadAccountDigits/snapshotSums, поэтому
     * суммы переводятся в минорные единицы уже по знакам этой валюты (в v35 суммы REAL хранятся в основных единицах,
     * пересчета значений не требуется; для RUB знаков 2, как у счета без валюты). Это UPDATE: total/active счетов не меняются.
     */
    private void assignDefaultCurrency(SQLiteDatabase db) {
        String where = " WHERE Currency IS NULL OR Currency < 0 OR Currency NOT IN (SELECT _id FROM ref_Currencies)";
        long count = queryLong(db, "SELECT COUNT(*) FROM ref_Accounts" + where);
        if (count == 0) return;
        long id = queryLong(db, "SELECT IFNULL(MIN(_id), 0) FROM ref_Currencies WHERE Code = 'RUB' AND IFNULL(Deleted, 0) = 0");
        String source = "RUB";
        if (id == 0) {
            id = queryLong(db, "SELECT IFNULL(MIN(_id), 0) FROM ref_Currencies WHERE IFNULL(Deleted, 0) = 0");
            source = "first live currency";
        }
        if (id == 0) {
            ContentValues cv = new ContentValues();
            cv.put("Deleted", 0);
            cv.put("Code", "RUB");
            cv.put("Symbol", "₽");
            cv.put("Name", "Russian Ruble");
            cv.put("DecimalCount", 2);
            cv.put("OrderNumber", 0);
            id = db.insertOrThrow("ref_Currencies", null, cv);
            add(mCreated, "ref_Currencies", 1);
            add(mCreatedActive, "ref_Currencies", 1);
            source = "created RUB";
        }
        long assigned = update(db, "UPDATE ref_Accounts SET Currency = " + id + where);
        log("ref_Accounts.Currency: default currency _id=" + id + " (" + source + ") assigned to " + assigned + " accounts without currency");
    }

    /** Шаблоны без счета-источника помечаются удаленными (orphans); имя и _id пишутся в лог для возможного восстановления. */
    private void logTemplatesWithoutAccount(SQLiteDatabase db) {
        Cursor c = db.rawQuery("SELECT _id, Name, IFNULL(Deleted, 0) FROM log_Templates WHERE SrcAccount IS NULL OR "
                + "SrcAccount NOT IN (SELECT _id FROM ref_Accounts) ORDER BY _id", null);
        try {
            log("log_Templates: " + c.getCount() + " templates without source account will be marked deleted");
            while (c.moveToNext()) {
                log("log_Templates without account: _id=" + c.getLong(0) + ", name='" + c.getString(1) + "', deleted=" + c.getLong(2));
            }
        } finally {
            c.close();
        }
    }

    /** SMS с Sender 0, -1 или висящей ссылкой остаются видимыми: привязываются к служебному активному отправителю "(unknown)". */
    private void attachSmsToUnknownSender(SQLiteDatabase db) {
        String where = " WHERE Sender IS NULL OR Sender NOT IN (SELECT _id FROM ref_Senders)";
        long count = queryLong(db, "SELECT COUNT(*) FROM log_Incoming_SMS" + where);
        if (count == 0) return;
        Set<String> names = new HashSet<>();
        Set<String> phones = new HashSet<>();
        Cursor c = db.rawQuery("SELECT Name, PhoneNo FROM ref_Senders WHERE IFNULL(Deleted, 0) = 0", null);
        try {
            while (c.moveToNext()) {
                names.add(c.isNull(0) ? "" : c.getString(0));
                phones.add(c.isNull(1) ? "" : c.getString(1));
            }
        } finally {
            c.close();
        }
        String name = "(unknown)";
        int n = 2;
        while (names.contains(name)) {
            name = "(unknown) " + n++;
        }
        String phone = "unknown";
        n = 2;
        while (phones.contains(phone)) {
            phone = "unknown " + n++;
        }
        ContentValues values = new ContentValues();
        values.put("Deleted", 0);
        values.put("Name", name);
        values.put("PhoneNo", phone);
        values.put("AmountPos", 0);
        values.put("BalancePos", 1);
        values.put("DateFormat", "");
        values.put("LeadingCurrencySymbol", 0);
        values.put("IsActive", 1);
        long id = db.insert("ref_Senders", null, values);
        if (id < 0) {
            throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "Cannot create service sender for SMS without sender");
        }
        add(mCreated, "ref_Senders", 1);
        add(mCreatedActive, "ref_Senders", 1);
        update(db, "UPDATE log_Incoming_SMS SET Sender = " + id + where);
        log("log_Incoming_SMS.Sender: " + count + " SMS without valid sender attached to service sender '" + name
                + "' (_id=" + id + "), kept visible");
    }

    private void nullify(SQLiteDatabase db, String table, String column, String parent) {
        if (isNotNull(db, table, column)) {
            // Колонка NOT NULL в старой схеме: NULL допустим только после пересборки (см. copyRef и rebuildAll)
            long n = queryLong(db, "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NOT NULL AND ("
                    + column + " < 0 OR " + column + " NOT IN (SELECT _id FROM " + parent + "))");
            if (n > 0) {
                log(table + "." + column + ": NOT NULL in old schema, " + n + " empty or dangling references will be set to NULL on rebuild");
            }
            return;
        }
        long n = update(db, "UPDATE " + table + " SET " + column + " = NULL WHERE " + column + " IS NOT NULL AND ("
                + column + " < 0 OR " + column + " NOT IN (SELECT _id FROM " + parent + "))");
        if (n > 0) {
            log(table + "." + column + ": " + n + " empty or dangling references set to NULL");
        }
    }

    private void nullifyParent(SQLiteDatabase db, String table) {
        long n = update(db, "UPDATE " + table + " SET ParentID = NULL WHERE ParentID IS NOT NULL AND ("
                + "ParentID < 0 OR ParentID = _id OR ParentID NOT IN (SELECT _id FROM " + table + "))");
        if (n > 0) {
            log(table + ".ParentID: " + n + " empty, self or dangling references set to NULL");
        }
    }

    /** Строки с висящей ссылкой в NOT NULL-колонке: Deleted = ++maxDel, ссылка на служебную удаленную запись. */
    private void orphans(SQLiteDatabase db, String table, String column, String parent) {
        List<Long> ids = new ArrayList<>();
        List<Boolean> wasActive = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT _id, IFNULL(Deleted, 0) FROM " + table + " WHERE " + column + " IS NULL OR "
                + column + " NOT IN (SELECT _id FROM " + parent + ")", null);
        try {
            while (c.moveToNext()) {
                ids.add(c.getLong(0));
                wasActive.add(c.getLong(1) == 0);
            }
        } finally {
            c.close();
        }
        if (ids.isEmpty()) return;
        long placeholder = placeholder(db, parent);
        long del = maxDel(db, table);
        long active = 0;
        for (int i = 0; i < ids.size(); i++) {
            del++;
            update(db, "UPDATE " + table + " SET " + column + " = " + placeholder + ", Deleted = " + del + " WHERE _id = " + ids.get(i));
            if (wasActive.get(i)) active++;
        }
        add(mMarkedDeleted, table, active);
        log(table + "." + column + ": " + ids.size() + " dangling references, rows marked deleted (" + active + " were active)");
    }

    /** Служебная удаленная запись родительской таблицы (создается один раз, Deleted &gt; 0). */
    private long placeholder(SQLiteDatabase db, String parent) {
        Long existing = mPlaceholders.get(parent);
        if (existing != null) return existing;
        ContentValues cv = new ContentValues();
        cv.put("Deleted", maxDel(db, parent) + 1);
        switch (parent) {
            case "ref_Accounts":
                cv.put("Type", 0);
                cv.put("Name", "(lost)");
                cv.putNull("Currency");
                cv.put("StartBalance", 0);
                cv.put("IsClosed", 1);
                break;
            case "ref_Accounts_Sets":
                cv.put("Name", "(lost)");
                break;
            case "ref_Categories":
                cv.put("Name", "(lost)");
                cv.put("COLOR", "#808080");
                cv.put("OrderNumber", 0);
                cv.put("Sign", 1);
                break;
            case "ref_Currencies":
                cv.put("Code", "(lost)");
                cv.put("Symbol", "?");
                cv.put("Name", "(lost)");
                cv.put("DecimalCount", 2);
                cv.put("OrderNumber", 0);
                break;
            case "ref_Debts":
                cv.put("Account", placeholder(db, "ref_Accounts"));
                cv.put("Payee", -1);
                cv.put("Category", -1);
                cv.put("Closed", 1);
                break;
            case "log_Transactions":
                cv.put("DateTime", 0);
                cv.put("SrcAccount", placeholder(db, "ref_Accounts"));
                cv.put("Amount", 0);
                cv.put("DestAccount", -1);
                cv.put("ExchangeRate", 1);
                cv.put("AutoCreated", 0);
                break;
            case "ref_Senders":
                cv.put("Name", "(lost)");
                cv.put("PhoneNo", "(lost)");
                cv.put("IsActive", 0);
                break;
            default:
                throw new DbUpgradeException(DbUpgradeException.CODE_FAILED, "No placeholder for " + parent);
        }
        long id = db.insertOrThrow(parent, null, cv);
        mPlaceholders.put(parent, id);
        add(mCreated, parent, 1);
        log("Placeholder row " + id + " created in " + parent);
        return id;
    }

    private void dedupe(SQLiteDatabase db, String table, String column, String partition) {
        String partitionExpr = partition == null ? "0" : partition;
        List<Long> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        Map<String, Set<String>> used = new HashMap<>();
        Cursor c = db.rawQuery("SELECT _id, " + column + ", " + partitionExpr + " FROM " + table
                + " WHERE IFNULL(Deleted, 0) = 0 ORDER BY _id", null);
        try {
            while (c.moveToNext()) {
                String name = c.isNull(1) ? "" : c.getString(1);
                String key = c.getString(2);
                ids.add(c.getLong(0));
                names.add(name);
                keys.add(key);
                Set<String> set = used.get(key);
                if (set == null) {
                    set = new HashSet<>();
                    used.put(key, set);
                }
                set.add(name);
            }
        } finally {
            c.close();
        }
        Map<String, Set<String>> seen = new HashMap<>();
        long renamed = 0;
        for (int i = 0; i < ids.size(); i++) {
            String key = keys.get(i);
            Set<String> seenSet = seen.get(key);
            if (seenSet == null) {
                seenSet = new HashSet<>();
                seen.put(key, seenSet);
            }
            String name = names.get(i);
            if (seenSet.add(name)) continue;
            int n = 2;
            String candidate = name + " (" + n + ")";
            while (used.get(key).contains(candidate)) {
                n++;
                candidate = name + " (" + n + ")";
            }
            used.get(key).add(candidate);
            seenSet.add(candidate);
            ContentValues cv = new ContentValues();
            cv.put(column, candidate);
            db.update(table, cv, "_id = ?", new String[]{String.valueOf(ids.get(i))});
            renamed++;
        }
        if (renamed > 0) {
            mRenamedTables.add(table);
            log(table + "." + column + ": " + renamed + " duplicates renamed");
        }
    }

    /** ref_Debts: один долг на счет среди активных (в старой схеме это гарантировал UNIQUE), лишние помечаются удаленными. */
    private void dedupeDebts(SQLiteDatabase db) {
        List<Long> extra = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT _id, Account FROM ref_Debts WHERE IFNULL(Deleted, 0) = 0 ORDER BY _id", null);
        try {
            Set<Long> seen = new HashSet<>();
            while (c.moveToNext()) {
                if (!seen.add(c.getLong(1))) {
                    extra.add(c.getLong(0));
                }
            }
        } finally {
            c.close();
        }
        long del = maxDel(db, "ref_Debts");
        for (Long id : extra) {
            del++;
            update(db, "UPDATE ref_Debts SET Deleted = " + del + " WHERE _id = " + id);
        }
        if (!extra.isEmpty()) {
            add(mMarkedDeleted, "ref_Debts", extra.size());
            log("ref_Debts: " + extra.size() + " duplicate debts for one account marked deleted");
        }
    }
    //</editor-fold>

    //<editor-fold desc="Rebuild">
    private long readSeq(SQLiteDatabase db, String table) {
        Cursor c = db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = ?", new String[]{table});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    private interface Populate {
        void run(String newTable);
    }

    private void recreate(SQLiteDatabase db, String table, Populate populate) {
        long seq = readSeq(db, table);
        String tmp = table + "_new";
        db.execSQL("DROP TABLE IF EXISTS " + tmp);
        db.execSQL(SchemaV36.createTableSql(table, tmp));
        populate.run(tmp);
        db.execSQL("DROP TABLE " + table);
        db.execSQL("ALTER TABLE " + tmp + " RENAME TO " + table);
        if (!table.equals("log_Running_Balance")) {
            long changed = update(db, "UPDATE sqlite_sequence SET seq = MAX(seq, " + seq + ") WHERE name = '" + table + "'");
            if (changed == 0 && seq > 0) {
                db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES ('" + table + "', " + seq + ")");
            }
        }
        for (String sql : SchemaV36.createIndexesSql(table)) {
            db.execSQL(sql);
        }
        log("Rebuilt " + table);
    }

    /** INSERT ... SELECT без перевода значений. */
    private void copySql(SQLiteDatabase db, String table, final String newColumns, final String oldExpressions) {
        recreate(db, table, new Populate() {
            @Override
            public void run(String newTable) {
                db.execSQL("INSERT INTO " + newTable + " (" + newColumns + ") SELECT " + oldExpressions + " FROM " + table);
            }
        });
    }

    private interface RowMapper {
        void map(Cursor cursor, ContentValues values);
    }

    private void copyRows(SQLiteDatabase db, String table, final String selectSql, final RowMapper mapper) {
        recreate(db, table, new Populate() {
            @Override
            public void run(String newTable) {
                ContentValues values = new ContentValues();
                Cursor c = db.rawQuery(selectSql, null);
                try {
                    while (c.moveToNext()) {
                        values.clear();
                        mapper.map(c, values);
                        db.insertOrThrow(newTable, null, values);
                    }
                } finally {
                    c.close();
                }
            }
        });
    }

    /** Индекс колонки без учета регистра (в легаси-схемах бывает isActive вместо IsActive). */
    private static int idx(Cursor c, String name) {
        String[] names = c.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            if (names[i].equalsIgnoreCase(name)) return i;
        }
        throw new IllegalArgumentException("column '" + name + "' does not exist");
    }

    /** Колонка объявлена NOT NULL в текущей (старой) схеме таблицы. */
    private static boolean isNotNull(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) {
                if (column.equalsIgnoreCase(c.getString(1))) {
                    return c.getInt(3) != 0;
                }
            }
        } finally {
            c.close();
        }
        return false;
    }

    private final Map<String, Set<Long>> mParentIds = new HashMap<>();
    private final Map<String, Long> mRefNulled = new HashMap<>();

    /** Ссылка из NOT NULL-колонки старой схемы: -1, NULL и висящая ссылка становятся NULL (целевая колонка уже nullable). */
    private void copyRef(SQLiteDatabase db, Cursor c, ContentValues values, String oldName, String newName, String parent) {
        int index = idx(c, oldName);
        Set<Long> ids = mParentIds.get(parent);
        if (ids == null) {
            ids = new HashSet<>();
            Cursor p = db.rawQuery("SELECT _id FROM " + parent, null);
            try {
                while (p.moveToNext()) ids.add(p.getLong(0));
            } finally {
                p.close();
            }
            mParentIds.put(parent, ids);
        }
        if (c.isNull(index) || !ids.contains(c.getLong(index))) {
            if (!c.isNull(index)) add(mRefNulled, newName, 1);
            values.putNull(newName);
        } else {
            values.put(newName, c.getLong(index));
        }
    }

    private static void copy(Cursor c, ContentValues values, String oldName, String newName) {
        int index = idx(c, oldName);
        switch (c.getType(index)) {
            case Cursor.FIELD_TYPE_NULL:
                values.putNull(newName);
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                values.put(newName, c.getLong(index));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                values.put(newName, c.getDouble(index));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                values.put(newName, c.getBlob(index));
                break;
            default:
                values.put(newName, c.getString(index));
        }
    }

    private static void copyInt(Cursor c, ContentValues values, String oldName, String newName, long nullValue) {
        int index = idx(c, oldName);
        values.put(newName, c.isNull(index) ? nullValue : c.getLong(index));
    }

    private static double getDouble(Cursor c, String column) {
        int index = idx(c, column);
        return c.isNull(index) ? 0 : c.getDouble(index);
    }

    private static void common(Cursor c, ContentValues values) {
        copy(c, values, "_id", "_id");
        copyInt(c, values, "Deleted", "Deleted", 0);
    }

    private void rebuildAll(SQLiteDatabase db) {
        copySql(db, "ref_Currencies", "_id, Deleted, Code, Symbol, Name, DecimalCount, OrderNumber",
                "_id, IFNULL(Deleted, 0), Code, Symbol, Name, DecimalCount, OrderNumber");

        copyRows(db, "ref_Accounts", "SELECT * FROM ref_Accounts", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Type", "Type");
                copy(c, v, "Name", "Name");
                copy(c, v, "Currency", "CurrencyId");
                copy(c, v, "Emitent", "Emitent");
                copy(c, v, "Last4Digits", "Last4Digits");
                copy(c, v, "Comment", "Comment");
                copy(c, v, "IsClosed", "IsClosed");
                copy(c, v, "CustomOrder", "OrderNumber");
                copy(c, v, "SearchString", "SearchString");
                int digits = digitsOfAccount(c.getLong(idx(c, "_id")));
                v.put("StartBalance", toMinor(getDouble(c, "StartBalance"), digits));
                if (c.isNull(idx(c, "CreditLimit"))) {
                    v.putNull("CreditLimit");
                } else {
                    v.put("CreditLimit", toMinor(getDouble(c, "CreditLimit"), digits));
                }
            }
        });

        copySql(db, "ref_Accounts_Sets", "_id, Deleted, Name", "_id, IFNULL(Deleted, 0), IFNULL(Name, '')");
        copySql(db, "log_Accounts_Sets", "_id, Deleted, SetId, AccountId", "_id, IFNULL(Deleted, 0), SetID, AccountID");
        copySql(db, "ref_Categories", "_id, Deleted, Name, Color, ParentId, OrderNumber, FullName, SearchString",
                "_id, IFNULL(Deleted, 0), Name, COLOR, ParentID, OrderNumber, FullName, SearchString");
        copySql(db, "ref_Payees", "_id, Deleted, Name, DefaultCategoryId, ParentId, OrderNumber, FullName, SearchString",
                "_id, IFNULL(Deleted, 0), Name, DefCategory, ParentID, OrderNumber, FullName, SearchString");
        copySql(db, "ref_Projects", "_id, Deleted, Name, Color, IsActive, ParentId, OrderNumber, FullName, SearchString",
                "_id, IFNULL(Deleted, 0), Name, Color, IsActive, ParentID, OrderNumber, FullName, SearchString");
        copySql(db, "ref_Locations", "_id, Deleted, Name, Lon, Lat, Address, ParentId, OrderNumber, FullName, SearchString",
                "_id, IFNULL(Deleted, 0), Name, Lon, Lat, Address, ParentID, OrderNumber, FullName, SearchString");
        copySql(db, "ref_Departments", "_id, Deleted, Name, IsActive, ParentId, OrderNumber, FullName, SearchString",
                "_id, IFNULL(Deleted, 0), Name, IsActive, ParentID, OrderNumber, FullName, SearchString");

        copyRows(db, "ref_SimpleDebts", "SELECT * FROM ref_SimpleDebts", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Name", "Name");
                copy(c, v, "IsActive", "IsActive");
                copy(c, v, "Currency", "CurrencyId");
                copy(c, v, "SearchString", "SearchString");
                int digits = currencyDigits(db, c, "Currency");
                v.put("StartAmount", toMinor(getDouble(c, "StartAmount"), digits));
            }
        });

        copySql(db, "ref_Products", "_id, Deleted, Name, SearchString", "_id, IFNULL(Deleted, 0), IFNULL(Name, ''), IFNULL(SearchString, '')");
        copySql(db, "ref_Senders", "_id, Deleted, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, IsActive, AddCreditLimitToBalance",
                "_id, IFNULL(Deleted, 0), Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, IsActive, IFNULL(AddCreditLimitToBalance, 0)");

        final long[] rateZero = new long[1];
        copyRows(db, "log_Transactions", "SELECT * FROM log_Transactions", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "DateTime", "DateTime");
                copy(c, v, "SrcAccount", "SrcAccountId");
                copyRef(db, c, v, "DestAccount", "DestAccountId", "ref_Accounts");
                copy(c, v, "Payee", "PayeeId");
                copy(c, v, "Category", "CategoryId");
                copy(c, v, "Project", "ProjectId");
                copy(c, v, "SimpleDebt", "SimpleDebtId");
                copy(c, v, "Department", "DepartmentId");
                copy(c, v, "Location", "LocationId");
                copy(c, v, "Comment", "Comment");
                copy(c, v, "File", "File");
                copy(c, v, "Lon", "Lon");
                copy(c, v, "Lat", "Lat");
                copy(c, v, "Accuracy", "Accuracy");
                copyInt(c, v, "FN", "FN", 0);
                copyInt(c, v, "FD", "FD", 0);
                copyInt(c, v, "FP", "FP", 0);
                copyInt(c, v, "Split", "IsSplit", 0);
                copyInt(c, v, "AutoCreated", "IsAutoCreated", 0);
                copy(c, v, "SearchString", "SearchString");
                int digits = digitsOfAccount(c.getLong(idx(c, "SrcAccount")));
                v.put("Amount", toMinor(getDouble(c, "Amount"), digits));
                double rate = getDouble(c, "ExchangeRate");
                long rateMinor = toRate(rate);
                if (rateMinor == 0 && rate != 0) rateZero[0]++;
                v.put("ExchangeRate", rateMinor);
            }
        });
        if (rateZero[0] > 0) {
            log("log_Transactions: " + rateZero[0] + " exchange rates rounded to 0 (below 1e-12)");
        }

        copyRows(db, "log_Templates", "SELECT * FROM log_Templates", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Name", "Name");
                copy(c, v, "Type", "Type");
                copy(c, v, "SrcAccount", "SrcAccountId");
                copyRef(db, c, v, "DestAccount", "DestAccountId", "ref_Accounts");
                copy(c, v, "Payee", "PayeeId");
                copy(c, v, "Category", "CategoryId");
                copy(c, v, "Project", "ProjectId");
                copy(c, v, "Department", "DepartmentId");
                copy(c, v, "Location", "LocationId");
                copy(c, v, "Comment", "Comment");
                copy(c, v, "SearchString", "SearchString");
                int digits = digitsOfAccount(c.getLong(idx(c, "SrcAccount")));
                if (c.isNull(idx(c, "Amount"))) {
                    v.putNull("Amount");
                } else {
                    v.put("Amount", toMinor(getDouble(c, "Amount"), digits));
                }
                if (c.isNull(idx(c, "ExchangeRate"))) {
                    v.putNull("ExchangeRate");
                } else {
                    v.put("ExchangeRate", toRate(getDouble(c, "ExchangeRate")));
                }
            }
        });

        if (get(mRefNulled, "DestAccountId") > 0) {
            log("log_Transactions/log_Templates.DestAccount: " + get(mRefNulled, "DestAccountId") + " dangling references set to NULL during rebuild");
        }

        // log_Transactions уже новая: счет транзакции берется из SrcAccountId
        copyRows(db, "log_Products", "SELECT p.*, t.SrcAccountId AS _acc FROM log_Products p LEFT JOIN log_Transactions t ON t._id = p.TransactionID", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "TransactionID", "TransactionId");
                copy(c, v, "ProductID", "ProductId");
                copy(c, v, "CategoryID", "CategoryId");
                copy(c, v, "ProjectID", "ProjectId");
                int accIndex = idx(c, "_acc");
                int digits = c.isNull(accIndex) ? 2 : digitsOfAccount(c.getLong(accIndex));
                v.put("Price", toMinor(getDouble(c, "Price"), digits));
                v.put("Quantity", toMinor(getDouble(c, "Quantity"), 3));
            }
        });

        copySql(db, "ref_Debts", "_id, Deleted, AccountId, PayeeId, CategoryId, IsClosed, Comment",
                "_id, IFNULL(Deleted, 0), Account, "
                + "CASE WHEN Payee IS NULL OR Payee < 0 OR Payee NOT IN (SELECT _id FROM ref_Payees) THEN NULL ELSE Payee END, "
                + "CASE WHEN Category IS NULL OR Category < 0 OR Category NOT IN (SELECT _id FROM ref_Categories) THEN NULL ELSE Category END, "
                + "Closed, Comment");
        log("ref_Debts: empty or dangling Payee/Category references set to NULL during rebuild");

        copyRows(db, "ref_budget", "SELECT * FROM ref_budget", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Year", "Year");
                copy(c, v, "Month", "Month");
                copy(c, v, "Category", "CategoryId");
                copy(c, v, "Currency", "CurrencyId");
                int digits = currencyDigits(db, c, "Currency");
                v.put("Amount", toMinor(getDouble(c, "Amount"), digits));
            }
        });

        // ref_Debts уже новая: счет долга берется из AccountId
        copyRows(db, "ref_budget_debts", "SELECT b.*, d.AccountId AS _acc FROM ref_budget_debts b LEFT JOIN ref_Debts d ON d._id = b.Debt", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Year", "Year");
                copy(c, v, "Month", "Month");
                copy(c, v, "Debt", "DebtId");
                int accIndex = idx(c, "_acc");
                int digits = c.isNull(accIndex) ? 2 : digitsOfAccount(c.getLong(accIndex));
                v.put("Amount", toMinor(getDouble(c, "Amount"), digits));
            }
        });

        // Счетчики веток разбора: [0] миллисекунды, [1] yyyyMMdd  HH:mm, [2] не разобрано
        final long[] smsCounts = new long[3];
        // Основной формат v35: yyyyMMdd  HH:mm (локальная зона). Сначала Locale.US, затем локаль устройства (на случай нелатинских цифр)
        final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd  HH:mm", Locale.US);
        format.setLenient(false);
        final SimpleDateFormat formatLocal = new SimpleDateFormat("yyyyMMdd  HH:mm", Locale.getDefault());
        formatLocal.setLenient(false);
        // Допустимый диапазон UTC-миллисекунд: 2000-01-01 .. 2100-01-01
        final long minMillis = 946684800000L;
        final long maxMillis = 4102444800000L;
        copyRows(db, "log_Incoming_SMS", "SELECT * FROM log_Incoming_SMS", new RowMapper() {
            @Override
            public void map(Cursor c, ContentValues v) {
                common(c, v);
                copy(c, v, "Sender", "SenderId");
                copy(c, v, "Body", "Body");
                int index = idx(c, "DateTime");
                long time = mMigrationTime;
                int branch = 2;
                String text = null;
                int type = c.getType(index);
                if (type == Cursor.FIELD_TYPE_STRING) {
                    text = c.getString(index);
                } else if (type == Cursor.FIELD_TYPE_INTEGER) {
                    text = String.valueOf(c.getLong(index));
                }
                if (text != null) {
                    String trimmed = text.trim();
                    if (trimmed.length() >= 10 && trimmed.length() <= 15 && isDigits(trimmed)) {
                        // Только цифры: UTC-миллисекунды (в диапазоне 2000..2100, иначе дата не разобрана)
                        long millis = Long.parseLong(trimmed);
                        if (millis >= minMillis && millis <= maxMillis) {
                            time = millis;
                            branch = 0;
                        }
                    } else {
                        // Основной формат v35: yyyyMMdd  HH:mm (локальная зона)
                        ParsePosition position = new ParsePosition(0);
                        Date date = format.parse(text, position);
                        if (date == null) {
                            position = new ParsePosition(0);
                            date = formatLocal.parse(text, position);
                        }
                        // Строка должна быть разобрана целиком
                        if (date != null && position.getIndex() == text.length()) {
                            time = date.getTime();
                            branch = 1;
                        }
                    }
                }
                smsCounts[branch]++;
                v.put("DateTime", time);
            }
        });
        log("log_Incoming_SMS dates: " + smsCounts[0] + " as UTC milliseconds, " + smsCounts[1]
                + " as yyyyMMdd  HH:mm (device time zone), " + smsCounts[2] + " unparsed (migration time used)");

        copySql(db, "ref_Sms_Parser_Patterns", "_id, Deleted, Type, Object, Pattern", "_id, IFNULL(Deleted, 0), Type, Object, Pattern");

        // Пустая таблица: данные заполняет Frozen36.rebuildRunningBalance
        recreate(db, "log_Running_Balance", new Populate() {
            @Override
            public void run(String newTable) {
            }
        });
    }

    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private int currencyDigits(SQLiteDatabase db, Cursor c, String column) {
        int index = idx(c, column);
        if (c.isNull(index)) return 2;
        // ref_Currencies уже новая к этому моменту, DecimalCount остался прежним (после исправления)
        Cursor d = db.rawQuery("SELECT DecimalCount FROM ref_Currencies WHERE _id = ?", new String[]{String.valueOf(c.getLong(index))});
        try {
            return d.moveToFirst() ? d.getInt(0) : 2;
        } finally {
            d.close();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Verification">
    private void verify(SQLiteDatabase db, Map<String, long[]> before, Map<String, Map<Long, Long>> sumsBefore,
                        Map<String, Map<Long, BigDecimal>> exactBefore) {
        Cursor c = db.rawQuery("PRAGMA foreign_key_check", null);
        try {
            if (c.moveToFirst()) {
                throw new DbUpgradeException(DbUpgradeException.CODE_VERIFY,
                        "foreign_key_check: table " + c.getString(0) + ", row " + c.getLong(1) + ", parent " + c.getString(2));
            }
        } finally {
            c.close();
        }
        c = db.rawQuery("PRAGMA quick_check", null);
        try {
            if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                throw new DbUpgradeException(DbUpgradeException.CODE_VERIFY, "quick_check failed");
            }
        } finally {
            c.close();
        }

        for (String table : COUNT_TABLES) {
            if (table.equals("log_Running_Balance")) continue;
            long total = queryLong(db, "SELECT COUNT(*) FROM " + table);
            long active = queryLong(db, "SELECT COUNT(*) FROM " + table + " WHERE Deleted = 0");
            long[] was = before.get(table);
            long expectedTotal = was[0] - get(mHardDeletedTotal, table) + get(mCreated, table);
            long expectedActive = was[1] - get(mMarkedDeleted, table) - get(mHardDeletedActive, table)
                    + get(mCreatedActive, table);
            log("After: " + table + " total=" + total + " (expected " + expectedTotal + ") active=" + active + " (expected " + expectedActive + ")");
            if (total != expectedTotal || active != expectedActive) {
                throw new DbUpgradeException(DbUpgradeException.CODE_VERIFY, "Row count mismatch in " + table
                        + ": total " + total + "/" + expectedTotal + ", active " + active + "/" + expectedActive);
            }
        }

        compareSums(db, "accounts.StartBalance", "SELECT CurrencyId, SUM(StartBalance) FROM ref_Accounts GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "accounts.CreditLimit", "SELECT CurrencyId, SUM(IFNULL(CreditLimit, 0)) FROM ref_Accounts GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "transactions.Amount", "SELECT a.CurrencyId, SUM(t.Amount) FROM log_Transactions t JOIN ref_Accounts a ON a._id = t.SrcAccountId GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "templates.Amount", "SELECT a.CurrencyId, SUM(IFNULL(t.Amount, 0)) FROM log_Templates t JOIN ref_Accounts a ON a._id = t.SrcAccountId GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "products.Price", "SELECT a.CurrencyId, SUM(p.Price) FROM log_Products p JOIN log_Transactions t ON t._id = p.TransactionId JOIN ref_Accounts a ON a._id = t.SrcAccountId GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "budget.Amount", "SELECT CurrencyId, SUM(Amount) FROM ref_budget GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "budget_debts.Amount", "SELECT a.CurrencyId, SUM(b.Amount) FROM ref_budget_debts b JOIN ref_Debts d ON d._id = b.DebtId JOIN ref_Accounts a ON a._id = d.AccountId GROUP BY 1", sumsBefore, exactBefore);
        compareSums(db, "simpledebts.StartAmount", "SELECT CurrencyId, SUM(StartAmount) FROM ref_SimpleDebts GROUP BY 1", sumsBefore, exactBefore);
    }

    private void compareSums(SQLiteDatabase db, String key, String sqlAfter, Map<String, Map<Long, Long>> sumsBefore,
                             Map<String, Map<Long, BigDecimal>> exactBefore) {
        Map<Long, Long> after = sumsAfter(db, sqlAfter);
        Map<Long, Long> was = sumsBefore.get(key);
        Set<Long> currencies = new HashSet<>(was.keySet());
        currencies.addAll(after.keySet());
        for (Long currency : currencies) {
            long expected = was.containsKey(currency) ? was.get(currency) : 0;
            long actual = after.containsKey(currency) ? after.get(currency) : 0;
            BigDecimal exact = exactBefore.get(key).get(currency);
            if (exact != null) {
                BigDecimal roundingDiff = exact.subtract(BigDecimal.valueOf(expected));
                if (roundingDiff.signum() != 0) {
                    log(key + " currency " + currency + ": rounding difference " + roundingDiff.toPlainString() + " minor units");
                }
            }
            if (expected != actual) {
                throw new DbUpgradeException(DbUpgradeException.CODE_VERIFY, "Sum mismatch " + key + " currency " + currency
                        + ": before " + expected + ", after " + actual);
            }
        }
    }
    //</editor-fold>
}
