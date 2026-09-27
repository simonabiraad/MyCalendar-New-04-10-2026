package com.example.mycalendar2026sar;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Account Summary screen: Table-style report for a selected account.
 * Features account selection, periodic filtering (All, Weekly, Monthly, Yearly, Custom),
 * and grand totals with PDF/Excel export.
 */
public class AccountSummaryActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_WEEKLY = 1;
    private static final int FILTER_MONTHLY = 2;
    private static final int FILTER_YEARLY = 3;
    private static final int FILTER_CUSTOM = 4;

    private int currentFilter = FILTER_ALL;
    private final Calendar currentBaseDate = Calendar.getInstance();
    private long customStartMillis = -1;
    private long customEndMillis = -1;
    private String selectedAccount = "Account Summary";

    private RecyclerView recyclerView;
    private TextView emptyText, filterLabel, accountTitle;
    private TextView footerCashIn, footerCashOut, footerBalance;
    private Button btnAll, btnWeekly, btnMonthly, btnYearly;

    private SummaryAdapter adapter;
    private final List<SummaryRow> summaryList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_summary);

        recyclerView = findViewById(R.id.accountSummaryRecyclerView);
        emptyText = findViewById(R.id.accountSummaryEmptyText);
        filterLabel = findViewById(R.id.summaryFilterLabel);
        accountTitle = findViewById(R.id.summaryAccountTitle);
        
        footerCashIn = findViewById(R.id.footerCashIn);
        footerCashOut = findViewById(R.id.footerCashOut);
        footerBalance = findViewById(R.id.footerBalance);

        btnAll = findViewById(R.id.tabAll);
        btnWeekly = findViewById(R.id.tabWeekly);
        btnMonthly = findViewById(R.id.tabMonthly);
        btnYearly = findViewById(R.id.tabYearly);

        findViewById(R.id.accountSummaryBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnSummaryCalendar).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.btnSummaryReport).setOnClickListener(v -> showReportDialog());
        findViewById(R.id.summaryAccountPicker).setOnClickListener(this::showAccountPicker);

        findViewById(R.id.btnNavPrev).setOnClickListener(v -> navigateRange(-1));
        findViewById(R.id.btnNavNext).setOnClickListener(v -> navigateRange(1));

        btnAll.setOnClickListener(v -> { currentFilter = FILTER_ALL; refresh(); });
        btnWeekly.setOnClickListener(v -> { currentFilter = FILTER_WEEKLY; refresh(); });
        btnMonthly.setOnClickListener(v -> { currentFilter = FILTER_MONTHLY; refresh(); });
        btnYearly.setOnClickListener(v -> { currentFilter = FILTER_YEARLY; refresh(); });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SummaryAdapter(summaryList);
        recyclerView.setAdapter(adapter);

        applyColors();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        refresh();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        View backBtn = findViewById(R.id.accountSummaryBackButton);
        if (backBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) backBtn).setImageTintList(ColorStateList.valueOf(accent));
        }
        if (footerCashIn != null) {
            footerCashIn.setTextColor(accent);
        }
    }

    private void navigateRange(int direction) {
        if (currentFilter == FILTER_ALL) return;

        switch (currentFilter) {
            case FILTER_WEEKLY:
                currentBaseDate.add(Calendar.WEEK_OF_YEAR, direction);
                break;
            case FILTER_MONTHLY:
                currentBaseDate.add(Calendar.MONTH, direction);
                break;
            case FILTER_YEARLY:
                currentBaseDate.add(Calendar.YEAR, direction);
                break;
            case FILTER_CUSTOM:
                if (customStartMillis != -1 && customEndMillis != -1) {
                    long diff = customEndMillis - customStartMillis;
                    if (direction > 0) {
                        customStartMillis = customEndMillis + 1000;
                        customEndMillis = customStartMillis + diff;
                    } else {
                        customEndMillis = customStartMillis - 1000;
                        customStartMillis = customEndMillis - diff;
                    }
                }
                break;
        }
        refresh();
    }

    private void showAccountPicker(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add("Account Summary");
        List<Account> accounts = BalanceManager.loadAccounts(this);
        for (Account a : accounts) popup.getMenu().add(a.getName());
        
        popup.setOnMenuItemClickListener(item -> {
            selectedAccount = item.getTitle().toString();
            accountTitle.setText(selectedAccount);
            refresh();
            return true;
        });
        popup.show();
    }

    private void showReportDialog() {
        String[] options = {"PDF", "Excel"};
        int[] icons = {R.drawable.ic_pdf_logo, R.drawable.ic_excel_logo};

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle("Report");

        android.widget.ListAdapter listAdapter = new android.widget.ArrayAdapter<String>(this, R.layout.dialog_item_with_icon, R.id.itemText, options) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.itemIcon);
                icon.setImageResource(icons[position]);
                icon.setImageTintList(null); 
                return view;
            }
        };

        builder.setAdapter(listAdapter, (dialog, which) -> {
            if (which == 0) saveAsPdf();
            else saveAsExcel();
        });
        builder.show();
    }

    private void saveAsPdf() {
        if (summaryList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create(); // A4 size
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        
        int x = 40, y = 50;
        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("Account Summary Report", x, y, paint);
        
        y += 30;
        paint.setTextSize(12);
        paint.setFakeBoldText(false);
        canvas.drawText("Account: " + selectedAccount, x, y, paint);
        
        y += 20;
        canvas.drawText("Range: " + filterLabel.getText(), x, y, paint);
        
        y += 40;
        paint.setFakeBoldText(true);
        canvas.drawText("Date", x, y, paint);
        canvas.drawText("Cash In", x + 150, y, paint);
        canvas.drawText("Cash Out", x + 250, y, paint);
        canvas.drawText("Savings", x + 350, y, paint);
        
        y += 10;
        canvas.drawLine(x, y, 550, y, paint);
        
        paint.setFakeBoldText(false);
        for (SummaryRow row : summaryList) {
            y += 25;
            if (y > 780) { // Simple pagination check
                document.finishPage(page);
                page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, document.getPages().size() + 1).create());
                canvas = page.getCanvas();
                y = 50;
            }
            canvas.drawText(row.date, x, y, paint);
            canvas.drawText(String.format(Locale.US, "%,.2f", row.in), x + 150, y, paint);
            canvas.drawText(String.format(Locale.US, "%,.2f", row.out), x + 250, y, paint);
            canvas.drawText(String.format(Locale.US, "%,.2f", row.in - row.out), x + 350, y, paint);
        }
        
        y += 40;
        if (y > 800) {
            document.finishPage(page);
            page = document.startPage(new PdfDocument.PageInfo.Builder(595, 842, document.getPages().size() + 1).create());
            canvas = page.getCanvas();
            y = 50;
        }
        paint.setFakeBoldText(true);
        canvas.drawLine(x, y - 20, 550, y - 20, paint);
        canvas.drawText("TOTAL CASH IN:", x, y, paint);
        canvas.drawText(footerCashIn.getText().toString(), x + 150, y, paint);
        
        y += 20;
        canvas.drawText("TOTAL CASH OUT:", x, y, paint);
        canvas.drawText(footerCashOut.getText().toString(), x + 150, y, paint);
        
        y += 20;
        canvas.drawText("BALANCE:", x, y, paint);
        canvas.drawText(footerBalance.getText().toString(), x + 150, y, paint);

        document.finishPage(page);

        File file = new File(getCacheDir(), "Account_Summary.pdf");
        try {
            document.writeTo(new FileOutputStream(file));
            document.close();
            openFile(file, "application/pdf");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAsExcel() {
        if (summaryList.isEmpty()) {
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Account Summary Report\n");
        csv.append("Account,").append(selectedAccount).append("\n");
        csv.append("Range,").append(filterLabel.getText()).append("\n\n");
        csv.append("Date,Cash In,Cash Out,Savings\n");
        for (SummaryRow row : summaryList) {
            csv.append(row.date).append(",")
               .append(String.format(Locale.US, "%.2f", row.in)).append(",")
               .append(String.format(Locale.US, "%.2f", row.out)).append(",")
               .append(String.format(Locale.US, "%.2f", row.in - row.out)).append("\n");
        }
        csv.append("\nTOTALS\n");
        csv.append("Total Cash In,").append(footerCashIn.getText()).append("\n");
        csv.append("Total Cash Out,").append(footerCashOut.getText()).append("\n");
        csv.append("Balance,").append(footerBalance.getText()).append("\n");

        File file = new File(getCacheDir(), "Account_Summary.csv");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csv.toString().getBytes());
            fos.close();
            openFile(file, "text/csv");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save Excel", Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file type", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDatePicker() {
        MaterialDatePicker<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setTheme(R.style.CustomDatePickerTheme)
                .build();

        builder.addOnPositiveButtonClickListener(selection -> {
            if (selection.first != null && selection.second != null) {
                customStartMillis = selection.first;
                
                // Adjust end date to cover the full day (23:59:59)
                Calendar endCal = Calendar.getInstance();
                endCal.setTimeInMillis(selection.second);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                customEndMillis = endCal.getTimeInMillis();

                currentFilter = FILTER_CUSTOM;
                refresh();
            }
        });
        builder.show(getSupportFragmentManager(), "date_range_picker");
    }

    private void refresh() {
        updateTabsUI();
        updateRangeLabel();
        loadData();
    }

    private void updateTabsUI() {
        Button[] btns = {btnAll, btnWeekly, btnMonthly, btnYearly};
        int[] filters = {FILTER_ALL, FILTER_WEEKLY, FILTER_MONTHLY, FILTER_YEARLY};
        for (int i = 0; i < btns.length; i++) {
            if (currentFilter == filters[i]) {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                btns[i].setTextColor(Color.BLACK);
            } else {
                btns[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
                btns[i].setTextColor(Color.WHITE);
            }
        }
    }

    private void updateRangeLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        Calendar start = (Calendar) currentBaseDate.clone();
        Calendar end = (Calendar) currentBaseDate.clone();

        if (currentFilter == FILTER_ALL) {
            filterLabel.setText("All");
            return;
        }

        switch (currentFilter) {
            case FILTER_WEEKLY:
                start.set(Calendar.DAY_OF_WEEK, start.getFirstDayOfWeek());
                end.setTimeInMillis(start.getTimeInMillis());
                end.add(Calendar.DAY_OF_YEAR, 6);
                filterLabel.setText(sdf.format(start.getTime()) + " -> " + sdf.format(end.getTime()));
                break;
            case FILTER_MONTHLY:
                start.set(Calendar.DAY_OF_MONTH, 1);
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
                filterLabel.setText(sdf.format(start.getTime()) + " -> " + sdf.format(end.getTime()));
                break;
            case FILTER_YEARLY:
                start.set(Calendar.DAY_OF_YEAR, 1);
                end.set(Calendar.DAY_OF_YEAR, end.getActualMaximum(Calendar.DAY_OF_YEAR));
                filterLabel.setText(sdf.format(start.getTime()) + " -> " + sdf.format(end.getTime()));
                break;
            case FILTER_CUSTOM:
                filterLabel.setText(sdf.format(new Date(customStartMillis)) + " -> " + sdf.format(new Date(customEndMillis)));
                break;
        }
    }

    private void loadData() {
        summaryList.clear();
        List<Transaction> all = TransactionDbHelper.getInstance(this).getAllTransactionsAscending();
        
        long startTs = 0, endTs = Long.MAX_VALUE;
        if (currentFilter != FILTER_ALL) {
            if (currentFilter == FILTER_CUSTOM) {
                startTs = customStartMillis;
                endTs = customEndMillis;
            } else {
                Calendar c = (Calendar) currentBaseDate.clone();
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                if (currentFilter == FILTER_WEEKLY) {
                    c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
                    startTs = c.getTimeInMillis();
                    c.add(Calendar.DAY_OF_YEAR, 7);
                    endTs = c.getTimeInMillis() - 1;
                } else if (currentFilter == FILTER_MONTHLY) {
                    c.set(Calendar.DAY_OF_MONTH, 1);
                    startTs = c.getTimeInMillis();
                    c.add(Calendar.MONTH, 1);
                    endTs = c.getTimeInMillis() - 1;
                } else if (currentFilter == FILTER_YEARLY) {
                    c.set(Calendar.DAY_OF_YEAR, 1);
                    startTs = c.getTimeInMillis();
                    c.add(Calendar.YEAR, 1);
                    endTs = c.getTimeInMillis() - 1;
                }
            }
        }

        // Grouping logic
        SimpleDateFormat rowSdf;
        switch (currentFilter) {
            case FILTER_YEARLY: rowSdf = new SimpleDateFormat("MMM yyyy", Locale.getDefault()); break;
            case FILTER_ALL: rowSdf = new SimpleDateFormat("yyyy", Locale.getDefault()); break;
            default: rowSdf = new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()); break;
        }

        Map<String, SummaryRow> map = new TreeMap<>(Collections.reverseOrder());
        double totalIn = 0, totalOut = 0;

        for (Transaction t : all) {
            if ("Monthly Income".equalsIgnoreCase(t.getTitle())) continue;
            if (currentFilter != FILTER_ALL && (t.getTimestamp() < startTs || t.getTimestamp() > endTs)) continue;
            if (!selectedAccount.equals("Account Summary") && !selectedAccount.equalsIgnoreCase(t.getAccount())) continue;

            String key = rowSdf.format(new Date(t.getTimestamp()));
            SummaryRow s = map.get(key);
            if (s == null) {
                s = new SummaryRow(key);
                map.put(key, s);
            }
            if (t.isCashIn()) {
                s.in += t.getAmount();
                totalIn += t.getAmount();
            } else {
                s.out += t.getAmount();
                totalOut += t.getAmount();
            }
        }

        summaryList.addAll(map.values());
        adapter.notifyDataSetChanged();

        footerCashIn.setText(String.format(Locale.US, "%,.2f", totalIn));
        footerCashOut.setText(String.format(Locale.US, "%,.2f", totalOut));
        double balance = totalIn - totalOut;
        footerBalance.setText(String.format(Locale.US, "%,.2f", balance));
        int accent = ThemeManager.getMainAccentColor(this);
        footerCashIn.setTextColor(accent);
        footerBalance.setTextColor(balance >= 0 ? accent : ContextCompat.getColor(this, R.color.expense_red));

        emptyText.setVisibility(summaryList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class SummaryRow {
        String date;
        double in = 0, out = 0;
        SummaryRow(String d) { this.date = d; }
    }

    private static class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.VH> {
        private final List<SummaryRow> list;
        SummaryAdapter(List<SummaryRow> l) { this.list = l; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_summary_table_row, p, false));
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            SummaryRow s = list.get(pos);
            int rowAccent = ThemeManager.getMainAccentColor(h.itemView.getContext());
            h.date.setText(s.date);
            h.in.setText(String.format(Locale.US, "%,.2f", s.in));
            h.in.setTextColor(rowAccent);
            h.out.setText(String.format(Locale.US, "%,.2f", s.out));
            double savings = s.in - s.out;
            h.savings.setText(String.format(Locale.US, "%,.2f", savings));
            h.savings.setTextColor(savings >= 0 ? rowAccent : ContextCompat.getColor(h.itemView.getContext(), R.color.expense_red));
        }

        @Override public int getItemCount() { return list.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView date, in, out, savings;
            VH(View v) {
                super(v);
                date = v.findViewById(R.id.rowDate);
                in = v.findViewById(R.id.rowCashIn);
                out = v.findViewById(R.id.rowCashOut);
                savings = v.findViewById(R.id.rowSavings);
            }
        }
    }
}
