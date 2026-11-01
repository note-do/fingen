package com.yoshione.fingen.utils;

import android.app.Activity;
import android.graphics.Canvas;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.renderer.XAxisRendererHorizontalBarChart;
import com.github.mikephil.charting.utils.MPPointF;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.yoshione.fingen.FragmentBarChart;

public class FgHorizontalBarChartRenderer extends XAxisRendererHorizontalBarChart {
    private int mOutlineColor;

    public FgHorizontalBarChartRenderer(Activity activity, ViewPortHandler viewPortHandler, XAxis xAxis, Transformer trans, BarChart chart) {
        super(viewPortHandler, xAxis, trans, chart);

        mOutlineColor = ColorUtils.getTextInverseColor(activity);
    }

    @Override
    protected void drawLabels(Canvas c, float pos, MPPointF anchor) {
        // v4 right-aligns BOTTOM_INSIDE labels at the left edge, so they are cut off; keep them left-aligned inside the chart
        if (getXAxis().getPosition() == XAxis.XAxisPosition.BOTTOM_INSIDE) {
            anchor.x = 0f;
        }
        super.drawLabels(c, pos, anchor);
    }

    @Override
    protected void drawLabel(Canvas c, String label, float x, float y, MPPointF anchor, float angleDegrees) {
        ChartUtils.drawXAxisValue(c, label, x, y, getPaintAxisLabels(), anchor, angleDegrees, mOutlineColor);
        super.drawLabel(c, label, x, y, anchor, angleDegrees);
    }
}
