package com.yoshione.fingen.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Определяет, есть ли на устройстве телефония (приём и чтение SMS).
 */
public class SmsSupport {

    private static Boolean sAvailable;
    private static Boolean sOverride;

    private SmsSupport() {
    }

    public static synchronized boolean isAvailable(Context context) {
        if (sOverride != null) {
            return sOverride;
        }
        if (sAvailable == null) {
            // признак не меняется во время работы, кэшируем
            sAvailable = context.getApplicationContext().getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        }
        return sAvailable;
    }

    /**
     * Для тестов: принудительно задаёт результат {@link #isAvailable(Context)}, null сбрасывает.
     */
    @VisibleForTesting
    public static synchronized void setOverrideForTesting(@Nullable Boolean value) {
        sOverride = value;
    }
}
