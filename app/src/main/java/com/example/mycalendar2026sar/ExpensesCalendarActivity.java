package com.example.mycalendar2026sar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ExpensesCalendarActivity extends AppCompatActivity {

    private GridView calendarGrid;
    private TextView dateRangeText, accountSubtitle;
    private TextView totalCashInText, totalCashOutText, balanceText;
    private Calendar currentMonth;
    private CalendarAdapter adapter;
    private TransactionDbHelper dbHelper;
    private final Map<String, DaySummary> daySummaries = new HashMap<>();
    private String activeAccount = "Expenses";
    private List<Account> accountList = new ArrayList<>();

    private static class DaySummary {
        double cashIn = 0;
        double cashOut = 0;
        List<Transaction> transactions = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expenses_calendar);

        dbHelper = TransactionDbHelper.getInstance(this);
        currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        
        loadAccounts();

        calendarGrid = findViewById(R.id.calendarGrid);
        dateRangeText = findViewById(R.id.dateRangeText);
        accountSubtitle = findViewById(R.id.calendarAccountSubtitle);
        totalCashInText = findViewById(R.id.totalCashInText);
        totalCashOutText = findViewById(R.id.totalCashOutText);
        balanceText = findViewById(R.id.balanceText);

        findViewById(R.id.backButton).setOnClickListener(v -> handleBackNavigation());
        findViewById(R.id.prevMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateUI();
        });
        findViewById(R.id.nextMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateUI();
        });
        
        findViewById(R.id.moreButton).setOnClickListener(this::showMoreMenu);

        calendarGrid.setOnItemClickListener((parent, view, position, id) -> {
            Date date = (Date) adapter.getItem(position);
            showDayDetails(date);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.expenses_calendar_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        updateUI();
    }

    private void handleBackNavigation() {
        if (!activeAccount.equals("Expenses")) {
            activeAccount = "Expenses";
            updateUI();
        } else {
            finish();
        }
    }

    private void showMoreMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("Accounts");
        popup.setOnMenuItemClickListener(item -> {
            if ("Accounts".equals(item.getTitle())) {
                showAccountSelectionDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showAccountSelectionDialog() {
        List<String> names = new ArrayList<>();
        for (Account a : accountList) names.add(a.getName());
        
        String[] items = names.toArray(new String[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Select Account")
                .setItems(items, (dialog, which) -> {
                    activeAccount = items[which];
                    updateUI();
                })
                .show();
    }

    private void loadAccounts() {
        accountList = BalanceManager.loadAccounts(this);
        boolean hasExpenses = false;
        for (Account a : accountList) {
            if (a.getName().equalsIgnoreCase("Expenses")) {
                hasExpenses = true;
                break;
            }
        }
        if (!hasExpenses) {
            accountList.add(0, new Account("Expenses", 0.0));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        updateUI();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        if (accountSubtitle != null) accountSubtitle.setTextColor(accent);
        if (totalCashInText != null) totalCashInText.setTextColor(accent);
        if (balanceText != null) balanceText.setTextColor(accent);
        View backBtn = findViewById(R.id.backButton);
        if (backBtn instanceof android.widget.ImageView) {
            ((android.widget.ImageView) backBtn).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
    }

    private void updateUI() {
        applyColors();
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        Calendar rangeEnd = (Calendar) currentMonth.clone();
        rangeEnd.set(Calendar.DAY_OF_MONTH, currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH));
        
        String range = sdf.format(currentMonth.getTime()) + " -> " + sdf.format(rangeEnd.getTime());
        dateRangeText.setText(range);
        
        if (accountSubtitle != null) {
            accountSubtitle.setText(activeAccount);
        }

        loadDaySummaries();
        updateCalendarGrid();
        updateTotals();
    }

    private void loadDaySummaries() {
        daySummaries.clear();
        SimpleDateFormat keySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        List<Transaction> all = dbHelper.getAllTransactionsAscending();
        
        boolean isSummaryMode = activeAccount.equalsIgnoreCase("Expenses");
        
        for (Transaction t : all) {
            // INDEPENDENCE: Filter by account if not in summary mode
            if (!isSummaryMode && (t.getAccount() == null || !t.getAccount().equals(activeAccount))) continue;
            
            // Skip the aggregate row in calendar grid
            if ("Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            String key = keySdf.format(new Date(t.getTimestamp()));
            DaySummary summary = daySummaries.get(key);
            if (summary == null) {
                summary = new DaySummary();
                daySummaries.put(key, summary);
            }
            summary.transactions.add(t);
            if (t.isCashIn()) {
                summary.cashIn += t.getAmount();
            } else {
                summary.cashOut += t.getAmount();
            }
        }
    }

    private void updateCalendarGrid() {
        ArrayList<Date> days = new ArrayList<>();
        Calendar tempCal = (Calendar) currentMonth.clone();
        
        // Start from Monday
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK);
        int offset = dayOfWeek - Calendar.MONDAY;
        if (offset < 0) offset += 7;
        
        tempCal.add(Calendar.DAY_OF_MONTH, -offset);

        while (days.size() < 42) {
            days.add(tempCal.getTime());
            tempCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        adapter = new CalendarAdapter(days, currentMonth.get(Calendar.MONTH));
        calendarGrid.setAdapter(adapter);
    }

    private void updateTotals() {
        double globalCashIn = 0, globalCashOut = 0;
        boolean isSummaryMode = activeAccount.equalsIgnoreCase("Expenses");

        List<Transaction> all = dbHelper.getAllTransactionsAscending();
        for (Transaction t : all) {
            // INDEPENDENCE: Filter by account if not in summary mode
            if (!isSummaryMode && (t.getAccount() == null || !t.getAccount().equals(activeAccount))) continue;
            
            if ("Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            if (t.isCashIn()) {
                globalCashIn += t.getAmount();
            } else {
                globalCashOut += t.getAmount();
            }
        }

        totalCashInText.setText(String.format(Locale.US, "%,.2f", globalCashIn));
        totalCashOutText.setText(String.format(Locale.US, "%,.2f", globalCashOut));
        balanceText.setText(String.format(Locale.US, "%,.2f", globalCashIn - globalCashOut));
    }

    private void showDayDetails(Date date) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_calendar_day_details, null);
        dialog.setContentView(view);

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd-MMM-yyyy", Locale.getDefault());
        SimpleDateFormat keySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String key = keySdf.format(date);
        DaySummary summary = daySummaries.get(key);
        if (summary == null) summary = new DaySummary();

        TextView dateText = view.findViewById(R.id.dialogDateText);
        TextView cashInText = view.findViewById(R.id.dialogDayCashIn);
        TextView cashOutText = view.findViewById(R.id.dialogDayCashOut);
        RecyclerView recyclerView = view.findViewById(R.id.dialogTransactionList);
        Button btnIn = view.findViewById(R.id.btnDialogCashIn);
        Button btnOut = view.findViewById(R.id.btnDialogCashOut);

        int accent = ThemeManager.getMainAccentColor(this);
        dateText.setText(sdf.format(date));
        cashInText.setText(String.format(Locale.US, "%,.2f", summary.cashIn));
        cashInText.setTextColor(accent);
        cashOutText.setText(String.format(Locale.US, "%,.2f", summary.cashOut));

        btnIn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new DialogAdapter(summary.transactions));

        btnIn.setOnClickListener(v -> {
            dialog.dismiss();
            startAddTransaction(date, Transaction.TYPE_CASH_IN);
        });

        btnOut.setOnClickListener(v -> {
            dialog.dismiss();
            startAddTransaction(date, Transaction.TYPE_CASH_OUT);
        });

        dialog.show();
    }

    private void startAddTransaction(Date date, String type) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra("type", type);
        intent.putExtra("timestamp", date.getTime());
        // Pass the current active account to AddTransactionActivity
        intent.putExtra("account_context", activeAccount);
        startActivity(intent);
    }

    private class CalendarAdapter extends BaseAdapter {
        private final List<Date> days;
        private final int month;
        private final SimpleDateFormat keySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        CalendarAdapter(List<Date> days, int month) {
            this.days = days;
            this.month = month;
        }

        @Override public int getCount() { return days.size(); }
        @Override public Object getItem(int position) { return days.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ExpensesCalendarActivity.this).inflate(R.layout.item_expenses_calendar_day, parent, false);
            }
            
            Date date = days.get(position);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);

            TextView dayNumber = convertView.findViewById(R.id.dayNumber);
            TextView cashIn = convertView.findViewById(R.id.dayCashIn);
            TextView cashOut = convertView.findViewById(R.id.dayCashOut);

            dayNumber.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

            if (cal.get(Calendar.MONTH) != month) {
                dayNumber.setTextColor(0xFF666666);
                convertView.setBackgroundColor(0xFF000000);
            } else {
                dayNumber.setTextColor(0xFFFFFFFF);
                convertView.setBackgroundColor(0xFF1A1A1A);
            }

            int accent = ThemeManager.getMainAccentColor(ExpensesCalendarActivity.this);
            cashIn.setTextColor(accent);

            DaySummary summary = daySummaries.get(keySdf.format(date));
            if (summary != null) {
                if (summary.cashIn > 0) {
                    cashIn.setVisibility(View.VISIBLE);
                    cashIn.setText(String.format(Locale.US, "%,.2f", summary.cashIn));
                } else {
                    cashIn.setVisibility(View.GONE);
                }
                if (summary.cashOut > 0) {
                    cashOut.setVisibility(View.VISIBLE);
                    cashOut.setText(String.format(Locale.US, "%,.2f", summary.cashOut));
                } else {
                    cashOut.setVisibility(View.GONE);
                }
            } else {
                cashIn.setVisibility(View.GONE);
                cashOut.setVisibility(View.GONE);
            }

            return convertView;
        }
    }

    private static class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {
        private final List<Transaction> transactions;

        DialogAdapter(List<Transaction> transactions) {
            this.transactions = transactions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_transaction, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = transactions.get(position);
            holder.note.setText(t.getTitle());
            int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
            holder.cashIn.setTextColor(accent);
            if (t.getType().equals(Transaction.TYPE_CASH_IN)) {
                holder.cashIn.setText(String.format(Locale.US, "%,.2f", t.getAmount()));
                holder.cashOut.setText("");
            } else {
                holder.cashIn.setText("");
                holder.cashOut.setText(String.format(Locale.US, "%,.2f", t.getAmount()));
            }
        }

        @Override public int getItemCount() { return transactions.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView note, cashIn, cashOut;
            ViewHolder(View itemView) {
                super(itemView);
                note = itemView.findViewById(R.id.txNote);
                cashIn = itemView.findViewById(R.id.txCashIn);
                cashOut = itemView.findViewById(R.id.txCashOut);
            }
        }
    }
}
