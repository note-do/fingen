package com.yoshione.fingen.utils;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.core.content.ContextCompat;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import com.google.android.material.navigation.NavigationView;
import com.yoshione.fingen.ActivityMain;
import com.yoshione.fingen.ActivitySmsList;
import com.yoshione.fingen.DbSnapshot;
import com.yoshione.fingen.R;
import com.yoshione.fingen.ScreenTestUtils;
import com.yoshione.fingen.dao.SendersDAO;
import com.yoshione.fingen.dao.SmsDAO;
import com.yoshione.fingen.model.Sender;
import com.yoshione.fingen.model.Sms;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Devices without telephony: the "Incoming" entry is shown only when there are SMS records,
 * no SMS permission dialog is shown, and the "import SMS" action is hidden.
 * Telephony is forced with {@link SmsSupport#setOverrideForTesting(Boolean)}: false = no telephony,
 * true = telephony. Rows are removed in tearDown by DbSnapshot.
 */
@RunWith(AndroidJUnit4.class)
public class SmsSupportTest {

    private static final long TIMEOUT_MS = 10000;
    private static final long DIALOG_WAIT_MS = 5000;
    private static final long COUNTERS_WAIT_MS = 1500;
    private static final String[] PERMISSION_CONTROLLER_PACKAGES = {
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
    };

    @Rule
    public final GrantPermissionRule mLocationRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION);

    private Context mContext;
    private DbSnapshot mSnapshot;
    private Activity mActivity;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mSnapshot = DbSnapshot.take(mContext);
        mActivity = null;
    }

    @After
    public void tearDown() {
        SmsSupport.setOverrideForTesting(null);
        ScreenTestUtils.finish(mActivity);
        mSnapshot.restore(mContext);
    }

    @Test
    public void noTelephony_noSmsRecords_noSmsDialog_incomingHidden() throws Exception {
        // DbSnapshot restores only rows added by the test, so existing rows are not deleted: skip instead
        assumeTrue("log_Incoming_SMS is not empty on this device", countSmsRecords() == 0);
        assumeSmsPermissionNotGranted();

        // A sender makes ActivityMain want RECEIVE_SMS (as on telephony devices): only SmsSupport stops the request
        insertSender();
        SmsSupport.setOverrideForTesting(false);
        mActivity = ScreenTestUtils.launch(ActivityMain.class, null);

        assertNoSmsPermissionDialog(DIALOG_WAIT_MS);
        // Counters are loaded asynchronously after resume: give them time before checking absence.
        Thread.sleep(COUNTERS_WAIT_MS);

        assertIncomingAbsent(mActivity);
    }

    /** Control for the test above: with telephony and the same sender the SMS permission dialog appears. */
    @Test
    public void telephony_withSender_smsDialogShown() throws Exception {
        assumeSmsPermissionNotGranted();

        insertSender();
        SmsSupport.setOverrideForTesting(true);
        // The system dialog covers ActivityMain (PAUSED), so RESUMED is not awaited here
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent();
        intent.setClassName(instrumentation.getTargetContext(), ActivityMain.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity = instrumentation.startActivitySync(intent);

        waitForSmsPermissionDialog(TIMEOUT_MS);
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack();
    }

    @Test
    public void noTelephony_withSmsRecord_incomingShown() throws Exception {
        SmsSupport.setOverrideForTesting(false);
        insertSmsRecord();

        mActivity = ScreenTestUtils.launch(ActivityMain.class, null);

        waitForIncomingPresent(mActivity);
    }

    @Test
    public void noTelephony_importSmsHidden() throws Exception {
        SmsSupport.setOverrideForTesting(false);

        mActivity = ScreenTestUtils.launch(ActivitySmsList.class, null);

        waitForActionItem(mActivity, R.id.action_paste, true);
        assertFalse("action_import must be hidden without telephony",
                isActionItemShown(mActivity, R.id.action_import));
    }

    @Test
    public void telephony_importSmsShown() throws Exception {
        SmsSupport.setOverrideForTesting(true);

        mActivity = ScreenTestUtils.launch(ActivitySmsList.class, null);

        waitForActionItem(mActivity, R.id.action_import, true);
    }

    private long countSmsRecords() throws Exception {
        List<Sms> smss = SmsDAO.getInstance(mContext).getAllSmss();
        return smss.size();
    }

    private void assumeSmsPermissionNotGranted() {
        assumeTrue("RECEIVE_SMS is already granted on this device",
                ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECEIVE_SMS)
                        != PackageManager.PERMISSION_GRANTED);
    }

    private void insertSender() throws Exception {
        Sender sender = new Sender();
        sender.setName("SmsSupportTest " + System.nanoTime());
        sender.setPhoneNo("SmsSupportTest");
        SendersDAO.getInstance(mContext).createModel(sender);
    }

    private void insertSmsRecord() throws Exception {
        Sms sms = new Sms();
        sms.setmDateTime(new Date());
        sms.setSenderId(0);
        sms.setmBody("SmsSupportTest " + System.nanoTime());
        SmsDAO.getInstance(mContext).createModel(sms);
    }

    /** The SMS permission dialog of the system permission controller must not appear within the wait. */
    private void assertNoSmsPermissionDialog(long waitMs) throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            for (String pkg : PERMISSION_CONTROLLER_PACKAGES) {
                UiObject2 dialog = device.findObject(By.pkg(pkg).textContains("SMS"));
                if (dialog != null) {
                    fail("SMS permission dialog is shown without telephony: package " + pkg
                            + ", text \"" + dialog.getText() + "\"");
                }
            }
            Thread.sleep(500);
        }
    }

    private void waitForSmsPermissionDialog(long waitMs) throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        long end = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < end) {
            for (String pkg : PERMISSION_CONTROLLER_PACKAGES) {
                if (device.findObject(By.pkg(pkg).textContains("SMS")) != null) {
                    return;
                }
            }
            Thread.sleep(500);
        }
        fail("SMS permission dialog is not shown with telephony and a sender");
    }

    private void waitForIncomingPresent(final Activity activity) throws Exception {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            if (isIncomingPresent(activity)) {
                return;
            }
            Thread.sleep(300);
        }
        fail("\"Incoming\" entry is not shown although log_Incoming_SMS has records");
    }

    private void assertIncomingAbsent(final Activity activity) {
        // Control: the navigation menu is rendered, so the absence of the entry is meaningful.
        assertTrue("navigation menu is not rendered", hasDrawerText(activity, R.string.ent_settings));
        assertFalse("\"Incoming\" drawer entry must be hidden without telephony and without SMS records",
                hasDrawerText(activity, R.string.ent_incoming));
        assertFalse("action_incoming must be hidden without telephony and without SMS records",
                isActionItemShown(activity, R.id.action_incoming));
    }

    private boolean isIncomingPresent(Activity activity) {
        return hasDrawerText(activity, R.string.ent_incoming)
                && isActionItemShown(activity, R.id.action_incoming);
    }

    private void waitForActionItem(Activity activity, int itemId, boolean expected) throws Exception {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            if (isActionItemShown(activity, itemId) == expected) {
                return;
            }
            Thread.sleep(300);
        }
        fail("action item " + activity.getResources().getResourceEntryName(itemId)
                + (expected ? " is not shown" : " is shown"));
    }

    /** Looks for a drawer entry by its title on the UI thread. */
    private boolean hasDrawerText(final Activity activity, final int titleResId) {
        final String title = activity.getString(titleResId);
        final boolean[] found = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                NavigationView navigationView = activity.findViewById(R.id.navigationView);
                found[0] = navigationView != null && findTextView(navigationView, title) != null;
            }
        });
        return found[0];
    }

    /** Checks whether the action bar shows the item with the given menu id, on the UI thread. */
    private boolean isActionItemShown(final Activity activity, final int itemId) {
        final boolean[] found = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                found[0] = findActionItem(activity.getWindow().getDecorView(), itemId) != null;
            }
        });
        return found[0];
    }

    private static TextView findTextView(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) {
            return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextView(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Finds an action bar item that is currently laid out, by its menu item id. */
    private static ActionMenuItemView findActionItem(View root, int itemId) {
        if (root instanceof ActionMenuItemView) {
            ActionMenuItemView item = (ActionMenuItemView) root;
            if (item.getItemData() != null && item.getItemData().getItemId() == itemId) {
                return item;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                ActionMenuItemView found = findActionItem(group.getChildAt(i), itemId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
