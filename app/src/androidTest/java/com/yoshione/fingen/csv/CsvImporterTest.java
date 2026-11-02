package com.yoshione.fingen.csv;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.DbSnapshot;
import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.dao.CategoriesDAO;
import com.yoshione.fingen.dao.PayeesDAO;
import com.yoshione.fingen.dao.TransactionsDAO;
import com.yoshione.fingen.interfaces.IProgressEventsListener;
import com.yoshione.fingen.model.Account;
import com.yoshione.fingen.model.Transaction;
import com.yoshione.fingen.utils.ImportParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Import from a CSV file into the database: Fingen format (loadFingenCSV) and user format (loadCustomCSV).
 * Every test writes its own file to the app cache and restores the database snapshot afterwards.
 */
@RunWith(AndroidJUnit4.class)
public class CsvImporterTest {

    private static final String FINGEN_HEADER = "date;time;account;amount;currency;type;exrate;category;payee;location;project;department;note;lon;lat;fn;fd;fp";
    private static final String CUSTOM_HEADER = "Date;Time;Account;Amount;Currency;Category;Payee;Comment";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private Context mContext;
    private DbSnapshot mSnapshot;
    private File mCsvFile;
    private RecordingListener mListener;
    private String mAccount;
    private String mCategory;
    private String mPayee;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        String suffix = String.valueOf(System.nanoTime());
        mAccount = "CsvTest account " + suffix;
        mCategory = "CsvTest category " + suffix;
        mPayee = "CsvTest payee " + suffix;
        mCsvFile = new File(mContext.getCacheDir(), "csv_import_test_" + suffix + ".csv");
        mListener = new RecordingListener();
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
        if (mCsvFile != null) {
            mCsvFile.delete();
        }
    }

    @Test
    public void loadFingenCSV_importsRowsWithFieldsFromFile() throws Exception {
        writeCsv(FINGEN_HEADER,
                "2024-03-01;10:15:00;" + mAccount + ";-150.50;USD;Expense;1.0;" + mCategory + ";" + mPayee + ";;;;note expense;0;0;0;0;0",
                "2024-03-02;12:00:00;" + mAccount + ";1000.00;USD;Income;1.0;;;;;;note income;0;0;0;0;0");

        runFingen();

        assertEquals(CsvImporter.class.getSimpleName() + " must report OK", IProgressEventsListener.CODE_OK, mListener.lastCode());
        List<Transaction> imported = transactionsOf(mAccount);
        assertEquals("row count", 2, imported.size());

        Transaction expense = findByComment(imported, "note expense");
        assertNotNull("expense row must be imported", expense);
        assertEquals("amount", 0, expense.getAmount().compareTo(new BigDecimal("-150.50")));
        assertEquals("date and time", "2024-03-01 10:15:00", formatDateTime(expense));
        assertEquals("category name", mCategory, categoryName(expense));
        assertEquals("payee name", mPayee, payeeName(expense));

        Transaction income = findByComment(imported, "note income");
        assertNotNull("income row must be imported", income);
        assertEquals("amount", 0, income.getAmount().compareTo(new BigDecimal("1000.00")));
        assertEquals("date and time", "2024-03-02 12:00:00", formatDateTime(income));
    }

    @Test
    public void loadCustomCSV_importsRowsWithFieldsFromFile() throws Exception {
        writeCsv(CUSTOM_HEADER,
                "2024-03-05;08:30:00;" + mAccount + ";-42,75;USD;" + mCategory + ";" + mPayee + ";custom expense",
                "2024-03-06;09:00:00;" + mAccount + ";500;USD;;;custom income");

        runCustom();

        assertEquals(CsvImporter.class.getSimpleName() + " must report OK", IProgressEventsListener.CODE_OK, mListener.lastCode());
        List<Transaction> imported = transactionsOf(mAccount);
        assertEquals("row count", 2, imported.size());

        Transaction expense = findByComment(imported, "custom expense");
        assertNotNull("expense row must be imported", expense);
        // Comma decimal separator is converted to a dot by parseAmount.
        assertEquals("amount", 0, expense.getAmount().compareTo(new BigDecimal("-42.75")));
        assertEquals("date and time", "2024-03-05 08:30:00", formatDateTime(expense));
        assertEquals("category name", mCategory, categoryName(expense));
        assertEquals("payee name", mPayee, payeeName(expense));

        Transaction income = findByComment(imported, "custom income");
        assertNotNull("income row must be imported", income);
        assertEquals("amount", 0, income.getAmount().compareTo(new BigDecimal("500")));
        assertEquals("date and time", "2024-03-06 09:00:00", formatDateTime(income));
    }

    @Test
    public void emptyFile_addsNoTransactions() throws Exception {
        writeCsv();
        int before = allTransactionsCount();

        runFingen();
        assertEquals("loadFingenCSV on empty file", before, allTransactionsCount());

        runCustom();
        assertEquals("loadCustomCSV on empty file", before, allTransactionsCount());
    }

    @Test
    public void malformedFile_fingen_addsNoTransactions() throws Exception {
        writeCsv("this is not a csv file", "no separators in this line", "still plain text");
        int before = allTransactionsCount();

        runFingen();

        assertEquals("row count after malformed Fingen import", before, allTransactionsCount());
    }

    @Test
    public void malformedFile_custom_addsNoTransactions() throws Exception {
        writeCsv("this is not a csv file", "no separators in this line", "still plain text");
        int before = allTransactionsCount();

        runCustom();

        assertEquals("row count after malformed custom import", before, allTransactionsCount());
    }

    @Test
    public void malformedFile_leavesNoOpenDbTransaction() throws Exception {
        writeCsv("this is not a csv file", "no separators in this line", "still plain text");

        runFingen();
        assertFalse("loadFingenCSV must end its transaction after a failed import",
                DBHelper.getInstance(mContext).getWritableDatabase().inTransaction());

        runCustom();
        assertFalse("loadCustomCSV must end its transaction after a failed import",
                DBHelper.getInstance(mContext).getWritableDatabase().inTransaction());
    }

    private void runFingen() throws IOException {
        CsvImporter importer = new CsvImporter(mContext, mCsvFile.getAbsolutePath(), 0, false);
        importer.setmCsvImportProgressChangeListener(mListener);
        importer.loadFingenCSV();
    }

    private void runCustom() throws IOException {
        ImportParams params = new ImportParams(0, 1, 2, 3, 4, 5, 6, -1, -1, -1, 7, -1, -1, -1, -1, true);
        params.setDateFormat("yyyy-MM-dd", "HH:mm:ss");
        CsvImporter importer = new CsvImporter(mContext, mCsvFile.getAbsolutePath(), 0, false);
        importer.setmCsvImportProgressChangeListener(mListener);
        importer.loadCustomCSV(params);
    }

    private void writeCsv(String... lines) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(line).append('\n');
        }
        FileOutputStream out = new FileOutputStream(mCsvFile);
        try {
            out.write(content.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private int allTransactionsCount() throws Exception {
        return TransactionsDAO.getInstance(mContext).getAllTransactions().size();
    }

    private List<Transaction> transactionsOf(String accountName) throws Exception {
        List<Transaction> result = new ArrayList<>();
        Account account = (Account) AccountsDAO.getInstance(mContext).getModelByName(accountName);
        if (account.getID() < 0) {
            return result;
        }
        for (Transaction transaction : TransactionsDAO.getInstance(mContext).getAllTransactions()) {
            if (transaction.getAccountID() == account.getID()) {
                result.add(transaction);
            }
        }
        return result;
    }

    private static Transaction findByComment(List<Transaction> transactions, String comment) {
        for (Transaction transaction : transactions) {
            if (comment.equals(transaction.getComment())) {
                return transaction;
            }
        }
        return null;
    }

    private static String formatDateTime(Transaction transaction) {
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(transaction.getDateTime());
    }

    private String categoryName(Transaction transaction) {
        return CategoriesDAO.getInstance(mContext).getCategoryByID(transaction.getCategoryID()).getName();
    }

    private String payeeName(Transaction transaction) {
        return PayeesDAO.getInstance(mContext).getPayeeByID(transaction.getPayeeID()).getName();
    }

    private static final class RecordingListener implements IProgressEventsListener {
        private final List<Integer> mCodes = new ArrayList<>();

        @Override
        public void onProgressChange(int progress) {
        }

        @Override
        public void onOperationComplete(int code) {
            mCodes.add(code);
        }

        @Override
        public void onOperationComplete(int code, int[] stats) {
            mCodes.add(code);
        }

        int lastCode() {
            assertTrue("importer must report completion", !mCodes.isEmpty());
            return mCodes.get(mCodes.size() - 1);
        }
    }
}
