package com.yoshione.fingen;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.dao.AccountsDAO;
import com.yoshione.fingen.model.Account;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AccountsDAOTest {

    @Test
    public void createAccount_isWrittenToDatabase() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        AccountsDAO accountsDAO = AccountsDAO.getInstance(context);

        Account account = new Account();
        account.setName("DAO test " + System.currentTimeMillis());
        account.setCabbageId(1);

        accountsDAO.createModel(account);

        assertTrue(accountsDAO.getModelById(account.getID()).getID() >= 0);
    }
}
