package com.example.mycalendar2026sar;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user maintain a list of frequently-used transaction names/payees
 * so they can be reused quickly when adding a new transaction. Backed by the
 * transaction_names table in the shared TransactionDbHelper.
 */
public class TransactionNamesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private NamesAdapter adapter;
    private TransactionDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_names);

        dbHelper = TransactionDbHelper.getInstance(this);
        recyclerView = findViewById(R.id.txNamesRecyclerView);
        emptyText = findViewById(R.id.txNamesEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.txNamesBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.txNamesAddButton).setOnClickListener(v -> showAddDialog());

        SearchView searchView = findViewById(R.id.txNamesSearchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
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

        loadNames();
    }

    private void loadNames() {
        List<TransactionDbHelper.NamedEntry> entries = dbHelper.getAllTransactionNames();
        adapter = new NamesAdapter(entries);
        recyclerView.setAdapter(adapter);
        emptyText.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showAddDialog() {
        EditText input = new EditText(this);
        input.setHint("Name");
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Add Transaction Name")
                .setView(input)
                .setPositiveButton("Add", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        dbHelper.addTransactionName(name);
                        loadNames();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(TransactionDbHelper.NamedEntry entry) {
        EditText input = new EditText(this);
        input.setText(entry.name);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Edit Name")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        dbHelper.updateTransactionName(entry.id, name);
                        loadNames();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(TransactionDbHelper.NamedEntry entry) {
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Delete Name")
                .setMessage("Delete \"" + entry.name + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    dbHelper.deleteTransactionName(entry.id);
                    loadNames();
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class NamesAdapter extends RecyclerView.Adapter<NamesAdapter.ViewHolder> {
        private final List<TransactionDbHelper.NamedEntry> all;
        private List<TransactionDbHelper.NamedEntry> filtered;

        NamesAdapter(List<TransactionDbHelper.NamedEntry> entries) {
            this.all = entries;
            this.filtered = new ArrayList<>(entries);
        }

        void filter(String query) {
            filtered = new ArrayList<>();
            if (query == null || query.isEmpty()) {
                filtered.addAll(all);
            } else {
                for (TransactionDbHelper.NamedEntry e : all) {
                    if (e.name.toLowerCase().contains(query.toLowerCase())) {
                        filtered.add(e);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction_name_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TransactionDbHelper.NamedEntry entry = filtered.get(position);
            holder.text.setText(entry.name);
            int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
            if (holder.editButton instanceof android.widget.ImageButton) {
                ((android.widget.ImageButton) holder.editButton).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
            }
            holder.editButton.setOnClickListener(v -> showEditDialog(entry));
            holder.deleteButton.setOnClickListener(v -> confirmDelete(entry));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            View editButton, deleteButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.nameRowText);
                editButton = itemView.findViewById(R.id.nameRowEditButton);
                deleteButton = itemView.findViewById(R.id.nameRowDeleteButton);
            }
        }
    }
}
