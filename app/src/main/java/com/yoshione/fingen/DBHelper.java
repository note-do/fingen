package com.yoshione.fingen;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import android.util.Log;
import com.yoshione.fingen.backup.BackupPassword;
import com.yoshione.fingen.dao.DatabaseUpgradeHelper;
import com.yoshione.fingen.db.RunningBalance;
import com.yoshione.fingen.db.SchemaV36;
import com.yoshione.fingen.db.UpdateHelper;
import com.yoshione.fingen.db.migrations.Migration17;
import com.yoshione.fingen.db.migrations.Migration36;
import com.yoshione.fingen.db.migrations.MigrationRunner;
import com.yoshione.fingen.db.migrations.MigrationStep;
import com.yoshione.fingen.interfaces.IOnUnzipComplete;
import com.yoshione.fingen.managers.CabbageManager;
import com.yoshione.fingen.managers.CurrencyScale;
import com.yoshione.fingen.model.Cabbage;
import com.yoshione.fingen.utils.BackupFolder;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.utils.Lg;
import com.yoshione.fingen.utils.Translit;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by slv on 12.08.2015.
 * 1
 */
public class DBHelper extends SQLiteOpenHelper implements BaseColumns {

    private static DBHelper mInstance = null;

    public SQLiteDatabase getDatabase() {
        return mDatabase;
    }

    private SQLiteDatabase mDatabase;
    public static final String DATABASE_NAME = "fingen.db";
    public static final int DATABASE_VERSION = 36;
    public static final String TAG = "DBHelper";

    //common fields
    public static final String C_ID = "_id";
    public static final String C_SYNC_DELETED = "Deleted";
    public static final String C_ORDERNUMBER = "OrderNumber";
    public static final String C_PARENTID = "ParentId";
    private static final String C_SEARCH_STRING = "SearchString";
    public static final String C_FULL_NAME = "FullName";
    private static String getFullNameColumn(String tableName) {
        return "(SELECT path FROM (with recursive m(path, _id, name) AS (SELECT Name, _id, Name FROM "+tableName+" WHERE ParentId IS NULL UNION ALL  SELECT path||'\\'||t.Name, t._id, t.Name FROM "+tableName+" t, m WHERE t.ParentId = m._id) SELECT * FROM m where _id = "+tableName+"._id)) AS FullName";
    }

    /**
     * Схема 36: имена колонок. Таблицы создает {@link SchemaV36}, здесь только константы для DAO и моделей.
     * Суммы в БД - INTEGER в минорных единицах валюты счета (см. managers/CurrencyScale), пустая ссылка - NULL (db/DbRefs).
     */

    //<editor-fold desc="ref_Currencies">
    public static final String T_REF_CURRENCIES = "ref_Currencies";
    public static final String C_REF_CURRENCIES_CODE = "Code";//трехсимвольный уникальный идентификатор валюты
    public static final String C_REF_CURRENCIES_SYMBOL = "Symbol";//символы, испульзуемые при выводе суммы (например $, руб.)
    public static final String C_REF_CURRENCIES_NAME = "Name";
    public static final String C_REF_CURRENCIES_DECIMALCOUNT = "DecimalCount";//количество знаков после запятой (0..8)
    public static final String T_REF_CURRENCIES_ALL_COLUMNS[] = new String[]{
            C_ID, C_SYNC_DELETED, C_REF_CURRENCIES_CODE,
            C_REF_CURRENCIES_SYMBOL, C_REF_CURRENCIES_NAME, C_REF_CURRENCIES_DECIMALCOUNT, C_ORDERNUMBER};
    //</editor-fold>

    //<editor-fold desc="ref_Accounts">
    public static final String T_REF_ACCOUNTS = "ref_Accounts";
    public static final String C_REF_ACCOUNTS_TYPE = "Type";
    public static final String C_REF_ACCOUNTS_NAME = "Name";
    public static final String C_REF_ACCOUNTS_CURRENCY = "CurrencyId";
    public static final String C_REF_ACCOUNTS_EMITENT = "Emitent";
    public static final String C_REF_ACCOUNTS_LAST4DIGITS = "Last4Digits";
    public static final String C_REF_ACCOUNTS_COMMENT = "Comment";
    public static final String C_REF_ACCOUNTS_STARTBALANCE = "StartBalance";
    public static final String C_REF_ACCOUNTS_ISCLOSED = "IsClosed";
    public static final String C_REF_ACCOUNTS_ORDER = "OrderNumber";
    public static final String C_REF_ACCOUNTS_CREDITLIMIT = "CreditLimit";
    private static final String C_REF_ACCOUNTS_INCOME = "(SELECT Income FROM log_Running_Balance WHERE AccountId = ref_Accounts._id AND DateTime = (SELECT MAX(DateTime) FROM log_Running_Balance WHERE AccountId = ref_Accounts._id )) AS Income";
    private static final String C_REF_ACCOUNTS_EXPENSE = "(SELECT Expense FROM log_Running_Balance WHERE AccountId = ref_Accounts._id AND DateTime = (SELECT MAX(DateTime) FROM log_Running_Balance WHERE AccountId = ref_Accounts._id )) AS Expense";
    public static final String[] T_REF_ACCOUNTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_ACCOUNTS_TYPE, C_REF_ACCOUNTS_NAME, C_REF_ACCOUNTS_CURRENCY,
            C_REF_ACCOUNTS_EMITENT, C_REF_ACCOUNTS_LAST4DIGITS, C_REF_ACCOUNTS_COMMENT,
            C_REF_ACCOUNTS_STARTBALANCE, C_REF_ACCOUNTS_ISCLOSED, C_REF_ACCOUNTS_ORDER,
            C_REF_ACCOUNTS_CREDITLIMIT, C_REF_ACCOUNTS_INCOME, C_REF_ACCOUNTS_EXPENSE, C_SEARCH_STRING};

    public static final String T_REF_ACCOUNTS_SETS = "ref_Accounts_Sets";
    public static final String C_REF_ACCOUNTS_SETS_NAME = "Name";
    public static final String[] T_REF_ACCOUNTS_SETS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_ACCOUNTS_SETS_NAME};
    //</editor-fold>

    //<editor-fold desc="log_Accounts_Sets">
    public static final String T_LOG_ACCOUNTS_SETS = "log_Accounts_Sets";
    public static final String C_LOG_ACCOUNTS_SETS_SET = "SetId";
    public static final String C_LOG_ACCOUNTS_SETS_ACCOUNT = "AccountId";
    public static final String[] T_LOG_ACCOUNTS_SETS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_ACCOUNTS_SETS_SET, C_LOG_ACCOUNTS_SETS_ACCOUNT};
    //</editor-fold>

    //<editor-fold desc="ref_Categories">
    public static final String T_REF_CATEGORIES = "ref_Categories";
    public static final String C_REF_CATEGORIES_NAME = "Name";
    public static final String C_REF_CATEGORIES_COLOR = "Color";
    public static final String C_REF_CATEGORIES_PLAN = "Plan";
    public static final String[] T_REF_CATEGORIES_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_CATEGORIES_NAME, C_REF_CATEGORIES_COLOR, C_PARENTID,
            C_ORDERNUMBER, C_FULL_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="ref_Payees">
    public static final String T_REF_PAYEES = "ref_Payees";
    public static final String C_REF_PAYEES_NAME = "Name";
    public static final String C_REF_PAYEES_DEFCATEGORY = "DefaultCategoryId";
    public static final String[] T_REF_PAYEES_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED, C_REF_PAYEES_NAME,
            C_REF_PAYEES_DEFCATEGORY, C_PARENTID, C_ORDERNUMBER, C_FULL_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="ref_Projects">
    public static final String T_REF_PROJECTS = "ref_Projects";
    public static final String C_REF_PROJECTS_NAME = "Name";
    public static final String C_REF_PROJECTS_COLOR = "Color";
    public static final String C_REF_PROJECTS_ISACTIVE = "IsActive";//Boolean
    public static final String[] T_REF_PROJECTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_PROJECTS_NAME, C_REF_PROJECTS_COLOR, C_REF_PROJECTS_ISACTIVE, C_PARENTID, C_ORDERNUMBER, C_FULL_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="ref_Locations">
    public static final String T_REF_LOCATIONS = "ref_Locations";
    public static final String C_REF_LOCATIONS_NAME = "Name";
    public static final String C_REF_LOCATIONS_LON = "Lon";
    public static final String C_REF_LOCATIONS_LAT = "Lat";
    public static final String C_REF_LOCATIONS_ADDRESS = "Address";
    public static final String[] T_REF_LOCATIONS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_LOCATIONS_NAME, C_REF_LOCATIONS_LON, C_REF_LOCATIONS_LAT, C_REF_LOCATIONS_ADDRESS,
            C_PARENTID, C_ORDERNUMBER, C_FULL_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="Departments">
    public static final String T_REF_DEPARTMENTS = "ref_Departments";
    public static final String C_REF_DEPARTMENTS_NAME = "Name";
    public static final String C_REF_DEPARTMENTS_ISACTIVE = "IsActive";//Boolean
    public static final String[] T_REF_DEPARTMENTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_DEPARTMENTS_NAME, C_REF_DEPARTMENTS_ISACTIVE, C_PARENTID, C_ORDERNUMBER, C_FULL_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="ref_SimpleDebts">
    public static final String T_REF_SIMPLEDEBTS = "ref_SimpleDebts";
    public static final String C_REF_SIMPLEDEBTS_NAME = "Name";
    public static final String C_REF_SIMPLEDEBTS_ISACTIVE = "IsActive";
    public static final String C_REF_SIMPLEDEBTS_START_AMOUNT = "StartAmount";
    public static final String C_REF_SIMPLEDEBTS_CABBAGE = "CurrencyId";
    public static final String[] T_REF_SIMPLEDEBTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_SIMPLEDEBTS_NAME, C_REF_SIMPLEDEBTS_ISACTIVE, C_REF_SIMPLEDEBTS_START_AMOUNT,
            C_REF_SIMPLEDEBTS_CABBAGE, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="log_Transactions">
    public static final String T_LOG_TRANSACTIONS = "log_Transactions";
    public static final String C_LOG_TRANSACTIONS_DATETIME = "DateTime";
    public static final String C_LOG_TRANSACTIONS_SRCACCOUNT = "SrcAccountId";
    public static final String C_LOG_TRANSACTIONS_PAYEE = "PayeeId";
    public static final String C_LOG_TRANSACTIONS_CATEGORY = "CategoryId";
    public static final String C_LOG_TRANSACTIONS_AMOUNT = "Amount";
    public static final String C_LOG_TRANSACTIONS_PROJECT = "ProjectId";
    public static final String C_LOG_TRANSACTIONS_SIMPLEDEBT = "SimpleDebtId";
    public static final String C_LOG_TRANSACTIONS_DEPARTMENT = "DepartmentId";
    public static final String C_LOG_TRANSACTIONS_LOCATION = "LocationId";
    public static final String C_LOG_TRANSACTIONS_COMMENT = "Comment";
    public static final String C_LOG_TRANSACTIONS_FILE = "File";
    public static final String C_LOG_TRANSACTIONS_DESTACCOUNT = "DestAccountId";
    public static final String C_LOG_TRANSACTIONS_EXCHANGERATE = "ExchangeRate";
    public static final String C_LOG_TRANSACTIONS_AUTOCREATED = "IsAutoCreated";
    public static final String C_LOG_TRANSACTIONS_LON = "Lon";
    public static final String C_LOG_TRANSACTIONS_LAT = "Lat";
    public static final String C_LOG_TRANSACTIONS_ACCURACY = "Accuracy";
    public static final String C_LOG_TRANSACTIONS_FN = "FN";
    public static final String C_LOG_TRANSACTIONS_FD = "FD";
    public static final String C_LOG_TRANSACTIONS_FP = "FP";
    public static final String C_LOG_TRANSACTIONS_SPLIT = "IsSplit";
    public static final String[] T_LOG_TRANSACTIONS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_TRANSACTIONS_DATETIME, C_LOG_TRANSACTIONS_SRCACCOUNT, C_LOG_TRANSACTIONS_PAYEE,
            C_LOG_TRANSACTIONS_CATEGORY, C_LOG_TRANSACTIONS_AMOUNT, C_LOG_TRANSACTIONS_PROJECT,
            C_LOG_TRANSACTIONS_SIMPLEDEBT, C_LOG_TRANSACTIONS_DEPARTMENT, C_LOG_TRANSACTIONS_LOCATION,
            C_LOG_TRANSACTIONS_COMMENT, C_LOG_TRANSACTIONS_FILE, C_LOG_TRANSACTIONS_DESTACCOUNT,
            C_LOG_TRANSACTIONS_EXCHANGERATE, C_LOG_TRANSACTIONS_AUTOCREATED, C_LOG_TRANSACTIONS_LON,
            C_LOG_TRANSACTIONS_LAT, C_LOG_TRANSACTIONS_ACCURACY, C_SEARCH_STRING,
            C_LOG_TRANSACTIONS_FN, C_LOG_TRANSACTIONS_FD, C_LOG_TRANSACTIONS_FP, C_LOG_TRANSACTIONS_SPLIT};
    //</editor-fold>

    //<editor-fold desc="log_Templates">
    public static final String T_LOG_TEMPLATES = "log_Templates";
    public static final String C_LOG_TEMPLATES_SRCACCOUNT = "SrcAccountId";
    public static final String C_LOG_TEMPLATES_PAYEE = "PayeeId";
    public static final String C_LOG_TEMPLATES_CATEGORY = "CategoryId";
    public static final String C_LOG_TEMPLATES_AMOUNT = "Amount";
    public static final String C_LOG_TEMPLATES_PROJECT = "ProjectId";
    public static final String C_LOG_TEMPLATES_DEPARTMENT = "DepartmentId";
    public static final String C_LOG_TEMPLATES_LOCATION = "LocationId";
    public static final String C_LOG_TEMPLATES_NAME = "Name";
    public static final String C_LOG_TEMPLATES_DESTACCOUNT = "DestAccountId";
    public static final String C_LOG_TEMPLATES_EXCHANGERATE = "ExchangeRate";
    public static final String C_LOG_TEMPLATES_COMMENT = "Comment";
    public static final String C_LOG_TEMPLATES_TYPE = "Type";
    public static final String[] T_LOG_TEMPLATES_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_TEMPLATES_SRCACCOUNT, C_LOG_TEMPLATES_PAYEE, C_LOG_TEMPLATES_CATEGORY,
            C_LOG_TEMPLATES_AMOUNT, C_LOG_TEMPLATES_PROJECT, C_LOG_TEMPLATES_DEPARTMENT,
            C_LOG_TEMPLATES_LOCATION, C_LOG_TEMPLATES_NAME, C_LOG_TEMPLATES_DESTACCOUNT,
            C_LOG_TEMPLATES_EXCHANGERATE, C_LOG_TEMPLATES_TYPE, C_LOG_TEMPLATES_COMMENT, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="log_Running_Balance">
    public static final String T_LOG_RB = "log_Running_Balance";
    public static final String C_LOG_RB_ACCOUNT_ID = "AccountId";
    public static final String C_LOG_RB_TRANSACTION_ID = "TransactionId";
    public static final String C_LOG_RB_DATETIME = "DateTime";
    public static final String C_LOG_RB_INCOME = "Income";
    public static final String C_LOG_RB_EXPENSE = "Expense";
    //</editor-fold>

    //<editor-fold desc="log_Incoming_SMS">
    public static final String T_LOG_INCOMING_SMS = "log_Incoming_SMS";
    public static final String C_LOG_INCOMING_SMS_DATETIME = "DateTime";//INTEGER, UTC миллисекунды
    public static final String C_LOG_INCOMING_SMS_SENDER = "SenderId";
    public static final String C_LOG_INCOMING_SMS_BODY = "Body";
    public static final String[] T_LOG_INCOMING_SMS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_INCOMING_SMS_DATETIME, C_LOG_INCOMING_SMS_SENDER, C_LOG_INCOMING_SMS_BODY};
    //</editor-fold>

    //<editor-fold desc="ref_Sms_Parser_Patterns">
    public static final String T_LOG_SMS_PARSER_PATTERNS = "ref_Sms_Parser_Patterns";
    public static final String C_LOG_SMS_PARSER_PATTERNS_TYPE = "Type";
    public static final String C_LOG_SMS_PARSER_PATTERNS_OBJECT = "Object";
    public static final String C_LOG_SMS_PARSER_PATTERNS_PATTERN = "Pattern";
    public static final String[] T_LOG_SMS_PARSER_PATTERNS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_SMS_PARSER_PATTERNS_TYPE, C_LOG_SMS_PARSER_PATTERNS_OBJECT, C_LOG_SMS_PARSER_PATTERNS_PATTERN};
    //</editor-fold>

    //<editor-fold desc="ref_Debts">
    public static final String T_REF_DEBTS = "ref_Debts";
    public static final String C_REF_DEBTS_ACCOUNT = "AccountId";
    public static final String C_REF_DEBTS_PAYEE = "PayeeId";
    public static final String C_REF_DEBTS_CATEGORY = "CategoryId";
    public static final String C_REF_DEBTS_CLOSED = "IsClosed";
    public static final String C_REF_DEBTS_COMMENT = "Comment";
    public static final String[] T_REF_DEBTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_DEBTS_ACCOUNT, C_REF_DEBTS_PAYEE, C_REF_DEBTS_CATEGORY, C_REF_DEBTS_CLOSED,
            C_REF_DEBTS_COMMENT};
    //</editor-fold>

    //<editor-fold desc="ref_budget">
    public static final String T_LOG_BUDGET = "ref_budget";
    public static final String C_LOG_BUDGET_YEAR = "Year";
    public static final String C_LOG_BUDGET_MONTH = "Month";
    public static final String C_LOG_BUDGET_CATEGORY = "CategoryId";
    public static final String C_LOG_BUDGET_AMOUNT = "Amount";
    public static final String C_LOG_BUDGET_CURRENCY = "CurrencyId";
    public static final String[] T_LOG_BUDGET_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_BUDGET_YEAR, C_LOG_BUDGET_MONTH, C_LOG_BUDGET_CATEGORY, C_LOG_BUDGET_AMOUNT,
            C_LOG_BUDGET_CURRENCY};
    //</editor-fold>

    //<editor-fold desc="ref_budget_debts">
    public static final String T_LOG_BUDGET_DEBTS = "ref_budget_debts";
    public static final String C_LOG_BUDGET_DEBTS_YEAR = "Year";
    public static final String C_LOG_BUDGET_DEBTS_MONTH = "Month";
    public static final String C_LOG_BUDGET_DEBTS_CREDIT = "DebtId";
    public static final String C_LOG_BUDGET_DEBTS_AMOUNT = "Amount";
    public static final String[] T_LOG_BUDGET_DEBTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_BUDGET_DEBTS_YEAR, C_LOG_BUDGET_DEBTS_MONTH, C_LOG_BUDGET_DEBTS_CREDIT,
            C_LOG_BUDGET_DEBTS_AMOUNT};
    //</editor-fold>

    //<editor-fold desc="ref_Senders">
    public static final String T_REF_SENDERS = "ref_Senders";
    public static final String C_REF_SENDERS_NAME = "Name";
    public static final String C_REF_SENDERS_PHONENO = "PhoneNo";
    public static final String C_REF_SENDERS_AMOUNTPOS = "AmountPos";
    public static final String C_REF_SENDERS_BALANCEPOS = "BalancePos";
    public static final String C_REF_SENDERS_DATEFORMAT = "DateFormat";
    public static final String C_REF_SENDERS_LEADING_CURRENCY_SYMBOL = "LeadingCurrencySymbol";
    public static final String C_REF_SENDERS_ISACTIVE = "IsActive";//Boolean
    public static final String C_REF_SENDERS_ADD_CREDIT_LIMIT_TO_BALANCE = "AddCreditLimitToBalance";//Boolean
    public static final String[] T_REF_SENDERS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_SENDERS_NAME, C_REF_SENDERS_PHONENO, C_REF_SENDERS_AMOUNTPOS, C_REF_SENDERS_BALANCEPOS,
            C_REF_SENDERS_DATEFORMAT, C_REF_SENDERS_LEADING_CURRENCY_SYMBOL, C_REF_SENDERS_ISACTIVE, C_REF_SENDERS_ADD_CREDIT_LIMIT_TO_BALANCE};
    //</editor-fold>

    //<editor-fold desc="ref_Products">
    public static final String T_REF_PRODUCTS = "ref_Products";
    public static final String C_REF_PRODUCTS_NAME = "Name";
    public static final String[] T_REF_PRODUCTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_REF_PRODUCTS_NAME, C_SEARCH_STRING};
    //</editor-fold>

    //<editor-fold desc="log_Products">
    public static final String T_LOG_PRODUCTS = "log_Products";
    public static final String C_LOG_PRODUCTS_TRANSACTIONID = "TransactionId";
    public static final String C_LOG_PRODUCTS_PRODUCTID = "ProductId";
    public static final String C_LOG_PRODUCTS_CATEGORY_ID = "CategoryId";
    public static final String C_LOG_PRODUCTS_PROJECT_ID = "ProjectId";
    public static final String C_LOG_PRODUCTS_PRICE = "Price";
    public static final String C_LOG_PRODUCTS_QUANTITY = "Quantity";
    public static final String[] T_LOG_PRODUCTS_ALL_COLUMNS = {
            C_ID, C_SYNC_DELETED,
            C_LOG_PRODUCTS_TRANSACTIONID, C_LOG_PRODUCTS_PRODUCTID, C_LOG_PRODUCTS_CATEGORY_ID, C_LOG_PRODUCTS_PROJECT_ID,
            C_LOG_PRODUCTS_PRICE, C_LOG_PRODUCTS_QUANTITY};
    //</editor-fold>

    /**
     * SQL-выражение: сумма транзакции (колонка Amount, минорные единицы) в десятичном виде, по валюте счета-источника.
     * Используется в фильтрах: в запросе должна быть колонка SrcAccountId.
     */
    public static String srcAmountDecimalSql(String amountColumn, String srcAccountColumn) {
        return "(" + amountColumn + " * 1.0 / (SELECT " + CurrencyScale.divisorSql("c.DecimalCount")
                + " FROM ref_Accounts a LEFT JOIN ref_Currencies c ON c._id = a.CurrencyId WHERE a._id = " + srcAccountColumn + "))";
    }

    //<editor-fold desc="Временные таблицы для подсчета суммы транзакций">
    public static final String T_SEARCH_TRANSACTIONS = "search_Transactions";
    //</editor-fold>

    private final Context mContext;

    private DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context.getApplicationContext();
        final DatabaseUpgradeHelper dbh = DatabaseUpgradeHelper.getInstance();
        dbh.setUpgrading(true);
        try {
            mDatabase = getWritableDatabase();
            // Opened successfully: the pre-restore copy is no longer needed (stays if opening throws)
            deleteRestoreBackup(mContext);
        } finally {
            dbh.setUpgrading(false);
        }
        while (dbh.isUpgrading()) {
            SystemClock.sleep(10);
        }
    }

    public synchronized static DBHelper getInstance(Context ctx) {
        if (mInstance == null) {
            mInstance = new DBHelper(ctx);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String table : SchemaV36.TABLES) {
            Log.d(TAG, "Create table " + table);
            db.execSQL(SchemaV36.createTableSql(table, table));
            for (String sql : SchemaV36.createIndexesSql(table)) {
                db.execSQL(sql);
            }
            if (table.equals(T_REF_CURRENCIES)) {
                Cabbage cabbage;
                List<String> codes = Arrays.asList("RUB", "USD", "EUR", "UAH", "BYN", "KZT", "ABC");
                for (String code : codes) {
                    cabbage = CabbageManager.createFromCode(code, mContext);
                    if (cabbage != null) {
                        db.insertOrThrow(T_REF_CURRENCIES, null, cabbage.getCV());
                    }
                }
            }
        }

        ContentValues cv = new ContentValues();
        cv.put(C_ID, 0);
        cv.put(C_REF_PRODUCTS_NAME, "default_product");
        db.insert(T_REF_PRODUCTS, "", cv);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
//        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Внешние ключи включаются после создания и апгрейда схемы: миграции работают с выключенными ключами.
     */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.setForeignKeyConstraintsEnabled(true);
        }
    }

    @SuppressLint({"DefaultLocale", "CallNeedsPermission"})
    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion) {
        Lg.log(TAG, "Upgrade database " + String.valueOf(oldVersion) + " -> " + String.valueOf(newVersion));

        if (oldVersion < 36) {
            // Место на диске и копия fingen.db.pre36 до первого шага цепочки (исключение откатывает апгрейд)
            Migration36.prepare(db);
        }

        //Сделали на всякий случай бэкап
        try {
            backupDB(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        MigrationRunner.run(db, mContext, oldVersion, getMigrationSteps());
    }

    /**
     * Шаги миграций по порядку выполнения. Условие шага — oldVersion &lt; belowVersion.
     * Новая миграция: отдельный класс db/migrations/Migration{N}, строка в этом списке.
     * Старые шаги — методы UpdateHelper, их условия и порядок сохранены как были
     * (в том числе повторный шаг с условием &lt; 25 в конце и шаг &lt; 34 для update33).
     */
    private List<MigrationStep> getMigrationSteps() {
        List<MigrationStep> steps = new ArrayList<>();
        steps.add(new MigrationStep(17, new Migration17()));
        steps.add(new MigrationStep(18, UpdateHelper::update18));
        steps.add(new MigrationStep(19, (db, context) -> UpdateHelper.update19(db)));
        steps.add(new MigrationStep(20, (db, context) -> UpdateHelper.update20(db)));
        steps.add(new MigrationStep(21, (db, context) -> UpdateHelper.update21(db)));
        steps.add(new MigrationStep(22, (db, context) -> UpdateHelper.update22(db)));
        steps.add(new MigrationStep(23, (db, context) -> UpdateHelper.update23(db)));
        steps.add(new MigrationStep(24, (db, context) -> UpdateHelper.update24(db)));
        steps.add(new MigrationStep(25, (db, context) -> UpdateHelper.update25(db)));
        steps.add(new MigrationStep(26, (db, context) -> UpdateHelper.update26(db)));
        steps.add(new MigrationStep(27, (db, context) -> UpdateHelper.update27(db, database -> UpdateHelper.legacyUpdateRunningBalance(database, context))));
        steps.add(new MigrationStep(28, (db, context) -> {
            try {
                UpdateHelper.legacyUpdateRunningBalance(db, context);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        steps.add(new MigrationStep(29, (db, context) -> UpdateHelper.update29(db)));
        steps.add(new MigrationStep(30, (db, context) -> UpdateHelper.update30(db)));
        steps.add(new MigrationStep(31, UpdateHelper::update31));
        steps.add(new MigrationStep(32, (db, context) -> UpdateHelper.update32(db)));
        steps.add(new MigrationStep(34, (db, context) -> UpdateHelper.update33(db)));
        steps.add(new MigrationStep(25, (db, context) -> {
            try {
                UpdateHelper.legacyUpdateRunningBalance(db, context);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        steps.add(new MigrationStep(36, new Migration36()));
        return steps;
    }

    String getSqliteVersion() {
        Cursor cursor = getDatabase().rawQuery("select sqlite_version() AS sqlite_version", null);
        String result = "N\\A";
        try {
            if (cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public void rebuildDB() throws IOException {
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            // От дочерних таблиц к родительским: внешние ключи включены.
            // Строки, на которые есть RESTRICT-ссылки (товары, отправители), удаляются, только если на них больше нет ссылок
            String tableNames[] = new String[]{T_LOG_PRODUCTS, T_LOG_TEMPLATES, T_LOG_TRANSACTIONS,
                    T_LOG_ACCOUNTS_SETS, T_LOG_BUDGET_DEBTS, T_LOG_BUDGET, T_REF_DEBTS, T_LOG_SMS_PARSER_PATTERNS,
                    T_REF_ACCOUNTS_SETS, T_REF_ACCOUNTS, T_REF_SIMPLEDEBTS,
                    T_REF_CATEGORIES, T_REF_PAYEES, T_REF_PROJECTS, T_REF_LOCATIONS, T_REF_DEPARTMENTS
            };
            for (String tableName : tableNames) {
                db.delete(tableName, "Deleted > 0", null);
            }
            db.delete(T_LOG_INCOMING_SMS, "Deleted > 0", null);
            db.delete(T_REF_SENDERS, "Deleted > 0 AND _id NOT IN (SELECT " + C_LOG_INCOMING_SMS_SENDER + " FROM " + T_LOG_INCOMING_SMS + ")", null);
            db.delete(T_REF_PRODUCTS, "Deleted > 0 AND _id NOT IN (SELECT " + C_LOG_PRODUCTS_PRODUCTID + " FROM " + T_LOG_PRODUCTS + ")", null);

            updateFullNames("ref_Categories", true, db);
            updateFullNames("ref_Payees", true, db);
            updateFullNames("ref_Projects", true, db);
            updateFullNames("ref_Locations", true, db);
            updateFullNames("ref_Departments", true, db);

            updateFullNames("ref_Accounts", false, db);
            updateFullNames("ref_SimpleDebts", false, db);
            updateFullNames("log_Templates", false, db);

            updateRunningBalance(db);

            updateLogProducts(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        db.execSQL("VACUUM");
    }

    public void updateRunningBalance(SQLiteDatabase database) throws IOException {
        RunningBalance.rebuild(database);
    }

    /**
     * Добавляет строку товара по умолчанию каждой транзакции без строк товаров (цена = сумма, количество 1).
     */
    public void updateLogProducts(SQLiteDatabase db) throws IOException {
        db.execSQL("INSERT INTO log_Products (Deleted, TransactionId, ProductId, CategoryId, ProjectId, Price, Quantity) "
                + "SELECT 0, t._id, 0, NULL, NULL, t.Amount, 1000 FROM log_Transactions t "
                + "WHERE t.Deleted = 0 AND t._id NOT IN (SELECT TransactionId FROM log_Products)");
    }

    public static void updateFullNames(String tableName, boolean useFullName, SQLiteDatabase db) {
        long t = System.currentTimeMillis();
        String nameColumn;
        if (tableName.equals(T_LOG_TRANSACTIONS)) {
            nameColumn = useFullName ? getFullNameColumn(tableName) : C_LOG_TRANSACTIONS_COMMENT;
        } else {
            nameColumn = useFullName ? getFullNameColumn(tableName) : "Name";
        }
        String fields[];
        if (useFullName) {
            fields = new String[]{C_ID, nameColumn, C_SEARCH_STRING, C_FULL_NAME};
        } else {
            fields = new String[]{C_ID, nameColumn, C_SEARCH_STRING};
        }
        Cursor cursor = db.query(tableName, fields, "Deleted = 0", null, null, null, null);
        ContentValues cv = new ContentValues();
        String translit;
        int i = 0;
        try {
            if (cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    cv.clear();
                    String source = cursor.getString(1) == null ? "" : cursor.getString(1);
                    if (useFullName) {
                        cv.put(C_FULL_NAME, source);
                    }
                    translit = Translit.toTranslit(source.toLowerCase());
                    if (!translit.equals(cursor.getString(2))) {
                        cv.put(C_SEARCH_STRING, translit);
                    }
                    if (cv.size() != 0) {
                        db.update(tableName, cv, "_id = " + cursor.getString(0), null);
                    }
                    cursor.moveToNext();
                    i++;
//                    Log.d(TAG, cursor.getString(0));
                }
            }
        } finally {
            cursor.close();
        }
//        t = System.currentTimeMillis() - t;
//        Log.d(TAG, "Update full names in " + tableName + " - " + String.valueOf(t) + "ms");
    }

    private String getDbPath() {
        return mContext.getDatabasePath(DATABASE_NAME).toString();
    }

    /**
     * Builds a backup zip in the app cache dir (backup/). Does not touch the user's backup folder.
     */
    public File backupDB(boolean vacuum) throws IOException {
        return backupDB(vacuum, false);
    }

    /**
     * @param copyToFolder also copy the file to the backup folder chosen by the user (SAF), if it is available.
     * @return the cache file, which is used for upload to Dropbox
     */
    public File backupDB(boolean vacuum, boolean copyToFolder) throws IOException {
        if (BackupPassword.isBackupBlocked(mContext)) {
            // Protection is on but the password is lost: no backup (neither local nor Dropbox) until it is entered again
            Log.w(TAG, "Backup skipped: backup password can't be read");
            BackupPassword.markWarning(mContext);
            return null;
        }
        if (vacuum) {
            mDatabase.execSQL("VACUUM");
        }
        String alpha = "";
        if (BuildConfig.FLAVOR.equals("nd")) alpha = "_alpha";
        @SuppressLint("SimpleDateFormat") String backupFile = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + alpha + ".zip";
        String backupPath = BackupFolder.getCacheDir(mContext).getAbsolutePath() + "/";

        File backup;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        String password = BackupPassword.get(mContext);
        boolean enableProtection = BackupPassword.isProtectionEnabled(mContext);
        if (enableProtection && password != null && !password.isEmpty()) {
            backup = FileUtils.zipAndEncrypt(getDbPath(), backupPath + backupFile, password);
        } else {
            backup = FileUtils.zip(new String[]{getDbPath()}, backupPath + backupFile);
        }
        Log.d(TAG, String.format("File %s saved to cache", backupFile));
        if (copyToFolder) {
            try {
                BackupFolder.copyToFolder(mContext, backup);
                Log.d(TAG, String.format("File %s copied to backup folder", backupFile));
                preferences.edit().putBoolean(FgConst.PREF_BACKUP_FOLDER_UNAVAILABLE, false).apply();
                deletePre36Copy();
            } catch (IOException e) {
                Log.e(TAG, "Can't copy backup to the backup folder: " + e.getMessage());
                preferences.edit().putBoolean(FgConst.PREF_BACKUP_FOLDER_UNAVAILABLE, true).apply();
            }
        }
        return backup;
    }

    /**
     * Deletes the pre-migration copy fingen.db.pre36 after the first successful backup (to the backup folder or to Dropbox).
     * Does nothing if the file does not exist; a deletion error is only logged.
     */
    public void deletePre36Copy() {
        try {
            File copy = new File(getDbPath() + Migration36.PRE_COPY_SUFFIX);
            if (copy.exists()) {
                if (copy.delete()) {
                    Log.i(TAG, "Deleted " + copy.getName() + " after a successful backup");
                } else {
                    Log.w(TAG, "Can't delete " + copy.getName());
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Can't delete pre36 copy: " + e.getMessage());
        }
    }

    void showRestoreDialog(final String filename, final AppCompatActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.ttl_confirm_action);
        builder.setMessage(R.string.msg_confirm_restore_db);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DBHelper.this.restoreDB(filename, activity);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private class OnOkListener implements DialogInterface.OnClickListener {
        String mZipFile;
        String mLocation;
        EditText mInput;
        IOnUnzipComplete mIOnUnzipComplete;

        public OnOkListener(String zipFile, String location, EditText input, IOnUnzipComplete IOnUnzipComplete) {
            mZipFile = zipFile;
            mLocation = location;
            mInput = input;
            mIOnUnzipComplete = IOnUnzipComplete;
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            FileUtils.unzipAndDecrypt(mZipFile, mLocation, mInput.getText().toString(), mIOnUnzipComplete);
        }
    }

    boolean isRestoredDbValid(File file) {
        if (!file.exists()) {
            return false;
        }
        SQLiteDatabase restored = null;
        Cursor c = null;
        try {
            restored = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = restored.rawQuery("PRAGMA quick_check", null);
            if (!c.moveToFirst() || !"ok".equalsIgnoreCase(c.getString(0))) {
                return false;
            }
            c.close();
            c = restored.rawQuery("PRAGMA user_version", null);
            // 16 is the oldest version with migrations (fixtures 16/21/23/30/34)
            return c.moveToFirst() && c.getInt(0) >= 16 && c.getInt(0) <= DATABASE_VERSION;
        } catch (Exception e) {
            Log.e(TAG, "Restored DB check failed", e);
            return false;
        } finally {
            if (c != null) {
                c.close();
            }
            if (restored != null) {
                restored.close();
            }
        }
    }

    /**
     * Deletes fingen.db.bak left by restoreDB. Call only after the DB is opened successfully.
     */
    public static void deleteRestoreBackup(Context context) {
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            // Set only after a successful restore; a failed rollback leaves the old DB in fingen.db.bak
            if (!preferences.getBoolean(FgConst.PREF_RESTORE_BAK_PENDING, false)) return;
            File db = context.getDatabasePath(DATABASE_NAME);
            File bak = new File(db.getParent() + "/fingen.db.bak");
            if (bak.exists() && !bak.delete()) {
                Log.w(TAG, "Can't delete " + bak.getPath());
                return;
            }
            preferences.edit().putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, false).apply();
        } catch (Exception e) {
            Log.w(TAG, "Can't delete restore backup", e);
        }
    }

    synchronized private void restoreDB(String filename, final AppCompatActivity activity) {
        final File file = new File(filename);
        final File db = new File(getDbPath());
        final IOnUnzipComplete completeListener = new IOnUnzipComplete() {
            @Override
            public void onComplete() {
                File restored = new File(db.getParent() + "/fingen.db.ex");
                if (!isRestoredDbValid(restored)) {
                    restored.delete();
                    onError();
                    return;
                }
                File bak = new File(db.getParent() + "/fingen.db.bak");
                // Close the connection and drop the singleton: no writes through the old connection,
                // and the journal must not stay bound to the replaced file
                close();
                synchronized (DBHelper.class) {
                    mInstance = null;
                }
                new File(db.getPath() + "-journal").delete();
                // WAL is not used, the files are removed for safety (no-op if absent)
                new File(db.getPath() + "-wal").delete();
                new File(db.getPath() + "-shm").delete();
                // Keep the current DB until the restored one is in place
                boolean failed = false;
                if (!db.renameTo(bak)) {
                    restored.delete();
                    failed = true;
                } else if (!restored.renameTo(db)) {
                    if (bak.renameTo(db)) {
                        restored.delete();
                    } else {
                        Log.e(TAG, "Restore failed and rollback failed, old DB is in " + bak.getPath());
                    }
                    failed = true;
                }
                // The connection is closed: the process restarts in any case.
                // commit(), not apply(): the flags must be on disk before the process exits
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
                if (failed) {
                    editor.putBoolean(FgConst.PREF_RESTORE_FAILED, true);
                    // A stale flag from an earlier restore must not delete the .bak that may hold the data
                    editor.putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, false);
                } else {
                    editor.putBoolean(FgConst.PREF_RESTORE_BAK_PENDING, true);
                }
                if (!editor.commit()) {
                    Log.e(TAG, "Can't save restore flags to preferences");
                }
                Intent restartIntent = Intent.makeRestartActivityTask(new ComponentName(mContext, ActivityMain.class));
                activity.startActivity(restartIntent);
                Runtime.getRuntime().exit(0);
            }

            @Override
            public void onError() {
                Toast.makeText(activity, "Error on restore DB", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onWrongPassword() {
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(activity.getString(R.string.ttl_enter_password));
                final EditText input = (EditText) activity.getLayoutInflater().inflate(R.layout.template_edittext, null);
                input.setText("");
                input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
                builder.setView(input);

                builder.setPositiveButton("OK", new OnOkListener(file.toString(), db.getParent() + "/", input, this));

                builder.show();
                input.requestFocus();
            }
        };
        if (file.exists()) {
            String savedPassword = BackupPassword.get(mContext);
            final String password = savedPassword == null ? "" : savedPassword;
            FileUtils.unzipAndDecrypt(file.toString(), db.getParent() + "/", password, completeListener);
        }
    }

    synchronized void clearDB() {
//        SQLiteDatabase db = getWritableDatabase();
        // От дочерних таблиц к родительским: внешние ключи включены
        String[] tables = new String[SchemaV36.TABLES.length];
        for (int i = 0; i < tables.length; i++) {
            tables[i] = SchemaV36.TABLES[tables.length - 1 - i];
        }
        mDatabase.beginTransaction();
        try {
            for (String table : tables) {
                mDatabase.delete(table, null, null);
            }
            mDatabase.setTransactionSuccessful();
        } finally {
            mDatabase.endTransaction();
        }
        CurrencyScale.invalidate();
        mDatabase.rawQuery("VACUUM", null);
    }

    public synchronized static int getMaxDel(SQLiteDatabase db, String tableName) {
        Cursor cursor = db.query(tableName, new String[]{String.format("MAX(%s) AS MAXDEL", DBHelper.C_SYNC_DELETED)}, null, null, null, null, null);
        int maxDel = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        maxDel = cursor.getInt(0);
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return maxDel;
    }

}

