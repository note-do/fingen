package com.yoshione.fingen;

import android.Manifest;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Grants declared runtime permissions and accepts system permission dialogs
 * (notifications, media access) which cannot be granted via GrantPermissionRule
 * because they are not declared in the manifest.
 *
 * Dialogs are accepted by a background thread that polls the UI during the test.
 * UiWatcher is not used: it fires only during UiObject searches, and registering it
 * while the poller runs its own searches throws IllegalStateException.
 */
public class SystemDialogsRule implements TestRule {

    private static final String[] CONTROLLER_PACKAGES = {
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
    };
    private static final String ALLOW_BUTTON_ID = "permission_allow_button";
    private static final long POLL_INTERVAL_MS = 500;

    private final TestRule mGrantRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION);

    @Override
    public Statement apply(Statement base, Description description) {
        return RuleChain.outerRule(mGrantRule).around(new TestRule() {
            @Override
            public Statement apply(final Statement inner, Description d) {
                return new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                        final boolean[] running = {true};
                        Thread poller = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while (running[0]) {
                                    try {
                                        clickAllow(device);
                                        Thread.sleep(POLL_INTERVAL_MS);
                                    } catch (InterruptedException e) {
                                        return;
                                    } catch (Exception ignored) {
                                        // UiAutomator can fail while the window changes
                                    }
                                }
                            }
                        }, "SystemDialogsPoller");
                        poller.setDaemon(true);
                        poller.start();
                        try {
                            inner.evaluate();
                        } finally {
                            running[0] = false;
                            poller.interrupt();
                        }
                    }
                };
            }
        }).apply(base, description);
    }

    private static boolean clickAllow(UiDevice device) {
        for (String pkg : CONTROLLER_PACKAGES) {
            UiObject2 button = device.findObject(By.res(pkg, ALLOW_BUTTON_ID));
            if (button == null) {
                button = device.findObject(By.pkg(pkg).text("Allow"));
            }
            if (button == null) {
                button = device.findObject(By.pkg(pkg).text("Разрешить"));
            }
            if (button != null) {
                button.click();
                return true;
            }
        }
        return false;
    }
}
