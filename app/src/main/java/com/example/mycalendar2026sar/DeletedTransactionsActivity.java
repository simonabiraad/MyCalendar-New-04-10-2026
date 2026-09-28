package com.example.mycalendar2026sar;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * Shows transactions that were deleted from the ledger (soft-deleted, so the
 * data is still in the database) and lets the user restore them or remove
 * them for good.
 */
public class DeletedTransactionsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private TransactionDbHelper dbHelper;
    private DeletedAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deleted_transactions);

        dbHelper = TransactionDbHelper.getInstance(this);
        recyclerView = findViewById(R.id.deletedRecyclerView);
        emptyText = findViewById(R.id.deletedEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.deletedBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.deletedEmptyButton).setOnClickListener(v -> confirmEmptyTrash());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        List<Transaction> deleted = dbHelper.getDeletedTransactions();
        adapter = new DeletedAdapter(deleted);
        recyclerView.setAdapter(adapter);
        boolean empty = deleted.isEmpty();
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void confirmEmptyTrash() {
        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Empty Trash")
                .setMessage("Permanently delete all transactions in the trash? This cannot be undone.")
                .setPositiveButton("Empty Trash", (d, w) -> {
                    dbHelper.emptyDeletedTransactions();
                    loadData();
                    Toast.makeText(this, "Trash emptied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null), this);
    }

    private class DeletedAdapter extends RecyclerView.Adapter<DeletedAdapter.ViewHolder> {
        private final List<Transaction> items;

        DeletedAdapter(List<Transaction> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_deleted_transaction_row, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction t = items.get(position);
            holder.title.setText(t.getTitle());
            holder.date.setText(DateFormat.format("dd MMM yyyy, hh:mm a", t.getTimestamp()));

            String sign = t.isCashIn() ? "+" : "-";
            holder.amount.setText(String.format(Locale.US, "%s%,.2f", sign, t.getAmount()));
            holder.amount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    t.isCashIn() ? R.color.income_green : R.color.expense_red));

            holder.restoreButton.setOnClickListener(v -> {
                dbHelper.restoreTransaction(t.getId());
                loadData();
                Toast.makeText(DeletedTransactionsActivity.this, "Restored", Toast.LENGTH_SHORT).show();
            });

            holder.foreverButton.setOnClickListener(v -> ThemeManager.showDialog(new AlertDialog.Builder(
                    DeletedTransactionsActivity.this, R.style.CustomAlertDialogTheme)
                    .setTitle("Delete Forever")
                    .setMessage("Permanently delete \"" + t.getTitle() + "\"? This cannot be undone.")
                    .setPositiveButton("Delete Forever", (d, w) -> {
                        dbHelper.permanentlyDeleteTransaction(t.getId());
                        loadData();
                    })
                    .setNegativeButton("Cancel", null), DeletedTransactionsActivity.this));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, amount, date;
            View restoreButton, foreverButton;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.deletedRowTitle);
                amount = itemView.findViewById(R.id.deletedRowAmount);
                date = itemView.findViewById(R.id.deletedRowDate);
                restoreButton = itemView.findViewById(R.id.deletedRowRestoreButton);
                foreverButton = itemView.findViewById(R.id.deletedRowForeverButton);
            }
        }
    }
}
