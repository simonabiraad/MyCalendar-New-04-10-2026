package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

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
