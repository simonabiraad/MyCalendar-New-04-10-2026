package com.example.mycalendar2026sar;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CountryManager {

    public static class Country {
        public String name;
        public String code; // ISO code
        public String currency;
        public String flag;

        public Country(String name, String code, String currency, String flag) {
            this.name = name;
            this.code = code;
            this.currency = currency;
            this.flag = flag;
        }

        @NonNull
        @Override
        public String toString() {
            return flag + " " + name + " (" + code + ")";
        }
    }

    private static final String PREF_NAME = "CountryPrefs";
    private static final String KEY_COUNTRY_CODE = "selected_country_code";

    public static List<Country> getCountries() {
        List<Country> list = new ArrayList<>();
        // Standard mapping of Country -> Currency
        list.add(new Country("Lebanon", "LB", "LBP", "🇱🇧"));
        list.add(new Country("United States", "US", "USD", "🇺🇸"));
        list.add(new Country("United Kingdom", "GB", "GBP", "🇬🇧"));
        list.add(new Country("European Union", "EU", "EUR", "🇪🇺"));
        list.add(new Country("Afghanistan", "AF", "AFN", "🇦🇫"));
        list.add(new Country("Albania", "AL", "ALL", "🇦🇱"));
        list.add(new Country("Algeria", "DZ", "DZD", "🇩🇿"));
        list.add(new Country("Angola", "AO", "AOA", "🇦🇴"));
        list.add(new Country("Antarctica", "AQ", "AQD", "🇦🇶"));
        list.add(new Country("Argentina", "AR", "ARS", "🇦🇷"));
        list.add(new Country("Armenia", "AM", "AMD", "🇦🇲"));
        list.add(new Country("Aruba", "AW", "AWG", "🇦🇼"));
        list.add(new Country("Australia", "AU", "AUD", "🇦🇺"));
        list.add(new Country("Austria", "AT", "EUR", "🇦🇹"));
        list.add(new Country("Azerbaijan", "AZ", "AZN", "🇦🇿"));
        list.add(new Country("Bahamas", "BS", "BSD", "🇧🇸"));
        list.add(new Country("Bahrain", "BH", "BHD", "🇧🇭"));
        list.add(new Country("Bangladesh", "BD", "BDT", "🇧🇩"));
        list.add(new Country("Barbados", "BB", "BBD", "🇧🇧"));
        list.add(new Country("Belarus", "BY", "BYN", "🇧🇾"));
        list.add(new Country("Belgium", "BE", "EUR", "🇧🇪"));
        list.add(new Country("Brazil", "BR", "BRL", "🇧🇷"));
        list.add(new Country("Canada", "CA", "CAD", "🇨🇦"));
        list.add(new Country("China", "CN", "CNY", "🇨🇳"));
        list.add(new Country("Egypt", "EG", "EGP", "🇪🇬"));
        list.add(new Country("France", "FR", "EUR", "🇫🇷"));
        list.add(new Country("Germany", "DE", "EUR", "🇩🇪"));
        list.add(new Country("India", "IN", "INR", "🇮🇳"));
        list.add(new Country("Iraq", "IQ", "IQD", "🇮🇶"));
        list.add(new Country("Italy", "IT", "EUR", "🇮🇹"));
        list.add(new Country("Japan", "JP", "JPY", "🇯🇵"));
        list.add(new Country("Jordan", "JO", "JOD", "🇯🇴"));
        list.add(new Country("Kuwait", "KW", "KWD", "🇰🇼"));
        list.add(new Country("Qatar", "QA", "QAR", "🇶🇦"));
        list.add(new Country("Russia", "RU", "RUB", "🇷🇺"));
        list.add(new Country("Saudi Arabia", "SA", "SAR", "🇸🇦"));
        list.add(new Country("Spain", "ES", "EUR", "🇪🇸"));
        list.add(new Country("Switzerland", "CH", "CHF", "🇨🇭"));
        list.add(new Country("Syria", "SY", "SYP", "🇸🇾"));
        list.add(new Country("Turkey", "TR", "TRY", "🇹🇷"));
        list.add(new Country("United Arab Emirates", "AE", "AED", "🇦🇪"));

        list.sort((c1, d2) -> c1.name.compareToIgnoreCase(d2.name));
        return list;
    }

    /**
     * Inspects accounts, transactions (Cash In / Cash Out / Expenses), and Money Vault tables to find all actively used currency codes.
     */
    public static Set<String> getUsedCurrencies(Context context) {
        Set<String> used = new LinkedHashSet<>();
        if (context == null) return used;

        // 1. Current selected default country currency
        try {
            String selCurr = getSelectedCurrencySymbol(context);
            if (selCurr != null && !selCurr.trim().isEmpty()) {
                used.add(selCurr.trim().toUpperCase(Locale.US));
            }
        } catch (Exception ignored) {}

        // 2. Currencies in Accounts
        try {
            List<Account> accounts = BalanceManager.loadAccounts(context);
            if (accounts != null) {
                for (Account acc : accounts) {
                    if (acc.getCurrency() != null && !acc.getCurrency().trim().isEmpty()) {
                        used.add(acc.getCurrency().trim().toUpperCase(Locale.US));
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Currencies in Transactions / Expenses / Cash In / Cash Out
        try {
            TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(context);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT DISTINCT " + TransactionDbHelper.COL_CURRENCY + " FROM " + TransactionDbHelper.TABLE_TRANSACTIONS + " WHERE " + TransactionDbHelper.COL_CURRENCY + " IS NOT NULL AND " + TransactionDbHelper.COL_CURRENCY + " != '' AND " + TransactionDbHelper.COL_DELETED + " = 0", null);
            if (c != null) {
                while (c.moveToNext()) {
                    String curr = c.getString(0);
                    if (curr != null && !curr.trim().isEmpty()) {
                        used.add(curr.trim().toUpperCase(Locale.US));
                    }
                }
                c.close();
            }
        } catch (Exception ignored) {}

        // 4. Currencies in Savings Vaults and Planned Payments
        try {
            TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(context);
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor c1 = db.rawQuery("SELECT DISTINCT " + TransactionDbHelper.COL_VAULT_CURRENCY + " FROM " + TransactionDbHelper.TABLE_SAVINGS_VAULTS, null);
            if (c1 != null) {
                while (c1.moveToNext()) {
                    String curr = c1.getString(0);
                    if (curr != null && !curr.trim().isEmpty()) used.add(curr.trim().toUpperCase(Locale.US));
                }
                c1.close();
            }

            Cursor c2 = db.rawQuery("SELECT DISTINCT " + TransactionDbHelper.COL_PP_CURRENCY + " FROM " + TransactionDbHelper.TABLE_PLANNED_PAYMENTS, null);
            if (c2 != null) {
                while (c2.moveToNext()) {
                    String curr = c2.getString(0);
                    if (curr != null && !curr.trim().isEmpty()) used.add(curr.trim().toUpperCase(Locale.US));
                }
                c2.close();
            }
        } catch (Exception ignored) {}

        return used;
    }

    /**
     * Returns the master list of countries with active/used currencies prioritized at the top,
     * ensuring each currency appears uniquely without duplicates.
     */
    public static List<Country> getSortedCountries(Context context) {
        List<Country> master = getCountries();
        Set<String> usedCodes = getUsedCurrencies(context);

        List<Country> prioritized = new ArrayList<>();
        List<Country> remaining = new ArrayList<>();
        Set<String> addedCurrencies = new HashSet<>();

        // Add matching country for each used currency code (first match per currency to prevent duplicates)
        for (String code : usedCodes) {
            if (addedCurrencies.contains(code)) continue;
            Country match = null;
            for (Country c : master) {
                if (code.equalsIgnoreCase(c.currency)) {
                    match = c;
                    break;
                }
            }
            if (match != null) {
                prioritized.add(match);
                addedCurrencies.add(code);
            } else {
                Country customC = new Country(code, code, code, "💱");
                prioritized.add(customC);
                addedCurrencies.add(code);
            }
        }

        // Add remaining countries whose currencies were not prioritized
        for (Country c : master) {
            if (!addedCurrencies.contains(c.currency)) {
                remaining.add(c);
            }
        }

        remaining.sort((c1, c2) -> c1.name.compareToIgnoreCase(c2.name));

        List<Country> result = new ArrayList<>();
        result.addAll(prioritized);
        result.addAll(remaining);
        return result;
    }

    public static void saveSelectedCountry(Context context, Country country) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_COUNTRY_CODE, country.code)
                .apply();
    }

    public static Country getSelectedCountry(Context context) {
        String code = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_COUNTRY_CODE, "LB"); // Default Lebanon
        for (Country c : getCountries()) {
            if (c.code.equals(code)) return c;
        }
        return new Country("Lebanon", "LB", "LBP", "🇱🇧");
    }

    public static String getSelectedCurrencySymbol(Context context) {
        return getSelectedCountry(context).currency;
    }
}
