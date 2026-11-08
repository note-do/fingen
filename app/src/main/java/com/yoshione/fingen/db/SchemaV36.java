package com.yoshione.fingen.db;

import android.database.sqlite.SQLiteDatabase;

/**
 * DDL схемы версии 36. Значения написаны литералами (не через константы DBHelper): миграция 35 -> 36
 * не должна зависеть от того, как DBHelper изменится в следующих версиях.
 * <p>
 * Внешние ключи всегда ссылаются на итоговые имена таблиц, поэтому таблицу можно создать под временным
 * именем ({@code X_new}), а потом переименовать в {@code X}.
 * Суммы хранятся целыми числами в минорных единицах валюты счета, курс - ×10^6, количество - ×10^3.
 */
public final class SchemaV36 {

    private SchemaV36() {
    }

    /**
     * Порядок создания и пересборки таблиц: родители, потом дети (log_Running_Balance последней).
     */
    public static final String[] TABLES = {
            "ref_Currencies", "ref_Accounts", "ref_Accounts_Sets", "log_Accounts_Sets",
            "ref_Categories", "ref_Payees", "ref_Projects", "ref_Locations", "ref_Departments",
            "ref_SimpleDebts", "ref_Products", "ref_Senders", "log_Transactions", "log_Templates",
            "log_Products", "ref_Debts", "ref_budget", "ref_budget_debts", "log_Incoming_SMS",
            "ref_Sms_Parser_Patterns", "log_Running_Balance"
    };

    private static final String COMMON = "_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, Deleted INTEGER NOT NULL DEFAULT 0, ";

    private static String ref(String table, String onDelete) {
        return " REFERENCES " + table + "(_id) ON DELETE " + onDelete + " ON UPDATE CASCADE";
    }

    /**
     * @param table итоговое имя таблицы
     * @param name  имя, под которым создается таблица (итоговое или временное)
     */
    public static String createTableSql(String table, String name) {
        switch (table) {
            case "ref_Currencies":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Code TEXT NOT NULL, Symbol TEXT NOT NULL, Name TEXT NOT NULL, "
                        + "DecimalCount INTEGER NOT NULL CHECK (DecimalCount BETWEEN 0 AND 8), "
                        + "OrderNumber INTEGER)";
            case "ref_Accounts":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Type INTEGER NOT NULL, Name TEXT NOT NULL, "
                        + "CurrencyId INTEGER" + ref("ref_Currencies", "SET NULL") + ", "
                        + "Emitent TEXT, Last4Digits INTEGER, Comment TEXT, "
                        + "StartBalance INTEGER NOT NULL, IsClosed INTEGER NOT NULL DEFAULT 0, "
                        + "OrderNumber INTEGER, CreditLimit INTEGER, SearchString TEXT)";
            case "ref_Accounts_Sets":
                return "CREATE TABLE " + name + " (" + COMMON + "Name TEXT NOT NULL)";
            case "log_Accounts_Sets":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "SetId INTEGER NOT NULL" + ref("ref_Accounts_Sets", "CASCADE") + ", "
                        + "AccountId INTEGER NOT NULL" + ref("ref_Accounts", "CASCADE") + ", "
                        + "UNIQUE (SetId, AccountId, Deleted) ON CONFLICT ABORT)";
            case "ref_Categories":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, Color TEXT, "
                        + "ParentId INTEGER" + ref("ref_Categories", "SET NULL") + ", "
                        + "OrderNumber INTEGER NOT NULL, FullName TEXT, SearchString TEXT)";
            case "ref_Payees":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, "
                        + "DefaultCategoryId INTEGER" + ref("ref_Categories", "SET NULL") + ", "
                        + "ParentId INTEGER" + ref("ref_Payees", "SET NULL") + ", "
                        + "OrderNumber INTEGER, FullName TEXT, SearchString TEXT)";
            case "ref_Projects":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, Color TEXT DEFAULT '#ffffff', IsActive INTEGER NOT NULL DEFAULT 1, "
                        + "ParentId INTEGER" + ref("ref_Projects", "SET NULL") + ", "
                        + "OrderNumber INTEGER, FullName TEXT, SearchString TEXT)";
            case "ref_Locations":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, Lon REAL NOT NULL, Lat REAL NOT NULL, Address TEXT, "
                        + "ParentId INTEGER" + ref("ref_Locations", "SET NULL") + ", "
                        + "OrderNumber INTEGER, FullName TEXT, SearchString TEXT)";
            case "ref_Departments":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, IsActive INTEGER NOT NULL DEFAULT 1, "
                        + "ParentId INTEGER" + ref("ref_Departments", "SET NULL") + ", "
                        + "OrderNumber INTEGER, FullName TEXT, SearchString TEXT)";
            case "ref_SimpleDebts":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, IsActive INTEGER NOT NULL DEFAULT 1, "
                        + "StartAmount INTEGER NOT NULL DEFAULT 0, "
                        + "CurrencyId INTEGER" + ref("ref_Currencies", "SET NULL") + ", "
                        + "SearchString TEXT)";
            case "ref_Products":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL DEFAULT '', SearchString TEXT NOT NULL DEFAULT '')";
            case "ref_Senders":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, PhoneNo TEXT NOT NULL, AmountPos INTEGER, BalancePos INTEGER, "
                        + "LeadingCurrencySymbol INTEGER, DateFormat TEXT, "
                        + "IsActive INTEGER NOT NULL DEFAULT 1, AddCreditLimitToBalance INTEGER NOT NULL DEFAULT 0)";
            case "log_Transactions":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "DateTime INTEGER NOT NULL, "
                        + "SrcAccountId INTEGER NOT NULL" + ref("ref_Accounts", "CASCADE") + ", "
                        + "DestAccountId INTEGER" + ref("ref_Accounts", "CASCADE") + ", "
                        + "PayeeId INTEGER" + ref("ref_Payees", "SET NULL") + ", "
                        + "CategoryId INTEGER" + ref("ref_Categories", "SET NULL") + ", "
                        + "ProjectId INTEGER" + ref("ref_Projects", "SET NULL") + ", "
                        + "SimpleDebtId INTEGER" + ref("ref_SimpleDebts", "SET NULL") + ", "
                        + "DepartmentId INTEGER" + ref("ref_Departments", "SET NULL") + ", "
                        + "LocationId INTEGER" + ref("ref_Locations", "SET NULL") + ", "
                        + "Amount INTEGER NOT NULL, ExchangeRate INTEGER NOT NULL DEFAULT 1000000000000, "
                        + "IsAutoCreated INTEGER NOT NULL DEFAULT 0, Comment TEXT, File TEXT, "
                        + "Lon REAL, Lat REAL, Accuracy INTEGER, "
                        + "FN INTEGER NOT NULL DEFAULT 0, FD INTEGER NOT NULL DEFAULT 0, FP INTEGER NOT NULL DEFAULT 0, "
                        + "IsSplit INTEGER NOT NULL DEFAULT 0, SearchString TEXT)";
            case "log_Templates":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Name TEXT NOT NULL, Type INTEGER NOT NULL, "
                        + "SrcAccountId INTEGER NOT NULL" + ref("ref_Accounts", "CASCADE") + ", "
                        + "DestAccountId INTEGER" + ref("ref_Accounts", "CASCADE") + ", "
                        + "PayeeId INTEGER" + ref("ref_Payees", "SET NULL") + ", "
                        + "CategoryId INTEGER" + ref("ref_Categories", "SET NULL") + ", "
                        + "ProjectId INTEGER" + ref("ref_Projects", "SET NULL") + ", "
                        + "DepartmentId INTEGER" + ref("ref_Departments", "SET NULL") + ", "
                        + "LocationId INTEGER" + ref("ref_Locations", "SET NULL") + ", "
                        + "Amount INTEGER, ExchangeRate INTEGER DEFAULT 1000000000000, Comment TEXT, SearchString TEXT)";
            case "log_Products":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "TransactionId INTEGER NOT NULL" + ref("log_Transactions", "CASCADE") + ", "
                        + "ProductId INTEGER NOT NULL DEFAULT 0" + ref("ref_Products", "RESTRICT") + ", "
                        + "CategoryId INTEGER DEFAULT NULL" + ref("ref_Categories", "SET NULL") + ", "
                        + "ProjectId INTEGER DEFAULT NULL" + ref("ref_Projects", "SET NULL") + ", "
                        + "Price INTEGER NOT NULL DEFAULT 0, "
                        + "Quantity INTEGER NOT NULL DEFAULT 1000 CHECK (Quantity >= 0))";
            case "ref_Debts":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "AccountId INTEGER NOT NULL" + ref("ref_Accounts", "CASCADE") + ", "
                        + "PayeeId INTEGER" + ref("ref_Payees", "SET NULL") + ", "
                        + "CategoryId INTEGER" + ref("ref_Categories", "SET NULL") + ", "
                        + "IsClosed INTEGER NOT NULL DEFAULT 0, Comment TEXT)";
            case "ref_budget":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Year INTEGER NOT NULL, Month INTEGER NOT NULL, "
                        + "CategoryId INTEGER NOT NULL" + ref("ref_Categories", "CASCADE") + ", "
                        + "Amount INTEGER NOT NULL, "
                        + "CurrencyId INTEGER NOT NULL" + ref("ref_Currencies", "CASCADE") + ", "
                        + "UNIQUE (Deleted, Year, Month, CategoryId, CurrencyId) ON CONFLICT REPLACE)";
            case "ref_budget_debts":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Year INTEGER NOT NULL, Month INTEGER NOT NULL, "
                        + "DebtId INTEGER NOT NULL" + ref("ref_Debts", "CASCADE") + ", "
                        + "Amount INTEGER NOT NULL DEFAULT 0, "
                        + "UNIQUE (Deleted, Year, Month, DebtId) ON CONFLICT REPLACE)";
            case "log_Incoming_SMS":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "DateTime INTEGER NOT NULL, "
                        + "SenderId INTEGER NOT NULL" + ref("ref_Senders", "RESTRICT") + ", "
                        + "Body TEXT NOT NULL)";
            case "ref_Sms_Parser_Patterns":
                return "CREATE TABLE " + name + " (" + COMMON
                        + "Type INTEGER NOT NULL, Object TEXT NOT NULL, Pattern TEXT NOT NULL, "
                        + "UNIQUE (Type, Pattern, Deleted) ON CONFLICT REPLACE)";
            case "log_Running_Balance":
                return "CREATE TABLE " + name + " ("
                        + "AccountId INTEGER NOT NULL" + ref("ref_Accounts", "CASCADE") + ", "
                        + "TransactionId INTEGER NOT NULL" + ref("log_Transactions", "CASCADE") + ", "
                        + "DateTime INTEGER NOT NULL, Income INTEGER NOT NULL DEFAULT 0, Expense INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (AccountId, TransactionId))";
            default:
                throw new IllegalArgumentException("Unknown table " + table);
        }
    }

    private static String idx(String table, String name, String columns) {
        return "CREATE INDEX idx_" + table + "_" + name + " ON " + table + " (" + columns + ")";
    }

    private static String uidx(String table, String name, String columns) {
        return "CREATE UNIQUE INDEX idx_" + table + "_" + name + " ON " + table + " (" + columns + ") WHERE Deleted = 0";
    }

    public static String[] createIndexesSql(String table) {
        switch (table) {
            case "ref_Currencies":
                return new String[]{uidx(table, "Code", "Code")};
            case "ref_Accounts":
                return new String[]{uidx(table, "Name", "Name"), idx(table, "CurrencyId", "CurrencyId")};
            case "ref_Accounts_Sets":
                return new String[]{uidx(table, "Name", "Name")};
            case "log_Accounts_Sets":
                return new String[]{idx(table, "AccountId", "AccountId")};
            case "ref_Categories":
            case "ref_Projects":
            case "ref_Locations":
            case "ref_Departments":
                return new String[]{uidx(table, "Name_ParentId", "Name, IFNULL(ParentId, 0)"), idx(table, "ParentId", "ParentId")};
            case "ref_Payees":
                return new String[]{uidx(table, "Name_ParentId", "Name, IFNULL(ParentId, 0)"), idx(table, "ParentId", "ParentId"),
                        idx(table, "DefaultCategoryId", "DefaultCategoryId")};
            case "ref_SimpleDebts":
                return new String[]{uidx(table, "Name", "Name"), idx(table, "CurrencyId", "CurrencyId")};
            case "ref_Products":
                return new String[]{uidx(table, "Name", "Name")};
            case "ref_Senders":
                return new String[]{uidx(table, "Name", "Name"), uidx(table, "PhoneNo", "PhoneNo")};
            case "log_Transactions":
                return new String[]{
                        idx(table, "Deleted_DateTime", "Deleted, DateTime"),
                        idx(table, "SrcAccountId_DateTime", "SrcAccountId, DateTime"),
                        idx(table, "DestAccountId", "DestAccountId"),
                        idx(table, "PayeeId", "PayeeId"),
                        idx(table, "CategoryId", "CategoryId"),
                        idx(table, "ProjectId", "ProjectId"),
                        idx(table, "SimpleDebtId", "SimpleDebtId"),
                        idx(table, "DepartmentId", "DepartmentId"),
                        idx(table, "LocationId", "LocationId")};
            case "log_Templates":
                return new String[]{
                        idx(table, "SrcAccountId", "SrcAccountId"),
                        idx(table, "DestAccountId", "DestAccountId"),
                        idx(table, "PayeeId", "PayeeId"),
                        idx(table, "CategoryId", "CategoryId"),
                        idx(table, "ProjectId", "ProjectId"),
                        idx(table, "DepartmentId", "DepartmentId"),
                        idx(table, "LocationId", "LocationId")};
            case "log_Products":
                return new String[]{
                        idx(table, "TransactionId_Deleted", "TransactionId, Deleted"),
                        idx(table, "ProductId", "ProductId"),
                        idx(table, "CategoryId", "CategoryId"),
                        idx(table, "ProjectId", "ProjectId")};
            case "ref_Debts":
                return new String[]{uidx(table, "AccountId", "AccountId"),
                        idx(table, "PayeeId", "PayeeId"), idx(table, "CategoryId", "CategoryId")};
            case "ref_budget":
                return new String[]{idx(table, "CategoryId", "CategoryId"), idx(table, "CurrencyId", "CurrencyId")};
            case "ref_budget_debts":
                return new String[]{idx(table, "DebtId", "DebtId")};
            case "log_Incoming_SMS":
                return new String[]{idx(table, "DateTime", "DateTime"), idx(table, "SenderId", "SenderId")};
            case "ref_Sms_Parser_Patterns":
                return new String[0];
            case "log_Running_Balance":
                return new String[]{idx(table, "AccountId_DateTime", "AccountId, DateTime"),
                        idx(table, "TransactionId", "TransactionId")};
            default:
                throw new IllegalArgumentException("Unknown table " + table);
        }
    }

    /**
     * Создает все таблицы и индексы схемы 36 (чистая установка). Данные по умолчанию не добавляет.
     */
    public static void createAll(SQLiteDatabase db) {
        for (String table : TABLES) {
            db.execSQL(createTableSql(table, table));
            for (String sql : createIndexesSql(table)) {
                db.execSQL(sql);
            }
        }
    }
}
