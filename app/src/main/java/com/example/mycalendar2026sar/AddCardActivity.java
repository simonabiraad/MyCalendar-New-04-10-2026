package com.example.mycalendar2026sar;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AddCardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_card);

        findViewById(R.id.addCardBackButton).setOnClickListener(v -> finish());

        EditText etCardNumber = findViewById(R.id.etCardNumber);
        EditText etExpiry = findViewById(R.id.etExpiry);
        EditText etCvc = findViewById(R.id.etCvc);
        Button btnSaveCard = findViewById(R.id.btnSaveCard);

        String userEmail = getUserEmail();
        SharedPreferences savedPrefs = getSharedPreferences("SavedCardsPrefs", MODE_PRIVATE);
        String savedCard = savedPrefs.getString("card_" + userEmail, null);
        if (savedCard != null && etCardNumber != null) {
            etCardNumber.setText(savedCard);
        }

        if (btnSaveCard != null) {
            btnSaveCard.setOnClickListener(v -> {
                String cardNumber = etCardNumber != null ? etCardNumber.getText().toString().trim() : "";
                String expiry = etExpiry != null ? etExpiry.getText().toString().trim() : "";
                String cvc = etCvc != null ? etCvc.getText().toString().trim() : "";

                if (cardNumber.isEmpty() || expiry.isEmpty() || cvc.isEmpty()) {
                    Toast.makeText(this, "Please fill in all required card fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                savedPrefs.edit().putString("card_" + userEmail, cardNumber).apply();
                Toast.makeText(this, "Card saved successfully for " + userEmail, Toast.LENGTH_LONG).show();
                finish();
            });
        }

        applyColors();
    }

    private String getUserEmail() {
        SharedPreferences prefs = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE);
        String email = prefs.getString("UserEmail", "");
        if (email.isEmpty()) {
            try {
                android.accounts.Account[] accounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                if (accounts.length > 0 && accounts[0].name != null) {
                    return accounts[0].name;
                }
            } catch (Exception ignored) {}

            String name = prefs.getString("UserName", "");
            if (!name.isEmpty()) {
                return name.toLowerCase().replaceAll("\\s+", "") + "@gmail.com";
            }
            return "user@gmail.com";
        }
        return email;
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        ImageButton backBtn = findViewById(R.id.addCardBackButton);
        if (backBtn != null) {
            backBtn.setImageTintList(ColorStateList.valueOf(accent));
        }

        Button btnSaveCard = findViewById(R.id.btnSaveCard);
        if (btnSaveCard != null) {
            btnSaveCard.setBackgroundTintList(ColorStateList.valueOf(accent));
        }

        ImageView ivSmallLogo = findViewById(R.id.ivSmallLogo);
        if (ivSmallLogo != null) {
            if (ThemeManager.isDarkMode(this)) {
                ivSmallLogo.setColorFilter(0xFFFF0000, PorterDuff.Mode.SRC_IN);
            } else {
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                ivSmallLogo.setColorFilter(new ColorMatrixColorFilter(matrix));
            }
        }
    }
}
