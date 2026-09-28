package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

public class ThemeManager {

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";

    public static final String COLOR_PREFS_NAME = "AppColors";
    public static final String KEY_MAIN_THEME = "color_main_theme";

    /**
     * Gets the global main accent color set in "Main Theme (Buttons/Title)".
     */
    public static int getMainAccentColor(Context context) {
        if (context == null) return 0xFF4CAF50;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        int defaultColor = ContextCompat.getColor(context, R.color.light_green);
        return colorPrefs.getInt(KEY_MAIN_THEME, defaultColor);
    }

    /**
     * Sets the global main accent color.
     */
    public static void setMainAccentColor(Context context, int color) {
        if (context == null) return;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        colorPrefs.edit().putInt(KEY_MAIN_THEME, color).apply();
    }

    /**
     * Styles the Positive, Negative, and Neutral buttons of an AlertDialog
     * to match the current Main Theme accent color.
     */
    public static void styleDialogButtons(AlertDialog dialog, Context context) {
        if (dialog == null || context == null) return;
        int accent = getMainAccentColor(context);
        Button pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(accent);
        Button neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(accent);
        Button neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(accent);
    }

    public static void styleDialogButtons(android.app.AlertDialog dialog, Context context) {
        if (dialog == null || context == null) return;
        int accent = getMainAccentColor(context);
        Button pos = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        if (pos != null) pos.setTextColor(accent);
        Button neg = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
        if (neg != null) neg.setTextColor(accent);
        Button neu = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
        if (neu != null) neu.setTextColor(accent);
    }

    /**
     * Helper method to show an androidx AlertDialog and automatically style its buttons on show.
     */
    public static AlertDialog showDialog(AlertDialog.Builder builder, Context context) {
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog, context));
        dialog.show();
        return dialog;
    }

    /**
     * Helper method to show an android.app AlertDialog and automatically style its buttons on show.
     */
    public static android.app.AlertDialog showDialog(android.app.AlertDialog.Builder builder, Context context) {
        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog, context));
        dialog.show();
        return dialog;
    }

    /**
     * Applies saved theme on app startup or activity creation.
     */
    public static void applyTheme(Context context) {
        boolean isDark = isDarkMode(context);
        int mode = isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    /**
     * Checks whether Dark Mode is currently active (default true = Dark Mode ON).
     */
    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_DARK_MODE, true); // Default is Dark Mode
    }

    /**
     * Sets Dark Mode state and immediately applies theme globally across activities.
     */
    public static void setDarkMode(Context context, boolean isDark, Activity currentActivity) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply();

        int mode = isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        AppCompatDelegate.setDefaultNightMode(mode);

        if (currentActivity != null && !currentActivity.isFinishing()) {
            currentActivity.recreate();
        }
    }
}
