package com.example.mycalendar2026sar;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class AddRemoveCurrencyActivity extends AppCompatActivity {

    private List<DenomManager.Denomination> denomList;
    private DenomAdapter adapter;
    private String countryCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_remove_currency);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddDenom).setOnClickListener(v -> showAddEditDialog(null));

        countryCode = CountryManager.getSelectedCountry(this).code;
        denomList = DenomManager.getDenominations(this, countryCode);
        sortList();

        RecyclerView recyclerView = findViewById(R.id.denomRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DenomAdapter();
        recyclerView.setAdapter(adapter);
    }

    private void sortList() {
        if (denomList != null) {
            denomList.sort((d1, d2) -> Double.compare(d2.value, d1.value));
        }
    }

    private void showAddEditDialog(DenomManager.Denomination item) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 10);

        final EditText input = new EditText(this);
        input.setHint("Value (e.g. 0.50)");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setTextColor(android.graphics.Color.WHITE);
        if (item != null) input.setText(String.valueOf(item.value));
        layout.addView(input);

        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle(item == null ? "Add" : "Edit")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String valStr = input.getText().toString();
                    if (!valStr.isEmpty()) {
                        try {
                            double val = Double.parseDouble(valStr);
                            if (item == null) {
                                denomList.add(new DenomManager.Denomination(val, true));
                            } else {
                                item.value = val;
                            }
                            sortList();
                            DenomManager.saveDenominations(this, countryCode, denomList);
                            adapter.notifyDataSetChanged();
                        } catch (NumberFormatException ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null), this);
    }

    private String formatValue(double value) {
        if (value == (long) value) {
            return String.format(Locale.US, "%,d", (long) value);
        } else {
            return String.format(Locale.US, "%,.2f", value);
        }
    }

    private class DenomAdapter extends RecyclerView.Adapter<DenomAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_denom_setting, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DenomManager.Denomination d = denomList.get(position);
            holder.txtValue.setText(formatValue(d.value));
            holder.sw.setOnCheckedChangeListener(null);
            holder.sw.setChecked(d.enabled);
            ThemeManager.styleSwitch(holder.sw, AddRemoveCurrencyActivity.this);
            holder.sw.setOnCheckedChangeListener((btn, isChecked) -> {
                d.enabled = isChecked;
                DenomManager.saveDenominations(AddRemoveCurrencyActivity.this, countryCode, denomList);
            });
            
            // Allow tap on text to edit as well
            holder.txtValue.setOnClickListener(v -> showAddEditDialog(d));
            
            holder.btnDelete.setOnClickListener(v -> {
                ThemeManager.showDialog(new AlertDialog.Builder(AddRemoveCurrencyActivity.this, R.style.CustomAlertDialogTheme)
                        .setTitle("Delete")
                        .setMessage("Delete " + formatValue(d.value) + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            denomList.remove(position);
                            DenomManager.saveDenominations(AddRemoveCurrencyActivity.this, countryCode, denomList);
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton("Cancel", null), AddRemoveCurrencyActivity.this);
            });
        }

        @Override public int getItemCount() { return denomList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtValue;
            Switch sw;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                txtValue = v.findViewById(R.id.txtDenomValue);
                sw = v.findViewById(R.id.switchDenom);
                btnDelete = v.findViewById(R.id.btnDeleteDenom);
            }
        }
    }
}
