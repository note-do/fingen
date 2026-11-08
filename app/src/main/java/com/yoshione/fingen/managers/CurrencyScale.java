package com.yoshione.fingen.managers;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Число знаков после запятой по счету и валюте. Суммы в БД - целые в минорных единицах валюты счета
 * (копейки), курс обмена - ×10^12, количество товара - ×10^3. Модели работают с BigDecimal,
 * перевод выполняется в DAO.
 * <p>
 * Кэш «счет -> знаки» и «валюта -> знаки» сбрасывается {@link #invalidate()} при изменении счетов и валют
 * (BaseDAO); если в кэше нет записи, значение читается из БД.
 */
public final class CurrencyScale {

    public static final int DEFAULT_DIGITS = 2;
    public static final int MAX_DIGITS = 8;
    public static final int RATE_DIGITS = 12;
    public static final int QUANTITY_DIGITS = 3;

    private static final ConcurrentHashMap<Long, Integer> sAccountDigits = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> sCurrencyDigits = new ConcurrentHashMap<>();

    private CurrencyScale() {
    }

    public static void invalidate() {
        sAccountDigits.clear();
        sCurrencyDigits.clear();
    }

    private static int clamp(int digits) {
        return digits < 0 ? DEFAULT_DIGITS : Math.min(digits, MAX_DIGITS);
    }

    public static int forAccount(SQLiteDatabase db, long accountId) {
        if (accountId < 0) return DEFAULT_DIGITS;
        Integer cached = sAccountDigits.get(accountId);
        if (cached != null) return cached;
        int digits = queryDigits(db, "SELECT c.DecimalCount FROM ref_Accounts a JOIN ref_Currencies c ON c._id = a.CurrencyId WHERE a._id = ?", accountId);
        if (digits >= 0) {
            sAccountDigits.put(accountId, digits);
            return digits;
        }
        return DEFAULT_DIGITS;
    }

    public static int forCurrency(SQLiteDatabase db, long currencyId) {
        if (currencyId < 0) return DEFAULT_DIGITS;
        Integer cached = sCurrencyDigits.get(currencyId);
        if (cached != null) return cached;
        int digits = queryDigits(db, "SELECT DecimalCount FROM ref_Currencies WHERE _id = ?", currencyId);
        if (digits >= 0) {
            sCurrencyDigits.put(currencyId, digits);
            return digits;
        }
        return DEFAULT_DIGITS;
    }

    /** Знаки счета, которому принадлежит транзакция. */
    public static int forTransaction(SQLiteDatabase db, long transactionId) {
        if (transactionId < 0) return DEFAULT_DIGITS;
        int digits = queryDigits(db, "SELECT c.DecimalCount FROM log_Transactions t JOIN ref_Accounts a ON a._id = t.SrcAccountId "
                + "JOIN ref_Currencies c ON c._id = a.CurrencyId WHERE t._id = ?", transactionId);
        return digits >= 0 ? digits : DEFAULT_DIGITS;
    }

    /** Знаки счета долга (ref_Debts). */
    public static int forDebt(SQLiteDatabase db, long debtId) {
        if (debtId < 0) return DEFAULT_DIGITS;
        int digits = queryDigits(db, "SELECT c.DecimalCount FROM ref_Debts d JOIN ref_Accounts a ON a._id = d.AccountId "
                + "JOIN ref_Currencies c ON c._id = a.CurrencyId WHERE d._id = ?", debtId);
        return digits >= 0 ? digits : DEFAULT_DIGITS;
    }

    private static int queryDigits(SQLiteDatabase db, String sql, long id) {
        Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(id)});
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return clamp(cursor.getInt(0));
            }
        } finally {
            cursor.close();
        }
        return -1;
    }

    //<editor-fold desc="Conversion">
    public static long toMinor(BigDecimal value, int digits) {
        if (value == null) return 0;
        return value.movePointRight(digits).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    public static BigDecimal fromMinor(long minor, int digits) {
        return BigDecimal.valueOf(minor, digits);
    }

    /** Курс (BigDecimal) -> целое ×10^12; единственное место перевода курса в хранимый вид. */
    public static long toRate(BigDecimal rate) {
        if (rate == null) return 0;
        try {
            return rate.movePointRight(RATE_DIGITS).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Exchange rate is out of range: " + rate.toPlainString(), e);
        }
    }

    public static BigDecimal fromRate(long rate) {
        return BigDecimal.valueOf(rate, RATE_DIGITS);
    }

    public static long toQuantity(BigDecimal quantity) {
        return toMinor(quantity, QUANTITY_DIGITS);
    }

    public static BigDecimal fromQuantity(long quantity) {
        return BigDecimal.valueOf(quantity, QUANTITY_DIGITS);
    }
    //</editor-fold>

    /**
     * SQL-выражение делителя 10^знаки для колонки с числом знаков (NULL считается как 2).
     */
    public static String divisorSql(String digitsColumn) {
        return "(CASE IFNULL(" + digitsColumn + ", 2) WHEN 0 THEN 1.0 WHEN 1 THEN 10.0 WHEN 2 THEN 100.0 WHEN 3 THEN 1000.0 WHEN 4 THEN 10000.0 WHEN 5 THEN 100000.0 " + "WHEN 6 THEN 1000000.0 WHEN 7 THEN 10000000.0 ELSE 100000000.0 END)";
    }
}
