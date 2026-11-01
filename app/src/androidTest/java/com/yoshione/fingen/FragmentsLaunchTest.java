package com.yoshione.fingen;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.yoshione.fingen.model.Cabbage;
import com.yoshione.fingen.model.ProductEntry;
import com.yoshione.fingen.model.Sender;
import com.yoshione.fingen.model.SimpleDebt;
import com.yoshione.fingen.model.SmsMarker;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests: dialog fragments touched by the ViewBinding migration are shown from ActivityMain
 * without crashing. Fragments inside Activity hosts are covered by ScreenLaunchTest.
 */
@RunWith(AndroidJUnit4.class)
public class FragmentsLaunchTest {

    @Rule
    public final SystemDialogsRule dialogs = new SystemDialogsRule();

    private AppCompatActivity mHost;
    private DialogFragment mDialog;
    private android.app.DialogFragment mPlatformDialog;

    @Before
    public void setUp() throws Exception {
        mHost = (AppCompatActivity) ScreenTestUtils.launch(ActivityMain.class, null);
    }

    @After
    public void tearDown() {
        final DialogFragment dialog = mDialog;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (dialog != null) {
                    dialog.dismissAllowingStateLoss();
                }
            }
        });
        final android.app.DialogFragment platformDialog = mPlatformDialog;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (platformDialog != null) {
                    platformDialog.dismissAllowingStateLoss();
                }
            }
        });
        ScreenTestUtils.finish(mHost);
    }

    /** Some dialogs still extend the platform android.app.DialogFragment. */
    private void show(final android.app.DialogFragment dialog) {
        mPlatformDialog = dialog;
        final boolean[] showing = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                dialog.show(mHost.getFragmentManager(), "smoke_test_dialog");
                mHost.getFragmentManager().executePendingTransactions();
                assertNotNull(dialog.getDialog());
                showing[0] = dialog.getDialog().isShowing();
            }
        });
        assertTrue(dialog.getClass().getSimpleName() + " dialog is not showing", showing[0]);
    }

    private void show(final DialogFragment dialog) {
        mDialog = dialog;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                dialog.show(mHost.getSupportFragmentManager(), "smoke_test_dialog");
                mHost.getSupportFragmentManager().executePendingTransactions();
            }
        });
        final boolean[] showing = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                assertNotNull(dialog.getDialog());
                showing[0] = dialog.getDialog().isShowing();
            }
        });
        assertTrue(dialog.getClass().getSimpleName() + " dialog is not showing", showing[0]);
    }

    @Test
    public void cabbageEdit() {
        show(FragmentCabbageEdit.newInstance("t", new Cabbage()));
    }

    @Test
    public void senderEdit() {
        show(FragmentSenderEdit.newInstance("t", new Sender()));
    }

    @Test
    public void simpleDebtEdit() {
        show(FragmentSimpleDebtEdit.newInstance("t", new SimpleDebt()));
    }

    @Test
    public void smsMarkerEdit() {
        show(FragmentSmsMarkerEdit.newInstance("t", new SmsMarker()));
    }

    @Test
    public void sortAccounts() {
        show(FragmentSortAccounts.newInstance("t"));
    }

    @Test
    public void tabOrder() {
        show(new FragmentTabOrderDialog());
    }

    @Test
    public void dateFilterEdit() {
        show(FragmentDateFilterEdit.newInstance(0, 0));
    }

    @Test
    public void productEntryEdit() {
        show(FragmentProductEntryEdit.newInstance("t", new ProductEntry()));
    }

    @Test
    public void copyBudget() {
        show(FragmentCopyBudget.newInstance("t", 2024, 1, true));
    }

    @Test
    public void amountEdit() {
        show(FragmentAmountEdit.newInstance("t", BigDecimal.ZERO, new Cabbage(), -1));
    }
}
