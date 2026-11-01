package com.yoshione.fingen;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TableLayout;

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
