package com.sloth.browser;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Children in equal columns, rows as tall as their tallest cell.
 *
 * <p>The cards are the direct children, so a count of children is a count of cards, and a
 * row that wraps is the grid's business rather than a LinearLayout per row that has to be
 * rebuilt whenever one card goes. Hidden children take no cell, which is what lets the tab
 * filter close the gaps it makes.</p>
 */
final class SlothGrid extends ViewGroup {

    private final int columns;
    private final int gap;

    SlothGrid(Context context, int columns, int gapPx) {
        super(context);
        this.columns = Math.max(1, columns);
        this.gap = Math.max(0, gapPx);
    }

    int columns() {
        return columns;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int inner = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int cell = Math.max(0, (inner - gap * (columns - 1)) / columns);
        int cellSpec = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY);
        int height = getPaddingTop() + getPaddingBottom();
        int rowHeight = 0;
        int inRow = 0;
        int rows = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = child.getLayoutParams();
            int childHeightSpec = lp != null && lp.height > 0
                    ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            child.measure(cellSpec, childHeightSpec);
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            inRow++;
            if (inRow == columns) {
                height += rowHeight + (rows > 0 ? gap : 0);
                rows++;
                rowHeight = 0;
                inRow = 0;
            }
        }
        if (inRow > 0) {
            height += rowHeight + (rows > 0 ? gap : 0);
        }
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int inner = Math.max(0, (r - l) - getPaddingLeft() - getPaddingRight());
        int cell = Math.max(0, (inner - gap * (columns - 1)) / columns);
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowHeight = 0;
        int inRow = 0;
        // every cell in a row starts at the same top and takes the row's height, so a short
        // card does not leave its neighbour's edge hanging lower
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (inRow == 0) {
                rowHeight = rowHeightFrom(i);
            }
            child.layout(x, y, x + cell, y + rowHeight);
            inRow++;
            x += cell + gap;
            if (inRow == columns) {
                inRow = 0;
                x = getPaddingLeft();
                y += rowHeight + gap;
            }
        }
    }

    /** The tallest visible child among the next {@code columns} visible ones. */
    private int rowHeightFrom(int start) {
        int tallest = 0;
        int seen = 0;
        for (int i = start; i < getChildCount() && seen < columns; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            tallest = Math.max(tallest, child.getMeasuredHeight());
            seen++;
        }
        return tallest;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }
}
