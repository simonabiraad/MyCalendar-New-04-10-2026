package com.example.mycalendar2026sar;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.MaterialDatePicker;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import android.content.Intent;

public class TransactionsAllAccountsActivity extends AppCompatActivity {

    private static final int TAB_ALL = 0;
    private static final int TAB_DAILY = 1;
    private static final int TAB_WEEKLY = 2;
    private static final int TAB_MONTHLY = 3;
    private static final int TAB_YEARLY = 4;

    private int currentTab = TAB_ALL;
    private TransactionDbHelper dbHelper;
    private AllTxAdapter adapter;
    private RecyclerView recyclerView;
    private TextView labelCurrentTab, footerCashIn, footerCashOut, footerBalance;
    private Button btnAll, btnDaily, btnWeekly, btnMonthly, btnYearly;

    // Filters
    private String searchQuery = "";
    private Long filterDate = null;
    private Long filterDateStart = null, filterDateEnd = null;
    private String filterAccount = null;

    private List<Transaction> currentFilteredTransactions = new ArrayList<>();
    private double currentFilteredIn = 0, currentFilteredOut = 0;
    private String pendingExportContent = "";

    private final ActivityResultLauncher<Intent> saveFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveReportToUri(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transactions_all_accounts);

        dbHelper = TransactionDbHelper.getInstance(this);

        btnAll = findViewById(R.id.tabAll);
        btnDaily = findViewById(R.id.tabDaily);
        btnWeekly = findViewById(R.id.tabWeekly);
        btnMonthly = findViewById(R.id.tabMonthly);
        btnYearly = findViewById(R.id.tabYearly);
        labelCurrentTab = findViewById(R.id.currentTabLabel);
        footerCashIn = findViewById(R.id.footerCashIn);
        footerCashOut = findViewById(R.id.footerCashOut);
        footerBalance = findViewById(R.id.footerBalance);
        recyclerView = findViewById(R.id.allTxRecyclerView);

        findViewById(R.id.allTxBackButton).setOnClickListener(v -> finish());

        // Toolbar Extras
        SearchView searchView = findViewById(R.id.allTxSearchView);
        ImageButton btnSave = findViewById(R.id.allTxSaveButton);
        ImageButton btnMore = findViewById(R.id.allTxMoreButton);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                refresh();
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                refresh();
                return true;
            }
        });

        btnSave.setOnClickListener(this::showSavePopupMenu);
        btnMore.setOnClickListener(this::showMorePopupMenu);

        btnAll.setOnClickListener(v -> {
            currentTab = TAB_ALL;
            filterDate = null;
            filterDateStart = null;
            filterDateEnd = null;
            filterAccount = null;
            refresh();
        });
        btnDaily.setOnClickListener(v -> { currentTab = TAB_DAILY; refresh(); });
        btnWeekly.setOnClickListener(v -> { currentTab = TAB_WEEKLY; refresh(); });
        btnMonthly.setOnClickListener(v -> { currentTab = TAB_MONTHLY; refresh(); });
        btnYearly.setOnClickListener(v -> { currentTab = TAB_YEARLY; refresh(); });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AllTxAdapter();
        recyclerView.setAdapter(adapter);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.allTxToolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        refresh();
    }

    private void showSavePopupMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "Report").setIcon(R.drawable.ic_report_logo);
        popup.getMenu().add(0, 2, 0, "Save as PDF").setIcon(R.drawable.ic_pdf_logo);
        popup.getMenu().add(0, 3, 0, "Save as Excel").setIcon(R.drawable.ic_excel_logo);
        popup.getMenu().add(0, 4, 0, "Print as PDF").setIcon(R.drawable.ic_menu_print_color);
        popup.getMenu().add(0, 5, 0, "Print as Excel").setIcon(R.drawable.ic_menu_print_color);

        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuHelper = field.get(popup);
            if (menuHelper != null) {
                Class<?> classPopupHelper = menuHelper.getClass();
                java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuHelper, true);
            }
        } catch (Exception ignored) {}
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                startActivity(new android.content.Intent(this, ReportAllActivity.class));
            } else if (id == 2) {
                handlePdfExport();
            } else if (id == 3) {
                handleExcelExport();
            } else if (id == 4) {
                handlePdfExport(); // Reusing the same for now as per current logic
            } else if (id == 5) {
                handleExcelExport();
            }
            return true;
        });
        popup.show();
    }

    private void handlePdfExport() {
        if (currentFilteredTransactions.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        String html = ReportUtils.generateHtmlReport(currentFilteredTransactions, "Transaction Report");
        ReportUtils.printHtml(this, html, "Transactions_Report");
    }

    private void handleExcelExport() {
        if (currentFilteredTransactions.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingExportContent = ReportUtils.generateCsvReport(currentFilteredTransactions);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "Transactions_Report.csv");
        saveFileLauncher.launch(intent);
    }

    private void saveReportToUri(Uri uri) {
        try {
            java.io.OutputStream os = getContentResolver().openOutputStream(uri);
            if (os != null) {
                java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(os));
                writer.print(pendingExportContent);
                writer.flush();
                writer.close();
                os.close();
                Toast.makeText(this, "File saved successfully", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show();
        }
    }

    private void showMorePopupMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "Date").setIcon(R.drawable.ic_menu_calendar_color);
        popup.getMenu().add(0, 2, 0, "Select Date Range").setIcon(R.drawable.ic_menu_calendar_color);
        popup.getMenu().add(0, 3, 0, "Accounts").setIcon(R.drawable.ic_menu_accounts_color);

        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuHelper = field.get(popup);
            if (menuHelper != null) {
                Class<?> classPopupHelper = menuHelper.getClass();
                java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuHelper, true);
            }
        } catch (Exception ignored) {}

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) showDatePicker();
            else if (id == 2) showDateRangePicker();
            else if (id == 3) showAccountFilterDialog();
            return true;
        });
        popup.show();
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, dayOfMonth);
            filterDate = selected.getTimeInMillis();
            filterDateStart = null;
            filterDateEnd = null;
            refresh();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setTheme(R.style.CustomDatePickerTheme)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            filterDateStart = selection.first;
            filterDateEnd = selection.second != null ? selection.second + 86399999 : null;
            filterDate = null;
            refresh();
        });
        picker.show(getSupportFragmentManager(), "DATE_RANGE_PICKER");
    }

    private void showAccountFilterDialog() {
        List<Account> accountsFromPrefs = BalanceManager.loadAccounts(this);
        List<String> accountNames = new ArrayList<>();
        accountNames.add("Expenses"); // Summary mode
        for (Account a : accountsFromPrefs) {
            accountNames.add(a.getName());
        }

        final String[] items = accountNames.toArray(new String[0]);
        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Select Account")
                .setItems(items, (dialog, which) -> {
                    filterAccount = items[which];
                    refresh();
                })
                .setNeutralButton("Clear Filter", (dialog, which) -> {
                    filterAccount = null;
                    refresh();
                }), this);
    }

    private void refresh() {
        updateTabButtonsUI();
        
        List<Transaction> all = dbHelper.getAllTransactionsAscending();
        currentFilteredTransactions.clear();
        
        java.util.Map<String, Double> cashInMap = new java.util.HashMap<>();
        java.util.Map<String, Double> cashOutMap = new java.util.HashMap<>();
        
        Calendar now = Calendar.getInstance();
        
        for (int i = all.size() - 1; i >= 0; i--) {
            Transaction t = all.get(i);
            if ("Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            if (matchesAllFilters(t, now)) {
                currentFilteredTransactions.add(t);
                String curr = t.getCurrency();
                if (t.isCashIn()) {
                    cashInMap.put(curr, cashInMap.getOrDefault(curr, 0.0) + t.getAmount());
                } else {
                    cashOutMap.put(curr, cashOutMap.getOrDefault(curr, 0.0) + t.getAmount());
                }
            }
        }

        updateFooterUI(cashInMap, cashOutMap);

        // Group by Date for display
        List<AllTxItem> displayItems = new ArrayList<>();
        String lastDate = "";
        for (Transaction t : currentFilteredTransactions) {
            String date = DateFormat.format("EEE, dd MMM yyyy", t.getTimestamp()).toString();
            if (!date.equals(lastDate)) {
                displayItems.add(new AllTxItem(date));
                lastDate = date;
            }
            displayItems.add(new AllTxItem(t));
        }

        adapter.setItems(displayItems);
    }

    private void updateFooterUI(java.util.Map<String, Double> cashInMap, java.util.Map<String, Double> cashOutMap) {
        // If multiple currencies, we show them as a concatenated string or just the first one with a (+) sign
        // But for best user experience, let's show the main currency if one is dominant, or just "Multi"
        // Better: let's pick the first one from the map for the simple footer.
        
        if (cashInMap.isEmpty() && cashOutMap.isEmpty()) {
            footerCashIn.setText("0.00");
            footerCashOut.setText("0.00");
            footerBalance.setText("0.00");
            return;
        }

        String mainCurr = "USD";
        if (!cashInMap.isEmpty()) mainCurr = cashInMap.keySet().iterator().next();
        else if (!cashOutMap.isEmpty()) mainCurr = cashOutMap.keySet().iterator().next();

        double in = cashInMap.getOrDefault(mainCurr, 0.0);
        double out = cashOutMap.getOrDefault(mainCurr, 0.0);

        String suffix = (cashInMap.size() > 1 || cashOutMap.size() > 1) ? " (+)" : "";

        footerCashIn.setText(String.format(Locale.US, "%,.2f %s%s", in, mainCurr, suffix));
        footerCashOut.setText(String.format(Locale.US, "%,.2f %s%s", out, mainCurr, suffix));
        footerBalance.setText(String.format(Locale.US, "%,.2f %s%s", in - out, mainCurr, suffix));
    }

    private boolean matchesAllFilters(Transaction t, Calendar now) {
        if (!matchesTabFilter(t, now)) return false;

        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase();
            String title = t.getTitle().toLowerCase();
            String account = (t.getAccount() != null ? t.getAccount() : "").toLowerCase();
            if (!title.contains(q) && !account.contains(q)) return false;
        }

        if (filterDate != null) {
            if (!isSameDay(t.getTimestamp(), filterDate)) return false;
        }

        if (filterDateStart != null && filterDateEnd != null) {
            long time = t.getTimestamp();
            if (time < filterDateStart || time > filterDateEnd) return false;
        }

        if (filterAccount != null) {
            String txAccount = t.getAccount();
            if (filterAccount.equals("Expenses")) {
                // Summary mode filter: normally shows everything, but if explicitly selected, 
                // maybe show transactions with no account or "Expenses" account
                if (txAccount != null && !txAccount.equals("Expenses")) return false;
            } else {
                if (!filterAccount.equalsIgnoreCase(txAccount)) return false;
            }
        }

        return true;
    }

    private boolean isSameDay(long t1, long t2) {
        Calendar c1 = Calendar.getInstance(); c1.setTimeInMillis(t1);
        Calendar c2 = Calendar.getInstance(); c2.setTimeInMillis(t2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private boolean matchesTabFilter(Transaction t, Calendar now) {
        if (currentTab == TAB_ALL) return true;

        Calendar txCal = Calendar.getInstance();
        txCal.setTimeInMillis(t.getTimestamp());

        switch (currentTab) {
            case TAB_DAILY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                       now.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR);
            case TAB_WEEKLY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                       now.get(Calendar.WEEK_OF_YEAR) == txCal.get(Calendar.WEEK_OF_YEAR);
            case TAB_MONTHLY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                       now.get(Calendar.MONTH) == txCal.get(Calendar.MONTH);
            case TAB_YEARLY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR);
            default:
                return true;
        }
    }

    private void updateTabButtonsUI() {
        Button[] btns = {btnAll, btnDaily, btnWeekly, btnMonthly, btnYearly};
        String[] labels = {"All", "Daily", "Weekly", "Monthly", "Yearly"};
        
        for (int i = 0; i < btns.length; i++) {
            if (i == currentTab) {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                btns[i].setTextColor(Color.BLACK);
                
                String labelText = labels[i];
                if (filterAccount != null) labelText += " (" + filterAccount + ")";
                else if (filterDate != null) labelText += " (" + DateFormat.format("dd/MM", filterDate) + ")";
                else if (filterDateStart != null) labelText += " (Custom Range)";
                
                labelCurrentTab.setText(labelText);
            } else {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
                btns[i].setTextColor(Color.WHITE);
            }
        }
    }

    private static class AllTxItem {
        static final int TYPE_HEADER = 0;
        static final int TYPE_ROW = 1;

        final int type;
        final String headerDate;
        final Transaction transaction;

        AllTxItem(String headerDate) {
            this.type = TYPE_HEADER;
            this.headerDate = headerDate;
            this.transaction = null;
        }

        AllTxItem(Transaction transaction) {
            this.type = TYPE_ROW;
            this.headerDate = null;
            this.transaction = transaction;
        }
    }

    private class AllTxAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<AllTxItem> items = new ArrayList<>();

        void setItems(List<AllTxItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == AllTxItem.TYPE_HEADER) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_all_accounts_date_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_all_accounts_row, parent, false);
                return new RowViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AllTxItem item = items.get(position);
            int accent = ThemeManager.getMainAccentColor(TransactionsAllAccountsActivity.this);
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).text.setText(item.headerDate);
                ((HeaderViewHolder) holder).text.setTextColor(accent);
            } else {
                RowViewHolder row = (RowViewHolder) holder;
                Transaction t = item.transaction;
                row.title.setText(t.getTitle());
                row.time.setText(DateFormat.format("hh:mm a", t.getTimestamp()));
                row.account.setText(t.getAccount() != null ? t.getAccount() : "---");
                row.amount.setText(String.format(Locale.US, "%,.2f %s", t.getAmount(), t.getCurrency()));
                row.amount.setTextColor(t.isCashIn() ? accent : ContextCompat.getColor(TransactionsAllAccountsActivity.this, R.color.expense_red));
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            HeaderViewHolder(View v) { super(v); text = v.findViewById(R.id.headerDateText); }
        }

        class RowViewHolder extends RecyclerView.ViewHolder {
            TextView title, time, account, amount;
            RowViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.rowTitle);
                time = v.findViewById(R.id.rowTime);
                account = v.findViewById(R.id.rowAccount);
                amount = v.findViewById(R.id.rowAmount);
            }
        }
    }
}
