package com.yoshione.fingen.utils;

import android.app.Activity;
import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge helper: targetSdk 36 draws content under system bars on all screens.
 * The AppBar color goes under the status bar, the root gets bottom/side padding
 * (navigation bar, cutout, keyboard).
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    /**
     * Draw under system bars on all API levels, keep light (white) status bar icons:
     * the toolbar is always colorPrimary.
     */
    public static void enable(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(false);
    }

    /**
     * Bars and cutout insets, bottom is max(navigation bar, keyboard).
     */
    public static Insets contentInsets(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
        return Insets.of(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
    }

    /**
     * Top inset to appBar padding (or to root, when there is no appBar), left/right/bottom to root padding.
     * Consumes insets.
     */
    public static void applyToRoot(View root, View appBar) {
        final int rootLeft = root.getPaddingLeft();
        final int rootTop = root.getPaddingTop();
        final int rootRight = root.getPaddingRight();
        final int rootBottom = root.getPaddingBottom();
        final int barTop = appBar != null ? appBar.getPaddingTop() : 0;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = contentInsets(insets);
            if (appBar != null) {
                appBar.setPadding(appBar.getPaddingLeft(), barTop + i.top, appBar.getPaddingRight(), appBar.getPaddingBottom());
                v.setPadding(rootLeft + i.left, rootTop, rootRight + i.right, rootBottom + i.bottom);
            } else {
                v.setPadding(rootLeft + i.left, rootTop + i.top, rootRight + i.right, rootBottom + i.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
