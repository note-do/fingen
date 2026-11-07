package com.yoshione.fingen.utils;

import android.content.Context;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Ready-made test database (user_version 35, about 100 rows per table): assets/fixtures/fingen_v35_100.db
 * in the androidTest APK. It is built by assets/fixtures/make_seed.py from a clean DB created by
 * DBHelper.onCreate, see the script header for the contents and conventions.
 * <p>
 * copyTo replaces the real fingen.db of the app under test. The previous content is lost: take a
 * backup first if the device holds data that is needed (tests run on a test device, see CLAUDE.md).
 */
public final class FixtureDb {

    public static final String ASSET_PATH = "fixtures/fingen_v35_100.db";

    /** Rows of log_Transactions in the fixture, including 8 with Deleted = 1. */
    public static final int TRANSACTIONS_TOTAL = 100;
    public static final int TRANSACTIONS_DELETED = 8;

    private static final String DB_NAME = "fingen.db";

    private static final Class<?>[] DAO_CLASSES = {
            AccountsDAO.class, AccountsSetsLogDAO.class, AccountsSetsRefDAO.class, BudgetCreditsDAO.class,
            BudgetDAO.class, CabbagesDAO.class, CategoriesDAO.class, CreditsDAO.class, DepartmentsDAO.class,
            LocationsDAO.class, PayeesDAO.class, ProductEntrysDAO.class, ProductsDAO.class, ProjectsDAO.class,
            SendersDAO.class, SimpleDebtsDAO.class, SmsDAO.class, SmsMarkersDAO.class, TemplatesDAO.class,
            TransactionsDAO.class
    };

    private FixtureDb() {
    }

    /**
     * Closes the DBHelper connection, drops the DBHelper and DAO singletons (they cache the open
     * database), removes -journal/-wal/-shm and copies the fixture over fingen.db. The next
     * DBHelper.getInstance(context) opens the fixture (version 35, no migration).
     */
    public static void copyTo(Context ctx) throws IOException {
        DBHelper.getInstance(ctx).close();
        resetSingletons();

        File db = ctx.getDatabasePath(DB_NAME);
        File dir = db.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Can't create " + dir.getPath());
        }
        new File(db.getPath() + "-journal").delete();
        new File(db.getPath() + "-wal").delete();
        new File(db.getPath() + "-shm").delete();

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = testContext.getAssets().open(ASSET_PATH);
             OutputStream out = new FileOutputStream(db, false)) {
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
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
