package com.yoshione.fingen;

import android.app.Activity;
import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.Collection;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MainScreenPermissionsTest {

    private static final long TIMEOUT_MS = 30000;

    private final ActivityTestRule<ActivityMain> mActivityRule =
            new ActivityTestRule<>(ActivityMain.class, false, true);

    @Rule
    public final TestRule rules = RuleChain.outerRule(new SystemDialogsRule()).around(mActivityRule);

    @Test
    public void mainScreenResumesWithoutBlockingDialogs() throws Exception {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            if (isResumed()) {
                return;
            }
            Thread.sleep(300);
        }
        assertTrue("ActivityMain did not reach RESUMED", false);
    }

    private boolean isResumed() {
        final boolean[] result = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Collection<Activity> activities = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED);
                for (Activity a : activities) {
                    if (a instanceof ActivityMain) {
                        result[0] = true;
                    }
                }
            }
        });
        return result[0];
    }
}
