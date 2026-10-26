package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.widget.Button;

import androidx.core.content.ContextCompat;

public class BottomNavigationHelper {

    public static void setupBottomNavigation(final Activity activity, int activeTabId) {
        Button homeBtn = activity.findViewById(R.id.navHomeButton);
        Button eventBtn = activity.findViewById(R.id.navEventButton);
        Button taskBtn = activity.findViewById(R.id.navTaskButton);
        Button secureBtn = activity.findViewById(R.id.navSecureBoxButton);
        Button expensesBtn = activity.findViewById(R.id.navExpensesButton);

        if (homeBtn == null) return;

        int unselectedColor = ContextCompat.getColor(activity, R.color.text_primary);
        int activeAccent = ThemeManager.getMainAccentColor(activity);

        homeBtn.setTextColor(unselectedColor);
        eventBtn.setTextColor(unselectedColor);
        taskBtn.setTextColor(unselectedColor);
        secureBtn.setTextColor(unselectedColor);
        expensesBtn.setTextColor(unselectedColor);

        // Set active tab color to selected Main Theme accent color
        if (activeTabId == R.id.navHomeButton) homeBtn.setTextColor(activeAccent);
        else if (activeTabId == R.id.navEventButton) eventBtn.setTextColor(activeAccent);
        else if (activeTabId == R.id.navTaskButton) taskBtn.setTextColor(activeAccent);
        else if (activeTabId == R.id.navSecureBoxButton) secureBtn.setTextColor(activeAccent);
        else if (activeTabId == R.id.navExpensesButton) expensesBtn.setTextColor(activeAccent);

        boolean isLightMode = ThemeManager.isLightModeMonochromeIcons(activity);
        ColorStateList blackTint = ColorStateList.valueOf(0xFF000000);

        if (homeBtn instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton hb = (com.google.android.material.button.MaterialButton) homeBtn;
            hb.setIconTint(isLightMode ? blackTint : null);

            ((com.google.android.material.button.MaterialButton) eventBtn).setIconTint(isLightMode ? blackTint : null);
            ((com.google.android.material.button.MaterialButton) taskBtn).setIconTint(isLightMode ? blackTint : null);
            ((com.google.android.material.button.MaterialButton) secureBtn).setIconTint(isLightMode ? blackTint : null);
            ((com.google.android.material.button.MaterialButton) expensesBtn).setIconTint(isLightMode ? blackTint : null);
        }

        homeBtn.setOnClickListener(v -> {
            if (!(activity instanceof MainActivity)) {
                Intent intent = new Intent(activity, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
            }
        });

        eventBtn.setOnClickListener(v -> {
            if (!(activity instanceof EventsActivity)) {
                Intent intent = new Intent(activity, EventsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
            }
        });

        taskBtn.setOnClickListener(v -> {
            if (!(activity instanceof TaskActivity)) {
                Intent intent = new Intent(activity, TaskActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
            }
        });

        secureBtn.setOnClickListener(v -> {
            if (!(activity instanceof SecureBoxActivity)) {
                Intent intent = new Intent(activity, SecureBoxActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
            }
        });

        expensesBtn.setOnClickListener(v -> {
            if (!(activity instanceof ExpensesActivity)) {
                SecurityHelper.authenticateIfExpensesProtected(activity, () -> {
                    Intent intent = new Intent(activity, ExpensesActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    activity.startActivity(intent);
                });
            }
        });
    }
}
