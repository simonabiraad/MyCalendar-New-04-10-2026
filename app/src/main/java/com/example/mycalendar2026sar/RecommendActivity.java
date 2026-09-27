package com.example.mycalendar2026sar;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class RecommendActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommend);

        findViewById(R.id.recommendBackButton).setOnClickListener(v -> finish());
        applyColors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        TextView tipsTitle = findViewById(R.id.tvFinancialTipsTitle);
        if (tipsTitle != null) {
            tipsTitle.setTextColor(accent);
        }
        ImageButton backBtn = findViewById(R.id.recommendBackButton);
        if (backBtn != null) {
            backBtn.setImageTintList(ColorStateList.valueOf(accent));
        }
    }
}
