package com.yoshione.fingen.db;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Пересчет log_Running_Balance с нуля (схема 36, суммы в минорных единицах валюты счета).
 * Формула как в старом скрипте update_running_balance: для каждой пары (счет, транзакция) считаются
 * накопленные Income (суммы &gt; 0 по счету-источнику и входящие переводы) и Expense (суммы &lt; 0 по счету-источнику)
 * по всем операциям счета с DateTime не позже даты этой транзакции.
 */
public final class RunningBalance {

    private RunningBalance() {
    }

    /**
     * Сумма, которая зачисляется на счет-получатель, в минорных единицах его валюты, со знаком, противоположным сумме списания
     * (в старом скрипте Amount*ExchangeRate*-1).
     */
    public static long destAmountMinor(long srcMinor, long rate, int srcDigits, int destDigits) {
        BigDecimal value = BigDecimal.valueOf(srcMinor).multiply(BigDecimal.valueOf(rate))
                .movePointLeft(12).movePointRight(destDigits - srcDigits);
        return value.setScale(0, RoundingMode.HALF_UP).negate().longValue();
    }

    private static final class Event {
        final long transactionId;
        final long dateTime;
        final long income;
        final long expense;

        Event(long transactionId, long dateTime, long income, long expense) {
            this.transactionId = transactionId;
            this.dateTime = dateTime;
            this.income = income;
            this.expense = expense;
        }
    }

    public static void rebuild(SQLiteDatabase db) {
        Map<Long, List<Event>> byAccount = new HashMap<>();
        Cursor cursor = db.rawQuery("SELECT t._id, t.SrcAccountId, t.DestAccountId, t.DateTime, t.Amount, t.ExchangeRate, "
                + "cs.DecimalCount, cd.DecimalCount "
                + "FROM log_Transactions t "
                + "LEFT JOIN ref_Accounts sa ON sa._id = t.SrcAccountId LEFT JOIN ref_Currencies cs ON cs._id = sa.CurrencyId "
                + "LEFT JOIN ref_Accounts da ON da._id = t.DestAccountId LEFT JOIN ref_Currencies cd ON cd._id = da.CurrencyId "
                + "WHERE t.Deleted = 0", null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long src = cursor.getLong(1);
                long dateTime = cursor.getLong(3);
                long amount = cursor.getLong(4);
                int srcDigits = cursor.isNull(6) ? 2 : cursor.getInt(6);
                if (amount > 0) {
                    add(byAccount, src, new Event(id, dateTime, amount, 0));
                } else if (amount < 0) {
                    add(byAccount, src, new Event(id, dateTime, 0, amount));
                }
                if (!cursor.isNull(2)) {
                    int destDigits = cursor.isNull(7) ? 2 : cursor.getInt(7);
                    long destAmount = destAmountMinor(amount, cursor.getLong(5), srcDigits, destDigits);
                    add(byAccount, cursor.getLong(2), new Event(id, dateTime, destAmount, 0));
                }
            }
        } finally {
            cursor.close();
        }

        db.execSQL("DELETE FROM log_Running_Balance");
        SQLiteStatement insert = db.compileStatement("INSERT OR REPLACE INTO log_Running_Balance "
                + "(AccountId, TransactionId, DateTime, Income, Expense) VALUES (?, ?, ?, ?, ?)");
        try {
            for (Map.Entry<Long, List<Event>> entry : byAccount.entrySet()) {
                List<Event> events = entry.getValue();
                Collections.sort(events, new Comparator<Event>() {
                    @Override
                    public int compare(Event a, Event b) {
                        return Long.compare(a.dateTime, b.dateTime);
                    }
                });
                int i = 0;
                long income = 0;
                long expense = 0;
                while (i < events.size()) {
                    int j = i;
                    long dateTime = events.get(i).dateTime;
                    while (j < events.size() && events.get(j).dateTime == dateTime) {
                        income += events.get(j).income;
                        expense += events.get(j).expense;
                        j++;
                    }
                    // Все операции с одинаковым DateTime учитываются в каждой из их строк (DateTime &lt;= lt.DateTime)
                    for (int k = i; k < j; k++) {
                        insert.clearBindings();
                        insert.bindLong(1, entry.getKey());
                        insert.bindLong(2, events.get(k).transactionId);
                        insert.bindLong(3, dateTime);
                        insert.bindLong(4, income);
                        insert.bindLong(5, expense);
                        insert.executeInsert();
                    }
                    i = j;
                }
            }
        } finally {
            insert.close();
        }
    }

    private static void add(Map<Long, List<Event>> map, long accountId, Event event) {
        List<Event> list = map.get(accountId);
        if (list == null) {
            list = new ArrayList<>();
            map.put(accountId, list);
        }
        list.add(event);
    }
}
