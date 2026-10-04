package com.example.mycalendar2026sar;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTransactionLongClickListener {
        void onLongClick(Transaction transaction);
    }

    public interface OnTransactionClickListener {
        void onClick(Transaction transaction);
    }

    private List<TransactionListItem> items = new ArrayList<>();
    private final OnTransactionLongClickListener longClickListener;
    private OnTransactionClickListener clickListener;

    public TransactionAdapter(OnTransactionLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.clickListener = listener;
    }

    public void updateItems(List<TransactionListItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TransactionListItem.TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_transaction_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_transaction_row, parent, false);
            return new RowViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TransactionListItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.headerText.setText(item.getHeaderText());
            headerHolder.headerText.setTextColor(ThemeManager.getMainAccentColor(holder.itemView.getContext()));
        } else if (holder instanceof RowViewHolder) {
            RowViewHolder rowHolder = (RowViewHolder) holder;
            Transaction transaction = item.getTransaction();
            int mainAccent = ThemeManager.getMainAccentColor(holder.itemView.getContext());

            String displayTitle = transaction.getTitle();
            String notes = transaction.getNotes();

            // Logic: Do not display generic "Cash In" or "Cash Out"
            // Priority: 1. Category/Item (if not generic), 2. Notes, 3. Placeholder
            if (displayTitle.equalsIgnoreCase("Cash In") || displayTitle.equalsIgnoreCase("Cash Out")) {
                if (notes != null && !notes.trim().isEmpty()) {
                    displayTitle = notes;
                } else {
                    displayTitle = "---"; // Placeholder for empty entries
                }
            }

            rowHolder.title.setText(displayTitle);
            rowHolder.time.setText(DateFormat.format("hh:mm a", transaction.getTimestamp()));

            String formattedAmount = CurrencyFormatter.formatAmount(transaction.getAmount(), transaction.getCurrency());

            if (transaction.isCashIn()) {
                rowHolder.cashIn.setText(formattedAmount);
                rowHolder.cashIn.setTextColor(mainAccent);
                rowHolder.cashOut.setText("");
            } else {
                rowHolder.cashOut.setText(formattedAmount);
                rowHolder.cashOut.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.expense_red));
                rowHolder.cashIn.setText("");
            }

            rowHolder.balance.setText(CurrencyFormatter.formatAmount(item.getBalanceAfter(), transaction.getCurrency()));

            rowHolder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(transaction);
                }
            });

            rowHolder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(transaction);
                }
                return true;
            });
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerTitle);
        }
    }

    static class RowViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView time;
        TextView cashIn;
        TextView cashOut;
        TextView balance;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.transactionTitle);
            time = itemView.findViewById(R.id.transactionTime);
            cashIn = itemView.findViewById(R.id.transactionCashIn);
            cashOut = itemView.findViewById(R.id.transactionCashOut);
            balance = itemView.findViewById(R.id.transactionBalance);
        }
    }
}
