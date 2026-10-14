package com.example.mycalendar2026sar;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class ColorPickerDialog extends Dialog {

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    private final OnColorSelectedListener listener;
    private int currentColor;
    private final float[] hsv = new float[3];
    private boolean isUpdating = false;

    private View previewView;
    private EditText hexInput;
    private SeekBar hueSeekBar, satSeekBar, valSeekBar;
    private SeekBar redSeekBar, greenSeekBar, blueSeekBar;
    private TextView hueText, satText, valText;
    private TextView redText, greenText, blueText;
    private GradientDrawable satDrawable, valDrawable;

    public ColorPickerDialog(@NonNull Context context, int initialColor, OnColorSelectedListener listener) {
        super(context, R.style.CustomAlertDialogTheme);
        this.currentColor = initialColor;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_color_picker);

        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        previewView = findViewById(R.id.colorPreview);
        hexInput = findViewById(R.id.hexInput);

        hueSeekBar = findViewById(R.id.hueSeekBar);
        satSeekBar = findViewById(R.id.satSeekBar);
        valSeekBar = findViewById(R.id.valSeekBar);

        redSeekBar = findViewById(R.id.redSeekBar);
        greenSeekBar = findViewById(R.id.greenSeekBar);
        blueSeekBar = findViewById(R.id.blueSeekBar);

        hueText = findViewById(R.id.hueText);
        satText = findViewById(R.id.satText);
        valText = findViewById(R.id.valText);

        redText = findViewById(R.id.redText);
        greenText = findViewById(R.id.greenText);
        blueText = findViewById(R.id.blueText);

        setupHueGradient();
        setupSatValGradients();

        Color.colorToHSV(currentColor, hsv);

        setupListeners();
        setupPresets();
        setupNeonPresetsUI();

        updateAllFromColor(currentColor, false);

        TextView btnModeNormal = findViewById(R.id.btnModeNormal);
        TextView btnModeNeon = findViewById(R.id.btnModeNeon);

        Runnable updateColorModeUI = () -> {
            boolean isNeon = ThemeManager.isNeonMode(getContext());
            int accent = ThemeManager.getMainAccentColor(getContext());
            int inactiveText = Color.parseColor("#888888");

            if (btnModeNormal != null) {
                btnModeNormal.setBackgroundResource(!isNeon ? R.drawable.bg_period_selected : 0);
                btnModeNormal.setBackgroundTintList(!isNeon ? android.content.res.ColorStateList.valueOf(accent) : null);
                btnModeNormal.setTextColor(!isNeon ? Color.BLACK : inactiveText);
            }
            if (btnModeNeon != null) {
                btnModeNeon.setBackgroundResource(isNeon ? R.drawable.bg_period_selected : 0);
                btnModeNeon.setBackgroundTintList(isNeon ? android.content.res.ColorStateList.valueOf(accent) : null);
                btnModeNeon.setTextColor(isNeon ? Color.BLACK : inactiveText);
            }
        };

        updateColorModeUI.run();

        if (btnModeNormal != null) {
            btnModeNormal.setOnClickListener(v -> {
                ThemeManager.setColorStyleMode(getContext(), "normal");
                updateColorModeUI.run();
                setupPresets();
                setupNeonPresetsUI();
                int restoredColor = ThemeManager.getMainAccentColor(getContext());
                updateAllFromColor(restoredColor, false);
            });
        }

        if (btnModeNeon != null) {
            btnModeNeon.setOnClickListener(v -> {
                ThemeManager.setColorStyleMode(getContext(), "neon");
                updateColorModeUI.run();
                setupPresets();
                setupNeonPresetsUI();
                int restoredColor = ThemeManager.getMainAccentColor(getContext());
                updateAllFromColor(restoredColor, false);
            });
        }

        int mainTheme = ThemeManager.getMainAccentColor(getContext());
        TextView cancelBtn = findViewById(R.id.btnCancel);
        if (cancelBtn != null) cancelBtn.setTextColor(mainTheme);
        TextView saveBtn = findViewById(R.id.btnSave);
        if (saveBtn != null) saveBtn.setTextColor(mainTheme);

        findViewById(R.id.btnCancel).setOnClickListener(v -> dismiss());
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            ThemeManager.setMainAccentColor(getContext(), currentColor);
            if (listener != null) {
                listener.onColorSelected(currentColor);
            }
            dismiss();
        });
    }

    private void setupHueGradient() {
        int[] rainbow = new int[]{
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        GradientDrawable hueDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, rainbow);
        hueDrawable.setCornerRadius(12f);
        hueSeekBar.setProgressDrawable(hueDrawable);
    }

    private void setupSatValGradients() {
        satDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFFFFF, currentColor});
        satDrawable.setCornerRadius(12f);
        satSeekBar.setProgressDrawable(satDrawable);

        valDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF000000, currentColor});
        valDrawable.setCornerRadius(12f);
        valSeekBar.setProgressDrawable(valDrawable);
    }

    private void updateSatValGradientBackgrounds() {
        float[] pureHueHsv = new float[]{hsv[0], 1.0f, 1.0f};
        int pureHueColor = Color.HSVToColor(pureHueHsv);

        if (satDrawable != null) {
            satDrawable.setColors(new int[]{0xFFFFFFFF, pureHueColor});
        }
        if (valDrawable != null) {
            valDrawable.setColors(new int[]{0xFF000000, pureHueColor});
        }
    }

    private void setupListeners() {
        SeekBar.OnSeekBarChangeListener hsvListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || isUpdating) return;
                hsv[0] = hueSeekBar.getProgress();
                hsv[1] = satSeekBar.getProgress() / 100f;
                hsv[2] = valSeekBar.getProgress() / 100f;

                int color = Color.HSVToColor(hsv);
                updateAllFromColor(color, true);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        hueSeekBar.setOnSeekBarChangeListener(hsvListener);
        satSeekBar.setOnSeekBarChangeListener(hsvListener);
        valSeekBar.setOnSeekBarChangeListener(hsvListener);

        SeekBar.OnSeekBarChangeListener rgbListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || isUpdating) return;
                int r = redSeekBar.getProgress();
                int g = greenSeekBar.getProgress();
                int b = blueSeekBar.getProgress();

                int color = Color.rgb(r, g, b);
                updateAllFromColor(color, true);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        redSeekBar.setOnSeekBarChangeListener(rgbListener);
        greenSeekBar.setOnSeekBarChangeListener(rgbListener);
        blueSeekBar.setOnSeekBarChangeListener(rgbListener);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                String hex = s.toString().trim();
                if (hex.startsWith("#")) hex = hex.substring(1);
                if (hex.length() == 6 || hex.length() == 8) {
                    try {
                        int parsed = Color.parseColor("#" + hex);
                        updateAllFromColor(parsed, true);
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    public static class NeonPreset {
        public final String name;
        public final int color;
        public NeonPreset(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    public static final NeonPreset[] NEON_PRESETS = new NeonPreset[]{
            new NeonPreset("Blue & Black Neon", 0xFF00E5FF),
            new NeonPreset("Purple & Black Neon", 0xFFDF00FF),
            new NeonPreset("Yellow & Black Neon", 0xFFFFE500),
            new NeonPreset("Cyan & Black Neon", 0xFF00F5D4),
            new NeonPreset("Pink & Black Neon", 0xFFFF0055),
            new NeonPreset("Green & Black Neon", 0xFF39FF14)
    };

    private void setupNeonPresetsUI() {
        LinearLayout container = findViewById(R.id.neonPresetsGrid);
        if (container == null) return;
        container.removeAllViews();

        float density = getContext().getResources().getDisplayMetrics().density;
        int paddingPx = (int) (8 * density);
        int marginPx = (int) (4 * density);
        int swatchSizePx = (int) (24 * density);

        for (NeonPreset preset : NEON_PRESETS) {
            boolean isSelected = (currentColor == preset.color);

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(paddingPx * 2, paddingPx, paddingPx * 2, paddingPx);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, marginPx, 0, marginPx);
            row.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(12 * density);
            if (isSelected) {
                shape.setColor(0x33FFFFFF);
                shape.setStroke((int) (2 * density), preset.color);
            } else {
                shape.setColor(0x1A1A1A1A);
                shape.setStroke((int) (1 * density), 0x33FFFFFF);
            }
            row.setBackground(shape);

            // Swatch
            View swatch = new View(getContext());
            LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(swatchSizePx, swatchSizePx);
            swatchLp.setMarginEnd((int) (12 * density));
            swatch.setLayoutParams(swatchLp);

            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(preset.color);
            circle.setStroke((int) (1 * density), Color.WHITE);
            swatch.setBackground(circle);

            // Name
            TextView nameTv = new TextView(getContext());
            nameTv.setText(preset.name);
            nameTv.setTextColor(isSelected ? preset.color : Color.WHITE);
            nameTv.setTextSize(14);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameTv.setLayoutParams(nameLp);

            // Selection Indicator
            TextView checkTv = new TextView(getContext());
            checkTv.setText(isSelected ? "✓" : "");
            checkTv.setTextColor(preset.color);
            checkTv.setTextSize(16);
            checkTv.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(swatch);
            row.addView(nameTv);
            row.addView(checkTv);

            row.setOnClickListener(v -> {
                ThemeManager.setColorStyleMode(getContext(), "neon");
                updateAllFromColor(preset.color, false);
                setupNeonPresetsUI();
            });

            container.addView(row);
        }
    }

    private void updateAllFromColor(int color, boolean fromUserEdit) {
        isUpdating = true;
        currentColor = color;
        Color.colorToHSV(color, hsv);

        if (previewView != null) {
            previewView.setBackgroundColor(color);
        }

        String hexStr = String.format("#%06X", (0xFFFFFF & color));
        if (!fromUserEdit || !hexInput.hasFocus()) {
            hexInput.setText(hexStr);
        }

        hueSeekBar.setProgress((int) hsv[0]);
        satSeekBar.setProgress((int) (hsv[1] * 100));
        valSeekBar.setProgress((int) (hsv[2] * 100));

        hueText.setText("Hue: " + (int) hsv[0] + "°");
        satText.setText("Saturation: " + (int) (hsv[1] * 100) + "%");
        valText.setText("Brightness: " + (int) (hsv[2] * 100) + "%");

        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        redSeekBar.setProgress(r);
        greenSeekBar.setProgress(g);
        blueSeekBar.setProgress(b);

        redText.setText("Red: " + r);
        greenText.setText("Green: " + g);
        blueText.setText("Blue: " + b);

        updateSatValGradientBackgrounds();

        isUpdating = false;
    }

    private void setupPresets() {
        LinearLayout presetsContainer = findViewById(R.id.presetsContainer);
        if (presetsContainer == null) return;

        int[] presetColors = new int[]{
                0xFF2E7D32, // Friendly Green
                0xFF4CAF50, // Vibrant Green
                0xFF8BC34A, // Light Green
                0xFF1976D2, // Friendly Blue
                0xFF2196F3, // Vibrant Blue
                0xFF0288D1, // Light Blue
                0xFF7B1FA2, // Friendly Purple
                0xFF9C27B0, // Vibrant Purple
                0xFF00796B, // Friendly Teal
                0xFF009688, // Vibrant Teal
                0xFFE65100, // Friendly Orange
                0xFFFF9800, // Vibrant Orange
                0xFFC21807, // Friendly Red
                0xFFE53935, // Vibrant Red
                0xFFFF8F00, // Friendly Amber
                0xFFFFC107, // Vibrant Gold
                0xFF3F51B5, // Indigo
                0xFFE91E63, // Pink
                0xFF795548, // Brown
                0xFF9E9E9E, // Gray
                0xFFFFFFFF, // White
                0xFF000000  // Black
        };

        presetsContainer.removeAllViews();
        int size = (int) (32 * getContext().getResources().getDisplayMetrics().density);
        int margin = (int) (4 * getContext().getResources().getDisplayMetrics().density);

        for (int c : presetColors) {
            int finalColor = ThemeManager.isNeonMode(getContext()) ? ThemeManager.toNeonColor(c) : c;
            View swatch = new View(getContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(finalColor);
            drawable.setCornerRadius(size / 2f);
            drawable.setStroke((int) (1 * getContext().getResources().getDisplayMetrics().density), 0x66FFFFFF);
            swatch.setBackground(drawable);

            swatch.setOnClickListener(v -> updateAllFromColor(finalColor, false));
            presetsContainer.addView(swatch);
        }
    }
}
