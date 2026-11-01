package com.yoshione.fingen;

import android.content.Intent;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.yoshione.fingen.databinding.FragmentBarChartBinding;
import com.yoshione.fingen.filters.AbstractFilter;
import com.yoshione.fingen.filters.AmountFilter;
import com.yoshione.fingen.filters.DateRangeFilter;
import com.yoshione.fingen.model.DateEntry;
import com.yoshione.fingen.model.DateRangeSum;
import com.yoshione.fingen.utils.CabbageFormatter;
import com.yoshione.fingen.utils.ColorUtils;
import com.yoshione.fingen.utils.FgHorizontalBarChartRenderer;
import com.yoshione.fingen.utils.FgLargeValuesFormatter;
import com.yoshione.fingen.utils.NormalValuesFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Created by Leonid on 16.08.2015.
 * FragmentTimeBarChart
 */
public class FragmentTimeBarChart extends Fragment implements OnChartValueSelectedListener {

    private static final float BAR_WIDTH_SINGLE = 0.8f;
    private static final float BAR_WIDTH_PAIR = 0.4f;
    private static final float BAR_OFFSET = 0.2f;
    public static final String TAG = "FragmentBarChart";
    private final boolean mDrawBarLabels = false;
    HorizontalBarChart mBarChart;
    private FragmentBarChartBinding mBinding;
    FloatingActionButton mFab;
    LinearLayout mFabLayout;
    private FgLargeValuesFormatter largeValuesFormatter;
    private NormalValuesFormatter mormalValuesFormatter;
//    public MyMarkerView mMyMarkerView;

    @SuppressWarnings("unchecked")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        R.layout.fragment_bar_chart

        View view = inflater.inflate(R.layout.fragment_bar_chart, container, false);
        mBinding = FragmentBarChartBinding.bind(view);
        mBarChart = mBinding.barChart;
        mFab = mBinding.fab;
        mFabLayout = mBinding.fabLayout;

        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Highlight> highlight = mBarChart.getHighlighted();

                if (highlight == null || highlight.isEmpty()) {
                    return;
                }

                Entry<?> entry = mBarChart.getData().getEntryForHighlight(highlight.get(0));

                if (entry == null) {
                    return;
                }

                DateRangeSum range = (DateRangeSum) entry.getData();
                ReportBuilder reportBuilder = ReportBuilder.getInstance(getActivity());
                ArrayList<AbstractFilter> filters = new ArrayList<>();

                DateRangeFilter dateRangeFilter = new DateRangeFilter(new Random().nextInt(Integer.MAX_VALUE), getActivity());
                dateRangeFilter.setmStartDate(range.getDateStart());
                dateRangeFilter.setmEndDate(range.getDateEnd());
                filters.add(dateRangeFilter);

                AmountFilter amountFilter = new AmountFilter(new Random().nextInt(Integer.MAX_VALUE));
                switch (reportBuilder.getActiveShowIndex()) {
                    case ReportBuilder.SHOW_EXPENSE:
                        amountFilter.setmIncome(false);
                        amountFilter.setmOutcome(true);
                        amountFilter.setmTransfer(AmountFilter.TRANSACTION_TYPE_BOTH);
                        filters.add(amountFilter);
                        break;
                    case ReportBuilder.SHOW_INCOME:
                        amountFilter.setmIncome(true);
                        amountFilter.setmOutcome(false);
                        amountFilter.setmTransfer(AmountFilter.TRANSACTION_TYPE_BOTH);
                        filters.add(amountFilter);
                        break;
                }

                for (AbstractFilter filter : reportBuilder.getFilterList()) {
                    if (!filter.getClass().equals(DateRangeFilter.class)) {
                        filters.add(filter);
                    }
                }

                Intent intent = new Intent(getActivity(), ActivityTransactions.class);
                intent.putParcelableArrayListExtra("filter_list", filters);
                intent.putExtra(FgConst.HIDE_FAB, true);
                intent.putExtra(FgConst.LOCK_SLIDINGUP_PANEL, true);
                Objects.requireNonNull(getActivity()).startActivity(intent);
            }
        });

        setupBarChart();

        largeValuesFormatter = new FgLargeValuesFormatter();
        mormalValuesFormatter = new NormalValuesFormatter();

        return view;
    }

    private boolean isShrinkValues() {
        return PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(FgConst.PREF_SHRINK_CHART_LABELS, true);
    }

    private String formatValue(float value) {
        if (isShrinkValues()) {
            return largeValuesFormatter.makePretty(value);
        } else {
            return mormalValuesFormatter.getFormattedValue(value);
        }
    }

    private IValueFormatter getValueFormatter() {
        if (isShrinkValues()) {
            return largeValuesFormatter;
        } else {
            return mormalValuesFormatter;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateChart();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    private void setupBarChart() {
        mBarChart.setDrawBarShadowEnabled(false);
        mBarChart.setDrawValueAboveBarEnabled(true);
        mBarChart.getDescription().setEnabled(false);
        mBarChart.setMaxVisibleCount(Integer.MAX_VALUE);
        mBarChart.setPinchZoomEnabled(false);
        mBarChart.setDrawGridBackgroundEnabled(false);
        mBarChart.setOnChartValueSelectedListener(this);
        mBarChart.setHighlightPerDragEnabled(false);

        int textColor = ColorUtils.getTextColor(getActivity());

        XAxis xl = mBarChart.getXAxis();
        xl.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        xl.setDrawAxisLineEnabled(true);
        xl.setDrawGridLinesEnabled(true);
        xl.setGridLineWidth(0.3f);
        xl.setTextColor(textColor);

        YAxis yl = mBarChart.getAxisLeft();
        yl.setDrawAxisLineEnabled(true);
        yl.setDrawGridLinesEnabled(true);
        yl.setGridLineWidth(0.3f);
        yl.setAxisMinimum(0f);
        yl.setTextColor(textColor);

        YAxis yr = mBarChart.getAxisRight();
        yr.setDrawAxisLineEnabled(true);
        yr.setDrawGridLinesEnabled(false);
        yr.setAxisMinimum(0f);
        yr.setTextColor(textColor);

        mBarChart.setRendererXAxis(new FgHorizontalBarChartRenderer(getActivity(), mBarChart.getViewPortHandler(),
                mBarChart.getXAxis(), mBarChart.getTransformer(YAxis.AxisDependency.LEFT), mBarChart));
        mBarChart.getLegend().setEnabled(false);
    }

    private Pair<ArrayList<IBarDataSet<?>>, ArrayList<String>> getDatasetsInAndEx(List<DateEntry> entries) {
        int i = 0;
        ArrayList<BarEntry<DateRangeSum>> yValsExpense = new ArrayList<>();
        ArrayList<BarEntry<DateRangeSum>> yValsIncome = new ArrayList<>();
        ArrayList<String> xVals = new ArrayList<>();
        DateFormat df = ReportBuilder.getInstance(getActivity()).getDateFormatter();
        BarEntry<DateRangeSum> barEntry;
        DateRangeSum rangeSum;
        Pair<Date, Date> datePair;

        for (DateEntry entry : entries) {
            if (entry.getExpense().compareTo(BigDecimal.ZERO) > 0) {
                datePair = getPairDates(entry.getDate());
                rangeSum = new DateRangeSum(datePair.first, datePair.second, entry.getExpense());
                barEntry = new BarEntry<>(i + BAR_OFFSET, entry.getExpense().setScale(2, RoundingMode.HALF_EVEN).floatValue(), null, rangeSum);
                yValsExpense.add(barEntry);
            }
            if (entry.getIncome().compareTo(BigDecimal.ZERO) > 0) {
                datePair = getPairDates(entry.getDate());
                rangeSum = new DateRangeSum(datePair.first, datePair.second, entry.getIncome());
                barEntry = new BarEntry<>(i - BAR_OFFSET, entry.getIncome().setScale(2, RoundingMode.HALF_EVEN).floatValue(), null, rangeSum);
                yValsIncome.add(barEntry);
            }
            if (entry.getExpense().compareTo(BigDecimal.ZERO) > 0 | entry.getIncome().compareTo(BigDecimal.ZERO) > 0) {
                xVals.add(df.format(entry.getDate()));
                i++;
            }
        }

        BarDataSet<DateRangeSum> setExpense = new BarDataSet<>(yValsExpense, getString(R.string.ent_outcome));
        setExpense.setColor(ContextCompat.getColor(Objects.requireNonNull(getActivity()), R.color.negative_color));
        setExpense.setValueFormatter(getValueFormatter());
        setExpense.setDrawValuesEnabled(mDrawBarLabels);
        BarDataSet<DateRangeSum> setIncome = new BarDataSet<>(yValsIncome, getString(R.string.ent_income));
        setIncome.setColor(ContextCompat.getColor(getActivity(), R.color.positive_color));
        setIncome.setValueFormatter(getValueFormatter());
        setIncome.setDrawValuesEnabled(mDrawBarLabels);

        int textColor = ColorUtils.getTextColor(getActivity());
        setExpense.setValueTextColor(textColor);
        setIncome.setValueTextColor(textColor);

        ArrayList<IBarDataSet<?>> dataSets = new ArrayList<>();
        dataSets.add(setIncome);
        dataSets.add(setExpense);

        mBarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        return new Pair<>(dataSets, xVals);
    }

    private Pair<ArrayList<IBarDataSet<?>>, ArrayList<String>> getDatasetsInMinusEx(List<DateEntry> entries) {
        int i = 0;
        ArrayList<BarEntry<DateRangeSum>> yValsSum = new ArrayList<>();
        ArrayList<String> xVals = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        DateFormat df = ReportBuilder.getInstance(getActivity()).getDateFormatter();
        BarEntry<DateRangeSum> barEntry;
        DateRangeSum rangeSum;
        Pair<Date, Date> datePair;

        BigDecimal sum;
        for (DateEntry entry : entries) {
            sum = entry.getIncome().subtract(entry.getExpense());
            datePair = getPairDates(entry.getDate());
            rangeSum = new DateRangeSum(datePair.first, datePair.second, sum);
            barEntry = new BarEntry<>(i, sum.abs().floatValue(), null, rangeSum);
            yValsSum.add(barEntry);
            xVals.add(String.format("%s (%s)", df.format(entry.getDate()), formatValue(sum.abs().floatValue())));
            if (sum.compareTo(BigDecimal.ZERO) >= 0) {
                colors.add(ContextCompat.getColor(Objects.requireNonNull(getActivity()), R.color.positive_color));
            } else {
                colors.add(ContextCompat.getColor(Objects.requireNonNull(getActivity()), R.color.negative_color));
            }
            i++;
        }

        BarDataSet<DateRangeSum> setSum = new BarDataSet<>(yValsSum, getString(R.string.ent_income) + " - " + getString(R.string.ent_outcome));
        setSum.setColors(colors);
        setSum.setValueFormatter(getValueFormatter());
        setSum.setDrawValuesEnabled(mDrawBarLabels);

        int textColor = ColorUtils.getTextColor(getActivity());
        setSum.setValueTextColor(textColor);

        ArrayList<IBarDataSet<?>> dataSets = new ArrayList<>();
        dataSets.add(setSum);
        mBarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        return new Pair<>(dataSets, xVals);
    }

    private Pair<ArrayList<IBarDataSet<?>>, ArrayList<String>> getDatasetsSingle(List<DateEntry> entries, boolean expense) {
        int i = 0;
        ArrayList<BarEntry<DateRangeSum>> yVals = new ArrayList<>();
        ArrayList<String> xVals = new ArrayList<>();
        DateFormat df = ReportBuilder.getInstance(getActivity()).getDateFormatter();
        BarEntry<DateRangeSum> barEntry;
        BigDecimal value;
        DateRangeSum rangeSum;
        Pair<Date, Date> datePair;

        for (DateEntry entry : entries) {
            value = (expense ? entry.getExpense() : entry.getIncome()).setScale(2, RoundingMode.HALF_EVEN);
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                datePair = getPairDates(entry.getDate());
                rangeSum = new DateRangeSum(datePair.first, datePair.second, value);
                barEntry = new BarEntry<>(i, value.floatValue(), null, rangeSum);
                yVals.add(barEntry);
                xVals.add(String.format("%s (%s)", df.format(entry.getDate()), formatValue(value.floatValue())));
                i++;
            }
        }

        BarDataSet set = new BarDataSet<>(yVals, getString(expense ? R.string.ent_outcome : R.string.ent_income));
        set.setColor(ContextCompat.getColor(getActivity(), expense ? R.color.negative_color : R.color.positive_color));
        set.setValueFormatter(getValueFormatter());
        set.setValueTextColor(ColorUtils.getTextColor(getActivity()));
        set.setDrawValuesEnabled(mDrawBarLabels);

        ArrayList<IBarDataSet<?>> dataSets = new ArrayList<>();
        dataSets.add(set);
        mBarChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        return new Pair<>(dataSets, xVals);
    }

    public void updateChart() {
        setData(ReportBuilder.getInstance(getActivity()).getDatesDataset());
    }

    private void setData(List<DateEntry> entries) {
        ReportBuilder reportBuilder = ReportBuilder.getInstance(getActivity());
        if (entries.size() == 0) {
            mBarChart.clear();
            return;
        }
        Pair<ArrayList<IBarDataSet<?>>, ArrayList<String>> pair;
        switch (reportBuilder.getActiveShowIndex()) {
            case ReportBuilder.SHOW_INCOME_AND_EXPENSE:
                pair = getDatasetsInAndEx(entries);
                break;
            case ReportBuilder.SHOW_INCOME_MINUS_EXPENSE:
                pair = getDatasetsInMinusEx(entries);
                break;
            case ReportBuilder.SHOW_INCOME:
                pair = getDatasetsSingle(entries, false);
                break;
            case ReportBuilder.SHOW_EXPENSE:
                pair = getDatasetsSingle(entries, true);
                break;
            default:
                return;
        }

        if (pair.second.size() == 0) {
            mBarChart.clear();
            return;
        }

        BarData data = new BarData(pair.first);
        data.setValueTextSize(10f);
        // two sets (income and expense) share a category: bars are placed side by side by hand (see BAR_OFFSET),
        // BarData.groupBars is not used because it needs dense data sets
        data.setBarWidth(pair.first.size() > 1 ? BAR_WIDTH_PAIR : BAR_WIDTH_SINGLE);

        int categories = pair.second.size();
        XAxis xAxis = mBarChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(pair.second));
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setAxisMinimum(-0.5f);
        xAxis.setAxisMaximum(categories - 0.5f);
        xAxis.setLabelCount(Math.min(categories, 15));
//        data.setValueTypeface(tf);

        mBarChart.setData(data);
        mBarChart.fitScreen();
        mBarChart.animateY(1000);
        mBarChart.highlightValues(null);

        List<Highlight> highlighted = mBarChart.getHighlighted();
        if (highlighted == null || highlighted.isEmpty()) {
            mFabLayout.setVisibility(View.GONE);
        } else {
            mFabLayout.setVisibility(View.VISIBLE);
        }
    }

    private Pair<Date, Date> getPairDates(Date date) {
        int dateRange = PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(getActivity())).getInt("report_date_range", 0);
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        switch (dateRange) {
            case ReportBuilder.DATE_RANGE_Day:
                break;
            case ReportBuilder.DATE_RANGE_Month:
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                break;
            case ReportBuilder.DATE_RANGE_YEAR:
                c.set(Calendar.MONTH, c.getActualMaximum(Calendar.MONTH));
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
                break;
        }
        return new Pair<>(date, c.getTime());
    }

    @Override
    public void onValueSelected(Entry<?> e, Highlight h) {CabbageFormatter cabbageFormatter = null;
        try {
            cabbageFormatter = new CabbageFormatter(ReportBuilder.getInstance(getActivity()).getActiveCabbage());
            DateRangeSum rangeSum = (DateRangeSum) e.getData();
            String s = String.format("%s", cabbageFormatter.format(rangeSum.getSum()));
            Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        mFabLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNothingSelected() {
        mFabLayout.setVisibility(View.GONE);
    }
}
