package com.example.mycalendar2026sar;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private Button expenseToggle, incomeToggle;
    private boolean isExpenseView = true;
    private boolean isSelectionMode = false;
    private CategoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        isSelectionMode = getIntent().getBooleanExtra("selection_mode", false);
        isExpenseView = getIntent().getBooleanExtra("is_expense", true);

        recyclerView = findViewById(R.id.categoryRecyclerView);
        expenseToggle = findViewById(R.id.categoryExpenseToggle);
        incomeToggle = findViewById(R.id.categoryIncomeToggle);
        androidx.appcompat.widget.SearchView searchView = findViewById(R.id.categorySearchView);

        findViewById(R.id.categoryBackButton).setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        updateList();

        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null) adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null) adapter.filter(newText);
                return true;
            }
        });

        expenseToggle.setOnClickListener(v -> {
            if (!isExpenseView) {
                isExpenseView = true;
                updateToggleStyles();
                updateList();
            }
        });

        incomeToggle.setOnClickListener(v -> {
            if (isExpenseView) {
                isExpenseView = false;
                updateToggleStyles();
                updateList();
            }
        });
    }

    private void updateToggleStyles() {
        if (isExpenseView) {
            expenseToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_green));
            incomeToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
        } else {
            expenseToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
            incomeToggle.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.light_green));
        }
    }

    private void updateList() {
        List<CategoryItem> items = new ArrayList<>();
        items.add(new CategoryItem("Other", "➕")); // Add Other at top
        if (isExpenseView) {
            addExpenseItems(items);
        } else {
            addIncomeItems(items);
        }
        adapter = new CategoryAdapter(items, item -> {
            String finalTitle = item.name;
            if ("Other".equals(item.name)) {
                android.widget.EditText nameInput = new android.widget.EditText(this);
                nameInput.setHint("Enter Name");
                ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Custom Category")
                        .setView(nameInput)
                        .setPositiveButton("Next", (d, w) -> {
                            String customName = nameInput.getText().toString().trim();
                            if (!customName.isEmpty()) {
                                if (isSelectionMode) {
                                    returnResult(customName);
                                } else {
                                    showAmountDialog(customName);
                                }
                            }
                        })
                        .setNegativeButton("Cancel", null), this);
            } else {
                if (isSelectionMode) {
                    returnResult(finalTitle);
                } else {
                    showAmountDialog(finalTitle);
                }
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private void returnResult(String category) {
        Intent data = new Intent();
        data.putExtra("category", category);
        setResult(RESULT_OK, data);
        finish();
    }

    private void showAmountDialog(String title) {
        android.widget.EditText amountInput = new android.widget.EditText(this);
        amountInput.setHint("0.00");
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        
        ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Enter Amount for " + title)
                .setView(amountInput)
                .setPositiveButton("Save", (d, w) -> {
                    String amountStr = amountInput.getText().toString().trim();
                    if (!amountStr.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(amountStr.replace(",", ""));
                            String account = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                                    .getString("ActiveAccount", "Expenses");
                            String type = isExpenseView ? Transaction.TYPE_CASH_OUT : Transaction.TYPE_CASH_IN;
                            
                            TransactionDbHelper.getInstance(this).addTransaction(
                                    title, amount, type, System.currentTimeMillis(), account);
                            
                            android.widget.Toast.makeText(this, "Saved to " + account, android.widget.Toast.LENGTH_SHORT).show();
                            finish(); // Return to all view
                        } catch (NumberFormatException ignored) {}
                    }
                })
                .setNegativeButton("Cancel", null), this);
    }

    private void addExpenseItems(List<CategoryItem> items) {
        int[] resIds = {
            R.string.cat_air_tickets, R.string.cat_auto_rickshaw, R.string.cat_bike, R.string.cat_bills,
            R.string.cat_cable_tv, R.string.cat_car, R.string.cat_car_insurance, R.string.cat_card_fee,
            R.string.cat_cigarette, R.string.cat_clothes, R.string.cat_drinks, R.string.cat_driver,
            R.string.cat_durables, R.string.cat_education, R.string.cat_electricity, R.string.cat_emi,
            R.string.cat_entertainment, R.string.cat_fast_food, R.string.cat_festivals, R.string.cat_fitness,
            R.string.cat_food, R.string.cat_fruits_vegetables, R.string.cat_fuel, R.string.cat_furniture,
            R.string.cat_gas, R.string.cat_gifts, R.string.cat_groceries, R.string.cat_health,
            R.string.cat_health_insurance, R.string.cat_hobby, R.string.cat_home_insurance,
            R.string.cat_house_hold_expenses, R.string.cat_insurance, R.string.cat_internet,
            R.string.cat_investment_expense, R.string.cat_kids, R.string.cat_laundry, R.string.cat_maid_servant,
            R.string.cat_medicine, R.string.cat_milk, R.string.cat_mobile, R.string.cat_other_expense,
            R.string.cat_parking, R.string.cat_party, R.string.cat_personal_grooming, R.string.cat_pet,
            R.string.cat_rent, R.string.cat_repair_maintenance, R.string.cat_restaurant_hotel,
            R.string.cat_savings, R.string.cat_shopping, R.string.cat_social, R.string.cat_stationery,
            R.string.cat_taxes, R.string.cat_taxi, R.string.cat_toiletries, R.string.cat_toll,
            R.string.cat_toys, R.string.cat_transportation, R.string.cat_vacation, R.string.cat_water
        };
        for (int id : resIds) {
            String name = getString(id);
            items.add(new CategoryItem(name, getCategoryEmoji(name)));
        }
    }

    private void addIncomeItems(List<CategoryItem> items) {
        int[] resIds = {
            R.string.cat_salary, R.string.cat_bonus, R.string.cat_business,
            R.string.cat_investment_income, R.string.cat_other_income
        };
        for (int id : resIds) {
            String name = getString(id);
            items.add(new CategoryItem(name, getCategoryEmoji(name)));
        }
    }

    private String getCategoryEmoji(String name) {
        String lower = name.toLowerCase();
        
        // Income
        if (lower.contains("salary")) return "💰";
        if (lower.contains("bonus")) return "🎁";
        if (lower.contains("business")) return "💼";
        if (lower.contains("investment")) return "📈";
        if (lower.contains("income")) return "💵";
        
        // Expenses
        if (lower.contains("air tickets") || lower.contains("flight")) return "✈️";
        if (lower.contains("auto rickshaw")) return "🛺";
        if (lower.contains("bike")) return "🏍️";
        if (lower.contains("bills")) return "🧾";
        if (lower.contains("cable")) return "📺";
        if (lower.contains("car insurance")) return "🛡️";
        if (lower.contains("car")) return "🚗";
        if (lower.contains("card fee")) return "💳";
        if (lower.contains("cigarette")) return "🚬";
        if (lower.contains("clothes")) return "👕";
        if (lower.contains("drinks")) return "🍺";
        if (lower.contains("driver")) return "👨‍✈️";
        if (lower.contains("durables")) return "📺";
        if (lower.contains("education")) return "📚";
        if (lower.contains("electricity")) return "💡";
        if (lower.contains("emi")) return "💸";
        if (lower.contains("entertainment")) return "🎬";
        if (lower.contains("fast food")) return "🍕";
        if (lower.contains("festivals")) return "🏮";
        if (lower.contains("fitness")) return "🏋️";
        if (lower.contains("fruits")) return "🍎";
        if (lower.contains("fuel")) return "⛽";
        if (lower.contains("furniture")) return "🛋️";
        if (lower.contains("gas")) return "🔥";
        if (lower.contains("gifts")) return "🎁";
        if (lower.contains("groceries")) return "🛒";
        if (lower.contains("health insurance")) return "🏥";
        if (lower.contains("health")) return "💊";
        if (lower.contains("hobby")) return "🎨";
        if (lower.contains("home insurance")) return "🏡";
        if (lower.contains("house hold")) return "🏠";
        if (lower.contains("insurance")) return "🛡️";
        if (lower.contains("internet")) return "🌐";
        if (lower.contains("kids")) return "👶";
        if (lower.contains("laundry")) return "🧺";
        if (lower.contains("maid")) return "🧹";
        if (lower.contains("medicine")) return "💊";
        if (lower.contains("milk")) return "🥛";
        if (lower.contains("mobile")) return "📱";
        if (lower.contains("parking")) return "🅿️";
        if (lower.contains("party")) return "🥳";
        if (lower.contains("grooming")) return "✂️";
        if (lower.contains("pet")) return "🐾";
        if (lower.contains("rent")) return "🔑";
        if (lower.contains("repair")) return "🛠️";
        if (lower.contains("restaurant") || lower.contains("food")) return "🍔";
        if (lower.contains("savings")) return "🐷";
        if (lower.contains("shopping")) return "🛍️";
        if (lower.contains("social")) return "🤝";
        if (lower.contains("stationery")) return "✏️";
        if (lower.contains("taxes")) return "🏛️";
        if (lower.contains("taxi")) return "🚕";
        if (lower.contains("toiletries")) return "🧻";
        if (lower.contains("toll")) return "🛣️";
        if (lower.contains("toys")) return "🧸";
        if (lower.contains("transport")) return "🚌";
        if (lower.contains("vacation")) return "🌴";
        if (lower.contains("water")) return "💧";
        
        return "📝";
    }

    private static class CategoryItem {
        String name;
        String emoji;

        CategoryItem(String name, String emoji) {
            this.name = name;
            this.emoji = emoji;
        }
    }

    private static class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
        private final List<CategoryItem> allItems;
        private List<CategoryItem> filteredItems;
        private final OnCategoryClickListener listener;

        interface OnCategoryClickListener {
            void onCategoryClick(CategoryItem item);
        }

        CategoryAdapter(List<CategoryItem> items, OnCategoryClickListener listener) {
            this.allItems = items;
            this.filteredItems = new ArrayList<>(items);
            this.listener = listener;
        }

        void filter(String query) {
            filteredItems = new ArrayList<>();
            if (query.isEmpty()) {
                filteredItems.addAll(allItems);
            } else {
                for (CategoryItem item : allItems) {
                    if (item.name.toLowerCase().contains(query.toLowerCase())) {
                        filteredItems.add(item);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryItem item = filteredItems.get(position);
            holder.nameText.setText(item.name);
            holder.emojiText.setText(item.emoji);
            holder.itemView.setOnClickListener(v -> listener.onCategoryClick(item));
        }

        @Override
        public int getItemCount() {
            return filteredItems.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView emojiText;

            ViewHolder(View view) {
                super(view);
                nameText = view.findViewById(R.id.categoryName);
                emojiText = view.findViewById(R.id.categoryIconText);
            }
        }
    }
}
