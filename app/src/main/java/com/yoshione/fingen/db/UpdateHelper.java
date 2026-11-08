package com.yoshione.fingen.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.yoshione.fingen.classes.TableInfo;
import com.yoshione.fingen.utils.ColorUtils;
import com.yoshione.fingen.utils.DateTimeFormatter;
import com.yoshione.fingen.utils.Translit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.database.sqlite.SQLiteDatabase;

/**
 * Created by slv on 28.03.2017.
 * UpdateHelper
 */

public class UpdateHelper {

    //<editor-fold desc="Frozen DBHelper constants (do not change: old migrations must not depend on the current schema)" defaultstate="collapsed">
    private static final String LEGACY_C_ID = "_id";
    private static final String LEGACY_C_SYNC_FBID = "FBID";
    private static final String LEGACY_C_SYNC_TS = "TS";
    private static final String LEGACY_C_SYNC_DELETED = "Deleted";
    private static final String LEGACY_C_SYNC_DIRTY = "Dirty";
    private static final String LEGACY_C_SYNC_LASTEDITED = "LastEdited";
    private static final String LEGACY_C_SEARCH_STRING = "SearchString";
    private static final String LEGACY_C_FULL_NAME = "FullName";
    private static final String LEGACY_COMMON_FIELDS =
            LEGACY_C_ID + " INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,\n"
                    + LEGACY_C_SYNC_FBID + " TEXT,\n"
                    + LEGACY_C_SYNC_TS + " INTEGER,\n"
                    + LEGACY_C_SYNC_DELETED + " INTEGER,\n"
                    + LEGACY_C_SYNC_DIRTY + " INTEGER,\n"
                    + LEGACY_C_SYNC_LASTEDITED + " TEXT,\n";
    private static final String LEGACY_T_REF_CATEGORIES = "ref_Categories";
    private static final String LEGACY_T_REF_PROJECTS = "ref_Projects";
    private static final String LEGACY_C_REF_PROJECTS_COLOR = "Color";
    private static final String LEGACY_T_LOG_TRANSACTIONS = "log_Transactions";
    private static final String LEGACY_C_LOG_TRANSACTIONS_COMMENT = "Comment";
    private static final String LEGACY_C_LOG_TRANSACTIONS_FN = "FN";
    private static final String LEGACY_C_LOG_TRANSACTIONS_FD = "FD";
    private static final String LEGACY_C_LOG_TRANSACTIONS_FP = "FP";
    private static final String LEGACY_C_LOG_TRANSACTIONS_SPLIT = "Split";
    private static final String LEGACY_T_REF_SENDERS = "ref_Senders";
    private static final String LEGACY_C_REF_SENDERS_ADD_CREDIT_LIMIT_TO_BALANCE = "AddCreditLimitToBalance";
    private static final String LEGACY_T_REF_PRODUCTS = "ref_Products";
    private static final String LEGACY_C_REF_PRODUCTS_NAME = "Name";
    private static final String LEGACY_T_LOG_PRODUCTS = " log_Products";
    private static final String LEGACY_C_LOG_PRODUCTS_TRANSACTIONID = "TransactionID";
    private static final String LEGACY_C_LOG_PRODUCTS_PRODUCTID = "ProductID";
    private static final String LEGACY_C_LOG_PRODUCTS_CATEGORY_ID = "CategoryID";
    private static final String LEGACY_C_LOG_PRODUCTS_PROJECT_ID = "ProjectID";
    private static final String LEGACY_C_LOG_PRODUCTS_PRICE = "Price";
    private static final String LEGACY_C_LOG_PRODUCTS_QUANTITY = "Quantity";
    private static final String LEGACY_SQL_CREATE_TABLE_REF_PRODUCTS = "CREATE TABLE " + LEGACY_T_REF_PRODUCTS + " ("
            + LEGACY_COMMON_FIELDS
            + LEGACY_C_REF_PRODUCTS_NAME + " TEXT NOT NULL DEFAULT '', "
            + LEGACY_C_SEARCH_STRING + " TEXT NOT NULL DEFAULT '', "
            + "UNIQUE (" + LEGACY_C_REF_PRODUCTS_NAME + ", " + LEGACY_C_SYNC_DELETED + ") ON CONFLICT ABORT);";
    private static final String LEGACY_SQL_CREATE_TABLE_LOG_PRODUCTS = "CREATE TABLE " + LEGACY_T_LOG_PRODUCTS + "\n("
            + LEGACY_COMMON_FIELDS
            + LEGACY_C_LOG_PRODUCTS_TRANSACTIONID + " INTEGER REFERENCES [" + LEGACY_T_LOG_TRANSACTIONS + "]([" + LEGACY_C_ID + "]) ON DELETE SET NULL ON UPDATE CASCADE,\n"
            + LEGACY_C_LOG_PRODUCTS_PRODUCTID + " INTEGER REFERENCES [" + LEGACY_T_REF_PRODUCTS + "]([" + LEGACY_C_ID + "]) ON DELETE SET NULL ON UPDATE CASCADE,\n"
            + LEGACY_C_LOG_PRODUCTS_CATEGORY_ID + " INTEGER DEFAULT -1 REFERENCES [" + LEGACY_T_REF_CATEGORIES + "]([" + LEGACY_C_ID + "]) ON DELETE SET NULL ON UPDATE CASCADE,\n"
            + LEGACY_C_LOG_PRODUCTS_PROJECT_ID + " INTEGER DEFAULT -1 REFERENCES [" + LEGACY_T_REF_PROJECTS + "]([" + LEGACY_C_ID + "]) ON DELETE SET NULL ON UPDATE CASCADE,\n"
            + LEGACY_C_LOG_PRODUCTS_PRICE + " REAL NOT NULL DEFAULT 0,\n"
            + LEGACY_C_LOG_PRODUCTS_QUANTITY + " REAL NOT NULL DEFAULT 1 CHECK (Quantity >= 0));";
    //</editor-fold>

    /**
     * Frozen copy of DBHelper.updateRunningBalance as of schema v35 (script assets/sql/update_running_balance_v35.sql).
     */
    public static void legacyUpdateRunningBalance(SQLiteDatabase database, Context context) throws IOException {
        StringBuilder buf = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open("sql/update_running_balance_v35.sql"), "UTF-8"))) {
            String str;
            while ((str = in.readLine()) != null) {
                buf.append(str);
                if (!str.isEmpty()) {
                    buf.append("\n");
                }
            }
        }
        for (String sql : buf.toString().split(";")) {
            if (sql != null && !sql.isEmpty() && !sql.equals("\n")) {
                android.util.Log.d("UpdateHelper", sql);
                database.execSQL(sql);
            }
        }
    }

    private static String legacyGetFullNameColumn(String tableName) {
        return "(SELECT path FROM (with recursive m(path, _id, name) AS (SELECT Name, _id, Name FROM " + tableName + " WHERE ParentId = -1 UNION ALL  SELECT path||'\\'||t.Name, t._id, t.Name FROM " + tableName + " t, m WHERE t.ParentId = m._id) SELECT * FROM m where _id = " + tableName + "._id)) AS FullName";
    }

    /**
     * Frozen copy of DBHelper.updateFullNames as of schema v35.
     */
    private static void legacyUpdateFullNames(String tableName, boolean useFullName, SQLiteDatabase db) {
        String nameColumn;
        if (tableName.equals(LEGACY_T_LOG_TRANSACTIONS)) {
            nameColumn = useFullName ? legacyGetFullNameColumn(tableName) : LEGACY_C_LOG_TRANSACTIONS_COMMENT;
        } else {
            nameColumn = useFullName ? legacyGetFullNameColumn(tableName) : "Name";
        }
        String fields[];
        if (useFullName) {
            fields = new String[]{LEGACY_C_ID, nameColumn, LEGACY_C_SEARCH_STRING, LEGACY_C_FULL_NAME};
        } else {
            fields = new String[]{LEGACY_C_ID, nameColumn, LEGACY_C_SEARCH_STRING};
        }
        Cursor cursor = db.query(tableName, fields, "Deleted = 0", null, null, null, null);
        ContentValues cv = new ContentValues();
        String translit;
        try {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    cv.clear();
                    String source = cursor.getString(1) == null ? "" : cursor.getString(1);
                    if (useFullName) {
                        cv.put(LEGACY_C_FULL_NAME, source);
                    }
                    translit = Translit.toTranslit(source.toLowerCase());
                    if (!translit.equals(cursor.getString(2))) {
                        cv.put(LEGACY_C_SEARCH_STRING, translit);
                    }
                    if (cv.size() != 0) {
                        db.update(tableName, cv, "_id = " + cursor.getString(0), null);
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            cursor.close();
        }
    }

    //<editor-fold desc="tableInfo17" defaultstate="collapsed">
    private static TableInfo tableInfo17[] = new TableInfo[]{
            new TableInfo("ref_Currencies", "", "_id, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts", "", "_id, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories", "", "_id, Name, COLOR, ParentCategory, OrderNumber, Sign"),
            new TableInfo("ref_Payees", "", "_id, Name, DefCategory"),
            new TableInfo("ref_Projects", "", "_id, Name, isActive"),
            new TableInfo("ref_Locations", "", "_id, Name, Lon, Lat, Radius, Address"),
            new TableInfo("ref_Departments", "", "_id, Name, isActive"),
            new TableInfo("ref_SimpleDebts", "", "_id, Name, isActive"),
            new TableInfo("log_Transactions", "", "_id, DateTime, SrcAccount, Payee, Category, Amount, Project, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lat, Lon, Accuracy, Department, SimpleDebt"),
            new TableInfo("log_Templates", "", "_id, SrcAccount, Payee, Category, Amount, Project, Location, Name, DestAccount, ExchangeRate, Type, Department"),
            new TableInfo("log_Incoming_SMS", "", "_id, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns", "", "_id, Type, Object, Pattern"),
            new TableInfo("ref_Debts", "", "_id, Account, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget", "", "_id, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts", "", "_id, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders", "", "_id, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo18" defaultstate="collapsed">
    private static TableInfo tableInfo18[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentCategory, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentCategory, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT UNIQUE NOT NULL, PhoneNo TEXT UNIQUE NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo19" defaultstate="collapsed">
    private static TableInfo tableInfo19[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentCategory, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentCategory, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo20" defaultstate="collapsed">
    private static TableInfo tableInfo20[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentCategory, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentCategory, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL, Comment TEXT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type, Comment"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo21" defaultstate="collapsed">
    private static TableInfo tableInfo21[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentCategory, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentCategory, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL, Comment TEXT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type, Comment"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo22" defaultstate="collapsed">
    private static TableInfo tableInfo22[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentID INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentID, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, ParentID INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory, ParentID, OrderNumber"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive, ParentID, OrderNumber"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, ParentID INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address, ParentID, OrderNumber"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive, ParentID, OrderNumber"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL, Comment TEXT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type, Comment"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    //<editor-fold desc="tableInfo23" defaultstate="collapsed">
    private static TableInfo tableInfo23[] = new TableInfo[]{
            new TableInfo("ref_Currencies",
                    "CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, OrderNumber INTEGER, UNIQUE (Code, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount, OrderNumber"),
            new TableInfo("ref_Accounts",
                    "CREATE TABLE ref_Accounts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Name TEXT NOT NULL, Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE, Emitent TEXT, Last4Digits INTEGER, Comment TEXT, StartBalance REAL NOT NULL, IsClosed INTEGER NOT NULL, CustomOrder INTEGER, CreditLimit REAL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Name, Currency, Emitent, Last4Digits, Comment, StartBalance, IsClosed, CustomOrder, CreditLimit"),
            new TableInfo("ref_Categories",
                    "CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentID INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, COLOR, ParentID, OrderNumber, Sign"),
            new TableInfo("ref_Payees",
                    "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, ParentID INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory, ParentID, OrderNumber"),
            new TableInfo("ref_Projects",
                    "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive, ParentID, OrderNumber"),
            new TableInfo("ref_Locations",
                    "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, ParentID INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address, ParentID, OrderNumber"),
            new TableInfo("ref_Departments",
                    "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive, ParentID, OrderNumber"),
            new TableInfo("ref_SimpleDebts",
                    "CREATE TABLE ref_SimpleDebts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"),
            new TableInfo("log_Transactions",
                    "CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, SrcAccount, Payee, Category, Amount, Project, SimpleDebt, Department, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lon, Lat, Accuracy"),
            new TableInfo("log_Templates",
                    "CREATE TABLE log_Templates (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Name TEXT NOT NULL, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, Type INTEGER NOT NULL, Comment TEXT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, SrcAccount, Payee, Category, Amount, Project, Department, Location, Name, DestAccount, ExchangeRate, Type, Comment"),
            new TableInfo("log_Incoming_SMS",
                    "CREATE TABLE log_Incoming_SMS (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime TEXT NOT NULL, Sender INTEGER NOT NULL, Body TEXT NOT NULL);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, DateTime, Sender, Body"),
            new TableInfo("ref_Sms_Parser_Patterns",
                    "CREATE TABLE ref_Sms_Parser_Patterns (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Type, Object, Pattern"),
            new TableInfo("ref_Debts",
                    "CREATE TABLE ref_Debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Account INTEGER NOT NULL ON CONFLICT ABORT REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER NOT NULL REFERENCES ref_Payees(_id) ON DELETE CASCADE ON UPDATE CASCADE, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Closed INTEGER NOT NULL, SrcAccount INTEGER, Comment TEXT, UNIQUE (Account, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Account ON CONFLICT ABORT, Payee, Category, Closed, SrcAccount, Comment"),
            new TableInfo("ref_budget",
                    "CREATE TABLE ref_budget (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Category INTEGER NOT NULL REFERENCES ref_Categories(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, Currency INTEGER NOT NULL REFERENCES ref_Currencies(_id) ON DELETE CASCADE ON UPDATE CASCADE, UNIQUE (Deleted, Year, Month, Category, Currency) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Category, Amount, Currency"),
            new TableInfo("ref_budget_debts",
                    "CREATE TABLE ref_budget_debts (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Year INTEGER NOT NULL, Month INTEGER NOT NULL, Debt INTEGER NOT NULL REFERENCES ref_Debts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Amount REAL NOT NULL, UNIQUE (Deleted, Year, Month, Debt) ON CONFLICT REPLACE);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Year, Month, Debt, Amount"),
            new TableInfo("ref_Senders",
                    "CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);",
                    "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive")
    };
    //</editor-fold>

    public static void update18(SQLiteDatabase db, Context context) {
        //переходим со строкового хранения дат в таблице транзакций на числовое
        //Переименовали старую версию таблицы
        db.execSQL(String.format("ALTER TABLE %s RENAME TO t_transactions_old", "log_Transactions"));

        //Создали новую версию таблицы
        db.execSQL("CREATE TABLE log_Transactions (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, DateTime INTEGER NOT NULL, SrcAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, Payee INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, Category INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, Amount REAL NOT NULL, Project INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, SimpleDebt INTEGER REFERENCES ref_SimpleDebts(_id) ON DELETE SET NULL ON UPDATE CASCADE, Department INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, Location INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, Comment TEXT, File TEXT, DestAccount INTEGER NOT NULL REFERENCES ref_Accounts(_id) ON DELETE CASCADE ON UPDATE CASCADE, ExchangeRate REAL NOT NULL, AutoCreated INTEGER NOT NULL, Lon REAL, Lat REAL, Accuracy INTEGER);");

        Cursor cursor = db.query("t_transactions_old", "_id, DateTime, SrcAccount, Payee, Category, Amount, Project, Location, Comment, File, DestAccount, ExchangeRate, AutoCreated, Lat, Lon, Accuracy, Department, SimpleDebt".split(","), null, null, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    DateTimeFormatter dtf = DateTimeFormatter.getInstance(context);
                    ContentValues values = new ContentValues();
                    while (!cursor.isAfterLast()) {
                        values.clear();
                        values.put("DateTime", dtf.parseDateTimeSqlString(cursor.getString(cursor.getColumnIndexOrThrow("DateTime"))).getTime());
                        values.put("SrcAccount", cursor.getLong(cursor.getColumnIndexOrThrow("SrcAccount")));
                        values.put("Payee", cursor.getLong(cursor.getColumnIndexOrThrow("Payee")));
                        values.put("Category", cursor.getLong(cursor.getColumnIndexOrThrow("Category")));
                        values.put("Amount", cursor.getDouble(cursor.getColumnIndexOrThrow("Amount")));
                        values.put("Project", cursor.getLong(cursor.getColumnIndexOrThrow("Project")));
                        values.put("Department", cursor.getLong(cursor.getColumnIndexOrThrow("Department")));
                        values.put("Location", cursor.getLong(cursor.getColumnIndexOrThrow("Location")));
                        values.put("Comment", cursor.getString(cursor.getColumnIndexOrThrow("Comment")));
                        values.put("File", cursor.getString(cursor.getColumnIndexOrThrow("File")));
                        values.put("DestAccount", cursor.getLong(cursor.getColumnIndexOrThrow("DestAccount")));
                        values.put("ExchangeRate", cursor.getDouble(cursor.getColumnIndexOrThrow("ExchangeRate")));
                        values.put("AutoCreated", cursor.getInt(cursor.getColumnIndexOrThrow("AutoCreated")) == 1);
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow("Lat"))) {
                            values.put("Lat", cursor.getDouble(cursor.getColumnIndexOrThrow("Lat")));
                        } else {
                            values.put("Lat", 0);
                        }
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow("Lon"))) {
                            values.put("Lon", cursor.getDouble(cursor.getColumnIndexOrThrow("Lon")));
                        } else {
                            values.put("Lon", 0);
                        }
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow("Accuracy"))) {
                            values.put("Accuracy", cursor.getInt(cursor.getColumnIndexOrThrow("Accuracy")));
                        } else {
                            values.put("Accuracy", -1);
                        }
                        if (!cursor.isNull(cursor.getColumnIndexOrThrow("SimpleDebt"))) {
                            values.put("SimpleDebt", cursor.getLong(cursor.getColumnIndexOrThrow("SimpleDebt")));
                        } else {
                            values.put("SimpleDebt", -1);
                        }

                        db.insert("log_Transactions", null, values);

                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        db.execSQL("DROP TABLE t_transactions_old");

        //создаем в таблицах поля, необходимые для работы синхронизации и меняем constraints (пересоздаем все таблицы)
        ContentValues cv = new ContentValues();
        cv.put("FBID", "");
        cv.put("TS", -1);
        cv.put("Deleted", 0);
        cv.put("Dirty", false);
        cv.put("LastEdited", "");
        for (int i = 0; i < tableInfo17.length; i++) {
            db.execSQL(String.format("ALTER TABLE %s RENAME TO %s_old", tableInfo17[i].getTableName(), tableInfo17[i].getTableName()));
            db.execSQL(tableInfo18[i].getTableCreateSQL());
            db.execSQL(String.format("INSERT INTO %s (%s) SELECT %s FROM %s_old", tableInfo18[i].getTableName(), tableInfo17[i].getTableFields(), tableInfo17[i].getTableFields(), tableInfo17[i].getTableName()));
            db.execSQL(String.format("DROP TABLE %s_old", tableInfo17[i].getTableName()));
            db.update(tableInfo18[i].getTableName(), cv, null, null);
        }
    }

    public static void update19(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE ref_Senders RENAME TO ref_Senders_old");
        db.execSQL("CREATE TABLE ref_Senders (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, LeadingCurrencySymbol INTEGER, DateFormat TEXT, isActive INTEGER NOT NULL, UNIQUE (Name, Deleted) ON CONFLICT ABORT, UNIQUE (PhoneNo, Deleted) ON CONFLICT ABORT);");
        db.execSQL(
                "INSERT INTO ref_Senders (_id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive) " +
                        "SELECT _id, FBID, TS, Deleted, Dirty, LastEdited, Name, PhoneNo, AmountPos, BalancePos, LeadingCurrencySymbol, DateFormat, isActive FROM ref_Senders_old");
        db.execSQL("DROP TABLE ref_Senders_old");
    }

    public static void update20(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE log_Templates ADD COLUMN Comment TEXT;");
    }

    public static void update21(SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put("Comment", "");
        db.update("log_Templates", cv, "Comment IS NULL", null);
    }

    public static void update22(SQLiteDatabase db) {
        /*
          План:
          1. Меняем в таблице ref_Categories название поля ParentCategory на ParentID
          2. Заводим в таблицах ref_Payees, ref Projects, ref_Departments, ref_Locations поля
               ParentID и OrderNumber
         */
        db.execSQL("ALTER TABLE ref_Categories RENAME TO ref_Categories_old");
        db.execSQL("CREATE TABLE ref_Categories (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, COLOR TEXT, ParentID INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER NOT NULL, Sign INTEGER NOT NULL, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);");
        String oldColumns = "_id,FBID,TS,Deleted,Dirty,LastEdited,Name,COLOR,ParentCategory,OrderNumber,Sign";
        String newColumns = "_id,FBID,TS,Deleted,Dirty,LastEdited,Name,COLOR,ParentID,OrderNumber,Sign";
        db.execSQL(String.format("INSERT INTO ref_Categories (%s) SELECT %s FROM ref_Categories_old", newColumns, oldColumns));
        db.execSQL("DROP TABLE ref_Categories_old");

        List<TableInfo> infos = new ArrayList<>();
        String tableNames[] = new String[]{"ref_Payees", "ref_Projects", "ref_Locations", "ref_Departments"};
        String tableCreateSQL[] = new String[]{
                "CREATE TABLE ref_Payees (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, DefCategory INTEGER REFERENCES ref_Categories(_id) ON DELETE SET NULL ON UPDATE CASCADE, ParentID INTEGER REFERENCES ref_Payees(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                "CREATE TABLE ref_Projects (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Projects(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                "CREATE TABLE ref_Locations (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Radius INTEGER, Address TEXT, ParentID INTEGER REFERENCES ref_Locations(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);",
                "CREATE TABLE ref_Departments (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT NOT NULL, isActive INTEGER NOT NULL, ParentID INTEGER REFERENCES ref_Departments(_id) ON DELETE SET NULL ON UPDATE CASCADE, OrderNumber INTEGER, UNIQUE (Name, ParentID, Deleted) ON CONFLICT ABORT);"
        };
        String tableFields[] = new String[]{
                "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, DefCategory",
                "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive",
                "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, Lon, Lat, Radius, Address",
                "_id, FBID, TS, Deleted, Dirty, LastEdited, Name, isActive"
        };

        ContentValues cv = new ContentValues();
        cv.put("ParentID", -1);
        cv.put("OrderNumber", 0);

        for (int i = 0; i < tableNames.length; i++) {
            db.execSQL(String.format("ALTER TABLE %s RENAME TO %s_old", tableNames[i], tableNames[i]));
            db.execSQL(tableCreateSQL[i]);
            db.execSQL(String.format("INSERT INTO %s (%s) SELECT %s FROM %s_old", tableNames[i], tableFields[i], tableFields[i], tableNames[i]));
            db.execSQL(String.format("DROP TABLE %s_old", tableNames[i]));
            db.update(tableNames[i], cv, null, null);
        }

        cv = new ContentValues();
        cv.put("Project", -1);
        db.update("log_Transactions", cv, String.format("%s = 0", "Project"), null);
        cv = new ContentValues();
        cv.put("Department", -1);
        db.update("log_Transactions", cv, String.format("%s = 0", "Department"), null);
        cv = new ContentValues();
        cv.put("Location", -1);
        db.update("log_Transactions", cv, String.format("%s = 0", "Location"), null);
    }

    public static void update23(SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put("OrderNumber", 0);
        db.execSQL("ALTER TABLE ref_Currencies RENAME TO ref_Currencies_old");
        db.execSQL("CREATE TABLE ref_Currencies (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, DecimalCount INTEGER NOT NULL, OrderNumber INTEGER, UNIQUE (Code, Deleted) ON CONFLICT ABORT);");
        db.execSQL(
                "INSERT INTO ref_Currencies (_id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount) " +
                "SELECT _id, FBID, TS, Deleted, Dirty, LastEdited, Code, Symbol, Name, DecimalCount " +
                "FROM ref_Currencies_old");
        db.execSQL("DROP TABLE ref_Currencies_old");
        db.update("ref_Currencies", cv, null, null);
    }

    public static void update24(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ref_Accounts_Sets (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, Name TEXT, UNIQUE (Name, Deleted) ON CONFLICT ABORT);");
        db.execSQL("CREATE TABLE log_Accounts_Sets (_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, FBID TEXT, TS INTEGER, Deleted INTEGER, Dirty INTEGER, LastEdited TEXT, SetID INTEGER NOT NULL ON CONFLICT ABORT REFERENCES [ref_Accounts_Sets]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,AccountID INTEGER NOT NULL ON CONFLICT ABORT REFERENCES [ref_Accounts]([_id]) ON DELETE CASCADE ON UPDATE CASCADE,UNIQUE (SetID, AccountID, Deleted) ON CONFLICT ABORT);");

        ContentValues values = new ContentValues();

        values.put("Lat", 0);
        db.update("log_Transactions", values, "Lat IS NULL", null);

        values.clear();
        values.put("Lon", 0);
        db.update("log_Transactions", values, "Lon IS NULL", null);

        values.clear();
        values.put("Accuracy", -1);
        db.update("log_Transactions", values, "Accuracy IS NULL", null);

        values.clear();
        values.put("SimpleDebt", -1);
        db.update("log_Transactions", values, "SimpleDebt IS NULL", null);

        values.clear();
        values.put("ParentID", -1);
        db.update("ref_Categories", values, "ParentID IS NULL", null);

        db.execSQL("CREATE INDEX [idx] ON [log_Transactions] ([Deleted], [DateTime], [SrcAccount], [DestAccount], [Payee], [Category], [Project], [Department], [Location], [SimpleDebt]);");
    }

    public static void update25(SQLiteDatabase db) {
        //добавляем колонку со стартовой суммой долга
        db.execSQL("ALTER TABLE ref_SimpleDebts ADD COLUMN StartAmount REAL NOT NULL DEFAULT 0;");
        //добавляем колонку с валютой долга
        db.execSQL("ALTER TABLE ref_SimpleDebts ADD COLUMN Currency INTEGER REFERENCES ref_Currencies(_id) ON DELETE SET NULL ON UPDATE CASCADE;");
        //считываем идентификаторы всех долгов
        Cursor cursorDebts = db.rawQuery("SELECT * FROM ref_SimpleDebts", null);
        List<Long> debtIDs = new ArrayList<>();
        if (cursorDebts != null) {
            try {
                if (cursorDebts.moveToFirst()) {
                    while (!cursorDebts.isAfterLast()) {
                        debtIDs.add(cursorDebts.getLong(cursorDebts.getColumnIndexOrThrow("_id")));
                        cursorDebts.moveToNext();
                    }
                }
            } finally {
                cursorDebts.close();
            }
        }
        //перебираем долги и проставляем валюту, как первую попавшуюся валюту транзакции по этому долгу
        Cursor cursorTransactions;
        Cursor cursorAccounts;
        boolean cabbageFound;
        long accountID;
        long cabbageID;
        for (long debtID : debtIDs) {
            cabbageFound = false;
            cursorTransactions = db.query("log_Transactions", new String[]{"SrcAccount"},
                    "SimpleDebt = " + String.valueOf(debtID), null, null, null, null);
            if (cursorTransactions != null) {
                try {
                    if (cursorTransactions.moveToFirst()) {
                        accountID = cursorTransactions.getLong(0);
                        cursorAccounts = db.query("ref_Accounts", new String[]{"Currency"},
                                "_id = " + String.valueOf(accountID), null, null, null, null);
                        if (cursorAccounts != null) {
                            try {
                                if (cursorAccounts.moveToFirst()) {
                                    cabbageID = cursorAccounts.getLong(0);
                                    cabbageFound = true;
                                    db.execSQL(String.format("UPDATE ref_SimpleDebts SET Currency = %s WHERE _id = %s",
                                            String.valueOf(cabbageID), String.valueOf(debtID)));
                                }
                            } finally {
                                cursorAccounts.close();
                            }
                        }
                    }
                } finally {
                    cursorTransactions.close();
                }
            }
            if (!cabbageFound) {
                db.delete("ref_SimpleDebts", String.format("_id = %s", String.valueOf(debtID)), null);
            }
        }
    }

    public static void update26(SQLiteDatabase db) {
        Cursor cursorIndexes = db.rawQuery("PRAGMA index_list(log_Transactions)", null);
        if (cursorIndexes.getCount() == 0) {
            db.execSQL("CREATE INDEX [idx] ON [log_Transactions] ([Deleted], [DateTime], [SrcAccount], [DestAccount], [Payee], [Category], [Project], [Department], [Location], [SimpleDebt]);");
        }
        cursorIndexes.close();
    }

    public static void update27(SQLiteDatabase db, IUpdateRunningBalance updateRB) {
        String tableNames[] = new String[]{"ref_Categories", "ref_Payees", "ref_Projects", "ref_Locations", "ref_Departments"};
        for (String tableName : tableNames) {
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN FullName TEXT;");
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN SearchString TEXT;");
            legacyUpdateFullNames(tableName, true, db);
        }
        tableNames = new String[]{"ref_Accounts", "ref_SimpleDebts", "log_Templates"};
        for (String tableName : tableNames) {
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN SearchString TEXT;");
            legacyUpdateFullNames(tableName, false, db);
        }

        //Создаем таблицу с текущим балансом и вычисляем балансы счетов после каждой транзакции
        db.execSQL("CREATE TABLE log_Running_Balance (\n" +
                "     AccountID INTEGER NOT NULL,\n" +
                "     TransactionID INTEGER NOT NULL,\n" +
                "     DateTimeRB INTEGER NOT NULL,\n" +
                "     Income REAL NOT NULL,\n" +
                "     Expense REAL NOT NULL,\n" +
                "     PRIMARY KEY (AccountID, TransactionID) );");
        db.execSQL("CREATE INDEX idx_RB_Accounts ON log_Running_Balance (AccountID);");
        db.execSQL("CREATE INDEX idx_RB_Transactions ON log_Running_Balance (TransactionID);");
        db.execSQL("CREATE INDEX idx_RB_DateTime ON log_Running_Balance (DateTimeRB);");
        try {
            updateRB.updateRunningBalance(db);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void update29(SQLiteDatabase db) {
        String tableName = LEGACY_T_LOG_TRANSACTIONS;
        String columnName = LEGACY_C_LOG_TRANSACTIONS_COMMENT;
        db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN SearchString TEXT;");
        db.execSQL("ALTER TABLE " + LEGACY_T_REF_SENDERS + " ADD COLUMN "+LEGACY_C_REF_SENDERS_ADD_CREDIT_LIMIT_TO_BALANCE+" INTEGER NOT NULL DEFAULT 0;");

        Cursor cursor = db.query(tableName, new String[]{LEGACY_C_ID, columnName}, "Deleted = 0", null, null, null, null);
        ContentValues cv = new ContentValues();
        try {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    cv.clear();
                    String comment = cursor.getString(1);
                    cv.put("SearchString", Translit.toTranslit(comment == null ? "" : comment.toLowerCase()));
                    db.update(tableName, cv, "_id = " + cursor.getString(0), null);
                    cursor.moveToNext();
                }
            }
        } finally {
            cursor.close();
        }
    }

    public static void update30(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + LEGACY_T_LOG_TRANSACTIONS + " ADD COLUMN "+LEGACY_C_LOG_TRANSACTIONS_FN+" INTEGER DEFAULT 0;");
        db.execSQL("ALTER TABLE " + LEGACY_T_LOG_TRANSACTIONS + " ADD COLUMN "+LEGACY_C_LOG_TRANSACTIONS_FD+" INTEGER DEFAULT 0;");
        db.execSQL("ALTER TABLE " + LEGACY_T_LOG_TRANSACTIONS + " ADD COLUMN "+LEGACY_C_LOG_TRANSACTIONS_FP+" INTEGER DEFAULT 0;");
    }

    public static void update31(SQLiteDatabase db, Context context) {
        db.execSQL("ALTER TABLE " + LEGACY_T_REF_PROJECTS + " ADD COLUMN "+LEGACY_C_REF_PROJECTS_COLOR+" TEXT DEFAULT '#ffffff';");

        Cursor cursorProjects = db.rawQuery("SELECT _id FROM ref_Projects", null);
        List<Long> projectIDs = new ArrayList<>();
        if (cursorProjects != null) {
            try {
                if (cursorProjects.moveToFirst()) {
                    while (!cursorProjects.isAfterLast()) {
                        projectIDs.add(cursorProjects.getLong(0));
                        cursorProjects.moveToNext();
                    }
                }
            } finally {
                cursorProjects.close();
            }
        }

        ContentValues cv = new ContentValues();
        for (long id : projectIDs) {
            cv.clear();
            cv.put(LEGACY_C_REF_PROJECTS_COLOR, String.format("#%06X", (0xFFFFFF & ColorUtils.getColor(context))));
            db.update(LEGACY_T_REF_PROJECTS, cv, String.format("_id = %s", String.valueOf(id)), null);
        }
    }

    public static void update32(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + LEGACY_T_LOG_TRANSACTIONS + " ADD COLUMN "+LEGACY_C_LOG_TRANSACTIONS_SPLIT+" INTEGER DEFAULT 0;");

        db.execSQL(LEGACY_SQL_CREATE_TABLE_REF_PRODUCTS);
        db.execSQL(LEGACY_SQL_CREATE_TABLE_LOG_PRODUCTS);

        ContentValues cv = new ContentValues();
        cv.put(LEGACY_C_ID, 0);
        cv.put(LEGACY_C_REF_PRODUCTS_NAME, "default_product");
        db.insert(LEGACY_T_REF_PRODUCTS, "", cv);

        Cursor cursorTransactions = db.rawQuery("SELECT _id, Amount FROM log_Transactions WHERE Deleted = 0", null);
        if (cursorTransactions != null) {
            try {
                if (cursorTransactions.moveToFirst()) {
                    while (!cursorTransactions.isAfterLast()) {
                        cv.clear();
                        cv.put(LEGACY_C_SYNC_FBID, "");
                        cv.put(LEGACY_C_SYNC_TS, -1);
                        cv.put(LEGACY_C_SYNC_DELETED, 0);
                        cv.put(LEGACY_C_SYNC_DIRTY, 0);
                        cv.put(LEGACY_C_SYNC_LASTEDITED, "");
                        cv.put(LEGACY_C_LOG_PRODUCTS_TRANSACTIONID, cursorTransactions.getLong(0));
                        cv.put(LEGACY_C_LOG_PRODUCTS_PRODUCTID, 0);
                        cv.put(LEGACY_C_LOG_PRODUCTS_CATEGORY_ID, -1);
                        cv.put(LEGACY_C_LOG_PRODUCTS_PROJECT_ID, -1);
                        cv.put(LEGACY_C_LOG_PRODUCTS_PRICE, cursorTransactions.getDouble(1));
                        cv.put(LEGACY_C_LOG_PRODUCTS_QUANTITY, 1);
                        db.insert(LEGACY_T_LOG_PRODUCTS, null, cv);
                        cursorTransactions.moveToNext();
                    }
                }
            } finally {
                cursorTransactions.close();
            }
        }
        db.execSQL("CREATE INDEX [idx_Products] ON [log_Products] ([Deleted], [TransactionID], [ProductID]);");
    }

    public static void update33(SQLiteDatabase db) {
        db.execSQL("DELETE FROM log_Products WHERE TransactionID < 0");

        Cursor cursor = db.rawQuery("SELECT _id, Amount FROM log_Transactions " +
                "WHERE _id not in (SELECT TransactionID FROM log_Products) AND Deleted = 0", null);

        ContentValues cv = new ContentValues();

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        cv.clear();
                        cv.put(LEGACY_C_SYNC_FBID, "");
                        cv.put(LEGACY_C_SYNC_TS, -1);
                        cv.put(LEGACY_C_SYNC_DELETED, 0);
                        cv.put(LEGACY_C_SYNC_DIRTY, 0);
                        cv.put(LEGACY_C_SYNC_LASTEDITED, "");
                        cv.put(LEGACY_C_LOG_PRODUCTS_TRANSACTIONID, cursor.getLong(0));
                        cv.put(LEGACY_C_LOG_PRODUCTS_PRODUCTID, 0);
                        cv.put(LEGACY_C_LOG_PRODUCTS_CATEGORY_ID, -1);
                        cv.put(LEGACY_C_LOG_PRODUCTS_PROJECT_ID, -1);
                        cv.put(LEGACY_C_LOG_PRODUCTS_PRICE, cursor.getDouble(1));
                        cv.put(LEGACY_C_LOG_PRODUCTS_QUANTITY, 1);
                        db.insert(LEGACY_T_LOG_PRODUCTS, null, cv);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

}
