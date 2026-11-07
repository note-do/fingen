package com.yoshione.fingen.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.yoshione.fingen.R;

/**
 * Two-state icon: shows frontImage when unchecked and rearImage when checked.
 * Replaces eu.davidea.flipview.FlipView, which is not available on Maven Central anymore.
 */
public class FlipView extends FrameLayout {

    private static final int ANIMATION_DURATION = 150;

    private final ImageView mFrontImageView;
    private final ImageView mRearImageView;

    public FlipView(Context context) {
        this(context, null);
    }

    public FlipView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mFrontImageView = new ImageView(context);
        mRearImageView = new ImageView(context);
        mRearImageView.setAlpha(0f);

        addView(mFrontImageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(mRearImageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlipView);
            try {
                if (a.hasValue(R.styleable.FlipView_frontImage)) {
                    mFrontImageView.setImageResource(a.getResourceId(R.styleable.FlipView_frontImage, 0));
                }
                if (a.hasValue(R.styleable.FlipView_rearImage)) {
                    mRearImageView.setImageResource(a.getResourceId(R.styleable.FlipView_rearImage, 0));
                }
            } finally {
                a.recycle();
            }
        }
    }

    public ImageView getFrontImageView() {
        return mFrontImageView;
    }

    public ImageView getRearImageView() {
        return mRearImageView;
    }

    public void flip(boolean checked) {
        setCheckedState(checked, true);
    }

    public void flipSilently(boolean checked) {
        setCheckedState(checked, false);
    }

    private void setCheckedState(boolean checked, boolean animate) {
        float frontAlpha = checked ? 0f : 1f;
        float rearAlpha = checked ? 1f : 0f;

        if (animate) {
            mFrontImageView.animate().alpha(frontAlpha).setDuration(ANIMATION_DURATION).start();
            mRearImageView.animate().alpha(rearAlpha).setDuration(ANIMATION_DURATION).start();
        } else {
            mFrontImageView.animate().cancel();
            mRearImageView.animate().cancel();
            mFrontImageView.setAlpha(frontAlpha);
            mRearImageView.setAlpha(rearAlpha);
        }
    }
}
