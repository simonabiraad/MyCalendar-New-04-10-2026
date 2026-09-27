package com.example.mycalendar2026sar;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Redesigned Summary screen: Shows account cards with 3-column financial
 * breakdowns (Cash In, Cash Out, Balance) and global totals footer.
 * Supports filtering by All, Daily, Weekly, Monthly, and Yearly.
 */
public class SummaryActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_DAILY = 1;
    private static final int FILTER_WEEKLY = 2;
    private static final int FILTER_MONTHLY = 3;
    private static final int FILTER_YEARLY = 4;

    private int currentFilter = FILTER_ALL;
    private Long selectedTimestamp = null;

    private RecyclerView recyclerView;
    private TextView emptyText, filterLabel;
    private TextView footerCashIn, footerCashOut, footerBalance;
    private Button btnAll, btnDaily, btnWeekly, btnMonthly, btnYearly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        recyclerView = findViewById(R.id.summaryRecyclerView);
        emptyText = findViewById(R.id.summaryEmptyText);
        filterLabel = findViewById(R.id.summaryFilterLabel);

        footerCashIn = findViewById(R.id.footerCashIn);
        footerCashOut = findViewById(R.id.footerCashOut);
        footerBalance = findViewById(R.id.footerBalance);

        btnAll = findViewById(R.id.tabAll);
        btnDaily = findViewById(R.id.tabDaily);
        btnWeekly = findViewById(R.id.tabWeekly);
        btnMonthly = findViewById(R.id.tabMonthly);
        btnYearly = findViewById(R.id.tabYearly);

        findViewById(R.id.summaryBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnSummaryCalendar).setOnClickListener(v -> showDatePicker());

        btnAll.setOnClickListener(v -> { currentFilter = FILTER_ALL; selectedTimestamp = null; loadData(); });
        btnDaily.setOnClickListener(v -> { currentFilter = FILTER_DAILY; loadData(); });
        btnWeekly.setOnClickListener(v -> { currentFilter = FILTER_WEEKLY; loadData(); });
        btnMonthly.setOnClickListener(v -> { currentFilter = FILTER_MONTHLY; loadData(); });
        btnYearly.setOnClickListener(v -> { currentFilter = FILTER_YEARLY; loadData(); });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        if (selectedTimestamp != null) c.setTimeInMillis(selectedTimestamp);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);
            selectedTimestamp = selected.getTimeInMillis();
            currentFilter = FILTER_DAILY;
            loadData();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        loadData();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        View backBtn = findViewById(R.id.summaryBackButton);
        if (backBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) backBtn).setImageTintList(ColorStateList.valueOf(accent));
        }
        if (footerCashIn != null) {
            footerCashIn.setTextColor(accent);
        }
    }

    private void loadData() {
        applyColors();
        updateFilterUI();

        List<Account> accounts = BalanceManager.loadAccounts(this);
        if (accounts.isEmpty()) {
            accounts.add(new Account("Expenses", 0.00));
            BalanceManager.saveAccounts(this, accounts);
        }

        List<Transaction> transactions = TransactionDbHelper.getInstance(this).getAllTransactionsAscending();

        long startTime = getStartTime();
        long endTime = Long.MAX_VALUE;
        if (currentFilter == FILTER_DAILY && startTime != 0) {
             Calendar end = Calendar.getInstance();
             end.setTimeInMillis(startTime);
             end.set(Calendar.HOUR_OF_DAY, 23);
             end.set(Calendar.MINUTE, 59);
             end.set(Calendar.SECOND, 59);
             endTime = end.getTimeInMillis();
        } else if (currentFilter == FILTER_WEEKLY) {
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(startTime);
            end.add(Calendar.DAY_OF_YEAR, 6);
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            endTime = end.getTimeInMillis();
        } else if (currentFilter == FILTER_MONTHLY) {
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(startTime);
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            endTime = end.getTimeInMillis();
        } else if (currentFilter == FILTER_YEARLY) {
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(startTime);
            end.set(Calendar.DAY_OF_YEAR, end.getActualMaximum(Calendar.DAY_OF_YEAR));
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            endTime = end.getTimeInMillis();
        }

        Map<String, double[]> accountTotals = new TreeMap<>(); // name -> [in, out]
        for (Account a : accounts) accountTotals.put(a.getName(), new double[]{0, 0});

        double globalIn = 0, globalOut = 0;

        for (Transaction t : transactions) {
            if ("Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            if (currentFilter != FILTER_ALL) {
                if (t.getTimestamp() < startTime || t.getTimestamp() > endTime) continue;
            }

            String acc = t.getAccount() == null ? "" : t.getAccount();
            if (acc.isEmpty()) acc = "Expenses";

            double[] totals = accountTotals.get(acc);
            if (totals == null) {
                totals = new double[]{0, 0};
                accountTotals.put(acc, totals);
            }
            if (t.isCashIn()) {
                totals[0] += t.getAmount();
                globalIn += t.getAmount();
            } else {
                totals[1] += t.getAmount();
                globalOut += t.getAmount();
            }
        }

        footerCashIn.setText(String.format(Locale.US, "%,.2f", globalIn));
        footerCashOut.setText(String.format(Locale.US, "%,.2f", globalOut));
        double globalBalance = globalIn - globalOut;
        footerBalance.setText(String.format(Locale.US, "%,.2f", globalBalance));
        int accent = ThemeManager.getMainAccentColor(this);
        footerCashIn.setTextColor(accent);
        footerBalance.setTextColor(globalBalance >= 0 ? accent : ContextCompat.getColor(this, R.color.expense_red));

        List<AccountSummaryItem> displayList = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : accountTotals.entrySet()) {
            displayList.add(new AccountSummaryItem(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }

        recyclerView.setAdapter(new SummaryAdapter(displayList, accountName -> {
            Intent intent = new Intent(this, ExpensesActivity.class);
            intent.putExtra("active_account", accountName);
            startActivity(intent);
        }));

        emptyText.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private long getStartTime() {
        Calendar cal = Calendar.getInstance();
        if (selectedTimestamp != null) {
            cal.setTimeInMillis(selectedTimestamp);
        }

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (currentFilter == FILTER_WEEKLY) {
            cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        } else if (currentFilter == FILTER_MONTHLY) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else if (currentFilter == FILTER_YEARLY) {
            cal.set(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private void updateFilterUI() {
        Button[] btns = {btnAll, btnDaily, btnWeekly, btnMonthly, btnYearly};
        int[] filters = {FILTER_ALL, FILTER_DAILY, FILTER_WEEKLY, FILTER_MONTHLY, FILTER_YEARLY};
        String[] titles = {"All", "Daily", "Weekly", "Monthly", "Yearly"};

        for (int i = 0; i < btns.length; i++) {
            if (currentFilter == filters[i]) {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                btns[i].setTextColor(Color.BLACK);
                filterLabel.setText(titles[i]);
            } else {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
                btns[i].setTextColor(Color.WHITE);
            }
        }

        if (selectedTimestamp != null && currentFilter == FILTER_DAILY) {
            filterLabel.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(selectedTimestamp)));
        }
    }

    private static class AccountSummaryItem {
        final String name;
        final double in, out;
        AccountSummaryItem(String name, double in, double out) {
            this.name = name; this.in = in; this.out = out;
        }
    }

    private static class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.ViewHolder> {
        private final List<AccountSummaryItem> summaries;
        private final OnAccountClickListener listener;

        interface OnAccountClickListener {
            void onAccountClick(String accountName);
        }

        SummaryAdapter(List<AccountSummaryItem> summaries, OnAccountClickListener listener) {
            this.summaries = summaries;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_summary_account_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AccountSummaryItem s = summaries.get(position);
            int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
            holder.name.setText(s.name);
            holder.in.setText(String.format(Locale.US, "%,.2f", s.in));
            holder.in.setTextColor(accent);
            holder.out.setText(String.format(Locale.US, "%,.2f", s.out));
            double balance = s.in - s.out;
            holder.balance.setText(String.format(Locale.US, "%,.2f", balance));
            holder.balance.setTextColor(balance >= 0 ? accent : ContextCompat.getColor(holder.itemView.getContext(), R.color.expense_red));

            holder.itemView.setOnClickListener(v -> listener.onAccountClick(s.name));
        }

        @Override
        public int getItemCount() {
            return summaries.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, in, out, balance;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.cardAccountName);
                in = itemView.findViewById(R.id.cardAccountIn);
                out = itemView.findViewById(R.id.cardAccountOut);
                balance = itemView.findViewById(R.id.cardAccountBalance);
            }
        }
    }
}
