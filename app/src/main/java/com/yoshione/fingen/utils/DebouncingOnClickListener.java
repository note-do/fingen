package com.yoshione.fingen.utils;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Ignores clicks that arrive before the previous click has been handled.
 * Same behaviour as butterknife.internal.DebouncingOnClickListener, which the app used for @OnClick.
 */
public class DebouncingOnClickListener implements View.OnClickListener {

    private static boolean sEnabled = true;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static final Runnable sEnableAgain = () -> sEnabled = true;

    private final View.OnClickListener mDelegate;

    public DebouncingOnClickListener(View.OnClickListener delegate) {
        mDelegate = delegate;
    }

    @Override
    public void onClick(View v) {
        if (sEnabled) {
            sEnabled = false;
            sMainHandler.post(sEnableAgain);
            mDelegate.onClick(v);
        }
    }
}
