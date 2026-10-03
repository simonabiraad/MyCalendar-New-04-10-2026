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
import java.util.Set;

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

        // Collect all unique currencies from transactions AND accounts
        Set<String> currenciesSet = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction t : transactions) {
            if (t.getCurrency() != null && !t.getCurrency().trim().isEmpty()) {
                currenciesSet.add(t.getCurrency().trim().toUpperCase(Locale.US));
            }
        }
        for (Account a : accounts) {
            if (a.getCurrency() != null && !a.getCurrency().trim().isEmpty()) {
                currenciesSet.add(a.getCurrency().trim().toUpperCase(Locale.US));
            }
        }
        if (currenciesSet.isEmpty()) {
            currenciesSet.add("USD");
        }

        List<String> currencies = new ArrayList<>(currenciesSet);
        Collections.sort(currencies, (c1, c2) -> {
            if (c1.equalsIgnoreCase("USD")) return -1;
            if (c2.equalsIgnoreCase("USD")) return 1;
            if (c1.equalsIgnoreCase("EUR")) return -1;
            if (c2.equalsIgnoreCase("EUR")) return 1;
            if (c1.equalsIgnoreCase("LBP")) return -1;
            if (c2.equalsIgnoreCase("LBP")) return 1;
            return c1.compareTo(c2);
        });

        for (String currency : currencies) {
            IncomeCalculator.IncomeBreakdown inc = IncomeCalculator.calculateIncomeForCurrency(currency, accounts, transactions);
            double totalIncome = inc.getTotalIncome();
            Map<String, Double> incomeSourcesMap = inc.sourcesMap;

            double totalExpenses = 0;
            for (Transaction t : transactions) {
                String txCurr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                        ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";

                if (currency.equalsIgnoreCase(txCurr) && !t.isCashIn()) {
                    String title = t.getTitle() != null ? t.getTitle().trim().toLowerCase(Locale.US) : "";
                    if (!title.startsWith("transfer")) {
                        totalExpenses += t.getAmount();
                    }
                }
            }

            double balance = totalIncome - totalExpenses;

            // Inflate layout
            View currencyView = LayoutInflater.from(this).inflate(R.layout.layout_currency_chart, currencyChartsContainer, false);

            TextView header = currencyView.findViewById(R.id.currencyHeader);
            TextView tvIncome = currencyView.findViewById(R.id.tvIncome);
            TextView tvExpenses = currencyView.findViewById(R.id.tvExpenses);
            TextView tvBalance = currencyView.findViewById(R.id.tvBalance);
            PieChart currencyPieChart = currencyView.findViewById(R.id.currencyPieChart);
            LinearLayout detailsContainer = currencyView.findViewById(R.id.currencyDetailsContainer);

            header.setText(currency + " Breakdown");

            if ("LBP".equalsIgnoreCase(currency)) {
                tvIncome.setText(CurrencyFormatter.formatLbpAmount(totalIncome) + " LBP");
                tvExpenses.setText(CurrencyFormatter.formatLbpAmount(totalExpenses) + " LBP");
                tvBalance.setText(CurrencyFormatter.formatLbpAmount(balance) + " LBP");
            } else {
                String symbol = getCurrencySymbol(currency);
                tvIncome.setText(formatAmountWithSymbol(totalIncome, currency, symbol));
                tvExpenses.setText(formatAmountWithSymbol(totalExpenses, currency, symbol));
                tvBalance.setText(formatAmountWithSymbol(balance, currency, symbol));
            }

            // Setup PieChart for this currency
            setupCurrencyPieChart(currencyPieChart, detailsContainer, incomeSourcesMap, totalIncome, currency);

            currencyChartsContainer.addView(currencyView);
        }
    }

    private String formatAmountWithSymbol(double amount, String currency, String symbol) {
        if ("LBP".equalsIgnoreCase(currency)) {
            return CurrencyFormatter.formatLbpAmount(amount) + " LBP";
        }
        if (symbol.equalsIgnoreCase(currency)) {
            return String.format(Locale.US, "%,.2f %s", amount, currency);
        } else {
            return String.format(Locale.US, "%,.2f %s", amount, symbol);
        }
    }

    private void setupCurrencyPieChart(PieChart chart, LinearLayout container, Map<String, Double> incomeSourcesMap, double totalIncome, String currency) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : incomeSourcesMap.entrySet()) {
            if (entry.getValue() > 0) {
                entries.add(new PieEntry(entry.getValue().floatValue(), entry.getKey()));
            }
        }

        if (entries.isEmpty()) {
            chart.setNoDataText("No data for " + currency);
            chart.clear();
            container.removeAllViews();
            return;
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

        String currSymbol = getCurrencySymbol(currency);
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(chart) {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.1f%% %s", value, currSymbol);
            }

            @Override
            public String getPieLabel(float value, PieEntry pieEntry) {
                return String.format(Locale.US, "%.1f%% %s", value, currSymbol);
            }
        });
        chart.setData(data);
        chart.setUsePercentValues(true);
        chart.getDescription().setEnabled(false);
        chart.setDrawHoleEnabled(true);
        chart.setHoleRadius(55f);
        chart.setTransparentCircleRadius(60f);
        chart.setHoleColor(Color.BLACK);
        chart.setDrawEntryLabels(false);
        chart.getLegend().setEnabled(false);

        chart.setCenterText(generateCenterText("Income", totalIncome, currency));

        chart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry pe = (PieEntry) e;
                    if (pe.getLabel() != null) {
                        showSliceDetailsDialog(pe.getLabel(), currency);
                    }
                }
            }

            @Override
            public void onNothingSelected() {
            }
        });

        chart.animateY(1000);
        chart.invalidate();

        populateDetails(container, incomeSourcesMap, totalIncome, colors, true, currency);
    }

    private SpannableString generateCenterText(String label, double total, String currency) {
        String top = label + "\n";
        String bottom;
        if ("LBP".equalsIgnoreCase(currency)) {
            bottom = CurrencyFormatter.formatLbpAmount(total) + " LBP";
        } else {
            String symbol = getCurrencySymbol(currency);
            bottom = formatAmountWithSymbol(total, currency, symbol);
        }
        SpannableString s = new SpannableString(top + bottom);
        s.setSpan(new ForegroundColorSpan(Color.LTGRAY), 0, top.length(), 0);
        s.setSpan(new RelativeSizeSpan(0.85f), 0, top.length(), 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, top.length(), 0);

        s.setSpan(new ForegroundColorSpan(Color.WHITE), top.length(), s.length(), 0);
        s.setSpan(new RelativeSizeSpan(1.4f), top.length(), s.length(), 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), top.length(), s.length(), 0);
        return s;
    }

    private void populateDetails(LinearLayout container, Map<String, Double> totals, double grandTotal, int[] palette, boolean isAccount, String currency) {
        container.removeAllViews();
        List<Map.Entry<String, Double>> list = new ArrayList<>(totals.entrySet());
        Collections.sort(list, (a, b) -> b.getValue().compareTo(a.getValue()));

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

            String entryKey = entry.getKey();
            name.setText(entryKey);
            double val = entry.getValue();
            int p = (int) Math.round((val / (grandTotal > 0 ? grandTotal : 1.0)) * 100);

            percent.setText(p + "%");
            if ("LBP".equalsIgnoreCase(currency)) {
                amount.setText(CurrencyFormatter.formatLbpAmount(val) + " LBP");
            } else {
                String symbol = getCurrencySymbol(currency);
                amount.setText(formatAmountWithSymbol(val, currency, symbol));
            }

            if (isAccount) {
                icon.setImageResource(R.drawable.ic_menu_accounts_color);
            } else {
                icon.setImageResource(getIconForCategory(entryKey));
            }
            icon.setImageTintList(ColorStateList.valueOf(color));

            progress.setProgressTintList(ColorStateList.valueOf(color));
            progress.setProgress(p);

            row.setOnClickListener(v -> showSliceDetailsDialog(entryKey, currency));

            container.addView(row);
        }
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
        if (pieChart == null || detailsContainer == null) return;

        Map<String, Double> cashOutByCurrency = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Double> categoryTotalsWithCurrency = new java.util.LinkedHashMap<>();
        Map<String, String> categoryCurrencyMap = new HashMap<>();
        Map<String, String> categoryNameOnlyMap = new HashMap<>();

        for (Transaction t : transactions) {
            if (t != null && !t.isCashIn()) {
                String curr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                        ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";
                String category = t.getTitle() != null ? t.getTitle().trim() : "";
                String lowerTitle = category.toLowerCase(Locale.US);

                // Exclude transfers
                if (lowerTitle.startsWith("transfer")) {
                    continue;
                }

                if (category.isEmpty()) {
                    if (t.getNotes() != null && !t.getNotes().trim().isEmpty()) {
                        category = t.getNotes().trim();
                    } else {
                        category = "Other";
                    }
                }

                double amt = t.getAmount();

                Double prevCurr = cashOutByCurrency.get(curr);
                cashOutByCurrency.put(curr, (prevCurr != null ? prevCurr : 0.0) + amt);

                String displayKey = curr + ": " + category;
                Double prevVal = categoryTotalsWithCurrency.get(displayKey);
                categoryTotalsWithCurrency.put(displayKey, (prevVal != null ? prevVal : 0.0) + amt);
                categoryCurrencyMap.put(displayKey, curr);
                categoryNameOnlyMap.put(displayKey, category);
            }
        }

        if (cashOutByCurrency.isEmpty()) {
            pieChart.setNoDataText("No spending data");
            pieChart.clear();
            detailsContainer.removeAllViews();
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : categoryTotalsWithCurrency.entrySet()) {
            if (entry.getValue() > 0) {
                String displayKey = entry.getKey();
                String curr = categoryCurrencyMap.get(displayKey);
                Double currTotalObj = cashOutByCurrency.get(curr);
                double currTotal = (currTotalObj != null && currTotalObj > 0) ? currTotalObj : 1.0;
                float normalizedValue = (float) ((entry.getValue() / currTotal) * 100.0);
                entries.add(new PieEntry(normalizedValue, entry.getKey()));
            }
        }

        int[] colors = {
            Color.parseColor("#4285F4"), // Blue
            Color.parseColor("#34A853"), // Green
            Color.parseColor("#FBBC05"), // Orange
            Color.parseColor("#EA4335"), // Red
            Color.parseColor("#8E24AA"), // Purple
            Color.parseColor("#00ACC1"), // Teal
            Color.parseColor("#795548")  // Brown
        };

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTypeface(Typeface.DEFAULT_BOLD);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart) {
            @Override
            public String getFormattedValue(float value) {
                return super.getFormattedValue(value);
            }

            @Override
            public String getPieLabel(float value, PieEntry pieEntry) {
                String curr = "USD";
                if (pieEntry != null && pieEntry.getLabel() != null) {
                    String displayKey = pieEntry.getLabel();
                    String foundCurr = categoryCurrencyMap.get(displayKey);
                    if (foundCurr != null) curr = foundCurr;
                }
                String symbol = getCurrencySymbol(curr);
                return String.format(Locale.US, "%.1f%% %s", value, symbol);
            }
        });
        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleRadius(55f);
        pieChart.setTransparentCircleRadius(60f);
        pieChart.setHoleColor(Color.BLACK);
        pieChart.setDrawEntryLabels(false);
        pieChart.getLegend().setEnabled(false);

        // Center text: Dynamic totals for each currency separately
        pieChart.setCenterText(generateMultiCurrencyCenterText(cashOutByCurrency));

        pieChart.setOnChartValueSelectedListener(new com.github.mikephil.charting.listener.OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (e instanceof PieEntry) {
                    PieEntry pe = (PieEntry) e;
                    String displayKey = pe.getLabel();
                    if (displayKey != null) {
                        String curr = categoryCurrencyMap.get(displayKey);
                        String catNameOnly = categoryNameOnlyMap.get(displayKey);
                        if (curr == null) curr = "USD";
                        if (catNameOnly == null) catNameOnly = displayKey;
                        showSliceDetailsDialog(catNameOnly, curr);
                    }
                }
            }

            @Override
            public void onNothingSelected() {
            }
        });

        pieChart.animateY(1200);
        pieChart.invalidate();

        populateMultiCurrencyDetails(detailsContainer, categoryTotalsWithCurrency, cashOutByCurrency, categoryCurrencyMap, categoryNameOnlyMap, colors);
    }

    private SpannableString generateMultiCurrencyCenterText(Map<String, Double> cashOutByCurrency) {
        StringBuilder sb = new StringBuilder("Total spent\n");
        if (cashOutByCurrency.isEmpty()) {
            sb.append("0.00 $");
        } else {
            int count = 0;
            for (Map.Entry<String, Double> entry : cashOutByCurrency.entrySet()) {
                String curr = entry.getKey();
                double amt = entry.getValue();
                if (count > 0) sb.append("\n");

                if ("LBP".equalsIgnoreCase(curr)) {
                    sb.append(CurrencyFormatter.formatLbpAmount(amt)).append(" LBP");
                } else {
                    String symbol = getCurrencySymbol(curr);
                    if (symbol.equalsIgnoreCase(curr)) {
                        sb.append(String.format(Locale.US, "%,.2f %s", amt, curr));
                    } else {
                        sb.append(symbol).append(String.format(Locale.US, "%,.2f", amt));
                    }
                }
                count++;
            }
        }

        String fullText = sb.toString();
        SpannableString s = new SpannableString(fullText);
        int topEnd = "Total spent".length();

        s.setSpan(new ForegroundColorSpan(Color.LTGRAY), 0, topEnd, 0);
        s.setSpan(new RelativeSizeSpan(0.85f), 0, topEnd, 0);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, topEnd, 0);

        if (fullText.length() > topEnd) {
            s.setSpan(new ForegroundColorSpan(Color.WHITE), topEnd, fullText.length(), 0);
            int currencyCount = cashOutByCurrency.size();
            float fontMultiplier = currencyCount > 3 ? 0.9f : (currencyCount > 2 ? 1.1f : 1.3f);
            s.setSpan(new RelativeSizeSpan(fontMultiplier), topEnd, fullText.length(), 0);
            s.setSpan(new StyleSpan(Typeface.BOLD), topEnd, fullText.length(), 0);
        }

        return s;
    }

    private void populateMultiCurrencyDetails(LinearLayout container,
                                              Map<String, Double> categoryTotalsWithCurrency,
                                              Map<String, Double> cashOutByCurrency,
                                              Map<String, String> categoryCurrencyMap,
                                              Map<String, String> categoryNameOnlyMap,
                                              int[] palette) {
        container.removeAllViews();
        List<Map.Entry<String, Double>> list = new ArrayList<>(categoryTotalsWithCurrency.entrySet());
        // Sort by amount descending
        Collections.sort(list, (a, b) -> b.getValue().compareTo(a.getValue()));

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

            String displayKey = entry.getKey();
            String curr = categoryCurrencyMap.get(displayKey);
            String catNameOnly = categoryNameOnlyMap.get(displayKey);
            if (curr == null) curr = "USD";
            if (catNameOnly == null) catNameOnly = displayKey;

            name.setText(curr + ": " + catNameOnly);
            double val = entry.getValue();

            double currencyTotalSpent = cashOutByCurrency.getOrDefault(curr, 0.0);
            int p = (int) Math.round((val / (currencyTotalSpent > 0 ? currencyTotalSpent : 1.0)) * 100);

            percent.setText(p + "%");

            if ("LBP".equalsIgnoreCase(curr)) {
                amount.setText(CurrencyFormatter.formatLbpAmount(val) + " LBP");
            } else {
                String symbol = getCurrencySymbol(curr);
                amount.setText(formatAmountWithSymbol(val, curr, symbol));
            }

            icon.setImageResource(getIconForCategory(catNameOnly));
            icon.setImageTintList(ColorStateList.valueOf(color));

            progress.setProgressTintList(ColorStateList.valueOf(color));
            progress.setProgress(p);

            String catNameCopy = catNameOnly;
            String currCopy = curr;
            row.setOnClickListener(v -> showSliceDetailsDialog(catNameCopy, currCopy));

            container.addView(row);
        }
    }

    private void showSliceDetailsDialog(String categoryName, String currency) {
        if (isFinishing()) return;
        List<String> itemLines = new ArrayList<>();
        SimpleDateFormat dateSdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.US);
        String symbol = getCurrencySymbol(currency);

        List<Account> accounts = loadAccounts();
        List<Transaction> transactions = dbHelper.getAllTransactionsAscending();

        // 1. Check if categoryName matches an account name
        for (Account a : accounts) {
            if (a.getName() != null && a.getName().equalsIgnoreCase(categoryName) && currency.equalsIgnoreCase(a.getCurrency())) {
                String formattedAmt = formatAmountWithSymbol(a.getBalance(), currency, symbol);
                String line = a.getName() + " Account Balance — " + formattedAmt + " — Added — " + dateSdf.format(new Date()) + " — " + timeSdf.format(new Date());
                itemLines.add(line);
                break;
            }
        }

        // 2. Find all transactions matching this category / title / notes and currency
        for (Transaction t : transactions) {
            if (t == null) continue;
            String txCurr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                    ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";

            if (!currency.equalsIgnoreCase(txCurr)) continue;

            String title = t.getTitle() != null ? t.getTitle().trim() : "";
            String notes = t.getNotes() != null ? t.getNotes().trim() : "";
            String lowerTitle = title.toLowerCase(Locale.US);

            if (lowerTitle.startsWith("transfer") || lowerTitle.equalsIgnoreCase("monthly income")) {
                continue;
            }

            boolean matches = title.equalsIgnoreCase(categoryName) || notes.equalsIgnoreCase(categoryName);
            if (!matches && title.isEmpty() && categoryName.equalsIgnoreCase("Cash In")) {
                matches = t.isCashIn();
            }
            if (!matches && title.isEmpty() && categoryName.equalsIgnoreCase("Other")) {
                matches = !t.isCashIn();
            }

            if (matches) {
                String itemName = !title.isEmpty() ? title : (!notes.isEmpty() ? notes : (t.isCashIn() ? "Cash In" : "Expense"));
                String status = t.isCashIn() ? "Added" : "Removed";
                String formattedAmt = formatAmountWithSymbol(t.getAmount(), currency, symbol);
                String dateStr = dateSdf.format(new Date(t.getTimestamp()));
                String timeStr = timeSdf.format(new Date(t.getTimestamp()));

                String line = itemName + " — " + formattedAmt + " — " + status + " — " + dateStr + " — " + timeStr;
                itemLines.add(line);
            }
        }

        if (itemLines.isEmpty()) {
            String formattedZero = formatAmountWithSymbol(0, currency, symbol);
            itemLines.add(categoryName + " — " + formattedZero + " — Added — " + dateSdf.format(new Date()) + " — " + timeSdf.format(new Date()));
        }

        // Build popup dialog
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle(categoryName + " (" + currency + ")");

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        contentLayout.setPadding(padding, padding, padding, padding);

        for (String lineText : itemLines) {
            TextView tv = new TextView(this);
            tv.setText(lineText);
            tv.setTextColor(ContextCompat.getColor(this, R.color.white));
            tv.setTextSize(14f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setPadding(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
            contentLayout.addView(tv);
        }

        scrollView.addView(contentLayout);
        builder.setView(scrollView);
        builder.setPositiveButton("Close", null);

        ThemeManager.showDialog(builder, this);
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
