package com.yoshione.fingen;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TableLayout;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.yoshione.fingen.utils.ScreenUtils;

/**
 * Created by slv on 22.12.2017.
 *
 */

public class SumsTableOnGlobalLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
    private Context mContext;
    private TableLayout mLayoutSumTable;
    private BottomSheetBehavior<?> mBottomSheetBehavior;
    private View mContentView;
    private SlidingUpPanelLayout mSlidingUpPanelLayout; // TODO removed with slidinguppanel

    SumsTableOnGlobalLayoutListener(Context context, TableLayout layoutSumTable, SlidingUpPanelLayout slidingUpPanelLayout) {
        mContext = context;
        mLayoutSumTable = layoutSumTable;
        mSlidingUpPanelLayout = slidingUpPanelLayout;
    }

    /**
     * @param contentView view with the main content, its bottom padding is kept equal to the sheet peek height
     */
    SumsTableOnGlobalLayoutListener(Context context, TableLayout layoutSumTable, BottomSheetBehavior<?> bottomSheetBehavior, View contentView) {
        mContext = context;
        mLayoutSumTable = layoutSumTable;
        mBottomSheetBehavior = bottomSheetBehavior;
        mContentView = contentView;
    }

    @Override
    public void onGlobalLayout() {
        if (mSlidingUpPanelLayout != null && mLayoutSumTable != null && mLayoutSumTable.getChildCount() >= 2) {
            int height = mLayoutSumTable.getChildAt(0).getHeight()
                    + mLayoutSumTable.getChildAt(1).getHeight()
                    + ScreenUtils.dpToPx(4, mContext);
            if (height != 0) {
                mSlidingUpPanelLayout.setPanelHeight(height);
            }
            return;
        }
        if (mLayoutSumTable != null && mBottomSheetBehavior != null && mLayoutSumTable.getChildCount() >= 2) {
            int height = mLayoutSumTable.getChildAt(0).getHeight()
                    + mLayoutSumTable.getChildAt(1).getHeight()
                    + ScreenUtils.dpToPx(4, mContext);
            if (height != 0 && mBottomSheetBehavior.getPeekHeight() != height) {
                mBottomSheetBehavior.setPeekHeight(height);
                if (mContentView != null) {
                    mContentView.setPadding(mContentView.getPaddingLeft(), mContentView.getPaddingTop(),
                            mContentView.getPaddingRight(), height);
                }
            }
        }
    }
}
