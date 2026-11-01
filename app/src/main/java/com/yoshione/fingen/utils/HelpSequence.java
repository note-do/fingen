package com.yoshione.fingen.utils;

import android.app.Activity;
import android.view.View;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step help over screen elements. Replaces MaterialShowcaseSequence.
 */
public class HelpSequence {

    private final Activity mActivity;
    private final List<TapTarget> mTargets = new ArrayList<>();

    public HelpSequence(Activity activity) {
        mActivity = activity;
    }

    public HelpSequence add(View target, CharSequence text) {
        mTargets.add(TapTarget.forView(target, "", text).cancelable(false));
        return this;
    }

    public HelpSequence add(View target, int textRes) {
        return add(target, mActivity.getString(textRes));
    }

    public void start() {
        new TapTargetSequence(mActivity).targets(mTargets).start();
    }
}
