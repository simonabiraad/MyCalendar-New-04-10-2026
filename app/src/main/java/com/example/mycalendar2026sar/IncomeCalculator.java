package com.example.mycalendar2026sar;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IncomeCalculator {

    public static class IncomeBreakdown {
        public String currency;
        public double accountAmount = 0.0;
        public double cashInAmount = 0.0;
        public double bonusAmount = 0.0;
        public double salaryAmount = 0.0;
        public Map<String, Double> sourcesMap = new LinkedHashMap<>();

        public IncomeBreakdown(String currency) {
            this.currency = currency;
        }

        public double getTotalIncome() {
            return accountAmount + cashInAmount + bonusAmount + salaryAmount;
        }

        public double getTotal() {
            return accountAmount + cashInAmount + bonusAmount + salaryAmount;
        }
    }

    /**
     * Calculates Income for a specific currency strictly from manually added incoming sources:
     * - Account Amount (excluding default/generated "Expenses" account)
     * - Specific Cash In categories/entries (Salary, Bonus, Business, Gift, custom manually typed names)
     *
     * Excludes generic/system "Income", "Monthly Income", "Cash In" titles to prevent double counting.
     * Excludes Transfers, Expenses, Cash Out.
     *
     * @param targetCurrency Target currency (e.g., "USD", "EUR", "LBP")
     * @param accounts List of all accounts
     * @param transactions List of all transactions
     * @return IncomeBreakdown object containing calculated amounts and sources map
     */
    public static IncomeBreakdown calculateIncomeForCurrency(String targetCurrency, List<Account> accounts, List<Transaction> transactions) {
        String curr = (targetCurrency == null || targetCurrency.trim().isEmpty()) ? "USD" : targetCurrency.trim().toUpperCase(Locale.US);
        IncomeBreakdown breakdown = new IncomeBreakdown(curr);

        // 1. Account Amount: Sum of account balances for accounts in targetCurrency (excluding default Expenses account)
        if (accounts != null) {
            for (Account a : accounts) {
                if (a == null) continue;
                String accCurr = (a.getCurrency() != null && !a.getCurrency().trim().isEmpty())
                        ? a.getCurrency().trim().toUpperCase(Locale.US) : "USD";
                if (curr.equalsIgnoreCase(accCurr)) {
                    String accName = a.getName() != null ? a.getName().trim() : "";
                    String lowerAccName = accName.toLowerCase(Locale.US);

                    // Exclude default/automatically generated "Expenses" or "Expense" accounts
                    if (lowerAccName.equalsIgnoreCase("expenses") || lowerAccName.equalsIgnoreCase("expense")) {
                        continue;
                    }

                    double bal = a.getBalance();
                    if (bal > 0) {
                        breakdown.accountAmount += bal;
                        Double prev = breakdown.sourcesMap.get(accName);
                        double prevVal = (prev != null) ? prev : 0.0;
                        breakdown.sourcesMap.put(accName, prevVal + bal);
                    }
                }
            }
        }

        // 2. Cash In Transactions: Include specific incoming entries (selected category or custom name)
        if (transactions != null) {
            for (Transaction t : transactions) {
                if (t == null) continue;
                String txCurr = (t.getCurrency() != null && !t.getCurrency().trim().isEmpty())
                        ? t.getCurrency().trim().toUpperCase(Locale.US) : "USD";

                if (curr.equalsIgnoreCase(txCurr) && t.isCashIn()) {
                    String title = t.getTitle() != null ? t.getTitle().trim() : "";
                    String lowerTitle = title.toLowerCase(Locale.US);

                    // Exclude transfers
                    if (lowerTitle.startsWith("transfer") || lowerTitle.contains("transfer from") || lowerTitle.contains("transfer to")) {
                        continue;
                    }

                    // Exclude generic "Income", "Monthly Income", "Cash In" titles to prevent double counting "Income" as an input source
                    if (lowerTitle.equalsIgnoreCase("income") ||
                        lowerTitle.equalsIgnoreCase("monthly income") ||
                        lowerTitle.equalsIgnoreCase("cash in")) {
                        continue;
                    }

                    double amt = t.getAmount();

                    // Display Name rule: Selected category or exact manually entered name
                    String displaySource;
                    if (title.isEmpty()) {
                        if (t.getNotes() != null && !t.getNotes().trim().isEmpty()) {
                            displaySource = t.getNotes().trim();
                        } else {
                            continue; // Skip generic entry with no specific source name
                        }
                    } else {
                        displaySource = title;
                    }

                    if (lowerTitle.contains("salary")) {
                        breakdown.salaryAmount += amt;
                    } else if (lowerTitle.contains("bonus")) {
                        breakdown.bonusAmount += amt;
                    } else {
                        breakdown.cashInAmount += amt;
                    }

                    Double prev = breakdown.sourcesMap.get(displaySource);
                    double prevVal = (prev != null) ? prev : 0.0;
                    breakdown.sourcesMap.put(displaySource, prevVal + amt);
                }
            }
        }

        return breakdown;
    }

    /**
     * Convenience method to load accounts and transactions from context and calculate total for currency.
     */
    public static IncomeBreakdown calculateIncomeForCurrency(Context context, String currency) {
        List<Account> accounts = BalanceManager.loadAccounts(context);
        List<Transaction> transactions = TransactionDbHelper.getInstance(context).getAllTransactionsAscending();
        return calculateIncomeForCurrency(currency, accounts, transactions);
    }
}
