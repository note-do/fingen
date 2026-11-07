package com.yoshione.fingen.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView with a thin draggable scrollbar on the right edge.
 * The bar appears while scrolling and fades out after a short delay.
 * If the adapter implements {@link SectionIndexer}, a bubble with the section text is shown while dragging.
 */
public class FgFastScrollRecyclerView extends RecyclerView {

    public interface SectionIndexer {
        String getSectionText(int position);
    }

    private static final int HIDE_DELAY_MS = 1500;

    private final Paint mHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mHandleWidth;
    private final float mHandleHeight;
    private final float mHandleMargin;
    private final float mTouchWidth;
    private final float mBubblePadding;
    private final float mBubbleHeight;

    private boolean mVisible;
    private boolean mDragging;
    private float mDragFraction;
    private String mBubbleText;

    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDragging) {
                mVisible = false;
                invalidate();
            }
        }
    };

    public FgFastScrollRecyclerView(Context context) {
        this(context, null);
    }

    public FgFastScrollRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FgFastScrollRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        float density = context.getResources().getDisplayMetrics().density;
        mHandleWidth = 4 * density;
        mHandleHeight = 40 * density;
        mHandleMargin = 8 * density;
        mTouchWidth = 28 * density;
        mBubblePadding = 12 * density;
        mBubbleHeight = 40 * density;
        mHandlePaint.setColor(0xFF555555);
        mBubblePaint.setColor(0xFF777777);
        mTextPaint.setColor(0xFFFFFFFF);
        mTextPaint.setTextSize(18 * context.getResources().getDisplayMetrics().scaledDensity);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (mDragging || dy == 0 || !canScroll()) {
                    return;
                }
                show();
            }
        });
    }

    private boolean canScroll() {
        return computeVerticalScrollRange() > computeVerticalScrollExtent();
    }

    private void show() {
        mVisible = true;
        removeCallbacks(mHideRunnable);
        postDelayed(mHideRunnable, HIDE_DELAY_MS);
        invalidate();
    }

    private float scrollFraction() {
        int range = computeVerticalScrollRange() - computeVerticalScrollExtent();
        if (range <= 0) {
            return 0;
        }
        return Math.min(1f, Math.max(0f, computeVerticalScrollOffset() / (float) range));
    }

    private float handleTop() {
        float fraction = mDragging ? mDragFraction : scrollFraction();
        return fraction * (getHeight() - mHandleHeight);
    }

    private boolean isOnHandle(float x, float y) {
        float top = handleTop();
        return x >= getWidth() - mTouchWidth && y >= top && y <= top + mHandleHeight;
    }

    private void dragTo(float y) {
        mDragFraction = Math.min(1f, Math.max(0f, (y - mHandleHeight / 2) / (getHeight() - mHandleHeight)));
        Adapter adapter = getAdapter();
        LayoutManager lm = getLayoutManager();
        if (adapter == null || adapter.getItemCount() == 0 || !(lm instanceof LinearLayoutManager)) {
            return;
        }
        int position = Math.round(mDragFraction * (adapter.getItemCount() - 1));
        ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, 0);
        mBubbleText = adapter instanceof SectionIndexer ? ((SectionIndexer) adapter).getSectionText(position) : null;
        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (mVisible && e.getActionMasked() == MotionEvent.ACTION_DOWN && isOnHandle(e.getX(), e.getY())) {
            startDrag(e);
            return true;
        }
        return super.onInterceptTouchEvent(e);
    }

    private void startDrag(MotionEvent e) {
        mDragging = true;
        removeCallbacks(mHideRunnable);
        getParent().requestDisallowInterceptTouchEvent(true);
        stopScroll();
        dragTo(e.getY());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mDragging) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    dragTo(e.getY());
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    mBubbleText = null;
                    show();
                    break;
                default:
                    break;
            }
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);
        if (!mVisible || !canScroll()) {
            return;
        }
        float top = handleTop();
        float right = getWidth() - mHandleMargin / 2;
        mRect.set(right - mHandleWidth, top, right, top + mHandleHeight);
        c.drawRoundRect(mRect, mHandleWidth / 2, mHandleWidth / 2, mHandlePaint);
        if (mDragging && mBubbleText != null) {
            float bubbleWidth = mTextPaint.measureText(mBubbleText) + 2 * mBubblePadding;
            float bubbleRight = right - mHandleWidth - mHandleMargin;
            float bubbleTop = Math.max(0, Math.min(getHeight() - mBubbleHeight, top + mHandleHeight / 2 - mBubbleHeight / 2));
            mRect.set(bubbleRight - bubbleWidth, bubbleTop, bubbleRight, bubbleTop + mBubbleHeight);
            c.drawRoundRect(mRect, mBubbleHeight / 2, mBubbleHeight / 2, mBubblePaint);
            float textY = bubbleTop + mBubbleHeight / 2 - (mTextPaint.descent() + mTextPaint.ascent()) / 2;
            c.drawText(mBubbleText, mRect.centerX(), textY, mTextPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mHideRunnable);
        super.onDetachedFromWindow();
    }
}
