package com.example.mycalendar2026sar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class RecommendActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        findViewById(R.id.recommendBackButton).setOnClickListener(v -> finish());

        setupClickListeners();
        applyColors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private String getUserEmail() {
        SharedPreferences prefs = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE);
        String email = prefs.getString("UserEmail", "");
        if (email.isEmpty()) {
            try {
                android.accounts.Account[] accounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                if (accounts.length > 0) {
                    email = accounts[0].name;
                }
            } catch (Exception ignored) {}
        }
        if (email.isEmpty()) {
            String name = prefs.getString("UserName", "");
            if (!name.isEmpty()) {
                email = name.toLowerCase().replaceAll("\\s+", "") + "@gmail.com";
            } else {
                email = "user@gmail.com";
            }
        }
        return email;
    }

    private String getTrialCancelDate(int days) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, days);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US);
        return sdf.format(cal.getTime());
    }

    private void showPaymentBottomSheet(String itemTitle, String itemPrice, int trialDays) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_purchase_lifetime, null, false);

        android.view.View root = view.findViewById(R.id.dialogPaymentRoot);
        if (root != null) {
            root.setBackgroundColor(ThemeManager.getCardBackgroundColor(this));
        }

        TextView tvPaymentTitle = view.findViewById(R.id.tvPaymentTitle);
        TextView tvEmail = view.findViewById(R.id.tvUserEmail);
        TextView tvDescription = view.findViewById(R.id.tvPaymentDescription);

        int textPrimary = ThemeManager.getTextPrimaryColor(this);
        if (tvPaymentTitle != null) {
            tvPaymentTitle.setTextColor(textPrimary);
        }

        if (tvEmail != null) {
            tvEmail.setText(getUserEmail());
        }

        if (tvDescription != null) {
            if (trialDays > 0) {
                String cancelDate = getTrialCancelDate(trialDays);
                tvDescription.setText("Add a payment method to your Google Account to start your free trial. You won't be charged if you cancel before " + cancelDate + ".");
            } else {
                tvDescription.setText("Add a payment method to your Google Account to complete your purchase. Your payment information is only visible to Google.");
            }
        }

        Button btnAddCard = view.findViewById(R.id.btnAddCard);
        if (btnAddCard != null) {
            int accent = ThemeManager.getMainAccentColor(this);
            btnAddCard.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnAddCard.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(RecommendActivity.this, AddCardActivity.class);
                intent.putExtra("EXTRA_ITEM_TITLE", itemTitle);
                intent.putExtra("EXTRA_ITEM_PRICE", itemPrice);
                startActivity(intent);
            });
        }

        dialog.setContentView(view);
        dialog.show();
    }

    private void setupClickListeners() {
        Button btnPurchaseLifetime = findViewById(R.id.btnPurchaseLifetime);
        Button btnSubscribeMonthlyAuto = findViewById(R.id.btnSubscribeMonthlyAuto);
        Button btnSubscribeMonthlyManual = findViewById(R.id.btnSubscribeMonthlyManual);
        Button btnSubscribeYearlyAuto = findViewById(R.id.btnSubscribeYearlyAuto);
        Button btnSubscribeYearlyManual = findViewById(R.id.btnSubscribeYearlyManual);
        Button btnCancelSubscription = findViewById(R.id.btnCancelSubscription);
        Button btnWatchAd = findViewById(R.id.btnWatchAd);

        if (btnPurchaseLifetime != null) {
            btnPurchaseLifetime.setOnClickListener(v -> showPaymentBottomSheet("Lifetime (Expenses)", "$5.00", 0));
        }
        if (btnSubscribeMonthlyAuto != null) {
            btnSubscribeMonthlyAuto.setOnClickListener(v -> showPaymentBottomSheet("Monthly Auto Renewal", "$0.50", 3));
        }
        if (btnSubscribeMonthlyManual != null) {
            btnSubscribeMonthlyManual.setOnClickListener(v -> showPaymentBottomSheet("Monthly Manual Renewal", "$0.50", 0));
        }
        if (btnSubscribeYearlyAuto != null) {
            btnSubscribeYearlyAuto.setOnClickListener(v -> showPaymentBottomSheet("Yearly Auto Renewal", "$3.00", 5));
        }
        if (btnSubscribeYearlyManual != null) {
            btnSubscribeYearlyManual.setOnClickListener(v -> showPaymentBottomSheet("Yearly Manual Renewal", "$3.00", 0));
        }
        if (btnCancelSubscription != null) {
            btnCancelSubscription.setOnClickListener(v -> Toast.makeText(this, "No active subscription to cancel", Toast.LENGTH_SHORT).show());
        }
        if (btnWatchAd != null) {
            btnWatchAd.setOnClickListener(v -> Toast.makeText(this, "Loading video ad...", Toast.LENGTH_SHORT).show());
        }
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);

        ImageButton backBtn = findViewById(R.id.recommendBackButton);
        if (backBtn != null) {
            backBtn.setImageTintList(ColorStateList.valueOf(accent));
        }

        TextView tvLifetimeHeader = findViewById(R.id.tvLifetimeHeader);
        TextView tvMonthlyHeader = findViewById(R.id.tvMonthlyHeader);
        TextView tvYearlyHeader = findViewById(R.id.tvYearlyHeader);

        if (tvLifetimeHeader != null) tvLifetimeHeader.setTextColor(0xFFFFFFFF);
        if (tvMonthlyHeader != null) tvMonthlyHeader.setTextColor(0xFFFFFFFF);
        if (tvYearlyHeader != null) tvYearlyHeader.setTextColor(0xFFFFFFFF);

        int[] buttonIds = {
            R.id.btnPurchaseLifetime,
            R.id.btnSubscribeMonthlyAuto,
            R.id.btnSubscribeMonthlyManual,
            R.id.btnSubscribeYearlyAuto,
            R.id.btnSubscribeYearlyManual,
            R.id.btnCancelSubscription,
            R.id.btnWatchAd
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setBackgroundTintList(ColorStateList.valueOf(accent));
            }
        }
    }
}
