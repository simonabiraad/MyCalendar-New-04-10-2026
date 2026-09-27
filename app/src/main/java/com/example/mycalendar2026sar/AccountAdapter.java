package com.example.mycalendar2026sar;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {

    private List<Account> accounts;
    private List<Account> filteredAccounts;
    private OnAccountClickListener listener;
    private boolean editMode = false;

    public interface OnAccountClickListener {
        void onAccountClick(Account account);
        void onDeleteClick(Account account, int position);
        void onEditClick(Account account, int position);
        void onListChanged();
    }

    public AccountAdapter(List<Account> accounts, OnAccountClickListener listener) {
        this.accounts = accounts;
        this.filteredAccounts = new ArrayList<>(accounts);
        this.listener = listener;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        Account account = filteredAccounts.get(position);
        holder.nameText.setText(account.getName());
        holder.balanceText.setText(String.format(java.util.Locale.US, "%,.2f %s", account.getBalance(), account.getCurrency()));

        int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
        if (holder.btnDelete != null) {
            holder.btnDelete.setColorFilter(accent);
        }

        if (editMode) {
            holder.btnMoveUp.setVisibility(View.VISIBLE);
            holder.btnMoveDown.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.icon.setVisibility(View.GONE);
        } else {
            holder.btnMoveUp.setVisibility(View.GONE);
            holder.btnMoveDown.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.icon.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (editMode) {
                listener.onEditClick(account, position);
            } else {
                listener.onAccountClick(account);
            }
        });

        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(account, position));

        holder.btnMoveUp.setOnClickListener(v -> {
            if (position > 0) {
                int originalIndex = accounts.indexOf(account);
                int prevOriginalIndex = accounts.indexOf(filteredAccounts.get(position - 1));
                Collections.swap(accounts, originalIndex, prevOriginalIndex);
                Collections.swap(filteredAccounts, position, position - 1);
                notifyItemMoved(position, position - 1);
                listener.onListChanged();
            }
        });

        holder.btnMoveDown.setOnClickListener(v -> {
            if (position < filteredAccounts.size() - 1) {
                int originalIndex = accounts.indexOf(account);
                int nextOriginalIndex = accounts.indexOf(filteredAccounts.get(position + 1));
                Collections.swap(accounts, originalIndex, nextOriginalIndex);
                Collections.swap(filteredAccounts, position, position + 1);
                notifyItemMoved(position, position + 1);
                listener.onListChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredAccounts.size();
    }

    public void filter(String query) {
        filteredAccounts.clear();
        if (query.isEmpty()) {
            filteredAccounts.addAll(accounts);
        } else {
            for (Account account : accounts) {
                if (account.getName().toLowerCase().contains(query.toLowerCase())) {
                    filteredAccounts.add(account);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void updateList(List<Account> newList) {
        this.accounts = newList;
        this.filteredAccounts = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView balanceText;
        View icon;
        ImageButton btnMoveUp, btnMoveDown, btnDelete;

        public AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.accountName);
            balanceText = itemView.findViewById(R.id.accountBalance);
            icon = itemView.findViewById(R.id.accountIcon);
            btnMoveUp = itemView.findViewById(R.id.btnMoveUp);
            btnMoveDown = itemView.findViewById(R.id.btnMoveDown);
            btnDelete = itemView.findViewById(R.id.btnDeleteAccount);
        }
    }
}
