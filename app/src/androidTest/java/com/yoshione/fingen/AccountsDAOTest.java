package com.yoshione.fingen;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.model.Account;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AccountsDAOTest {

    @Rule
    public final TestRule systemDialogs = new SystemDialogsRule();

    private static final long CURRENCY_USD = 2;

    private Context mContext;
    private AccountsDAO mDao;

    private DbSnapshot mSnapshot;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        mDao = AccountsDAO.getInstance(mContext);
    }

    @After
    public void tearDown() {
        mSnapshot.restore(mContext);
    }

    private Account newAccount(String name) {
        Account account = new Account();
        account.setName(name);
        account.setCabbageId(CURRENCY_USD);
        return account;
    }

    private String uniqueName(String prefix) {
        return prefix + " " + System.nanoTime();
    }

    @Test
    public void createAccount_isReadBackById() throws Exception {
        String name = uniqueName("Acc");
        Account account = newAccount(name);

        Account created = (Account) mDao.createModel(account);

        Account stored = mDao.getAccountByID(created.getID());
        assertNotNull(stored);
        assertEquals(name, stored.getName());
        assertEquals(CURRENCY_USD, stored.getCabbageId());
    }

    @Test
    public void createAccount_isListed() throws Exception {
        String name = uniqueName("Listed");
        Account account = newAccount(name);

        mDao.createModel(account);

        List<Account> accounts = mDao.getAllAccounts(true);
        boolean found = false;
        for (Account a : accounts) {
            if (a.getName().equals(name)) {
                found = true;
            }
        }
        assertTrue("account must be in the list", found);
    }

    @Test
    public void createAccount_nameIsFoundByName() throws Exception {
        String name = uniqueName("Named");
        Account account = newAccount(name);

        mDao.createModel(account);

        assertTrue(mDao.getModelByName(name).getID() >= 0);
    }

    @Test
    public void renameAccount_isPersisted() throws Exception {
        Account account = (Account) mDao.createModel(newAccount(uniqueName("Before")));

        String renamed = uniqueName("After");
        account.setName(renamed);
        mDao.createModel(account);

        assertEquals(renamed, mDao.getAccountByID(account.getID()).getName());
    }

    @Test
    public void deleteAccount_isNotListed() throws Exception {
        String name = uniqueName("Deleted");
        Account account = (Account) mDao.createModel(newAccount(name));

        mDao.deleteModel(account, false, mContext);

        for (Account a : mDao.getAllAccounts(true)) {
            assertFalse("deleted account must not be listed", a.getName().equals(name));
        }
    }
}
