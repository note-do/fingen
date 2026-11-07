package com.yoshione.fingen;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Helpers for smoke tests: start a screen, wait for RESUMED, check that the content is displayed.
 */
final class ScreenTestUtils {

    static final long TIMEOUT_MS = 30000;

    private ScreenTestUtils() {
    }

    static Activity launch(Class<? extends Activity> cls, Intent extras) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = extras != null ? extras : new Intent();
        intent.setClassName(instrumentation.getTargetContext(), cls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.startActivitySync(intent);
        return waitResumed(cls);
    }

    static Activity waitResumed(Class<? extends Activity> cls) throws Exception {
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            Activity a = findResumed(cls);
            if (a != null) {
                return a;
            }
            Thread.sleep(300);
        }
        assertTrue(cls.getSimpleName() + " did not reach RESUMED", false);
        return null;
    }

    private static Activity findResumed(final Class<? extends Activity> cls) {
        final Activity[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (cls.isInstance(a)) {
                        result[0] = a;
                    }
                }
            }
        });
        return result[0];
    }

    /** Content root of the activity must be attached, visible and have a size. */
    static void assertContentDisplayed(final Activity activity) {
        final boolean[] shown = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View content = activity.findViewById(android.R.id.content);
                shown[0] = content != null && content.isShown()
                        && content.getWidth() > 0 && content.getHeight() > 0;
            }
        });
        assertTrue(activity.getClass().getSimpleName() + " content is not displayed", shown[0]);
    }

    static void assertViewDisplayed(final Activity activity, final int viewId) {
        final boolean[] shown = {false};
        final boolean[] exists = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                View v = activity.findViewById(viewId);
                exists[0] = v != null;
                shown[0] = v != null && v.isShown();
            }
        });
        assertTrue(activity.getClass().getSimpleName() + ": view not found", exists[0]);
        assertTrue(activity.getClass().getSimpleName() + ": view is not displayed", shown[0]);
    }

    static void finish(final Activity activity) {
        if (activity == null) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                activity.finish();
            }
        });
    }

    static void assertNotNullActivity(Activity a) {
        assertNotNull(a);
    }
}
