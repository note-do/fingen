package com.yoshione.fingen.db.migrations;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import android.database.sqlite.SQLiteDatabase;

/**
 * Миграция 16 → 17: отправители SMS.
 * Создает ref_Senders, переносит маркеры типа «Отправитель» из ref_Sms_Parser_Patterns,
 * переводит log_Incoming_SMS на id отправителя вместо номера телефона.
 * Перенесено из UpdateHelper.update17 без изменения логики.
 */
public class Migration17 implements DbMigration {

    @Override
    public void migrate(SQLiteDatabase db, Context context) {
        //создаем таблицу отправителей
        db.execSQL("CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Name TEXT UNIQUE NOT NULL, PhoneNo TEXT UNIQUE NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL);");

        //Импортируем в новую таблицу все маркеры типа "Отправитель"
        Cursor cursor = db.query("ref_Sms_Parser_Patterns", "_id, Type, Object, Pattern".split(","),
                "Type = " + 5, null,
                null, null, null);

        List<Pair<String, Long>> senders = new ArrayList<>();
        String phoneNo;

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        ContentValues values = new ContentValues();
                        values.put("Name", cursor.getString(cursor.getColumnIndexOrThrow("Object")));
                        phoneNo = cursor.getString(cursor.getColumnIndexOrThrow("Pattern"));
                        values.put("PhoneNo", phoneNo);
                        values.put("AmountPos", 0);
                        values.put("BalancePos", 1);
                        values.put("DateFormat", "");
                        values.put("LeadingCurrencySymbol", false);
                        values.put("IsActive", 1);

                        //Пишем напрямую в той же транзакции миграции: SendersDAO во время апгрейда ждет конца миграции
                        long senderId = db.insert("ref_Senders", null, values);
                        if (senderId >= 0) {
                            senders.add(new Pair<>(phoneNo, senderId));
                        }

                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        //После импорта грохаем все маркеры типа "Отправитель"
        db.delete("ref_Sms_Parser_Patterns", "Type = 5", null);

        //Модифицируем таблицу смс, чтобы она хранила id отправителя вместо номера телефона
        String tempSmsTableSQL = "CREATE TABLE tempsms (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);";
        db.execSQL(tempSmsTableSQL);
        cursor = db.query("log_Incoming_SMS", "_id,DateTime,Sender,Body".split(","), null, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        String smsSender = cursor.getString(cursor.getColumnIndexOrThrow("Sender"));
                        long senderId = 0;
                        if (smsSender != null && !smsSender.trim().isEmpty()) {
                            String key = smsSender.trim().toLowerCase();
                            Long found = null;
                            for (Pair<String, Long> sender : senders) {
                                if (sender.first != null && sender.first.trim().toLowerCase().equals(key)) {
                                    found = sender.second;
                                    break;
                                }
                            }
                            if (found == null) {
                                //Отправитель без маркера: создаем запись, чтобы не терять SMS
                                found = insertSmsSender(db, smsSender, smsSender);
                                if (found == null) {
                                    found = insertSmsSender(db, smsSender + " (SMS)", smsSender);
                                }
                                if (found != null) {
                                    senders.add(new Pair<>(smsSender, found));
                                }
                            }
                            if (found != null) {
                                senderId = found;
                            }
                        }
                        ContentValues values = new ContentValues();
                        values.put("_id", cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
                        String dateTime = cursor.getString(cursor.getColumnIndexOrThrow("DateTime"));
                        values.put("DateTime", dateTime == null ? "" : dateTime);
                        values.put("Sender", senderId);
                        String body = cursor.getString(cursor.getColumnIndexOrThrow("Body"));
                        values.put("Body", body == null ? "" : body);
                        db.insert("tempsms", null, values);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        db.execSQL("DROP TABLE log_Incoming_SMS");
        db.execSQL("ALTER TABLE tempsms RENAME TO log_Incoming_SMS");
    }

    /** Inserts a sender with default parser settings, returns its id or null if Name or PhoneNo is already used. */
    private static Long insertSmsSender(SQLiteDatabase db, String name, String phoneNo) {
        ContentValues values = new ContentValues();
        values.put("Name", name);
        values.put("PhoneNo", phoneNo);
        values.put("AmountPos", 0);
        values.put("BalancePos", 1);
        values.put("DateFormat", "");
        values.put("LeadingCurrencySymbol", false);
        values.put("isActive", 1);
        long id = db.insert("ref_Senders", null, values);
        return id >= 0 ? id : null;
    }
}
