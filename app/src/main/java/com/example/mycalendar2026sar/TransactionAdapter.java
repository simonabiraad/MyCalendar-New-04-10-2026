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

public class TransactionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnTransactionLongClickListener {
        void onLongClick(Transaction transaction);
    }

    public interface OnTransactionClickListener {
        void onClick(Transaction transaction);
    }

    public interface OnTransactionEditListener {
        void onEdit(Transaction transaction);
    }

    public interface OnTransactionDeleteListener {
        void onDelete(Transaction transaction);
    }

    private List<TransactionListItem> items = new ArrayList<>();
    private final OnTransactionLongClickListener longClickListener;
    private OnTransactionClickListener clickListener;
    private OnTransactionEditListener editListener;
    private OnTransactionDeleteListener deleteListener;
    private final SwipeRevealHelper swipeRevealHelper = new SwipeRevealHelper();

    public TransactionAdapter(OnTransactionLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTransactionEditListener(OnTransactionEditListener listener) {
        this.editListener = listener;
    }

    public void setOnTransactionDeleteListener(OnTransactionDeleteListener listener) {
        this.deleteListener = listener;
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

            int themeTextColor = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.white);
            int themeSecondaryColor = androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary);

            rowHolder.title.setText(displayTitle);
            rowHolder.title.setTextColor(themeTextColor);
            rowHolder.time.setText(DateFormat.format("hh:mm a", transaction.getTimestamp()));
            rowHolder.time.setTextColor(themeSecondaryColor);

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
            rowHolder.balance.setTextColor(themeTextColor);

            rowHolder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onLongClick(transaction);
                }
                return true;
            });

            if (rowHolder.foregroundContent != null && rowHolder.swipeActionsContainer != null) {
                swipeRevealHelper.attachSwipe(
                    rowHolder.foregroundContent,
                    rowHolder.swipeActionsContainer,
                    rowHolder.btnSwipeEdit,
                    rowHolder.btnSwipeDelete,
                    () -> {
                        if (editListener != null) {
                            editListener.onEdit(transaction);
                        }
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> {
                        if (deleteListener != null) {
                            deleteListener.onDelete(transaction);
                        }
                        return kotlin.Unit.INSTANCE;
                    },
                    () -> {
                        if (clickListener != null) {
                            clickListener.onClick(transaction);
                        }
                        return kotlin.Unit.INSTANCE;
                    }
                );
            }
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView headerText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerTitle);
        }
    }

    public static class RowViewHolder extends RecyclerView.ViewHolder {
        public View foregroundContent;
        public View swipeActionsContainer;
        public View btnSwipeEdit;
        public View btnSwipeDelete;
        TextView title;
        TextView time;
        TextView cashIn;
        TextView cashOut;
        TextView balance;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            foregroundContent = itemView.findViewById(R.id.foregroundContent);
            swipeActionsContainer = itemView.findViewById(R.id.swipeActionsContainer);
            btnSwipeEdit = itemView.findViewById(R.id.btnSwipeEdit);
            btnSwipeDelete = itemView.findViewById(R.id.btnSwipeDelete);
            title = itemView.findViewById(R.id.transactionTitle);
            time = itemView.findViewById(R.id.transactionTime);
            cashIn = itemView.findViewById(R.id.transactionCashIn);
            cashOut = itemView.findViewById(R.id.transactionCashOut);
            balance = itemView.findViewById(R.id.transactionBalance);
        }
    }
}
