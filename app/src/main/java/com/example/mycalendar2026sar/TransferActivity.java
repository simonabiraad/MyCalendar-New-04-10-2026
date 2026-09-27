package com.example.mycalendar2026sar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TransferActivity extends AppCompatActivity {

    private Spinner spinnerFrom, spinnerTo;
    private EditText editAmount;
    private Button btnTransfer;
    private TextView txtDate, txtTime;
    private Calendar selectedDateTime = Calendar.getInstance();
    private SimpleDateFormat dateSdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_transfer);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.transfer_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        spinnerFrom = findViewById(R.id.spinnerFromAccount);
        spinnerTo = findViewById(R.id.spinnerToAccount);
        editAmount = findViewById(R.id.editTransferAmount);
        btnTransfer = findViewById(R.id.btnPerformTransfer);
        txtDate = findViewById(R.id.txtTransferDate);
        txtTime = findViewById(R.id.txtTransferTime);

        findViewById(R.id.transferBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.transferDatePickerBox).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.transferTimePickerBox).setOnClickListener(v -> showTimePicker());

        updateDateTimeLabels();

        List<Account> accountList = BalanceManager.loadAccounts(this);
        List<String> accountNames = new ArrayList<>();
        for (Account a : accountList) {
            accountNames.add(a.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, accountNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);

        btnTransfer.setOnClickListener(v -> performTransfer());
        applyColors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        android.view.View backBtn = findViewById(R.id.transferBackButton);
        if (backBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) backBtn).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        if (btnTransfer != null) {
            btnTransfer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        TextView lbl1 = findViewById(R.id.lblDateTime);
        if (lbl1 != null) lbl1.setTextColor(accent);
        TextView lbl2 = findViewById(R.id.lblFromAccount);
        if (lbl2 != null) lbl2.setTextColor(accent);
        TextView lbl3 = findViewById(R.id.lblToAccount);
        if (lbl3 != null) lbl3.setTextColor(accent);
        TextView lbl4 = findViewById(R.id.lblAmount);
        if (lbl4 != null) lbl4.setTextColor(accent);
    }

    private void updateDateTimeLabels() {
        txtDate.setText(dateSdf.format(selectedDateTime.getTime()));
        txtTime.setText(timeSdf.format(selectedDateTime.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateTimeLabels();
        }, selectedDateTime.get(Calendar.YEAR), selectedDateTime.get(Calendar.MONTH), selectedDateTime.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedDateTime.set(Calendar.MINUTE, minute);
            updateDateTimeLabels();
        }, selectedDateTime.get(Calendar.HOUR_OF_DAY), selectedDateTime.get(Calendar.MINUTE), false).show();
    }

    private void performTransfer() {
        String from = (String) spinnerFrom.getSelectedItem();
        String to = (String) spinnerTo.getSelectedItem();
        String amountStr = editAmount.getText().toString().trim();

        if (from == null || to == null) return;
        if (from.equals(to)) {
            Toast.makeText(this, "Source and destination accounts must be different", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Account> accounts = BalanceManager.loadAccounts(this);
        Account fromAccount = null, toAccount = null;
        for (Account a : accounts) {
            if (a.getName().equals(from)) fromAccount = a;
            if (a.getName().equals(to)) toAccount = a;
        }

        if (fromAccount == null || toAccount == null) return;

        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr.replace(",", ""));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (amount <= 0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(this);
        long timestamp = selectedDateTime.getTimeInMillis();

        // If currencies are different, we should ideally ask for both amounts, 
        // but to keep UI exactly same, we use the same amount for both and warn if different.
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            Toast.makeText(this, "Warning: Transferring between different currencies (" 
                    + fromAccount.getCurrency() + " to " + toAccount.getCurrency() + ")", Toast.LENGTH_LONG).show();
        }

        // 1. Transaction: Transfer Out from 'from'
        dbHelper.addTransaction("Transfer to " + to, amount, fromAccount.getCurrency(), Transaction.TYPE_CASH_OUT, timestamp, from, "", "", "");
        BalanceManager.updateAccountBalance(this, from, -amount);

        // 2. Transaction: Transfer In to 'to'
        dbHelper.addTransaction("Transfer from " + from, amount, toAccount.getCurrency(), Transaction.TYPE_CASH_IN, timestamp, to, "", "", "");
        BalanceManager.updateAccountBalance(this, to, amount);

        Toast.makeText(this, "Transfer successful!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
