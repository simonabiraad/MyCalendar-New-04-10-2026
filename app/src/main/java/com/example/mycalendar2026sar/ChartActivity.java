package com.example.mycalendar2026sar;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChartActivity extends AppCompatActivity {

    private PieChart pieChart;
    private LineChart lineChart;
    private LinearLayout detailsContainer, currencyChartsContainer;
    private TransactionDbHelper dbHelper;
    private TextView currentBalanceTrendText;
    private TextView btnToday, btn7Days, btn30Days;
    private int selectedPeriodDays = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chart);

        dbHelper = TransactionDbHelper.getInstance(this);
        pieChart = findViewById(R.id.pieChart);
        lineChart = findViewById(R.id.lineChart);
        detailsContainer = findViewById(R.id.detailsContainer);
        currencyChartsContainer = findViewById(R.id.currencyChartsContainer);
        currentBalanceTrendText = findViewById(R.id.currentBalanceTrendText);
        btnToday = findViewById(R.id.btnPeriodToday);
        btn7Days = findViewById(R.id.btnPeriod7Days);
        btn30Days = findViewById(R.id.btnPeriod30Days);
        
        ImageButton backButton = findViewById(R.id.chartBackButton);
        backButton.setOnClickListener(v -> finish());

        btnToday.setOnClickListener(v -> selectPeriod(1)); // Change 0 to 1 for Today
        btn7Days.setOnClickListener(v -> selectPeriod(7));
        btn30Days.setOnClickListener(v -> selectPeriod(30));

        setupCharts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        ImageButton backButton = findViewById(R.id.chartBackButton);
        if (backButton != null) {
            backButton.setImageTintList(ColorStateList.valueOf(accent));
        }
        selectPeriod(selectedPeriodDays);
    }

    private void selectPeriod(int days) {
        selectedPeriodDays = days;
        int accent = ThemeManager.getMainAccentColor(this);
        
        // Reset button UI
        int inactiveText = Color.parseColor("#888888");
        btnToday.setBackgroundResource(0);
        btnToday.setBackgroundTintList(null);
        btnToday.setTextColor(inactiveText);
        btn7Days.setBackgroundResource(0);
        btn7Days.setBackgroundTintList(null);
        btn7Days.setTextColor(inactiveText);
        btn30Days.setBackgroundResource(0);
        btn30Days.setBackgroundTintList(null);
        btn30Days.setTextColor(inactiveText);

        if (days == 1) {
            btnToday.setBackgroundResource(R.drawable.bg_period_selected);
            btnToday.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnToday.setTextColor(Color.BLACK);
        } else if (days == 7) {
            btn7Days.setBackgroundResource(R.drawable.bg_period_selected);
            btn7Days.setBackgroundTintList(ColorStateList.valueOf(accent));
            btn7Days.setTextColor(Color.BLACK);
        } else if (days == 30) {
            btn30Days.setBackgroundResource(R.drawable.bg_period_selected);
            btn30Days.setBackgroundTintList(ColorStateList.valueOf(accent));
            btn30Days.setTextColor(Color.BLACK);
        }

        List<Transaction> transactions = dbHelper.getAllTransactionsAscending();
        setupLineChart(transactions);
    }

    private void setupCharts() {
        List<Transaction> transactions = dbHelper.getAllTransactionsAscending();
        setupPieChart(transactions);
        setupLineChart(transactions);
        setupCurrencyCharts(transactions);
    }

    private void setupCurrencyCharts(List<Transaction> transactions) {
        currencyChartsContainer.removeAllViews();
        List<Account> accounts = loadAccounts();

        // Group accounts by currency
        Map<String, List<Account>> groupedAccounts = new HashMap<>();
        for (Account a : accounts) {
            String curr = a.getCurrency();
            if (curr == null) curr = "USD";
            if (!groupedAccounts.containsKey(curr)) {
                groupedAccounts.put(curr, new ArrayList<>());
            }
            List<Account> accountList = groupedAccounts.get(curr);
            if (accountList != null) {
                accountList.add(a);
            }
        }

        // Sort currencies so they appear consistently (USD first, then EUR, then others)
        List<String> currencies = new ArrayList<>(groupedAccounts.keySet());
        Collections.sort(currencies, (c1, c2) -> {
            if (c1.equalsIgnoreCase("USD")) return -1;
            if (c2.equalsIgnoreCase("USD")) return 1;
            if (c1.equalsIgnoreCase("EUR")) return -1;
            if (c2.equalsIgnoreCase("EUR")) return 1;
            return c1.compareTo(c2);
        });

        for (String currency : currencies) {
            List<Account> currencyAccounts = groupedAccounts.get(currency);
            if (currencyAccounts == null) continue;

            // Calculate stats for this currency
            double totalAmount = 0;
            Map<String, Double> accountBalances = new HashMap<>();
            for (Account a : currencyAccounts) {
                totalAmount += a.getBalance();
                accountBalances.put(a.getName(), a.getBalance());
            }

            double income = 0;
            double expenses = 0;
            for (Transaction t : transactions) {
                if (currency.equalsIgnoreCase(t.getCurrency())) {
                    if (t.isCashIn()) {
                        income += t.getAmount();
                    } else {
                        expenses += t.getAmount();
                    }
                }
            }
            double balance = income - expenses;

            // Inflate layout
            View currencyView = LayoutInflater.from(this).inflate(R.layout.layout_currency_chart, currencyChartsContainer, false);

            TextView header = currencyView.findViewById(R.id.currencyHeader);
            TextView tvTotal = currencyView.findViewById(R.id.tvTotalAmount);
            TextView tvIncome = currencyView.findViewById(R.id.tvIncome);
            TextView tvExpenses = currencyView.findViewById(R.id.tvExpenses);
            TextView tvBalance = currencyView.findViewById(R.id.tvBalance);
            PieChart currencyPieChart = currencyView.findViewById(R.id.currencyPieChart);
            LinearLayout detailsContainer = currencyView.findViewById(R.id.currencyDetailsContainer);

            String symbol = getCurrencySymbol(currency);
            header.setText(currency + " Breakdown");
            tvTotal.setText(String.format(Locale.US, "%,.2f %s", totalAmount, symbol));
            tvIncome.setText(String.format(Locale.US, "%,.2f %s", income, symbol));
            tvExpenses.setText(String.format(Locale.US, "%,.2f %s", expenses, symbol));
            tvBalance.setText(String.format(Locale.US, "%,.2f %s", balance, symbol));

            // Setup PieChart for this currency
            setupCurrencyPieChart(currencyPieChart, detailsContainer, accountBalances, totalAmount, currency);

            currencyChartsContainer.addView(currencyView);
        }
    }

    private void setupCurrencyPieChart(PieChart chart, LinearLayout container, Map<String, Double> accountBalances, double totalBalance, String currency) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : accountBalances.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        int[] colors = {
                Color.parseColor("#4285F4"), // Blue
                Color.parseColor("#34A853"), // Green
                Color.parseColor("#FBBC05"), // Orange
                Color.parseColor("#EA4335"), // Red
                Color.parseColor("#8E24AA"), // Purple
                Color.parseColor("#00ACC1"), // Teal
                Color.parseColor("#795548")  // Brown
        };
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.setHoleColor(Color.BLACK);
        chart.setDrawEntryLabels(false);
        chart.getLegend().setEnabled(false);

        chart.setCenterText(generateCenterText("Total accounts", totalBalance, currency));

        chart.animateY(1000);
        chart.invalidate();

        populateDetails(container, accountBalances, totalBalance, colors, true, currency);
    }

    private String getCurrencySymbol(String currency) {
        if (currency == null) return "$";
        switch (currency.toUpperCase()) {
            case "USD": return "$";
            case "EUR": return "€";
            case "GBP": return "£";
            case "JPY": return "¥";
            case "CNY": return "¥";
            case "LBP": return "LBP";
            default: return currency;
        }
    }

    private List<Account> loadAccounts() {
        List<Account> list = new ArrayList<>();
        try {
            String json = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                    .getString("AccountList", null);
            if (json != null) {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    list.add(new Account(
                        obj.getString("name"),
                        obj.getDouble("balance"),
                        obj.optString("currency", "USD")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void setupPieChart(List<Transaction> transactions) {
        Map<String, Double> categoryTotals = new HashMap<>();
        double totalSpent = 0;

        for (Transaction t : transactions) {
            if (!t.isCashIn()) {
                String category = t.getTitle();
                double amount = t.getAmount();
                categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + amount);
                totalSpent += amount;
            }
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryTotals.entrySet()) {
            entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        int[] colors = {
            Color.parseColor("#4285F4"), // Blue
            Color.parseColor("#34A853"), // Green
            Color.parseColor("#FBBC05"), // Orange
            Color.parseColor("#EA4335"), // Red
            Color.parseColor("#8E24AA"), // Purple
            Color.parseColor("#00ACC1"), // Teal
            Color.parseColor("#795548")  // Brown
        };
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));
        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(50f);
        pieChart.setTransparentCircleRadius(55f);
        pieChart.setHoleColor(Color.BLACK);
        pieChart.setDrawEntryLabels(false);
        pieChart.getLegend().setEnabled(false);

        // Center text: Total spent
        pieChart.setCenterText(generateCenterText("Total spent", totalSpent, "USD"));

        pieChart.animateY(1200);
        pieChart.invalidate();

        populateDetails(detailsContainer, categoryTotals, totalSpent, colors, false, "USD");
    }

    private SpannableString generateCenterText(String label, double total, String currency) {
        String top = label + "\n";
        String symbol = getCurrencySymbol(currency);
        String bottom = String.format(Locale.US, "%,.2f %s", total, symbol);
        SpannableString s = new SpannableString(top + bottom);
        s.setSpan(new ForegroundColorSpan(Color.LTGRAY), 0, top.length(), 0);
        s.setSpan(new RelativeSizeSpan(1.0f), 0, top.length(), 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, top.length(), 0);
        
        s.setSpan(new ForegroundColorSpan(Color.WHITE), top.length(), s.length(), 0);
        s.setSpan(new RelativeSizeSpan(1.8f), top.length(), s.length(), 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), top.length(), s.length(), 0);
        return s;
    }

    private void populateDetails(LinearLayout container, Map<String, Double> totals, double grandTotal, int[] palette, boolean isAccount, String currency) {
        container.removeAllViews();
        List<Map.Entry<String, Double>> list = new ArrayList<>(totals.entrySet());
        // Sort by amount descending
        Collections.sort(list, (a, b) -> b.getValue().compareTo(a.getValue()));

        String symbol = getCurrencySymbol(currency);

        int colorIndex = 0;
        for (Map.Entry<String, Double> entry : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_chart_detail, container, false);
            
            ImageView icon = row.findViewById(R.id.catIcon);
            TextView name = row.findViewById(R.id.catName);
            TextView percent = row.findViewById(R.id.catPercent);
            TextView amount = row.findViewById(R.id.catAmount);
            ProgressBar progress = row.findViewById(R.id.catProgress);

            int color = palette[colorIndex % palette.length];
            colorIndex++;

            name.setText(entry.getKey());
            double val = entry.getValue();
            int p = (int) Math.round((val / grandTotal) * 100);
            
            percent.setText(p + "%");
            amount.setText(String.format(Locale.US, "%,.2f %s", val, symbol));
            
            if (isAccount) {
                icon.setImageResource(R.drawable.ic_menu_accounts_color);
            } else {
                icon.setImageResource(getIconForCategory(entry.getKey()));
            }
            icon.setImageTintList(ColorStateList.valueOf(color));
            
            progress.setProgressTintList(ColorStateList.valueOf(color));
            progress.setProgress(p);

            container.addView(row);
        }
    }

    private int getIconForCategory(String category) {
        String low = category.toLowerCase();
        if (low.contains("food") || low.contains("restaurant")) return R.drawable.ic_cat_food_color;
        if (low.contains("rent")) return R.drawable.ic_cat_rent_color;
        if (low.contains("bill")) return R.drawable.ic_cat_bill_color;
        if (low.contains("fuel")) return R.drawable.ic_cat_fuel_color;
        if (low.contains("shop")) return R.drawable.ic_cat_shop_color;
        if (low.contains("health") || low.contains("medicine")) return R.drawable.ic_cat_health_color;
        if (low.contains("travel") || low.contains("flight")) return R.drawable.ic_cat_travel_color;
        if (low.contains("mobile") || low.contains("phone")) return R.drawable.ic_cat_mobile_color;
        if (low.contains("transport")) return R.drawable.ic_cat_transport_color;
        if (low.contains("others")) return R.drawable.ic_cat_other_color;
        return R.drawable.ic_report_logo;
    }

    private void setupLineChart(List<Transaction> transactions) {
        ArrayList<Entry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        
        double runningBalance = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
        
        long cutoff = System.currentTimeMillis() - (selectedPeriodDays * 24L * 60 * 60 * 1000);
        
        Map<String, Double> dailyBalances = new HashMap<>();
        List<String> dateKeys = new ArrayList<>();
        
        for (Transaction t : transactions) {
            runningBalance += t.getSignedAmount();
            
            if (t.getTimestamp() >= cutoff) {
                String dateKey = sdf.format(new Date(t.getTimestamp()));
                if (!dailyBalances.containsKey(dateKey)) {
                    dateKeys.add(dateKey);
                }
                dailyBalances.put(dateKey, runningBalance);
            }
        }

        for (int i = 0; i < dateKeys.size(); i++) {
            String key = dateKeys.get(i);
            entries.add(new Entry(i, dailyBalances.get(key).floatValue()));
            labels.add(key);
        }

        if (entries.isEmpty()) {
            lineChart.setNoDataText("No trend data for this period");
            lineChart.clear();
            currentBalanceTrendText.setText("0.00 $");
            return;
        }

        currentBalanceTrendText.setText(String.format(Locale.US, "%,.2f $", runningBalance));

        LineDataSet dataSet = new LineDataSet(entries, "Balance");
        dataSet.setColor(Color.parseColor("#34A853"));
        dataSet.setCircleColor(Color.parseColor("#34A853"));
        dataSet.setLineWidth(3f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(this, R.drawable.chart_gradient));
        dataSet.setHighLightColor(Color.WHITE);
        dataSet.setDrawHorizontalHighlightIndicator(false);

        LineData data = new LineData(dataSet);
        lineChart.setData(data);
        lineChart.getDescription().setEnabled(false);
        
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#888888"));
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#22FFFFFF"));
        xAxis.setGridLineWidth(0.5f);
        xAxis.enableGridDashedLine(10f, 10f, 0f);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(Math.min(labels.size(), 5));

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#888888"));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#22FFFFFF"));
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        
        CustomMarkerView mv = new CustomMarkerView(this, R.layout.layout_chart_marker, labels);
        mv.setChartView(lineChart);
        lineChart.setMarker(mv);

        lineChart.animateX(800);
        lineChart.invalidate();
    }

    private static class CustomMarkerView extends MarkerView {
        private final TextView tvDate, tvValue;
        private final List<String> labels;

        public CustomMarkerView(android.content.Context context, int layoutResource, List<String> labels) {
            super(context, layoutResource);
            this.labels = labels;
            tvDate = findViewById(R.id.markerDate);
            tvValue = findViewById(R.id.markerValue);
        }

        @Override
        public void refreshContent(Entry e, Highlight highlight) {
            int index = (int) e.getX();
            if (index >= 0 && index < labels.size()) {
                tvDate.setText(labels.get(index));
            }
            tvValue.setText(String.format(Locale.US, "%,.2f $", e.getY()));
            super.refreshContent(e, highlight);
        }

        @Override
        public MPPointF getOffset() {
            return new MPPointF(-(getWidth() / 2f), -getHeight());
        }
    }
}
