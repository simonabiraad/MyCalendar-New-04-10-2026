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

    public static final String KEY_NORMAL_MAIN_THEME = "color_normal_main_theme";
    public static final String KEY_NEON_MAIN_THEME = "color_neon_main_theme";

    /**
     * Checks if Neon Color mode is currently active.
     */
    public static boolean isNeonMode(Context context) {
        if (context == null) return false;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        return "neon".equalsIgnoreCase(colorPrefs.getString(KEY_COLOR_STYLE, "normal"));
    }

    /**
     * Sets the global color style mode ("normal" or "neon")
     * and automatically restores that mode's last saved accent color.
     */
    public static void setColorStyleMode(Context context, String styleMode) {
        if (context == null) return;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);

        boolean isNeon = "neon".equalsIgnoreCase(styleMode);
        colorPrefs.edit().putString(KEY_COLOR_STYLE, isNeon ? "neon" : "normal").apply();

        int defaultNormal = ContextCompat.getColor(context, R.color.light_green);
        int defaultNeon = 0xFF00E5FF; // Default Neon Blue

        if (isNeon) {
            int savedNeon = colorPrefs.getInt(KEY_NEON_MAIN_THEME, defaultNeon);
            colorPrefs.edit().putInt(KEY_MAIN_THEME, savedNeon).apply();
        } else {
            int savedNormal = colorPrefs.getInt(KEY_NORMAL_MAIN_THEME, defaultNormal);
            colorPrefs.edit().putInt(KEY_MAIN_THEME, savedNormal).apply();
        }
    }

    /**
     * Gets the global main accent color set in "Main Theme (Buttons/Title)".
     * Independent for Normal Mode vs. Neon Mode.
     */
    public static int getMainAccentColor(Context context) {
        if (context == null) return 0xFF4CAF50;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        int defaultNormal = ContextCompat.getColor(context, R.color.light_green);
        int defaultNeon = 0xFF00E5FF;

        if (isNeonMode(context)) {
            int savedNeon = colorPrefs.getInt(KEY_NEON_MAIN_THEME, 0);
            if (savedNeon != 0) return savedNeon;
            int baseColor = colorPrefs.getInt(KEY_MAIN_THEME, defaultNeon);
            return toNeonColor(baseColor);
        } else {
            int savedNormal = colorPrefs.getInt(KEY_NORMAL_MAIN_THEME, 0);
            if (savedNormal != 0) return savedNormal;
            return colorPrefs.getInt(KEY_MAIN_THEME, defaultNormal);
        }
    }

    /**
     * Sets the global main accent color.
     */
    public static void setMainAccentColor(Context context, int color) {
        if (context == null) return;
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        colorPrefs.edit().putInt(KEY_MAIN_THEME, color).apply();

        if (isNeonMode(context)) {
            colorPrefs.edit().putInt(KEY_NEON_MAIN_THEME, color).apply();
        } else {
            colorPrefs.edit().putInt(KEY_NORMAL_MAIN_THEME, color).apply();
        }
    }

    /**
     * Gets the centralized application background color:
     * - When Neon Mode is ON: Always returns BLACK (#000000).
     * - When Dark Mode is ON: Returns BLACK (#000000) or saved app background.
     * - When Light Mode is ON: Returns Light Grey (#CFCFCF).
     */
    public static int getAppBackgroundColor(Context context) {
        if (context == null) return 0xFF000000;
        if (isNeonMode(context) || isDarkMode(context)) {
            return 0xFF000000;
        }
        SharedPreferences colorPrefs = context.getSharedPreferences(COLOR_PREFS_NAME, Context.MODE_PRIVATE);
        int defaultBg = ContextCompat.getColor(context, R.color.app_bg);
        int savedBg = colorPrefs.getInt("color_app_background", defaultBg);
        return (!colorPrefs.contains("color_app_background") || savedBg == android.graphics.Color.BLACK) ? defaultBg : savedBg;
    }

    /**
     * Gets the centralized card background color:
     * - When Neon Mode is ON: Returns Dark Black Card (#1A1A1A).
     * - When Dark Mode is ON: Returns Dark Card (#1A1A1A).
     * - When Light Mode is ON: Returns Light Card (#E0E0E0).
     */
    public static int getCardBackgroundColor(Context context) {
        if (context == null) return 0xFF1A1A1A;
        if (isNeonMode(context) || isDarkMode(context)) {
            return 0xFF1A1A1A;
        }
        return ContextCompat.getColor(context, R.color.card_bg);
    }

    /**
     * Gets the primary text color:
     * - When Neon Mode or Dark Mode is ON: Returns WHITE (#FFFFFF).
     * - When Light Mode is ON: Returns BLACK (#000000).
     */
    public static int getTextPrimaryColor(Context context) {
        if (context == null) return 0xFFFFFFFF;
        if (isNeonMode(context) || isDarkMode(context)) {
            return 0xFFFFFFFF;
        }
        return ContextCompat.getColor(context, R.color.white);
    }

    /**
     * Checks if interface icons should be monochrome black:
     * Returns true ONLY when in Light Mode (Dark Mode is OFF and Neon Mode is OFF).
     */
    public static boolean isLightModeMonochromeIcons(Context context) {
        if (context == null) return false;
        return !isDarkMode(context) && !isNeonMode(context);
    }

    /**
     * Tints an ImageView, ImageButton, or MaterialButton icon to Black in Light Mode,
     * or restores/clears tint in Dark Mode / Neon Mode.
     */
    public static void applyIconThemeTint(View iconView, Context context) {
        if (iconView == null || context == null) return;

        boolean isLightMonochrome = isLightModeMonochromeIcons(context);

        if (iconView instanceof android.widget.ImageView) {
            android.widget.ImageView iv = (android.widget.ImageView) iconView;
            if (isLightMonochrome) {
                iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF000000));
            } else {
                iv.setImageTintList(null);
            }
        } else if (iconView instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton mb = (com.google.android.material.button.MaterialButton) iconView;
            if (isLightMonochrome) {
                mb.setIconTint(android.content.res.ColorStateList.valueOf(0xFF000000));
            } else {
                mb.setIconTint(null);
            }
        }
    }

    /**
     * Recursively traverses a View hierarchy and applies the global icon theme rule to all ImageViews / ImageButtons.
     */
    public static void applyIconThemeToViewTree(View root, Context context) {
        if (root == null || context == null) return;

        if (root instanceof android.widget.ImageView) {
            applyIconThemeTint(root, context);
        } else if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyIconThemeToViewTree(vg.getChildAt(i), context);
            }
        }
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
            applyIconThemeToViewTree(window.getDecorView(), context);
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
