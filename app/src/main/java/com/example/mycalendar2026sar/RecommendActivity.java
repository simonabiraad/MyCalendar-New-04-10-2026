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

    private void showPurchaseBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_purchase_lifetime, null, false);

        TextView tvEmail = view.findViewById(R.id.tvUserEmail);
        if (tvEmail != null) {
            tvEmail.setText(getUserEmail());
        }

        Button btnAddCard = view.findViewById(R.id.btnAddCard);
        if (btnAddCard != null) {
            int accent = ThemeManager.getMainAccentColor(this);
            btnAddCard.setBackgroundTintList(ColorStateList.valueOf(accent));
            btnAddCard.setOnClickListener(v -> {
                dialog.dismiss();
                startActivity(new Intent(RecommendActivity.this, AddCardActivity.class));
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
            btnPurchaseLifetime.setOnClickListener(v -> showPurchaseBottomSheet());
        }
        if (btnSubscribeMonthlyAuto != null) {
            btnSubscribeMonthlyAuto.setOnClickListener(v -> Toast.makeText(this, "Monthly auto-renewal subscription selected", Toast.LENGTH_SHORT).show());
        }
        if (btnSubscribeMonthlyManual != null) {
            btnSubscribeMonthlyManual.setOnClickListener(v -> Toast.makeText(this, "Monthly manual subscription selected", Toast.LENGTH_SHORT).show());
        }
        if (btnSubscribeYearlyAuto != null) {
            btnSubscribeYearlyAuto.setOnClickListener(v -> Toast.makeText(this, "Yearly auto-renewal subscription selected", Toast.LENGTH_SHORT).show());
        }
        if (btnSubscribeYearlyManual != null) {
            btnSubscribeYearlyManual.setOnClickListener(v -> Toast.makeText(this, "Yearly manual subscription selected", Toast.LENGTH_SHORT).show());
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
