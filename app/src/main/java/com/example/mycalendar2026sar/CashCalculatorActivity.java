package com.example.mycalendar2026sar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CashCalculatorActivity extends AppCompatActivity {

    private TextView headerTotalDisplay, footerTotalCount, footerSubTotalAmount, footerGrandTotalAmount, txtOnlineStatus;
    private View onlineStatusIndicator, dotOnline, cardOnlineStatus, onlineAmountRow;
    private EditText editOnlineAmount;
    private LinearLayout dynamicRowsContainer;
    
    private final Map<Double, Integer> rowData = new HashMap<>();
    private final Map<Double, View> rowViews = new HashMap<>();
    private List<DenomManager.Denomination> denominations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cash_calculator);

        headerTotalDisplay = findViewById(R.id.headerTotalDisplay);
        footerTotalCount = findViewById(R.id.footerTotalCount);
        footerSubTotalAmount = findViewById(R.id.footerSubTotalAmount);
        footerGrandTotalAmount = findViewById(R.id.footerGrandTotalAmount);
        onlineStatusIndicator = findViewById(R.id.onlineStatusIndicator);
        dynamicRowsContainer = findViewById(R.id.dynamicRowsContainer);
        
        cardOnlineStatus = findViewById(R.id.cardOnlineStatus);
        dotOnline = findViewById(R.id.dotOnline);
        txtOnlineStatus = findViewById(R.id.txtOnlineStatus);
        onlineAmountRow = findViewById(R.id.onlineAmountRow);
        editOnlineAmount = findViewById(R.id.editOnlineAmount);

        findViewById(R.id.cashCalcBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnCashCalcDelete).setOnClickListener(v -> resetAll());
        findViewById(R.id.btnCashCalcShare).setOnClickListener(v -> shareBreakdown());
        findViewById(R.id.cashCalcMoreButton).setOnClickListener(this::showMoreMenu);
        
        cardOnlineStatus.setOnClickListener(v -> toggleOnlineStatus());

        editOnlineAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                calculateGrandTotal();
            }
        });

        applyColors();
        refreshDenominations();
        updateOnlineStatusIndicator();
    }

    private void toggleOnlineStatus() {
        SharedPreferences prefs = getSharedPreferences("SpeechSettings", MODE_PRIVATE);
        boolean current = prefs.getBoolean("is_online", false);
        boolean next = !current;
        prefs.edit().putBoolean("is_online", next).apply();
        updateOnlineStatusIndicator();
        Toast.makeText(this, next ? "Online" : "Offline", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        refreshDenominations();
        updateOnlineStatusIndicator();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);

        ImageButton backBtn = findViewById(R.id.cashCalcBackButton);
        if (backBtn != null) {
            backBtn.setImageTintList(ColorStateList.valueOf(accent));
        }

        View shareBtn = findViewById(R.id.btnCashCalcShare);
        if (shareBtn != null) {
            shareBtn.setBackgroundTintList(ColorStateList.valueOf(accent));
        }

        applyStrokeColor(findViewById(R.id.mainCalcBorderContainer), accent, 1);
        applyStrokeColor(findViewById(R.id.cardOnlineStatus), accent, 1);
        applyStrokeColor(findViewById(R.id.headerTotalDisplay), accent, 1);
        applyStrokeColor(findViewById(R.id.editOnlineAmount), accent, 1);

        applyTopBorderColor(findViewById(R.id.subtotalRow), accent);
        applyTopBorderColor(findViewById(R.id.grandTotalRow), accent);
    }

    private void applyStrokeColor(View view, int strokeColor, int strokeWidthDp) {
        if (view != null && view.getBackground() != null) {
            Drawable bg = view.getBackground().mutate();
            if (bg instanceof GradientDrawable) {
                int strokeWidthPx = (int) (strokeWidthDp * getResources().getDisplayMetrics().density);
                ((GradientDrawable) bg).setStroke(strokeWidthPx, strokeColor);
            }
        }
    }

    private void applyTopBorderColor(View view, int accentColor) {
        if (view != null && view.getBackground() != null) {
            Drawable bg = view.getBackground().mutate();
            if (bg instanceof LayerDrawable) {
                LayerDrawable ld = (LayerDrawable) bg;
                if (ld.getNumberOfLayers() > 0) {
                    Drawable item = ld.getDrawable(0);
                    if (item instanceof GradientDrawable) {
                        int strokeWidthPx = (int) (1 * getResources().getDisplayMetrics().density);
                        ((GradientDrawable) item).setStroke(strokeWidthPx, accentColor);
                    } else {
                        item.setTint(accentColor);
                    }
                }
            }
        }
    }

    private void refreshDenominations() {
        String countryCode = CountryManager.getSelectedCountry(this).code;
        denominations = DenomManager.getDenominations(this, countryCode);
        dynamicRowsContainer.removeAllViews();
        rowViews.clear();
        
        int accent = ThemeManager.getMainAccentColor(this);

        for (DenomManager.Denomination d : denominations) {
            if (!d.enabled) continue;
            
            View row = LayoutInflater.from(this).inflate(R.layout.item_cash_denom_row, dynamicRowsContainer, false);
            dynamicRowsContainer.addView(row);
            rowViews.put(d.value, row);

            TextView txtDenom = row.findViewById(R.id.txtDenomination);
            txtDenom.setText(formatValue(d.value));

            EditText editCount = row.findViewById(R.id.editCount);
            TextView txtRowTotal = row.findViewById(R.id.txtRowTotal);
            Button btnPlus = row.findViewById(R.id.btnPlus);
            Button btnMinus = row.findViewById(R.id.btnMinus);

            btnPlus.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnMinus.setBackgroundTintList(ColorStateList.valueOf(accent));
            applyStrokeColor(editCount, accent, 1);

            Integer currentQty = rowData.get(d.value);
            int existingQty = (currentQty != null) ? currentQty : 0;
            if (existingQty > 0) editCount.setText(String.valueOf(existingQty));
            txtRowTotal.setText(formatValue(existingQty * d.value));

            editCount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    int val = 0;
                    try {
                        if (s.length() > 0) val = Integer.parseInt(s.toString());
                    } catch (NumberFormatException ignored) {}
                    rowData.put(d.value, val);
                    txtRowTotal.setText(formatValue(val * d.value));
                    calculateGrandTotal();
                }
            });

            btnPlus.setOnClickListener(v -> {
                Integer current = rowData.get(d.value);
                int qty = (current != null) ? current : 0;
                editCount.setText(String.valueOf(qty + 1));
            });

            btnMinus.setOnClickListener(v -> {
                Integer current = rowData.get(d.value);
                int qty = (current != null) ? current : 0;
                if (qty > 0) editCount.setText(String.valueOf(qty - 1));
            });
        }
        calculateGrandTotal();
    }

    private void calculateGrandTotal() {
        double cashSubTotal = 0;
        int totalQty = 0;
        for (Map.Entry<Double, Integer> entry : rowData.entrySet()) {
            boolean found = false;
            for(DenomManager.Denomination d : denominations) {
                if (d.value == entry.getKey() && d.enabled) {
                    found = true;
                    break;
                }
            }
            if (found) {
                cashSubTotal += entry.getValue() * entry.getKey();
                totalQty += entry.getValue();
            }
        }

        double onlineAmount = 0;
        String onlineStr = editOnlineAmount.getText().toString();
        if (!onlineStr.isEmpty()) {
            try {
                onlineAmount = Double.parseDouble(onlineStr);
            } catch (NumberFormatException ignored) {}
        }

        double grandTotal = cashSubTotal + onlineAmount;

        headerTotalDisplay.setText(formatValue(grandTotal));
        footerTotalCount.setText(String.valueOf(totalQty));
        footerSubTotalAmount.setText(formatValue(cashSubTotal));
        footerGrandTotalAmount.setText(formatValue(grandTotal));
    }

    private String formatValue(double value) {
        if (value == (long) value) {
            return String.format(Locale.US, "%,d", (long) value);
        } else {
            return String.format(Locale.US, "%,.2f", value);
        }
    }

    private void updateOnlineStatusIndicator() {
        boolean isOnline = getSharedPreferences("SpeechSettings", MODE_PRIVATE)
                .getBoolean("is_online", false);
        
        int statusDrawable = isOnline ? R.drawable.status_dot_on : R.drawable.status_dot_off;

        // Update the card UI
        dotOnline.setBackgroundResource(statusDrawable);
        txtOnlineStatus.setText(isOnline ? "Online" : "Offline");

        // Update the top toolbar indicator
        onlineStatusIndicator.setBackgroundResource(statusDrawable);

        // Update the bottom Online section visibility
        onlineAmountRow.setVisibility(isOnline ? View.VISIBLE : View.GONE);
        if (!isOnline) {
            editOnlineAmount.setText("");
        }
        calculateGrandTotal();
    }

    private void showMoreMenu(View v) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
        popup.getMenuInflater().inflate(R.menu.menu_cash_calculator, popup.getMenu());

        // Force show icons
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
            if (id == R.id.action_add_remove_denom) {
                startActivity(new Intent(this, AddRemoveCurrencyActivity.class));
                return true;
            } else if (id == R.id.action_select_currency) {
                showSelectCountryDialog();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showSelectCountryDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_select_country, null);
        View selectorBox = view.findViewById(R.id.countrySelectorBox);
        TextView txtFlag = view.findViewById(R.id.txtSelectedFlag);
        TextView txtName = view.findViewById(R.id.txtSelectedCountryName);

        CountryManager.Country current = CountryManager.getSelectedCountry(this);
        txtFlag.setText(current.flag);
        txtName.setText(current.name);

        final CountryManager.Country[] selected = {current};

        selectorBox.setOnClickListener(v -> {
            showCountryListDialog(newCountry -> {
                selected[0] = newCountry;
                txtFlag.setText(newCountry.flag);
                txtName.setText(newCountry.name);
            });
        });

        ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(view)
                .setPositiveButton("OK", (dialog, which) -> {
                    CountryManager.saveSelectedCountry(this, selected[0]);
                    Toast.makeText(this, "Currency set to " + selected[0].currency, Toast.LENGTH_SHORT).show();
                    
                    // Force refresh denominations immediately
                    rowData.clear();
                    refreshDenominations();
                    updateOnlineStatusIndicator();
                })
                .setNegativeButton("Cancel", null), this);
    }

    private void showCountryListDialog(java.util.function.Consumer<CountryManager.Country> callback) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_country_list, null);
        EditText searchInput = view.findViewById(R.id.searchCountry);
        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.countryRecyclerView);

        List<CountryManager.Country> allCountries = CountryManager.getCountries();
        CountryAdapter adapter = new CountryAdapter(allCountries, callback);
        rv.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rv.setAdapter(adapter);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(view)
                .create();

        adapter.setDialog(dialog);

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                adapter.filter(s.toString());
            }
        });

        dialog.setOnShowListener(d -> ThemeManager.styleDialogButtons(dialog, this));
        dialog.show();
    }

    private class CountryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<CountryAdapter.ViewHolder> {
        private final List<CountryManager.Country> fullList;
        private List<CountryManager.Country> filteredList;
        private final java.util.function.Consumer<CountryManager.Country> callback;
        private androidx.appcompat.app.AlertDialog dialog;

        CountryAdapter(List<CountryManager.Country> list, java.util.function.Consumer<CountryManager.Country> callback) {
            this.fullList = list;
            this.filteredList = new ArrayList<>(list);
            this.callback = callback;
        }

        void setDialog(androidx.appcompat.app.AlertDialog dialog) { this.dialog = dialog; }

        void filter(String query) {
            filteredList = new ArrayList<>();
            for (CountryManager.Country c : fullList) {
                if (c.name.toLowerCase().contains(query.toLowerCase()) || c.code.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(c);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CountryManager.Country c = filteredList.get(position);
            holder.flag.setText(c.flag);
            holder.name.setText(String.format(Locale.US, "%s (%s)", c.name, c.code));
            holder.itemView.setOnClickListener(v -> {
                callback.accept(c);
                if (dialog != null) dialog.dismiss();
            });
        }

        @Override public int getItemCount() { return filteredList.size(); }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            TextView flag, name;
            ViewHolder(View v) {
                super(v);
                flag = v.findViewById(R.id.txtCountryFlag);
                name = v.findViewById(R.id.txtCountryName);
            }
        }
    }

    private void resetAll() {
        rowData.clear();
        refreshDenominations();
        Toast.makeText(this, "Reset complete", Toast.LENGTH_SHORT).show();
    }

    private void shareBreakdown() {
        StringBuilder sb = new StringBuilder("Cash Calculator Breakdown:\n\n");
        double total = 0;
        List<Double> keys = new ArrayList<>(rowViews.keySet());
        keys.sort(Collections.reverseOrder());

        for (double denom : keys) {
            Integer q = rowData.get(denom);
            int qty = (q != null) ? q : 0;
            if (qty > 0) {
                double rowTotal = (double) qty * denom;
                sb.append(String.format(Locale.US, "%s x %d = %s\n", formatValue(denom), qty, formatValue(rowTotal)));
                total += rowTotal;
            }
        }
        
        double onlineAmount = 0;
        String onlineStr = editOnlineAmount.getText().toString();
        if (!onlineStr.isEmpty()) {
            try {
                onlineAmount = Double.parseDouble(onlineStr);
                sb.append(String.format(Locale.US, "\nOnline: %s\n", formatValue(onlineAmount)));
            } catch (NumberFormatException ignored) {}
        }

        sb.append("\nTotal: ").append(formatValue(total + onlineAmount));

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, "Share via"));
    }
}
