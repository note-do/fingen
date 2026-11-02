package com.yoshione.fingen.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.utils.SecureStore;

/**
 * Backup password storage. The password is kept encrypted by SecureStore in the secure prefs file,
 * which is not included in Auto Backup. The "protection enabled" switch stays in the default prefs.
 * If protection is enabled but the password can't be read (new device, lost Keystore key),
 * backups are not created until the user enters the password again.
 */
public class BackupPassword {

    private static final String TAG = "BackupPassword";

    private BackupPassword() {
    }

    private static SharedPreferences securePrefs(Context context) {
        return context.getSharedPreferences(SecureStore.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences defaultPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean isProtectionEnabled(Context context) {
        return defaultPrefs(context).getBoolean(FgConst.PREF_ENABLE_BACKUP_PASSWORD, false);
    }

    /**
     * @return the password, "" if it is not set, or null if it can't be read
     * (the value can't be decrypted, or protection is enabled but there is no password on this device)
     */
    public static String get(Context context) {
        String encrypted = securePrefs(context).getString(FgConst.SECURE_BACKUP_PASSWORD, null);
        if (encrypted != null) {
            return SecureStore.decrypt(encrypted);
        }
        // not migrated yet (Keystore was unavailable)
        String legacy = defaultPrefs(context).getString(FgConst.PREF_BACKUP_PASSWORD, "");
        if (!legacy.isEmpty()) {
            return legacy;
        }
        return isProtectionEnabled(context) ? null : "";
    }

    /**
     * @return true if the backup must not be created: protection is on and the password can't be read
     */
    public static boolean isBackupBlocked(Context context) {
        return isProtectionEnabled(context) && get(context) == null;
    }

    /**
     * Remembers that a backup was skipped because of the lost password
     */
    public static void markWarning(Context context) {
        securePrefs(context).edit().putBoolean(FgConst.SECURE_BACKUP_PASSWORD_WARNING, true).commit();
    }

    /**
     * @return true if the user must enter the backup password again
     */
    public static boolean hasWarning(Context context) {
        return isProtectionEnabled(context)
                && (securePrefs(context).getBoolean(FgConst.SECURE_BACKUP_PASSWORD_WARNING, false)
                || isBackupBlocked(context));
    }

    /**
     * Saves the password (empty removes it) and clears the warning.
     *
     * @return false if the password could not be saved (Keystore error), nothing is changed then
     */
    public static boolean set(Context context, String password) {
        SharedPreferences secure = securePrefs(context);
        if (password == null || password.isEmpty()) {
            secure.edit()
                    .remove(FgConst.SECURE_BACKUP_PASSWORD)
                    .remove(FgConst.SECURE_BACKUP_PASSWORD_WARNING)
                    .commit();
        } else {
            String encrypted = SecureStore.encrypt(password);
            if (encrypted == null) {
                return false;
            }
            secure.edit()
                    .putString(FgConst.SECURE_BACKUP_PASSWORD, encrypted)
                    .remove(FgConst.SECURE_BACKUP_PASSWORD_WARNING)
                    .commit();
        }
        defaultPrefs(context).edit().remove(FgConst.PREF_BACKUP_PASSWORD).apply();
        return true;
    }

    /**
     * One-time move of the plain text password to SecureStore. Safe to call many times.
     * If Keystore is unavailable the plain value stays and the move is retried on the next start.
     */
    public static void migrate(Context context) {
        SharedPreferences secure = securePrefs(context);
        if (secure.getBoolean(FgConst.SECURE_BACKUP_PASSWORD_MIGRATED, false)) {
            return;
        }
        SharedPreferences prefs = defaultPrefs(context);
        String plain = prefs.getString(FgConst.PREF_BACKUP_PASSWORD, "");
        if (!plain.isEmpty()) {
            String encrypted = SecureStore.encrypt(plain);
            if (encrypted == null) {
                Log.w(TAG, "Keystore is unavailable, migration postponed");
                return;
            }
            secure.edit().putString(FgConst.SECURE_BACKUP_PASSWORD, encrypted).commit();
            if (!plain.equals(SecureStore.decrypt(encrypted))) {
                secure.edit().remove(FgConst.SECURE_BACKUP_PASSWORD).commit();
                Log.w(TAG, "Check of the encrypted password failed, migration postponed");
                return;
            }
        }
        prefs.edit().remove(FgConst.PREF_BACKUP_PASSWORD).commit();
        secure.edit().putBoolean(FgConst.SECURE_BACKUP_PASSWORD_MIGRATED, true).commit();
    }
}
