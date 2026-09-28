package com.example.mycalendar2026sar;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Category-wise breakdown report across all accounts: pick a period
 * (Month / Year / All Time) and a type (Expenses / Income), see each
 * category's total, share of the total, and a proportional bar - all
 * computed from the shared transaction database. No charting library
 * needed; the bars are plain weighted Views.
 */
public class ReportAllActivity extends AppCompatActivity {

    private static final int PERIOD_MONTH = 0;
    private static final int PERIOD_YEAR = 1;
    private static final int PERIOD_ALL = 2;

    private int currentPeriod = PERIOD_MONTH;
    private boolean showExpenses = true;
    private String selectedCurrency = "USD";

    private Button monthButton, yearButton, allButton, currencyButton;
    private Button expenseToggle, incomeToggle;
    private TextView totalText, emptyText;
    private RecyclerView recyclerView;
    private ReportAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_all);

        monthButton = findViewById(R.id.reportMonthButton);
        yearButton = findViewById(R.id.reportYearButton);
        allButton = findViewById(R.id.reportAllButton);
        currencyButton = findViewById(R.id.reportCurrencyButton);
        expenseToggle = findViewById(R.id.reportExpenseToggle);
        incomeToggle = findViewById(R.id.reportIncomeToggle);
        totalText = findViewById(R.id.reportTotalText);
        emptyText = findViewById(R.id.reportEmptyText);
        recyclerView = findViewById(R.id.reportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.reportBackButton).setOnClickListener(v -> finish());

        monthButton.setOnClickListener(v -> { currentPeriod = PERIOD_MONTH; refresh(); });
        yearButton.setOnClickListener(v -> { currentPeriod = PERIOD_YEAR; refresh(); });
        allButton.setOnClickListener(v -> { currentPeriod = PERIOD_ALL; refresh(); });

        currencyButton.setOnClickListener(v -> showCurrencyPicker());

        expenseToggle.setOnClickListener(v -> { showExpenses = true; refresh(); });
        incomeToggle.setOnClickListener(v -> { showExpenses = false; refresh(); });

        refresh();
    }

    private void refresh() {
        updateButtonStyles();
        currencyButton.setText(selectedCurrency);

        List<Transaction> all = TransactionDbHelper.getInstance(this).getAllTransactionsAscending();
        long periodStart = getPeriodStart();

        Map<String, Double> totals = new LinkedHashMap<>();
        double grandTotal = 0;
        for (Transaction t : all) {
            if (t.getTimestamp() < periodStart) continue;
            if ("Monthly Income".equals(t.getTitle())) continue; // system entry, not a real category
            
            // CURRENCY FILTER
            if (!selectedCurrency.equalsIgnoreCase(t.getCurrency())) continue;

            boolean wantCashIn = !showExpenses;
            if (t.isCashIn() != wantCashIn) continue;

            String category = t.getTitle();
            double prev = totals.containsKey(category) ? totals.get(category) : 0;
            totals.put(category, prev + t.getAmount());
            grandTotal += t.getAmount();
        }

        totalText.setText(String.format(Locale.US, "Total: %,.2f %s", grandTotal, selectedCurrency));

        List<ReportRow> rows = new ArrayList<>();
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            double percent = grandTotal > 0 ? (e.getValue() / grandTotal) * 100.0 : 0;
            rows.add(new ReportRow(e.getKey(), e.getValue(), percent));
        }
        Collections.sort(rows, (a, b) -> Double.compare(b.amount, a.amount));

        if (rows.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }

        adapter = new ReportAdapter(rows, showExpenses);
        recyclerView.setAdapter(adapter);
    }

    private long getPeriodStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        switch (currentPeriod) {
            case PERIOD_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                return cal.getTimeInMillis();
            case PERIOD_YEAR:
                cal.set(Calendar.DAY_OF_YEAR, 1);
                return cal.getTimeInMillis();
            default:
                return 0;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        refresh();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        View backBtn = findViewById(R.id.reportBackButton);
        if (backBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) backBtn).setImageTintList(ColorStateList.valueOf(accent));
        }
    }

    private void updateButtonStyles() {
        int active = ThemeManager.getMainAccentColor(this);
        int inactive = ContextCompat.getColor(this, R.color.gray);
        monthButton.setBackgroundTintList(ColorStateList.valueOf(currentPeriod == PERIOD_MONTH ? active : inactive));
        yearButton.setBackgroundTintList(ColorStateList.valueOf(currentPeriod == PERIOD_YEAR ? active : inactive));
        allButton.setBackgroundTintList(ColorStateList.valueOf(currentPeriod == PERIOD_ALL ? active : inactive));

        int expenseColor = ContextCompat.getColor(this, R.color.expense_red);
        int incomeColor = ThemeManager.getMainAccentColor(this);
        expenseToggle.setBackgroundTintList(ColorStateList.valueOf(showExpenses ? expenseColor : inactive));
        incomeToggle.setBackgroundTintList(ColorStateList.valueOf(!showExpenses ? incomeColor : inactive));
    }

    private void showCurrencyPicker() {
        List<CountryManager.Country> countries = CountryManager.getCountries();
        java.util.Set<String> uniqueCurrencies = new java.util.TreeSet<>();
        for (CountryManager.Country c : countries) uniqueCurrencies.add(c.currency);
        
        final String[] items = uniqueCurrencies.toArray(new String[0]);

        ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Select Currency")
                .setItems(items, (dialog, which) -> {
                    selectedCurrency = items[which];
                    refresh();
                }), this);
    }

    private static class ReportRow {
        final String category;
        final double amount;
        final double percent;

        ReportRow(String category, double amount, double percent) {
            this.category = category;
            this.amount = amount;
            this.percent = percent;
        }
    }

    private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {
        private final List<ReportRow> rows;
        private final boolean isExpense;

        ReportAdapter(List<ReportRow> rows, boolean isExpense) {
            this.rows = rows;
            this.isExpense = isExpense;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report_category_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReportRow row = rows.get(position);
            holder.name.setText(row.category);
            holder.amount.setText(String.format(Locale.US, "%,.2f", row.amount));
            holder.percent.setText(String.format(Locale.getDefault(), "%.0f%%", row.percent));

            int fillWeight = Math.max(1, (int) Math.round(row.percent));
            int emptyWeight = Math.max(0, 100 - fillWeight);
            ((LinearLayout.LayoutParams) holder.barFill.getLayoutParams()).weight = fillWeight;
            ((LinearLayout.LayoutParams) holder.barEmpty.getLayoutParams()).weight = emptyWeight;
            holder.barFill.requestLayout();
            holder.barEmpty.requestLayout();

            int barColor = isExpense ? ContextCompat.getColor(holder.itemView.getContext(), R.color.expense_red)
                    : ThemeManager.getMainAccentColor(holder.itemView.getContext());
            holder.barFill.setBackgroundColor(barColor);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, amount, percent;
            View barFill, barEmpty;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.reportRowName);
                amount = itemView.findViewById(R.id.reportRowAmount);
                percent = itemView.findViewById(R.id.reportRowPercent);
                barFill = itemView.findViewById(R.id.reportRowBarFill);
                barEmpty = itemView.findViewById(R.id.reportRowBarEmpty);
            }
        }
    }
}
