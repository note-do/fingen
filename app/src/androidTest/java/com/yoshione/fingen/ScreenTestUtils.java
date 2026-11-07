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
public final class ScreenTestUtils {

    static final long TIMEOUT_MS = 30000;

    private ScreenTestUtils() {
    }

    public static Activity launch(Class<? extends Activity> cls, Intent extras) throws Exception {
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

    /**
     * Starts the activity and waits until it is finishing (or destroyed) without reaching RESUMED.
     * Used for screens that close themselves on devices without the required feature.
     */
    static void launchExpectingFinish(Class<? extends Activity> cls, Intent extras) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = extras != null ? extras : new Intent();
        intent.setClassName(instrumentation.getTargetContext(), cls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity started = instrumentation.startActivitySync(intent);
        long end = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < end) {
            assertTrue(cls.getSimpleName() + " reached RESUMED on a device where it must close",
                    findResumed(cls) == null);
            if (isFinished(started)) {
                return;
            }
            // Fallback: the lifecycle monitor (weak references)
            Activity a = findAnyStage(cls);
            if (a != null && (a.isFinishing() || a.isDestroyed())) {
                return;
            }
            Thread.sleep(300);
        }
        assertTrue(cls.getSimpleName() + " did not finish", false);
    }

    private static boolean isFinished(final Activity activity) {
        if (activity == null) {
            return false;
        }
        final boolean[] result = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                result[0] = activity.isFinishing() || activity.isDestroyed();
            }
        });
        return result[0];
    }

    private static Activity findAnyStage(final Class<? extends Activity> cls) {
        final Activity[] result = {null};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                for (Stage stage : Stage.values()) {
                    for (Activity a : ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(stage)) {
                        if (cls.isInstance(a)) {
                            result[0] = a;
                        }
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

    public static void finish(final Activity activity) {
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
