package com.example.mycalendar2026sar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

public class ThemeManager {

    private static final String PREFS_NAME = "ThemePrefs";
    private static final String KEY_DARK_MODE = "is_dark_mode";

    public static final String COLOR_PREFS_NAME = "AppColors";
    public static final String KEY_MAIN_THEME = "color_main_theme";
    public static final String KEY_COLOR_STYLE = "color_style_mode"; // "normal" or "neon"

    /**
     * Converts any base color into its bright, glowing Neon version.
     */
    public static int toNeonColor(int baseColor) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(baseColor, hsv);

        float hue = hsv[0]; // 0 to 360

        if (hue >= 80 && hue <= 150) {
            // Green -> Neon Green (#39FF14)
            return 0xFF39FF14;
        } else if (hue >= 180 && hue <= 250) {
            // Blue/Cyan -> Neon Blue / Neon Cyan
            if (hue <= 200) {
                return 0xFF00F5D4; // Neon Cyan
            } else {
                return 0xFF00E5FF; // Neon Blue
            }
        } else if (hue >= 40 && hue < 80) {
            // Yellow -> Neon Yellow (#FFE500)
            return 0xFFFFE500;
        } else if (hue >= 15 && hue < 40) {
            // Orange -> Neon Orange (#FF5F00)
            return 0xFFFF5F00;
        } else if (hue >= 260 && hue <= 320) {
            // Purple / Magenta -> Neon Purple (#DF00FF)
            return 0xFFDF00FF;
        } else if (hue < 15 || hue > 330) {
            // Red -> Neon Red (#FF0055)
            return 0xFFFF0055;
        } else {
            hsv[1] = Math.max(hsv[1], 0.9f);
            hsv[2] = 1.0f;
            return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(baseColor), hsv);
        }
    }

    /**
     * Checks if Neon Color mode is currently active.
     */
    public static boolean isNeonMode(Context context) {
        if (context == null) return false;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        return "neon".equalsIgnoreCase(colorPrefs.getString(KEY_COLOR_STYLE, "normal"));
    }

    /**
     * Sets the global color style mode ("normal" or "neon").
     */
    public static void setColorStyleMode(Context context, String styleMode) {
        if (context == null) return;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        colorPrefs.edit().putString(KEY_COLOR_STYLE, styleMode).apply();
    }

    /**
     * Gets the global main accent color set in "Main Theme (Buttons/Title)".
     * Automatically converts to Neon version if Neon mode is active.
     */
    public static int getMainAccentColor(Context context) {
        if (context == null) return 0xFF4CAF50;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        int defaultColor = ContextCompat.getColor(context, R.color.light_green);
        int baseColor = colorPrefs.getInt(KEY_MAIN_THEME, defaultColor);

        if (isNeonMode(context)) {
            return toNeonColor(baseColor);
        }
        return baseColor;
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
     * to match the current Main Theme accent color, and styles any switches inside.
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

        Window window = dialog.getWindow();
        if (window != null && window.getDecorView() != null) {
            styleSwitchesInView(window.getDecorView(), context);
            applyThemeCursorToAllEditTexts(window.getDecorView());
        }
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

        Window window = dialog.getWindow();
        if (window != null && window.getDecorView() != null) {
            styleSwitchesInView(window.getDecorView(), context);
            applyThemeCursorToAllEditTexts(window.getDecorView());
        }
    }

    /**
     * Helper method to show an androidx AlertDialog and automatically style its buttons & switches on show.
     */
    public static AlertDialog showDialog(AlertDialog.Builder builder, Context context) {
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog, context));
        dialog.show();
        return dialog;
    }

    public static AlertDialog showDialog(AlertDialog dialog, Context context) {
        if (dialog == null) return null;
        dialog.setOnShowListener(d -> styleDialogButtons(dialog, context));
        dialog.show();
        return dialog;
    }

    /**
     * Helper method to show an android.app AlertDialog and automatically style its buttons & switches on show.
     */
    public static android.app.AlertDialog showDialog(android.app.AlertDialog.Builder builder, Context context) {
        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog, context));
        dialog.show();
        return dialog;
    }

    /**
     * Styles any Switch / SwitchCompat / CompoundButton to use the active Main Theme color.
     */
    public static void styleSwitch(CompoundButton switchView, Context context) {
        if (switchView == null || context == null) return;
        int accent = getMainAccentColor(context);

        int[][] states = new int[][] {
            new int[] { android.R.attr.state_checked },
            new int[] { -android.R.attr.state_checked }
        };

        int[] thumbColors = new int[] {
            accent,
            0xFF888888
        };

        int trackCheckedColor = (accent & 0x00FFFFFF) | 0x66000000;
        int[] trackColors = new int[] {
            trackCheckedColor,
            0xFF444444
        };

        ColorStateList thumbStateList = new ColorStateList(states, thumbColors);
        ColorStateList trackStateList = new ColorStateList(states, trackColors);

        if (switchView instanceof SwitchCompat) {
            ((SwitchCompat) switchView).setThumbTintList(thumbStateList);
            ((SwitchCompat) switchView).setTrackTintList(trackStateList);
        } else if (switchView instanceof Switch) {
            ((Switch) switchView).setThumbTintList(thumbStateList);
            ((Switch) switchView).setTrackTintList(trackStateList);
        } else {
            switchView.setButtonTintList(thumbStateList);
        }
    }

    /**
     * Recursively traverses a View tree and styles all Switch / SwitchCompat / CompoundButton elements.
     */
    public static void styleSwitchesInView(View view, Context context) {
        if (view == null || context == null) return;
        if (view instanceof CompoundButton) {
            styleSwitch((CompoundButton) view, context);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                styleSwitchesInView(vg.getChildAt(i), context);
            }
        }
    }

    /**
     * Applies cursor and selection handle tinting based on Dark Mode state and Main Theme color:
     * - When Dark Mode is enabled: Cursor & handles are WHITE (#FFFFFF).
     * - When Dark Mode is disabled (normal mode): Cursor & handles follow the color selected in Main Theme.
     */
    public static void applyThemeCursor(android.widget.EditText editText) {
        if (editText == null) return;
        Context context = editText.getContext();
        boolean isDark = isDarkMode(context);
        int cursorColor = isDark ? 0xFFFFFFFF : getMainAccentColor(context);

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.graphics.drawable.GradientDrawable cursorDrawable = new android.graphics.drawable.GradientDrawable();
                cursorDrawable.setColor(cursorColor);
                cursorDrawable.setSize((int) (2 * context.getResources().getDisplayMetrics().density), 0);
                editText.setTextCursorDrawable(cursorDrawable);

                android.graphics.drawable.Drawable hMid = androidx.core.content.ContextCompat.getDrawable(context, isDark ? R.drawable.white_handle : R.drawable.black_handle);
                android.graphics.drawable.Drawable hLeft = androidx.core.content.ContextCompat.getDrawable(context, isDark ? R.drawable.white_handle_left : R.drawable.black_handle_left);
                android.graphics.drawable.Drawable hRight = androidx.core.content.ContextCompat.getDrawable(context, isDark ? R.drawable.white_handle_right : R.drawable.black_handle_right);
                if (hMid != null) {
                    hMid = hMid.mutate();
                    hMid.setTint(cursorColor);
                    editText.setTextSelectHandle(hMid);
                }
                if (hLeft != null) {
                    hLeft = hLeft.mutate();
                    hLeft.setTint(cursorColor);
                    editText.setTextSelectHandleLeft(hLeft);
                }
                if (hRight != null) {
                    hRight = hRight.mutate();
                    hRight.setTint(cursorColor);
                    editText.setTextSelectHandleRight(hRight);
                }
            } else {
                java.lang.reflect.Field f = android.widget.TextView.class.getDeclaredField("mCursorDrawableRes");
                f.setAccessible(true);
                f.set(editText, isDark ? R.drawable.white_cursor : R.drawable.black_cursor);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Recursively traverses a View tree and applies theme-appropriate cursor to all EditTexts.
     */
    public static void applyThemeCursorToAllEditTexts(View view) {
        if (view == null) return;
        if (view instanceof android.widget.EditText) {
            applyThemeCursor((android.widget.EditText) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeCursorToAllEditTexts(vg.getChildAt(i));
            }
        }
    }

    public static void applyBlackCursor(android.widget.EditText editText) {
        applyThemeCursor(editText);
    }

    public static void applyBlackCursorToAllEditTexts(View view) {
        applyThemeCursorToAllEditTexts(view);
    }

    /**
     * Applies saved theme on app startup or activity creation.
     * When Dark Mode is ON, forces Dark Mode (MODE_NIGHT_YES).
     * When Dark Mode is OFF, forces Light / Color Inversion Mode (MODE_NIGHT_NO).
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
