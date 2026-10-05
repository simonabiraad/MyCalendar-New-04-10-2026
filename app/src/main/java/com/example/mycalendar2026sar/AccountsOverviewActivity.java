package com.example.mycalendar2026sar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Screen showing a list of all account cards with their current balance
 * and this month's cash-in / cash-out totals separated by currency.
 */
public class AccountsOverviewActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LinearLayout totalOverviewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts_overview);

        recyclerView = findViewById(R.id.overviewRecyclerView);
        totalOverviewContainer = findViewById(R.id.totalOverviewContainer);

        findViewById(R.id.overviewBackButton).setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    public static class AccountRowItem {
        public Account account;
        public String displayName;
        public String currency;
        public double balance;
        public double monthIn;
        public double monthOut;

        public AccountRowItem(Account account, String displayName, String currency, double balance, double monthIn, double monthOut) {
            this.account = account;
            this.displayName = displayName;
            this.currency = currency;
            this.balance = balance;
            this.monthIn = monthIn;
            this.monthOut = monthOut;
        }
    }

    private void loadData() {
        List<Account> accounts = BalanceManager.loadAccounts(this);

        if (accounts.isEmpty()) {
            accounts.add(new Account("Expenses", 0.00, "USD"));
            BalanceManager.saveAccounts(this, accounts);
        }

        List<Transaction> allTransactions = TransactionDbHelper.getInstance(this).getAllTransactionsAscending();

        // 1. Collect all unique currencies (including standard USD, EUR, LBP)
        Set<String> currencies = new LinkedHashSet<>();
        currencies.add("USD");
        currencies.add("EUR");
        currencies.add("LBP");

        for (Account a : accounts) {
            if (a != null && a.getCurrency() != null && !a.getCurrency().trim().isEmpty()) {
                currencies.add(a.getCurrency().trim().toUpperCase(Locale.US));
            }
        }
        for (Transaction t : allTransactions) {
            if (t != null && t.getCurrency() != null && !t.getCurrency().trim().isEmpty()) {
                currencies.add(t.getCurrency().trim().toUpperCase(Locale.US));
            }
        }

        // 2. Calculate Total Balance for EACH currency (Top Section)
        Map<String, Double> totalBalancesByCurrency = new LinkedHashMap<>();
        for (String curr : currencies) {
            double total = 0.0;
            // Add custom account balances matching this currency
            for (Account a : accounts) {
                if (a == null) continue;
                String accCurr = (a.getCurrency() != null && !a.getCurrency().trim().isEmpty())
                        ? a.getCurrency().trim().toUpperCase(Locale.US) : "USD";
                if (curr.equalsIgnoreCase(accCurr)) {
                    if (!a.getName().equalsIgnoreCase("Expenses")) {
                        total += a.getBalance();
                    }
                }
            }
            // Add transaction signed amounts for transactions in this currency
            for (Transaction t : allTransactions) {
                if (t == null) continue;
                String txCurr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                        ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";
                if (curr.equalsIgnoreCase(txCurr)) {
                    String accName = t.getAccount() == null ? "" : t.getAccount().trim();
                    if (accName.isEmpty() || accName.equalsIgnoreCase("Expenses")) {
                        total += t.getSignedAmount();
                    } else {
                        // Check if transaction currency differs from target account's default currency
                        Account targetAcc = null;
                        for (Account a : accounts) {
                            if (a != null && a.getName() != null && a.getName().equalsIgnoreCase(accName)) {
                                targetAcc = a;
                                break;
                            }
                        }
                        if (targetAcc == null) {
                            total += t.getSignedAmount();
                        } else {
                            String targetAccCurr = (targetAcc.getCurrency() != null && !targetAcc.getCurrency().trim().isEmpty())
                                    ? targetAcc.getCurrency().trim().toUpperCase(Locale.US) : "USD";
                            if (!targetAccCurr.equalsIgnoreCase(curr)) {
                                total += t.getSignedAmount();
                            }
                        }
                    }
                }
            }
            totalBalancesByCurrency.put(curr, total);
        }

        // Render Top Section
        totalOverviewContainer.removeAllViews();
        int index = 0;
        for (Map.Entry<String, Double> entry : totalBalancesByCurrency.entrySet()) {
            String curr = entry.getKey();
            double total = entry.getValue();

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            if (index > 0) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
                block.setLayoutParams(lp);
            }

            TextView labelTv = new TextView(this);
            labelTv.setText("Total Balance (" + curr + ")");
            labelTv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            labelTv.setTextSize(13);

            TextView valueTv = new TextView(this);
            valueTv.setTypeface(null, android.graphics.Typeface.BOLD);
            valueTv.setTextColor(ContextCompat.getColor(this, R.color.light_green));
            valueTv.setTextSize(totalBalancesByCurrency.size() > 1 ? 24 : 28);

            String formattedValue;
            if ("LBP".equalsIgnoreCase(curr)) {
                formattedValue = CurrencyFormatter.formatLbpAmount(total) + " LBP";
            } else {
                formattedValue = String.format(Locale.US, "%,.2f %s", total, curr);
            }
            valueTv.setText(formattedValue);

            block.addView(labelTv);
            block.addView(valueTv);

            totalOverviewContainer.addView(block);
            index++;
        }

        // 3. Calculate This-Month cash-in / cash-out per account & currency (Lower Section)
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();

        List<AccountRowItem> rowItems = new ArrayList<>();

        for (Account a : accounts) {
            if (a == null) continue;
            String accName = a.getName() == null ? "Expenses" : a.getName().trim();
            if (accName.isEmpty()) accName = "Expenses";

            String defaultCurr = (a.getCurrency() != null && !a.getCurrency().trim().isEmpty())
                    ? a.getCurrency().trim().toUpperCase(Locale.US) : "USD";

            Map<String, double[]> currMap = new LinkedHashMap<>(); // currency -> [balance, monthIn, monthOut]

            if (accName.equalsIgnoreCase("Expenses")) {
                // Ensure standard currencies (USD, EUR, LBP) exist for Expenses
                for (String c : currencies) {
                    currMap.put(c, new double[]{0, 0, 0});
                }
            } else {
                currMap.put(defaultCurr, new double[]{a.getBalance(), 0, 0});
            }

            for (Transaction t : allTransactions) {
                if (t == null) continue;
                String tAcc = t.getAccount() == null ? "" : t.getAccount().trim();
                if (tAcc.isEmpty()) tAcc = "Expenses";

                if (tAcc.equalsIgnoreCase(accName)) {
                    String txCurr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                            ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";

                    double[] data = currMap.get(txCurr);
                    if (data == null) {
                        data = new double[]{0, 0, 0};
                        currMap.put(txCurr, data);
                    }

                    if (accName.equalsIgnoreCase("Expenses")) {
                        data[0] += t.getSignedAmount();
                    } else if (!txCurr.equalsIgnoreCase(defaultCurr)) {
                        data[0] += t.getSignedAmount();
                    }

                    if (t.getTimestamp() >= monthStart) {
                        if (t.isCashIn()) {
                            data[1] += t.getAmount();
                        } else {
                            data[2] += t.getAmount();
                        }
                    }
                }
            }

            for (Map.Entry<String, double[]> entry : currMap.entrySet()) {
                String c = entry.getKey();
                double[] d = entry.getValue();

                String displayName;
                if (accName.equalsIgnoreCase("Expenses")) {
                    displayName = c + " Expenses";
                } else {
                    displayName = currMap.size() > 1 ? (accName + " (" + c + ")") : accName;
                }

                rowItems.add(new AccountRowItem(a, displayName, c, d[0], d[1], d[2]));
            }
        }

        recyclerView.setAdapter(new OverviewAdapter(rowItems, account -> {
            Intent intent = new Intent(this, ExpensesActivity.class);
            intent.putExtra("active_account", account.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }));
    }

    private static class OverviewAdapter extends RecyclerView.Adapter<OverviewAdapter.ViewHolder> {
        private final List<AccountRowItem> items;
        private final OnAccountClickListener listener;

        interface OnAccountClickListener {
            void onAccountClick(Account account);
        }

        OverviewAdapter(List<AccountRowItem> items, OnAccountClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_account_summary_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AccountRowItem item = items.get(position);
            holder.name.setText(item.displayName);

            String curr = item.currency;

            if ("LBP".equalsIgnoreCase(curr)) {
                holder.balance.setText(CurrencyFormatter.formatLbpAmount(item.balance) + " LBP");
                holder.in.setText("This month In: " + CurrencyFormatter.formatLbpAmount(item.monthIn) + " LBP");
                holder.out.setText("This month Out: " + CurrencyFormatter.formatLbpAmount(item.monthOut) + " LBP");
            } else {
                holder.balance.setText(String.format(Locale.US, "%,.2f %s", item.balance, curr));
                holder.in.setText(String.format(Locale.US, "This month In: %,.2f %s", item.monthIn, curr));
                holder.out.setText(String.format(Locale.US, "This month Out: %,.2f %s", item.monthOut, curr));
            }

            holder.itemView.setOnClickListener(v -> listener.onAccountClick(item.account));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, balance, in, out;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.rowAccountName);
                balance = itemView.findViewById(R.id.rowAccountBalance);
                in = itemView.findViewById(R.id.rowAccountIn);
                out = itemView.findViewById(R.id.rowAccountOut);
            }
        }
    }
}
