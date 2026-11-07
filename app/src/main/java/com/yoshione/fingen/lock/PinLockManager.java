package com.yoshione.fingen.lock;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.yoshione.fingen.FgConst;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * PIN protection. Stores a salted SHA-256 hash of the PIN and shows PinActivity
 * when the app comes back to foreground after the configured timeout.
 * Replaces the lollipin library.
 */
public final class PinLockManager {

    public static final String PREF_PIN_ENABLED = "enable_pin_lock";
    public static final String PREF_PIN_LENGTH = "pin_length";
    private static final String PREF_PIN_HASH = "pin_lock_hash";
    private static final String PREF_PIN_SALT = "pin_lock_salt";
    private static final String DEFAULT_PIN_LENGTH = "5";
    private static final String DEFAULT_TIMEOUT_SECONDS = "10";

    private static int sStartedActivities = 0;
    private static long sBackgroundedAt = 0;
    private static boolean sLocked = false;
    private static boolean sPinShowing = false;

    private PinLockManager() {
    }

    public static void init(Application application) {
        // PINs of the previous lock library are not migrated: drop the flag if no PIN is set here.
        // The flag without a PIN hash can only come from the old library, so the user is told once
        // (ActivityMain shows the notice and clears PREF_PIN_RESET_NOTICE_PENDING).
        if (!isPinSet(application) && getPrefs(application).getBoolean(PREF_PIN_ENABLED, false)) {
            getPrefs(application).edit()
                    .putBoolean(PREF_PIN_ENABLED, false)
                    .putBoolean(FgConst.PREF_PIN_RESET_NOTICE_PENDING, true)
                    .apply();
        }
        application.registerActivityLifecycleCallbacks(new LockCallbacks());
    }

    public static boolean isPinSet(Context context) {
        return getPrefs(context).getString(PREF_PIN_HASH, null) != null;
    }

    public static boolean isEnabled(Context context) {
        return isPinSet(context) && getPrefs(context).getBoolean(PREF_PIN_ENABLED, false);
    }

    public static int getPinLength(Context context) {
        return Integer.valueOf(getPrefs(context).getString(PREF_PIN_LENGTH, DEFAULT_PIN_LENGTH));
    }

    public static void setPin(Context context, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        getPrefs(context).edit()
                .putString(PREF_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(PREF_PIN_HASH, hash(pin, salt))
                .putBoolean(PREF_PIN_ENABLED, true)
                .apply();
    }

    public static void clearPin(Context context) {
        getPrefs(context).edit()
                .remove(PREF_PIN_HASH)
                .remove(PREF_PIN_SALT)
                .putBoolean(PREF_PIN_ENABLED, false)
                .apply();
    }

    public static boolean checkPin(Context context, String pin) {
        SharedPreferences prefs = getPrefs(context);
        String storedHash = prefs.getString(PREF_PIN_HASH, null);
        String salt = prefs.getString(PREF_PIN_SALT, null);
        return storedHash != null && salt != null
                && storedHash.equals(hash(pin, Base64.decode(salt, Base64.NO_WRAP)));
    }

    static void onUnlocked() {
        sLocked = false;
        sPinShowing = false;
    }

    static void onPinDismissed() {
        sPinShowing = false;
    }

    private static String hash(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            return Base64.encodeToString(digest.digest(pin.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long getTimeoutMillis(Context context) {
        return Integer.valueOf(getPrefs(context).getString(FgConst.PREF_PIN_LOCK_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)) * 1000L;
    }

    private static SharedPreferences getPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static class LockCallbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityStarted(Activity activity) {
            if (!(activity instanceof PinActivity) && isEnabled(activity) && !sPinShowing) {
                boolean timedOut = sStartedActivities == 0
                        && System.currentTimeMillis() - sBackgroundedAt >= getTimeoutMillis(activity);
                if (sLocked || timedOut) {
                    sLocked = true;
                    sPinShowing = true;
                    activity.startActivity(PinActivity.unlockIntent(activity));
                }
            }
            sStartedActivities++;
        }

        @Override
        public void onActivityStopped(Activity activity) {
            sStartedActivities--;
            if (sStartedActivities == 0) {
                sBackgroundedAt = System.currentTimeMillis();
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }
}
