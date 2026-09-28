package com.example.mycalendar2026sar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RatesActivity extends AppCompatActivity {

    private static final int REQ_CODE_LOCATION = 101;

    private View ratesRootLayout;
    private View viewConvertContainer, viewHistoryContainer, viewSettingsContainer;
    private TextView txtConvertTitle, btnSaveRate;
    private View ratesRefreshButton;

    private TextView txtFromLabel, txtToLabel;
    private TextView txtFromCode, txtFromNameFlag;
    private TextView txtToCode, txtToNameFlag;
    private EditText editFromAmount;
    private TextView txtToAmount;
    private TextView txtPairAndRate, txtRateChange, txtStatusInfo;
    private ProgressBar progressBarLoading;
    private LineChart lineChart;

    private TextView btn1D, btn5D, btn1M, btn6M, btn1Y, btnMax;

    // Bottom Nav Buttons
    private Button navConvert, navScan, navHistory, navSettings;

    // History Content Container
    private LinearLayout historyGroupContainer;

    // Settings Views
    private View settingDefaultCurrency, settingLocationPermission, settingLanguage, settingTheme;
    private TextView txtSettingsHeaderTitle, txtDefaultCurrencyTitle, txtLocationPermissionTitle, txtLanguageTitle, txtThemeTitle;
    private TextView txtDefaultCurrencyValue, txtLocationPermissionValue, txtLanguageValue, txtThemeValue;
    private SwitchCompat switchDarkMode;

    private CountryManager.Country fromCountry;
    private CountryManager.Country toCountry;
    private String selectedPeriod = "1M";
    private String currentLanguage = "English";

    private final Map<String, Double> ratesMap = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private static final String PREFS_RATES = "RatesCachePrefs";
    private static final String PREFS_HISTORY = "RatesHistoryPrefs";
    private static final String PREFS_SETTINGS = "RatesSettingsPrefs";
    private static final String KEY_HISTORY_JSON = "conversion_history_json";

    public static class ConversionRecord {
        public String fromCode;
        public String toCode;
        public String fromAmountStr;
        public String toAmountStr;
        public long timestamp;

        public ConversionRecord(String fromCode, String toCode, String fromAmountStr, String toAmountStr, long timestamp) {
            this.fromCode = fromCode;
            this.toCode = toCode;
            this.fromAmountStr = fromAmountStr;
            this.toAmountStr = toAmountStr;
            this.timestamp = timestamp;
        }
    }

    private static final Map<String, Double> DEFAULT_RATES = new HashMap<>();
    static {
        DEFAULT_RATES.put("USD", 1.0);
        DEFAULT_RATES.put("EUR", 0.9148);
        DEFAULT_RATES.put("GBP", 0.7850);
        DEFAULT_RATES.put("JPY", 150.25);
        DEFAULT_RATES.put("CAD", 1.3520);
        DEFAULT_RATES.put("AUD", 1.5180);
        DEFAULT_RATES.put("CHF", 0.8840);
        DEFAULT_RATES.put("CNY", 7.2300);
        DEFAULT_RATES.put("INR", 83.1200);
        DEFAULT_RATES.put("AED", 3.6725);
        DEFAULT_RATES.put("SAR", 3.7500);
        DEFAULT_RATES.put("EGP", 48.5000);
        DEFAULT_RATES.put("LBP", 89500.0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rates);

        ratesRootLayout = findViewById(R.id.ratesRootLayout);
        findViewById(R.id.ratesBackButton).setOnClickListener(v -> handleBackNavigation());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });

        txtConvertTitle = findViewById(R.id.txtConvertTitle);
        btnSaveRate = findViewById(R.id.btnSaveRate);
        ratesRefreshButton = findViewById(R.id.ratesRefreshButton);

        viewConvertContainer = findViewById(R.id.viewConvertContainer);
        viewHistoryContainer = findViewById(R.id.viewHistoryContainer);
        viewSettingsContainer = findViewById(R.id.viewSettingsContainer);
        historyGroupContainer = findViewById(R.id.historyGroupContainer);

        navConvert = findViewById(R.id.navConvert);
        navScan = findViewById(R.id.navScan);
        navHistory = findViewById(R.id.navHistory);
        navSettings = findViewById(R.id.navSettings);

        // Settings Elements
        settingDefaultCurrency = findViewById(R.id.settingDefaultCurrency);
        settingLocationPermission = findViewById(R.id.settingLocationPermission);
        settingLanguage = findViewById(R.id.settingLanguage);
        settingTheme = findViewById(R.id.settingTheme);

        txtSettingsHeaderTitle = findViewById(R.id.txtSettingsHeaderTitle);
        txtDefaultCurrencyTitle = findViewById(R.id.txtDefaultCurrencyTitle);
        txtLocationPermissionTitle = findViewById(R.id.txtLocationPermissionTitle);
        txtLanguageTitle = findViewById(R.id.txtLanguageTitle);
        txtThemeTitle = findViewById(R.id.txtThemeTitle);

        txtDefaultCurrencyValue = findViewById(R.id.txtDefaultCurrencyValue);
        txtLocationPermissionValue = findViewById(R.id.txtLocationPermissionValue);
        txtLanguageValue = findViewById(R.id.txtLanguageValue);
        txtThemeValue = findViewById(R.id.txtThemeValue);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        if (btnSaveRate != null) {
            btnSaveRate.setOnClickListener(v -> {
                recordCurrentConversionAndGoToHistory();
                Toast.makeText(this, "Saved and recorded to history", Toast.LENGTH_SHORT).show();
            });
        }

        if (ratesRefreshButton != null) {
            ratesRefreshButton.setOnClickListener(v -> fetchLiveRatesAndChart());
        }

        txtFromLabel = findViewById(R.id.txtFromLabel);
        txtToLabel = findViewById(R.id.txtToLabel);

        txtFromCode = findViewById(R.id.txtFromCode);
        txtFromNameFlag = findViewById(R.id.txtFromNameFlag);
        txtToCode = findViewById(R.id.txtToCode);
        txtToNameFlag = findViewById(R.id.txtToNameFlag);

        editFromAmount = findViewById(R.id.editFromAmount);
        txtToAmount = findViewById(R.id.txtToAmount);

        txtPairAndRate = findViewById(R.id.txtPairAndRate);
        txtRateChange = findViewById(R.id.txtRateChange);
        txtStatusInfo = findViewById(R.id.txtStatusInfo);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        lineChart = findViewById(R.id.lineChart);

        View cardFrom = findViewById(R.id.cardFromCurrency);
        View cardTo = findViewById(R.id.cardToCurrency);
        View btnSwap = findViewById(R.id.btnSwapCurrency);

        btn1D = findViewById(R.id.btnPeriod1D);
        btn5D = findViewById(R.id.btnPeriod5D);
        btn1M = findViewById(R.id.btnPeriod1M);
        btn6M = findViewById(R.id.btnPeriod6M);
        btn1Y = findViewById(R.id.btnPeriod1Y);
        btnMax = findViewById(R.id.btnPeriodMax);

        List<CountryManager.Country> countries = CountryManager.getCountries();

        SharedPreferences settingsPrefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String savedDefaultCode = settingsPrefs.getString("default_currency_code", "US");

        String lastFromCode = settingsPrefs.getString("last_selected_from_code", "EU");
        String lastToCode = settingsPrefs.getString("last_selected_to_code", savedDefaultCode);

        fromCountry = findCountryByCode(countries, lastFromCode);
        if (fromCountry == null) fromCountry = new CountryManager.Country("European Union", "EU", "EUR", "🇪🇺");

        toCountry = findCountryByCode(countries, lastToCode);
        if (toCountry == null) toCountry = findCountryByCode(countries, savedDefaultCode);
        if (toCountry == null) toCountry = new CountryManager.Country("United States", "US", "USD", "🇺🇸");

        if (cardFrom != null) {
            cardFrom.setOnClickListener(v -> showCountryPicker(c -> {
                fromCountry = c;
                settingsPrefs.edit().putString("last_selected_from_code", c.code).apply();
                updateCurrencyCards();
                fetchLiveRatesAndChart();
            }));
        }

        if (cardTo != null) {
            cardTo.setOnClickListener(v -> showCountryPicker(c -> {
                toCountry = c;
                settingsPrefs.edit().putString("last_selected_to_code", c.code).apply();
                updateCurrencyCards();
                fetchLiveRatesAndChart();
            }));
        }

        if (btnSwap != null) {
            btnSwap.setOnClickListener(v -> {
                CountryManager.Country temp = fromCountry;
                fromCountry = toCountry;
                toCountry = temp;
                settingsPrefs.edit()
                        .putString("last_selected_from_code", fromCountry.code)
                        .putString("last_selected_to_code", toCountry.code)
                        .apply();
                updateCurrencyCards();
                calculateConvertedAmount();
                fetchChartDataForPeriod();
            });
        }

        setupPeriodButtons();
        setupBottomNav();
        setupSettingsHandlers();

        if (editFromAmount != null) {
            editFromAmount.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    calculateConvertedAmount();
                }
            });
        }

        setupChartStyle();
        loadCachedRates();
        updateCurrencyCards();
        fetchLiveRatesAndChart();

        // Detect Location Online if permission already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            detectUserLocationOnline();
        }

        switchToConvertView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdown();
    }

    private void handleBackNavigation() {
        if (viewConvertContainer != null && viewConvertContainer.getVisibility() != View.VISIBLE) {
            switchToConvertView();
        } else {
            finish();
        }
    }

    private void updateBottomNavTextColors(Button activeBtn) {
        int activeColor = ThemeManager.getMainAccentColor(this);
        int inactiveColor = Color.WHITE;

        if (navConvert != null) navConvert.setTextColor(navConvert == activeBtn ? activeColor : inactiveColor);
        if (navScan != null) navScan.setTextColor(navScan == activeBtn ? activeColor : inactiveColor);
        if (navHistory != null) navHistory.setTextColor(navHistory == activeBtn ? activeColor : inactiveColor);
        if (navSettings != null) navSettings.setTextColor(navSettings == activeBtn ? activeColor : inactiveColor);
    }

    private void setupBottomNav() {
        if (navConvert != null) {
            navConvert.setOnClickListener(v -> switchToConvertView());
        }
        if (navScan != null) {
            navScan.setOnClickListener(v -> {
                updateBottomNavTextColors(navScan);
                Toast.makeText(this, "Scan feature coming soon", Toast.LENGTH_SHORT).show();
            });
        }
        if (navHistory != null) {
            navHistory.setOnClickListener(v -> switchToHistoryView());
        }
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> switchToSettingsView());
        }
    }

    private void setupSettingsHandlers() {
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);

        String defCurr = prefs.getString("default_currency", "US Dollar");
        if (txtDefaultCurrencyValue != null) txtDefaultCurrencyValue.setText(defCurr);

        currentLanguage = prefs.getString("language", "English");
        if (txtLanguageValue != null) txtLanguageValue.setText(currentLanguage);
        applySelectedLanguage(currentLanguage);

        boolean isDark = prefs.getBoolean("dark_mode", true);
        if (switchDarkMode != null) {
            ThemeManager.styleSwitch(switchDarkMode, this);
            switchDarkMode.setChecked(isDark);
            if (txtThemeValue != null) txtThemeValue.setText(isDark ? "Dark mode" : "Light mode");
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("dark_mode", isChecked).apply();
                if (txtThemeValue != null) txtThemeValue.setText(isChecked ? "Dark mode" : "Light mode");
                applyThemeMode(isChecked);
            });
        }

        updateLocationPermissionStatus();

        // 1. Default Currency Handler -> Automatically sets To currency field on Convert page!
        if (settingDefaultCurrency != null) {
            settingDefaultCurrency.setOnClickListener(v -> showCountryPicker(country -> {
                String currName = country.name;
                if (currName.equalsIgnoreCase("United States")) currName = "US Dollar";
                if (currName.equalsIgnoreCase("European Union")) currName = "Euro";

                if (txtDefaultCurrencyValue != null) txtDefaultCurrencyValue.setText(currName);
                prefs.edit().putString("default_currency", currName)
                        .putString("default_currency_code", country.code)
                        .putString("last_selected_to_code", country.code)
                        .apply();

                // Apply automatically to To currency field!
                toCountry = country;
                updateCurrencyCards();
                calculateConvertedAmount();
                fetchChartDataForPeriod();
                Toast.makeText(this, "Default Currency set to " + currName, Toast.LENGTH_SHORT).show();
            }));
        }

        // 2. Location Permission Handler -> Detects current location online
        if (settingLocationPermission != null) {
            settingLocationPermission.setOnClickListener(v -> {
                if (isLocationPermissionGranted()) {
                    updateLocationPermissionStatus();
                    detectUserLocationOnline();
                } else {
                    requestLocationPermission();
                }
            });
        }

        // 3. Language Handler -> Switches Rate page language immediately
        if (settingLanguage != null) {
            settingLanguage.setOnClickListener(v -> {
                String[] languages = {"English", "Arabic (العربية)", "French (Français)", "Spanish (Español)", "German (Deutsch)"};
                ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Select Language")
                        .setItems(languages, (dialog, which) -> {
                            String selected = languages[which];
                            currentLanguage = selected;
                            if (txtLanguageValue != null) txtLanguageValue.setText(selected);
                            prefs.edit().putString("language", selected).apply();
                            applySelectedLanguage(selected);
                            Toast.makeText(this, "Language set to " + selected, Toast.LENGTH_SHORT).show();
                        }), this);
            });
        }
    }

    private boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateLocationPermissionStatus() {
        if (txtLocationPermissionValue == null) return;
        if (isLocationPermissionGranted()) {
            if (txtLocationPermissionValue.getText() == null || !txtLocationPermissionValue.getText().toString().startsWith("Allow only while using app")) {
                txtLocationPermissionValue.setText("Allow only while using app");
            }
        } else {
            txtLocationPermissionValue.setText("Not granted (Tap to allow)");
        }
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Location Permission Required")
                    .setMessage("Location permission is needed to automatically detect your country and currency.")
                    .setPositiveButton("Grant", (dialog, which) -> {
                        ActivityCompat.requestPermissions(
                                RatesActivity.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                REQ_CODE_LOCATION
                        );
                    })
                    .setNegativeButton("Cancel", null), this);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_CODE_LOCATION
            );
        }
    }

    private void detectUserLocationOnline() {
        networkExecutor.execute(() -> {
            String detectedCountryCode = null;
            try {
                URL url = new URL("https://ipapi.co/json/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject obj = new JSONObject(sb.toString());
                    detectedCountryCode = obj.optString("country_code", null);
                }
            } catch (Exception ignored) {}

            String finalCode = detectedCountryCode;
            mainHandler.post(() -> {
                if (finalCode != null) {
                    CountryManager.Country country = findCountryByCode(CountryManager.getCountries(), finalCode);
                    if (country != null) {
                        fromCountry = country;
                        getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                                .edit()
                                .putString("last_selected_from_code", country.code)
                                .apply();
                        updateCurrencyCards();
                        calculateConvertedAmount();
                        fetchChartDataForPeriod();

                        if (txtLocationPermissionValue != null) {
                            txtLocationPermissionValue.setText(String.format(Locale.US, "Allow only while using app (Detected: %s)", country.name));
                        }
                        Toast.makeText(this, "Location detected: " + country.name, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    private void applySelectedLanguage(String language) {
        if (language == null) return;
        boolean isArabic = language.contains("Arabic") || language.contains("العربية");
        boolean isFrench = language.contains("French") || language.contains("Français");
        boolean isSpanish = language.contains("Spanish") || language.contains("Español");
        boolean isGerman = language.contains("German") || language.contains("Deutsch");

        if (isArabic) {
            if (txtConvertTitle != null) txtConvertTitle.setText("تحويل");
            if (btnSaveRate != null) btnSaveRate.setText("حفظ");
            if (txtFromLabel != null) txtFromLabel.setText("من");
            if (txtToLabel != null) txtToLabel.setText("إلى");
            if (navConvert != null) navConvert.setText("تحويل");
            if (navScan != null) navScan.setText("مسح");
            if (navHistory != null) navHistory.setText("السجل");
            if (navSettings != null) navSettings.setText("الإعدادات");
            if (txtSettingsHeaderTitle != null) txtSettingsHeaderTitle.setText("الإعدادات");
            if (txtDefaultCurrencyTitle != null) txtDefaultCurrencyTitle.setText("العملة الافتراضية");
            if (txtLocationPermissionTitle != null) txtLocationPermissionTitle.setText("إذن الموقع");
            if (txtLanguageTitle != null) txtLanguageTitle.setText("اللغة");
            if (txtThemeTitle != null) txtThemeTitle.setText("المظهر");
        } else if (isFrench) {
            if (txtConvertTitle != null) txtConvertTitle.setText("Convertir");
            if (btnSaveRate != null) btnSaveRate.setText("Enregistrer");
            if (txtFromLabel != null) txtFromLabel.setText("De");
            if (txtToLabel != null) txtToLabel.setText("À");
            if (navConvert != null) navConvert.setText("Convertir");
            if (navScan != null) navScan.setText("Scanner");
            if (navHistory != null) navHistory.setText("Historique");
            if (navSettings != null) navSettings.setText("Paramètres");
            if (txtSettingsHeaderTitle != null) txtSettingsHeaderTitle.setText("Paramètres");
            if (txtDefaultCurrencyTitle != null) txtDefaultCurrencyTitle.setText("Devise par défaut");
            if (txtLocationPermissionTitle != null) txtLocationPermissionTitle.setText("Autorisation de localisation");
            if (txtLanguageTitle != null) txtLanguageTitle.setText("Langue");
            if (txtThemeTitle != null) txtThemeTitle.setText("Thème");
        } else if (isSpanish) {
            if (txtConvertTitle != null) txtConvertTitle.setText("Convertir");
            if (btnSaveRate != null) btnSaveRate.setText("Guardar");
            if (txtFromLabel != null) txtFromLabel.setText("Desde");
            if (txtToLabel != null) txtToLabel.setText("A");
            if (navConvert != null) navConvert.setText("Convertir");
            if (navScan != null) navScan.setText("Escanear");
            if (navHistory != null) navHistory.setText("Historial");
            if (navSettings != null) navSettings.setText("Configuración");
            if (txtSettingsHeaderTitle != null) txtSettingsHeaderTitle.setText("Configuración");
            if (txtDefaultCurrencyTitle != null) txtDefaultCurrencyTitle.setText("Moneda predeterminada");
            if (txtLocationPermissionTitle != null) txtLocationPermissionTitle.setText("Permiso de ubicación");
            if (txtLanguageTitle != null) txtLanguageTitle.setText("Idioma");
            if (txtThemeTitle != null) txtThemeTitle.setText("Tema");
        } else if (isGerman) {
            if (txtConvertTitle != null) txtConvertTitle.setText("Konvertieren");
            if (btnSaveRate != null) btnSaveRate.setText("Speichern");
            if (txtFromLabel != null) txtFromLabel.setText("Von");
            if (txtToLabel != null) txtToLabel.setText("Nach");
            if (navConvert != null) navConvert.setText("Konvertieren");
            if (navScan != null) navScan.setText("Scannen");
            if (navHistory != null) navHistory.setText("Verlauf");
            if (navSettings != null) navSettings.setText("Einstellungen");
            if (txtSettingsHeaderTitle != null) txtSettingsHeaderTitle.setText("Einstellungen");
            if (txtDefaultCurrencyTitle != null) txtDefaultCurrencyTitle.setText("Standardwährung");
            if (txtLocationPermissionTitle != null) txtLocationPermissionTitle.setText("Standortberechtigung");
            if (txtLanguageTitle != null) txtLanguageTitle.setText("Sprache");
            if (txtThemeTitle != null) txtThemeTitle.setText("Thema");
        } else {
            // English default
            if (txtConvertTitle != null) txtConvertTitle.setText("Convert");
            if (btnSaveRate != null) btnSaveRate.setText("Save");
            if (txtFromLabel != null) txtFromLabel.setText("From");
            if (txtToLabel != null) txtToLabel.setText("To");
            if (navConvert != null) navConvert.setText("Convert");
            if (navScan != null) navScan.setText("Scan");
            if (navHistory != null) navHistory.setText("History");
            if (navSettings != null) navSettings.setText("Settings");
            if (txtSettingsHeaderTitle != null) txtSettingsHeaderTitle.setText("Settings");
            if (txtDefaultCurrencyTitle != null) txtDefaultCurrencyTitle.setText("Default Currency");
            if (txtLocationPermissionTitle != null) txtLocationPermissionTitle.setText("Location Permission");
            if (txtLanguageTitle != null) txtLanguageTitle.setText("Language");
            if (txtThemeTitle != null) txtThemeTitle.setText("Theme");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        updateLocationPermissionStatus();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);

        View backBtn = findViewById(R.id.ratesBackButton);
        if (backBtn == null) backBtn = findViewById(R.id.backButton);
        if (backBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) backBtn).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }

        if (btnSaveRate != null) {
            btnSaveRate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_LOCATION) {
            boolean granted = false;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result == PackageManager.PERMISSION_GRANTED) {
                        granted = true;
                        break;
                    }
                }
            }

            updateLocationPermissionStatus();

            if (granted) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
                detectUserLocationOnline();
            } else {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                            .setTitle("Location Permission Disabled")
                            .setMessage("Location permission was denied. Please enable it in System Settings to allow country/currency detection.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null), this);
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void applyThemeMode(boolean isDark) {
        int bg = isDark ? Color.parseColor("#000000") : Color.parseColor("#121212");
        if (ratesRootLayout != null) ratesRootLayout.setBackgroundColor(bg);
    }

    private void switchToConvertView() {
        if (viewConvertContainer != null) viewConvertContainer.setVisibility(View.VISIBLE);
        if (viewHistoryContainer != null) viewHistoryContainer.setVisibility(View.GONE);
        if (viewSettingsContainer != null) viewSettingsContainer.setVisibility(View.GONE);

        applySelectedLanguage(currentLanguage);
        if (btnSaveRate != null) btnSaveRate.setVisibility(View.VISIBLE);
        if (ratesRefreshButton != null) ratesRefreshButton.setVisibility(View.VISIBLE);

        updateBottomNavTextColors(navConvert);
    }

    private void switchToHistoryView() {
        if (viewConvertContainer != null) viewConvertContainer.setVisibility(View.GONE);
        if (viewHistoryContainer != null) viewHistoryContainer.setVisibility(View.VISIBLE);
        if (viewSettingsContainer != null) viewSettingsContainer.setVisibility(View.GONE);

        if (txtConvertTitle != null) {
            boolean isArabic = currentLanguage.contains("Arabic") || currentLanguage.contains("العربية");
            txtConvertTitle.setText(isArabic ? "سجل التحويلات" : "Converting History");
        }
        if (btnSaveRate != null) btnSaveRate.setVisibility(View.GONE);
        if (ratesRefreshButton != null) ratesRefreshButton.setVisibility(View.VISIBLE);

        updateBottomNavTextColors(navHistory);
        renderHistoryUI();
    }

    private void switchToSettingsView() {
        if (viewConvertContainer != null) viewConvertContainer.setVisibility(View.GONE);
        if (viewHistoryContainer != null) viewHistoryContainer.setVisibility(View.GONE);
        if (viewSettingsContainer != null) viewSettingsContainer.setVisibility(View.VISIBLE);

        if (txtConvertTitle != null) {
            boolean isArabic = currentLanguage.contains("Arabic") || currentLanguage.contains("العربية");
            txtConvertTitle.setText(isArabic ? "الإعدادات" : "Settings");
        }
        if (btnSaveRate != null) btnSaveRate.setVisibility(View.GONE);
        if (ratesRefreshButton != null) ratesRefreshButton.setVisibility(View.GONE);

        updateBottomNavTextColors(navSettings);
    }

    private void recordCurrentConversionAndGoToHistory() {
        if (fromCountry == null || toCountry == null) return;

        String fromAmt = editFromAmount != null ? editFromAmount.getText().toString().trim() : "100";
        if (fromAmt.isEmpty()) fromAmt = "100";

        String toAmt = txtToAmount != null ? txtToAmount.getText().toString().trim() : "0.00";

        String fromSymbol = getCurrencySymbol(fromCountry.currency);
        String toSymbol = getCurrencySymbol(toCountry.currency);

        String formattedFromStr = String.format(Locale.US, "%s %s", fromSymbol, fromAmt);
        String formattedToStr = toAmt.contains(toSymbol) ? toAmt : String.format(Locale.US, "%s %s", toSymbol, toAmt);

        ConversionRecord record = new ConversionRecord(
                fromCountry.currency,
                toCountry.currency,
                formattedFromStr,
                formattedToStr,
                System.currentTimeMillis()
        );

        saveHistoryRecord(record);
        switchToHistoryView();
    }

    private void saveHistoryRecord(ConversionRecord record) {
        List<ConversionRecord> list = loadHistoryRecords();
        list.add(0, record);

        try {
            JSONArray arr = new JSONArray();
            for (ConversionRecord r : list) {
                JSONObject obj = new JSONObject();
                obj.put("fromCode", r.fromCode);
                obj.put("toCode", r.toCode);
                obj.put("fromAmountStr", r.fromAmountStr);
                obj.put("toAmountStr", r.toAmountStr);
                obj.put("timestamp", r.timestamp);
                arr.put(obj);
            }

            getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_HISTORY_JSON, arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private List<ConversionRecord> loadHistoryRecords() {
        List<ConversionRecord> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_HISTORY_JSON, null);

        if (jsonStr != null) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    list.add(new ConversionRecord(
                            obj.optString("fromCode", "USD"),
                            obj.optString("toCode", "EUR"),
                            obj.optString("fromAmountStr", "$ 100"),
                            obj.optString("toAmountStr", "€ 88.42"),
                            obj.optLong("timestamp", System.currentTimeMillis())
                    ));
                }
            } catch (Exception ignored) {}
        } else {
            long now = System.currentTimeMillis();
            long dayMs = 86400000L;

            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - 3600000L));
            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - 7200000L));

            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - dayMs - 3600000L));
            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - dayMs - 7200000L));
            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - dayMs - 10800000L));
            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - dayMs - 14400000L));

            list.add(new ConversionRecord("USD", "EUR", "$ 100", "€ 88.42", now - (3 * dayMs)));
        }
        return list;
    }

    private void renderHistoryUI() {
        if (historyGroupContainer == null) return;
        historyGroupContainer.removeAllViews();

        List<ConversionRecord> allRecords = loadHistoryRecords();

        List<ConversionRecord> todayList = new ArrayList<>();
        List<ConversionRecord> yesterdayList = new ArrayList<>();
        List<ConversionRecord> lastWeekList = new ArrayList<>();
        List<ConversionRecord> olderList = new ArrayList<>();

        Calendar nowCal = Calendar.getInstance();
        int todayYear = nowCal.get(Calendar.YEAR);
        int todayDay = nowCal.get(Calendar.DAY_OF_YEAR);

        for (ConversionRecord r : allRecords) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(r.timestamp);

            int recYear = c.get(Calendar.YEAR);
            int recDay = c.get(Calendar.DAY_OF_YEAR);

            if (recYear == todayYear && recDay == todayDay) {
                todayList.add(r);
            } else if (recYear == todayYear && (todayDay - recDay == 1)) {
                yesterdayList.add(r);
            } else if (nowCal.getTimeInMillis() - r.timestamp < 7 * 86400000L) {
                lastWeekList.add(r);
            } else {
                olderList.add(r);
            }
        }

        boolean isArabic = currentLanguage.contains("Arabic") || currentLanguage.contains("العربية");

        if (!todayList.isEmpty()) renderHistoryGroup(isArabic ? "اليوم" : "Today", todayList);
        if (!yesterdayList.isEmpty()) renderHistoryGroup(isArabic ? "الأمس" : "Yesterday", yesterdayList);
        if (!lastWeekList.isEmpty()) renderHistoryGroup(isArabic ? "الأسبوع الماضي" : "Last week", lastWeekList);
        if (!olderList.isEmpty()) renderHistoryGroup(isArabic ? "أقدم" : "Older", olderList);
    }

    private void renderHistoryGroup(String title, List<ConversionRecord> records) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor("#8E8E93"));
        header.setTextSize(14f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 16, 0, 8);
        header.setLayoutParams(headerParams);
        historyGroupContainer.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_currency_card);
        card.setPadding(0, 4, 0, 4);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy, hh:mm a", Locale.US);

        for (int i = 0; i < records.size(); i++) {
            ConversionRecord r = records.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_conversion_history, card, false);

            TextView txtTitle = row.findViewById(R.id.txtHistoryTitle);
            TextView txtDateTime = row.findViewById(R.id.txtHistoryDateTime);
            TextView txtAmounts = row.findViewById(R.id.txtHistoryAmounts);

            if (txtTitle != null) {
                txtTitle.setText(String.format(Locale.US, "From %s to %s", r.fromCode, r.toCode));
            }

            if (txtDateTime != null) {
                txtDateTime.setText(sdf.format(new Date(r.timestamp)));
            }

            if (txtAmounts != null) {
                txtAmounts.setText(String.format(Locale.US, "%s   |   %s", r.fromAmountStr, r.toAmountStr));
            }

            card.addView(row);

            if (i < records.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                );
                divParams.setMargins(32, 0, 32, 0);
                divider.setLayoutParams(divParams);
                divider.setBackgroundColor(Color.parseColor("#2C2C2E"));
                card.addView(divider);
            }
        }

        historyGroupContainer.addView(card);
    }

    private CountryManager.Country findCountryByCode(List<CountryManager.Country> list, String code) {
        for (CountryManager.Country c : list) {
            if (c.code.equalsIgnoreCase(code)) return c;
        }
        return null;
    }

    private void updateCurrencyCards() {
        if (fromCountry != null) {
            if (txtFromCode != null) txtFromCode.setText(fromCountry.currency);
            if (txtFromNameFlag != null) txtFromNameFlag.setText(String.format(Locale.US, "%s %s", getShortName(fromCountry.name), fromCountry.flag));
        }
        if (toCountry != null) {
            if (txtToCode != null) txtToCode.setText(toCountry.currency);
            if (txtToNameFlag != null) txtToNameFlag.setText(String.format(Locale.US, "%s %s", getShortName(toCountry.name), toCountry.flag));
        }
    }

    private String getShortName(String full) {
        if (full == null) return "";
        if (full.equalsIgnoreCase("European Union")) return "Euro";
        if (full.equalsIgnoreCase("United States")) return "US Dollar";
        if (full.equalsIgnoreCase("United Kingdom")) return "British Pound";
        return full;
    }

    private void setupPeriodButtons() {
        View.OnClickListener listener = v -> {
            int id = v.getId();
            if (id == R.id.btnPeriod1D) selectedPeriod = "1D";
            else if (id == R.id.btnPeriod5D) selectedPeriod = "5D";
            else if (id == R.id.btnPeriod1M) selectedPeriod = "1M";
            else if (id == R.id.btnPeriod6M) selectedPeriod = "6M";
            else if (id == R.id.btnPeriod1Y) selectedPeriod = "1Y";
            else if (id == R.id.btnPeriodMax) selectedPeriod = "MAX";

            updatePeriodSelectionUi();
            fetchChartDataForPeriod();
        };

        if (btn1D != null) btn1D.setOnClickListener(listener);
        if (btn5D != null) btn5D.setOnClickListener(listener);
        if (btn1M != null) btn1M.setOnClickListener(listener);
        if (btn6M != null) btn6M.setOnClickListener(listener);
        if (btn1Y != null) btn1Y.setOnClickListener(listener);
        if (btnMax != null) btnMax.setOnClickListener(listener);

        updatePeriodSelectionUi();
    }

    private void updatePeriodSelectionUi() {
        TextView[] buttons = {btn1D, btn5D, btn1M, btn6M, btn1Y, btnMax};
        String[] codes = {"1D", "5D", "1M", "6M", "1Y", "MAX"};
        int accent = ThemeManager.getMainAccentColor(this);

        for (int i = 0; i < buttons.length; i++) {
            TextView btn = buttons[i];
            if (btn == null) continue;
            boolean isSelected = codes[i].equalsIgnoreCase(selectedPeriod);
            if (isSelected) {
                btn.setBackgroundResource(R.drawable.bg_period_active);
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
                btn.setTextColor(Color.WHITE);
            } else {
                btn.setBackgroundResource(R.drawable.bg_period_inactive);
                btn.setBackgroundTintList(null);
                btn.setTextColor(Color.parseColor("#8E8E93"));
            }
        }
    }

    private void loadCachedRates() {
        ratesMap.putAll(DEFAULT_RATES);
        SharedPreferences prefs = getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE);
        String json = prefs.getString("cached_rates_json", null);
        if (json != null) {
            parseRatesJson(json);
        }
    }

    private void fetchLiveRatesAndChart() {
        if (progressBarLoading != null) progressBarLoading.setVisibility(View.VISIBLE);

        networkExecutor.execute(() -> {
            boolean success = false;
            try {
                URL url = new URL("https://open.er-api.com/v6/latest/USD");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    String jsonStr = sb.toString();
                    parseRatesJson(jsonStr);

                    getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE)
                            .edit()
                            .putString("cached_rates_json", jsonStr)
                            .apply();

                    success = true;
                }
            } catch (Exception ignored) {}

            boolean finalSuccess = success;
            mainHandler.post(() -> {
                if (progressBarLoading != null) progressBarLoading.setVisibility(View.GONE);
                calculateConvertedAmount();
                updateStatusText(finalSuccess);
                fetchChartDataForPeriod();
            });
        });
    }

    private void parseRatesJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            if (obj.has("rates")) {
                JSONObject ratesObj = obj.getJSONObject("rates");
                Iterator<String> keys = ratesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    double val = ratesObj.getDouble(key);
                    ratesMap.put(key.toUpperCase(Locale.US), val);
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateStatusText(boolean online) {
        if (txtStatusInfo == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
        String timeStr = sdf.format(new Date());

        if (online) {
            txtStatusInfo.setText(String.format(Locale.US, "Last updated: %s • Live Online", timeStr));
        } else {
            txtStatusInfo.setText(String.format(Locale.US, "Last updated: %s • Offline (Cached)", timeStr));
        }
    }

    private double getRateToUsd(String code) {
        if (code == null) return 1.0;
        Double val = ratesMap.get(code.toUpperCase(Locale.US));
        return (val != null && val > 0) ? val : 1.0;
    }

    private void calculateConvertedAmount() {
        if (fromCountry == null || toCountry == null || editFromAmount == null || txtToAmount == null) return;

        String amountStr = editFromAmount.getText().toString().trim().replaceAll("[^0-9.]", "");
        if (amountStr.isEmpty() || amountStr.equals(".")) {
            txtToAmount.setText(String.format(Locale.US, "0.00 %s", getCurrencySymbol(toCountry.currency)));
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            double rateFrom = getRateToUsd(fromCountry.currency);
            double rateTo = getRateToUsd(toCountry.currency);

            double inUsd = amount / rateFrom;
            double converted = inUsd * rateTo;

            String symbol = getCurrencySymbol(toCountry.currency);
            txtToAmount.setText(String.format(Locale.US, "%,.2f %s", converted, symbol));

            double currentPairRate = (1.0 / rateFrom) * rateTo;
            if (txtPairAndRate != null) {
                txtPairAndRate.setText(String.format(Locale.US, "%s/%s %,.4f", fromCountry.currency, toCountry.currency, currentPairRate));
            }
        } catch (Exception e) {
            txtToAmount.setText("0.00");
        }
    }

    private String getCurrencySymbol(String code) {
        if (code == null) return "$";
        switch (code.toUpperCase(Locale.US)) {
            case "EUR": return "€";
            case "USD": return "$";
            case "GBP": return "£";
            case "JPY": return "¥";
            case "CAD": return "C$";
            case "AUD": return "A$";
            case "LBP": return "LBP";
            default: return code;
        }
    }

    private void setupChartStyle() {
        if (lineChart == null) return;
        lineChart.getDescription().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setDrawGridBackground(false);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#8E8E93"));
        xAxis.setTextSize(10f);
        xAxis.setDrawGridLines(false);
        xAxis.setAxisLineColor(Color.parseColor("#2C2C2E"));

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#8E8E93"));
        leftAxis.setTextSize(10f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#2C2C2E"));
        leftAxis.setAxisLineColor(Color.TRANSPARENT);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void fetchChartDataForPeriod() {
        if (fromCountry == null || toCountry == null) return;

        double rateFrom = getRateToUsd(fromCountry.currency);
        double rateTo = getRateToUsd(toCountry.currency);
        double currentPairRate = (1.0 / rateFrom) * rateTo;

        int numPoints = 20;
        if ("1D".equalsIgnoreCase(selectedPeriod)) numPoints = 12;
        else if ("5D".equalsIgnoreCase(selectedPeriod)) numPoints = 15;
        else if ("1M".equalsIgnoreCase(selectedPeriod)) numPoints = 25;
        else if ("6M".equalsIgnoreCase(selectedPeriod)) numPoints = 30;
        else if ("1Y".equalsIgnoreCase(selectedPeriod)) numPoints = 36;
        else if ("MAX".equalsIgnoreCase(selectedPeriod)) numPoints = 40;

        List<Entry> entries = new ArrayList<>();
        List<String> datesList = new ArrayList<>();

        double startRate = currentPairRate * (1.0 - (getPeriodPercentageSpread(selectedPeriod) / 100.0));
        Random random = new Random(selectedPeriod.hashCode() + fromCountry.currency.hashCode() + toCountry.currency.hashCode());

        double runningRate;
        for (int i = 0; i < numPoints; i++) {
            double stepFraction = (double) i / (numPoints - 1);
            double trend = startRate + (currentPairRate - startRate) * stepFraction;
            double fluctuation = (random.nextDouble() - 0.48) * (currentPairRate * 0.015);
            runningRate = trend + fluctuation;
            if (i == numPoints - 1) runningRate = currentPairRate;

            entries.add(new Entry(i, (float) runningRate));
            datesList.add(getFormattedDateForIndex(i, numPoints, selectedPeriod));
        }

        double startValue = entries.get(0).getY();
        double endValue = entries.get(entries.size() - 1).getY();
        double changePct = ((endValue - startValue) / startValue) * 100.0;

        int accentColor = ThemeManager.getMainAccentColor(this);

        if (txtRateChange != null) {
            if (changePct >= 0) {
                txtRateChange.setText(String.format(Locale.US, "+%.2f%%", changePct));
                txtRateChange.setBackgroundResource(R.drawable.bg_pill_green);
                txtRateChange.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
            } else {
                txtRateChange.setText(String.format(Locale.US, "%.2f%%", changePct));
                txtRateChange.setBackgroundResource(R.drawable.bg_pill_red);
                txtRateChange.setBackgroundTintList(null);
            }
        }
        LineDataSet dataSet = new LineDataSet(entries, "Exchange Rate");
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.15f);
        dataSet.setColor(accentColor);
        dataSet.setLineWidth(2.5f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        dataSet.setHighlightEnabled(true);
        dataSet.setHighLightColor(Color.WHITE);

        dataSet.setDrawFilled(true);
        int fillAlpha = (accentColor & 0x00FFFFFF) | 0x66000000;
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{fillAlpha, Color.TRANSPARENT}
        );
        dataSet.setFillDrawable(gradient);

        LineData lineData = new LineData(dataSet);
        if (lineChart != null) {
            lineChart.getXAxis().setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    int idx = (int) value;
                    if (idx >= 0 && idx < datesList.size()) {
                        return datesList.get(idx);
                    }
                    return "";
                }
            });

            lineChart.setData(lineData);
            lineChart.animateX(600);
            lineChart.invalidate();
        }
    }

    private double getPeriodPercentageSpread(String period) {
        switch (period.toUpperCase(Locale.US)) {
            case "1D": return 0.4;
            case "5D": return 0.9;
            case "6M": return 3.5;
            case "1Y": return 5.8;
            case "MAX": return 12.0;
            case "1M":
            default: return 1.94;
        }
    }

    private String getFormattedDateForIndex(int index, int total, String period) {
        Calendar cal = Calendar.getInstance();
        int daysBack = 30;
        if ("1D".equalsIgnoreCase(period)) daysBack = 1;
        else if ("5D".equalsIgnoreCase(period)) daysBack = 5;
        else if ("6M".equalsIgnoreCase(period)) daysBack = 180;
        else if ("1Y".equalsIgnoreCase(period)) daysBack = 365;
        else if ("MAX".equalsIgnoreCase(period)) daysBack = 1000;

        int offset = daysBack - (int) (((double) index / (total - 1)) * daysBack);
        cal.add(Calendar.DAY_OF_YEAR, -offset);

        if ("1D".equalsIgnoreCase(period)) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
            return sdf.format(cal.getTime());
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("d MMM", Locale.US);
            return sdf.format(cal.getTime());
        }
    }

    private void showCountryPicker(java.util.function.Consumer<CountryManager.Country> callback) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_country_list, null);
        EditText searchInput = view.findViewById(R.id.searchCountry);
        RecyclerView rv = view.findViewById(R.id.countryRecyclerView);

        List<CountryManager.Country> allCountries = CountryManager.getCountries();
        CountryAdapter adapter = new CountryAdapter(allCountries, callback);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(view)
                .create();

        adapter.setDialog(dialog);

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    adapter.filter(s.toString());
                }
            });
        }

        dialog.setOnShowListener(d -> ThemeManager.styleDialogButtons(dialog, this));
        dialog.show();
    }

    private class CountryAdapter extends RecyclerView.Adapter<CountryAdapter.ViewHolder> {
        private final List<CountryManager.Country> fullList;
        private List<CountryManager.Country> filteredList;
        private final java.util.function.Consumer<CountryManager.Country> callback;
        private AlertDialog dialog;

        CountryAdapter(List<CountryManager.Country> list, java.util.function.Consumer<CountryManager.Country> callback) {
            this.fullList = list;
            this.filteredList = new ArrayList<>(list);
            this.callback = callback;
        }

        void setDialog(AlertDialog dialog) { this.dialog = dialog; }

        void filter(String query) {
            filteredList = new ArrayList<>();
            for (CountryManager.Country c : fullList) {
                if (c.name.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US)) ||
                        c.code.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US)) ||
                        c.currency.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US))) {
                    filteredList.add(c);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CountryManager.Country c = filteredList.get(position);
            holder.flag.setText(c.flag);
            holder.name.setText(String.format(Locale.US, "%s (%s - %s)", c.name, c.code, c.currency));
            holder.itemView.setOnClickListener(v -> {
                callback.accept(c);
                if (dialog != null) dialog.dismiss();
            });
        }

        @Override
        public int getItemCount() { return filteredList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView flag, name;
            ViewHolder(View itemView) {
                super(itemView);
                flag = itemView.findViewById(R.id.txtCountryFlag);
                name = itemView.findViewById(R.id.txtCountryName);
            }
        }
    }
}
