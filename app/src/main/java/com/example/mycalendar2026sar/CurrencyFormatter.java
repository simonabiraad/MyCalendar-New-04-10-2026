package com.example.mycalendar2026sar;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CurrencyFormatter {

    /**
     * Formats a raw double amount for display with period thousands separators.
     * Examples: 1000 -> "1.000", 10000 -> "10.000", 250000000 -> "250.000.000"
     */
    public static String formatRawAmount(double amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        if (amount == (long) amount) {
            DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
            return formatter.format((long) amount);
        } else {
            DecimalFormatSymbols decSymbols = new DecimalFormatSymbols(Locale.US);
            decSymbols.setGroupingSeparator('.');
            decSymbols.setDecimalSeparator(',');
            DecimalFormat formatter = new DecimalFormat("#,##0.##", decSymbols);
            return formatter.format(amount);
        }
    }

    public static String formatLbpAmount(double amount) {
        return formatRawAmount(amount);
    }

    public static String formatAmount(double amount, String currency) {
        String suffix = (currency != null && !currency.trim().isEmpty()) ? " " + currency.trim() : "";
        return formatRawAmount(amount) + suffix;
    }

    public static String formatLiveText(String input, String currency) {
        if (input == null || input.isEmpty()) return "";
        String clean = input.replaceAll("[^0-9,.]", "");
        if (clean.isEmpty()) return "";

        String intPart;
        String decPart = "";

        if (clean.contains(",")) {
            int commaIdx = clean.indexOf(',');
            intPart = clean.substring(0, commaIdx).replaceAll("[^0-9]", "");
            decPart = "," + clean.substring(commaIdx + 1).replaceAll("[^0-9]", "");
        } else if (clean.contains(".") && clean.lastIndexOf('.') != clean.indexOf('.')) {
            intPart = clean.replaceAll("[^0-9]", "");
        } else if (clean.contains(".")) {
            int dotIdx = clean.indexOf('.');
            String afterDot = clean.substring(dotIdx + 1);
            if (afterDot.length() <= 2) {
                intPart = clean.substring(0, dotIdx).replaceAll("[^0-9]", "");
                decPart = "." + afterDot;
            } else {
                intPart = clean.replaceAll("[^0-9]", "");
            }
        } else {
            intPart = clean.replaceAll("[^0-9]", "");
        }

        String formattedInt = formatDigitsWithPeriod(intPart);
        return formattedInt + decPart;
    }

    /**
     * Safely parses formatted amount strings containing period thousands separators.
     * Examples: "1.000" -> 1000.0, "10.000" -> 10000.0, "250.000.000" -> 250000000.0, "10.000,50" -> 10000.50
     */
    public static double parseAmount(String input) {
        if (input == null || input.trim().isEmpty()) return 0.0;
        String str = input.trim();

        str = str.replaceAll("[^0-9,.]", "");
        if (str.isEmpty()) return 0.0;

        if (str.contains(",")) {
            String cleanInt = str.substring(0, str.indexOf(',')).replace(".", "");
            String cleanDec = str.substring(str.indexOf(',') + 1).replace(".", "");
            str = cleanInt + "." + cleanDec;
        } else {
            int firstDot = str.indexOf('.');
            int lastDot = str.lastIndexOf('.');
            if (firstDot != -1 && firstDot != lastDot) {
                str = str.replace(".", "");
            } else if (firstDot != -1) {
                String afterDot = str.substring(firstDot + 1);
                if (afterDot.length() == 3) {
                    str = str.replace(".", "");
                }
            }
        }

        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Attaches an automatic live TextWatcher to an EditText field to format amounts with period thousands separators while typing.
     */
    public static void addAmountTextWatcher(final EditText editText) {
        if (editText == null) return;

        editText.addTextChangedListener(new TextWatcher() {
            private String current = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString();
                if (input.equals(current)) return;

                int selectionStart = editText.getSelectionStart();
                int originalLength = input.length();

                String clean = input.replaceAll("[^0-9,.]", "");
                if (clean.isEmpty()) {
                    current = "";
                    editText.setText("");
                    return;
                }

                String intPart;
                String decPart = "";

                if (clean.contains(",")) {
                    int commaIdx = clean.indexOf(',');
                    intPart = clean.substring(0, commaIdx).replaceAll("[^0-9]", "");
                    decPart = "," + clean.substring(commaIdx + 1).replaceAll("[^0-9]", "");
                } else if (clean.contains(".") && clean.lastIndexOf('.') != clean.indexOf('.')) {
                    intPart = clean.replaceAll("[^0-9]", "");
                } else if (clean.contains(".")) {
                    int dotIdx = clean.indexOf('.');
                    String afterDot = clean.substring(dotIdx + 1);
                    if (afterDot.length() <= 2) {
                        intPart = clean.substring(0, dotIdx).replaceAll("[^0-9]", "");
                        decPart = "." + afterDot;
                    } else {
                        intPart = clean.replaceAll("[^0-9]", "");
                    }
                } else {
                    intPart = clean.replaceAll("[^0-9]", "");
                }

                String formattedInt = formatDigitsWithPeriod(intPart);
                String formatted = formattedInt + decPart;

                current = formatted;
                editText.setText(formatted);

                int newLength = formatted.length();
                int newCursor = selectionStart + (newLength - originalLength);
                if (newCursor < 0) newCursor = 0;
                if (newCursor > formatted.length()) newCursor = formatted.length();
                editText.setSelection(newCursor);
            }
        });
    }

    private static String formatDigitsWithPeriod(String digits) {
        if (digits == null || digits.isEmpty()) return "";
        try {
            long val = Long.parseLong(digits);
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
            symbols.setGroupingSeparator('.');
            DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
            return formatter.format(val);
        } catch (NumberFormatException e) {
            return formatHugeDigits(digits, '.');
        }
    }

    private static String formatHugeDigits(String digits, char separator) {
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(separator);
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }
}
