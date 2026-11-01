package com.yoshione.fingen.widgets;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Sticky group headers for RecyclerView with vertical LinearLayoutManager.
 * Items with the same header id (see {@link StickyHeaderAdapter#getHeaderId}) form a group.
 * The header is drawn above the first item of every group, the header of the topmost group sticks to the top.
 */
public class StickyHeaderDecoration extends RecyclerView.ItemDecoration {

    public interface StickyHeaderAdapter<VH extends RecyclerView.ViewHolder> {
        long getHeaderId(int position);

        VH onCreateHeaderViewHolder(ViewGroup parent);

        void onBindHeaderViewHolder(VH viewHolder, int position);
    }

    private final StickyHeaderAdapter mAdapter;
    private final LongSparseArray<RecyclerView.ViewHolder> mHeaderCache = new LongSparseArray<>();

    public StickyHeaderDecoration(StickyHeaderAdapter adapter) {
        mAdapter = adapter;
        if (adapter instanceof RecyclerView.Adapter) {
            ((RecyclerView.Adapter) adapter).registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    invalidateHeaders();
                }
            });
        }
    }

    public void invalidateHeaders() {
        mHeaderCache.clear();
    }

    private boolean hasHeader(int position) {
        return position == 0 || mAdapter.getHeaderId(position) != mAdapter.getHeaderId(position - 1);
    }

    @SuppressWarnings("unchecked")
    private View getHeader(RecyclerView parent, int position) {
        long id = mAdapter.getHeaderId(position);
        RecyclerView.ViewHolder holder = mHeaderCache.get(id);
        if (holder == null) {
            holder = mAdapter.onCreateHeaderViewHolder(parent);
            mHeaderCache.put(id, holder);
        }
        View header = holder.itemView;
        mAdapter.onBindHeaderViewHolder(holder, position);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(), View.MeasureSpec.UNSPECIFIED);
        int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                parent.getPaddingLeft() + parent.getPaddingRight(), header.getLayoutParams().width);
        int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                parent.getPaddingTop() + parent.getPaddingBottom(), header.getLayoutParams().height);
        header.measure(childWidth, childHeight);
        header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());
        return header;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && hasHeader(position)) {
            outRect.top = getHeader(parent, position).getHeight();
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int count = parent.getChildCount();
        if (count == 0 || mAdapter == null) return;

        int minTop = parent.getClipToPadding() ? parent.getPaddingTop() : 0;
        View firstChild = null;
        int firstPosition = RecyclerView.NO_POSITION;

        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;
            if (firstChild == null) {
                firstChild = child;
                firstPosition = position;
            } else if (hasHeader(position)) {
                View header = getHeader(parent, position);
                drawHeader(canvas, parent, header, (int) child.getY() - header.getHeight());
            }
        }

        if (firstChild == null) return;

        // sticky header of the topmost group, pushed up by the header of the next group
        View header = getHeader(parent, firstPosition);
        int top = Math.max((int) firstChild.getY() - header.getHeight(), minTop);
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child == firstChild) continue;
            int position = parent.getChildAdapterPosition(child);
            if (position != RecyclerView.NO_POSITION && hasHeader(position)) {
                int nextHeaderHeight = getHeader(parent, position).getHeight();
                int limit = (int) child.getY() - nextHeaderHeight - header.getHeight();
                top = Math.min(top, limit);
                break;
            }
        }
        // header view of the first group may have been re-measured by getHeader of another group; rebind
        header = getHeader(parent, firstPosition);
        drawHeader(canvas, parent, header, top);
    }

    private void drawHeader(Canvas canvas, RecyclerView parent, View header, int top) {
        canvas.save();
        canvas.translate(parent.getPaddingLeft(), top);
        header.draw(canvas);
        canvas.restore();
    }
}
