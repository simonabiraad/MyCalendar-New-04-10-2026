package com.example.mycalendar2026sar;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;

import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

import androidx.appcompat.widget.SwitchCompat;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.mlkit.common.model.DownloadConditions;

public class MainActivity extends AppCompatActivity {

    private GridView calendarGrid;
    private TextView monthYearText;
    private EditText noteInput;
    private TextView remarkLabel;
    private LinearLayout dayRemarksContainer;
    private LinearLayout remarkHistoryContainer;
    private LinearLayout archiveHistoryContainer;
    private LinearLayout deletedHistoryContainer;
    private ScrollView mainScrollView;
    
    // Selection Mode
    private boolean isSelectionMode = false;
    private final java.util.HashSet<String> selectedNotes = new java.util.HashSet<>();
    private LinearLayout selectionBar;
    private TextView selectionCountText;

    private Calendar calendar;
    private Calendar selectedDate;
    private String currentDateKey;
    private EditText currentDialogInput;
    private SharedPreferences sharedPreferences;
    private SharedPreferences archivePreferences;
    private SharedPreferences deletedPreferences;
    private SharedPreferences reminderPreferences;
    private SharedPreferences securityPrefs;
    private SharedPreferences colorPrefs;
    private SharedPreferences fontPrefs;
    private SharedPreferences appSettingsPrefs;
    private CalendarAdapter adapter;

    private boolean isKeyboardModeActive = false;

    private SharedPreferences speechPrefs;
    private String speechSourceLang = "en-US";
    private String speechTargetLang = "ar";
    private boolean speechAutoLang = true;
    private boolean speechTranslateEnabled = true;

    // Custom Menu
    private View menuDimmer;
    private View customMenuContainer;
    private View settingsPanelContainer;
    private ImageView settingsArrow;

    private String lastDeletedNote;
    private int lastDeletedIndex;
    private String lastDeletedDateKey;

    private boolean isVoiceCommandMode = false;

    private int voiceTargetIndex = -1;
    private SharedPreferences voiceTargetPrefs = null;
    private String voiceTargetDateKey = null;

    private final ActivityResultLauncher<Intent> voiceRecognitionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        
                        if (speechTranslateEnabled) {
                            // In translate mode, identify language and then translate
                            translateText(spokenText);
                        } else {
                            // If translation is OFF, just handle text directly as detected by STT
                            handleRecognizedText(spokenText);
                        }
                    }
                }
            });

    private void translateText(final String text) {
        if (speechAutoLang) {
            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
            languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener(languageCode -> {
                        if (languageCode.equals("und")) {
                            // If language detection failed, do not use speechSourceLang as fallback
                            // to avoid interference. Just handle text as is.
                            handleRecognizedText(text);
                        } else {
                            // Use detected language as source for translation
                            performTranslation(text, languageCode);
                        }
                    })
                    .addOnFailureListener(e -> handleRecognizedText(text));
        } else {
            // Use manually selected Speaking Language as source
            performTranslation(text, getMLKitCode(speechSourceLang));
        }
    }

    private void performTranslation(final String text, String sourceCode) {
        String targetCode = getMLKitCode(speechTargetLang);

        if (sourceCode.equals(targetCode)) {
            handleRecognizedText(text);
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build();
        final Translator translator = Translation.getClient(options);

        DownloadConditions conditions = new DownloadConditions.Builder()
                .build();
        
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(unused -> {
                    translator.translate(text)
                            .addOnSuccessListener(translatedText -> {
                                handleRecognizedText(translatedText);
                                translator.close();
                            })
                            .addOnFailureListener(e -> {
                                handleRecognizedText(text);
                                Toast.makeText(this, "Translation failed", Toast.LENGTH_SHORT).show();
                                translator.close();
                            });
                })
                .addOnFailureListener(e -> {
                    handleRecognizedText(text);
                    Toast.makeText(this, "Failed to download translation model", Toast.LENGTH_SHORT).show();
                    translator.close();
                });
    }


    private String getMLKitCode(String bcpCode) {
        if (bcpCode.startsWith("en")) return TranslateLanguage.ENGLISH;
        if (bcpCode.startsWith("ar")) return TranslateLanguage.ARABIC;
        if (bcpCode.startsWith("fr")) return TranslateLanguage.FRENCH;
        if (bcpCode.startsWith("es")) return TranslateLanguage.SPANISH;
        if (bcpCode.startsWith("de")) return TranslateLanguage.GERMAN;
        if (bcpCode.startsWith("zh")) return TranslateLanguage.CHINESE;
        if (bcpCode.startsWith("it")) return TranslateLanguage.ITALIAN;
        if (bcpCode.startsWith("ja")) return TranslateLanguage.JAPANESE;
        if (bcpCode.startsWith("ru")) return TranslateLanguage.RUSSIAN;
        if (bcpCode.startsWith("pt")) return TranslateLanguage.PORTUGUESE;
        return TranslateLanguage.ENGLISH;
    }

    private void handleRecognizedText(String text) {
        if (currentDialogInput != null) {
            String existingText = currentDialogInput.getText().toString();
            currentDialogInput.setText(existingText.isEmpty() ? text : existingText + " " + text);
        } else if (voiceTargetPrefs != null && voiceTargetDateKey != null && voiceTargetIndex != -1) {
            appendVoiceToNote(text, voiceTargetIndex, voiceTargetDateKey, voiceTargetPrefs);
        } else if (noteInput.hasFocus()) {
            String existingText = noteInput.getText().toString();
            noteInput.setText(existingText.isEmpty() ? text : existingText + " " + text);
        } else {
            showNewNoteDialog(text);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Notification permission denied. Reminders won't show.", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> exportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        performExport(uri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        performImport(uri);
                    }
                }
            });

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, "calendar_reminder_channel");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            fallback.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(fallback);
        }
    }

    private void setKeyboardMode(boolean isOpen) {
        if (isOpen && !isKeyboardModeActive) {
            isKeyboardModeActive = true;
            // Removed automatic reset to today
            setViewsVisibility(View.GONE);
            updateRemarkLabelAndHistory();
        } else if (!isOpen && isKeyboardModeActive) {
            isKeyboardModeActive = false;
            setViewsVisibility(View.VISIBLE);
            updateRemarkLabelAndHistory();
        }
    }

    private void setViewsVisibility(int visibility) {
        int[] ids = {
                R.id.headerTextContainer, R.id.mainMenuButton, R.id.aiAssistantButton,
                R.id.notificationSettingsButton, R.id.calendarHeader, R.id.weekdayLayout,
                R.id.calendarGrid, R.id.remarkHistoryTitle, R.id.remarkHistoryContainer,
                R.id.archiveHistoryTitle, R.id.archiveHistoryContainer, R.id.deletedHistoryTitle,
                R.id.deletedHistoryContainer
        };
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) v.setVisibility(visibility);
        }
    }

    private void updateRemarkLabelAndHistory() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        currentDateKey = sdf.format(selectedDate.getTime());
        SimpleDateFormat displaySdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        remarkLabel.setText(getString(R.string.remark_for, displaySdf.format(selectedDate.getTime())));
        loadRemarksForSelectedDate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            setKeyboardMode(isKeyboardVisible);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customMenuContainer != null && customMenuContainer.getVisibility() == View.VISIBLE) {
                    hideCustomMenu();
                    return;
                }
                new AlertDialog.Builder(MainActivity.this, R.style.CustomAlertDialogTheme)
                        .setTitle(R.string.close_app)
                        .setMessage(R.string.close_app_msg)
                        .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                        .setNegativeButton(R.string.no, null)
                        .show();
            }
        });

        calendarGrid = findViewById(R.id.calendarGrid);
        monthYearText = findViewById(R.id.monthYearText);
        noteInput = findViewById(R.id.noteInput);
        noteInput.setTextColor(Color.WHITE);
        remarkLabel = findViewById(R.id.remarkLabel);
        dayRemarksContainer = findViewById(R.id.dayRemarksContainer);
        remarkHistoryContainer = findViewById(R.id.remarkHistoryContainer);
        archiveHistoryContainer = findViewById(R.id.archiveHistoryContainer);
        deletedHistoryContainer = findViewById(R.id.deletedHistoryContainer);
        mainScrollView = findViewById(R.id.mainScrollView);

        initSelectionBar();

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navHomeButton);
        ImageButton prevMonth = findViewById(R.id.prevMonth);
        ImageButton nextMonth = findViewById(R.id.nextMonth);

        sharedPreferences = getSharedPreferences("CalendarNotes", Context.MODE_PRIVATE);
        archivePreferences = getSharedPreferences("ArchivedNotes", Context.MODE_PRIVATE);
        deletedPreferences = getSharedPreferences("DeletedNotes", Context.MODE_PRIVATE);
        reminderPreferences = getSharedPreferences("ReminderStatus", Context.MODE_PRIVATE);
        securityPrefs = getSharedPreferences("SecuritySettings", Context.MODE_PRIVATE);
        colorPrefs = getSharedPreferences("AppColors", Context.MODE_PRIVATE);
        fontPrefs = getSharedPreferences("AppFonts", Context.MODE_PRIVATE);
        speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
        appSettingsPrefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);

        speechSourceLang = speechPrefs.getString("speech_source_lang", "en-US");
        speechTargetLang = speechPrefs.getString("speech_target_lang", "ar");
        speechAutoLang = speechPrefs.getBoolean("speech_auto_lang", true);
        speechTranslateEnabled = speechPrefs.getBoolean("speech_translate_enabled", true);

        // Hide folders initially
        archiveHistoryContainer.setVisibility(View.GONE);
        deletedHistoryContainer.setVisibility(View.GONE);

        // Always initialize to current day
        selectedDate = Calendar.getInstance();
        calendar = (Calendar) selectedDate.clone();
        calendar.set(Calendar.DAY_OF_MONTH, 1); // Start at beginning of current month

        refreshUIColors();

        updateCalendar();
        updateRemarkHistory();

        findViewById(R.id.remarkHistoryTitle).setOnClickListener(v -> {
            if (remarkHistoryContainer.getVisibility() == View.VISIBLE) {
                remarkHistoryContainer.setVisibility(View.GONE);
            } else {
                remarkHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.remarkHistoryTitle).getTop()));
            }
        });

        findViewById(R.id.archiveHistoryTitle).setOnClickListener(v -> {
            if (archiveHistoryContainer.getVisibility() == View.VISIBLE) {
                archiveHistoryContainer.setVisibility(View.GONE);
            } else {
                archiveHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.archiveHistoryTitle).getTop()));
            }
        });

        findViewById(R.id.deletedHistoryTitle).setOnClickListener(v -> {
            if (deletedHistoryContainer.getVisibility() == View.VISIBLE) {
                deletedHistoryContainer.setVisibility(View.GONE);
            } else {
                deletedHistoryContainer.setVisibility(View.VISIBLE);
                mainScrollView.post(() -> mainScrollView.smoothScrollTo(0, findViewById(R.id.deletedHistoryTitle).getTop()));
            }
        });
        
        noteInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                saveNote();
                return true;
            }
            return false;
        });

        findViewById(R.id.voiceNoteButton).setOnClickListener(v -> {
            noteInput.requestFocus();
            startVoiceRecognition();
        });

        findViewById(R.id.addNoteIconButton).setOnClickListener(v -> saveNote());

        findViewById(R.id.mainMenuButton).setOnClickListener(v -> toggleCustomMenu());

        findViewById(R.id.notificationSettingsButton).setOnClickListener(v -> openNotificationSettings());

        prevMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, -1);
            updateCalendar();
        });
        nextMonth.setOnClickListener(v -> {
            calendar.add(Calendar.MONTH, 1);
            updateCalendar();
        });

        findViewById(R.id.aiAssistantButton).setOnClickListener(v -> showSpeechTranslationDialog());

        createNotificationChannel();
        setupCustomMenu();
        handleIntent(getIntent());
    }

    private void setupCustomMenu() {
        menuDimmer = findViewById(R.id.menuDimmer);
        customMenuContainer = findViewById(R.id.customMenuContainer);
        settingsPanelContainer = findViewById(R.id.settingsPanelContainer);
        settingsArrow = findViewById(R.id.settingsArrow);

        menuDimmer.setOnClickListener(v -> hideCustomMenu());

        // Main Menu Items
        findViewById(R.id.menuNewNote).setOnClickListener(v -> { hideCustomMenu(); showNewNoteDialog(""); });
        findViewById(R.id.menuNewVoiceNote).setOnClickListener(v -> { hideCustomMenu(); startVoiceRecognition(); });
        findViewById(R.id.menuEvents).setOnClickListener(v -> {
            hideCustomMenu();
            Intent intent = new Intent(this, NotificationDetailsActivity.class);
            intent.putExtra("mode", "add");
            intent.putExtra("date", currentDateKey);
            startActivity(intent);
        });
        findViewById(R.id.menuNewStickyNote).setOnClickListener(v -> { hideCustomMenu(); startActivity(new Intent(this, TaskActivity.class)); });
        findViewById(R.id.menuSecureBox).setOnClickListener(v -> { hideCustomMenu(); launchSecureBox(false); });
        findViewById(R.id.menuExpenses).setOnClickListener(v -> { hideCustomMenu(); launchExpenses(); });
        
        findViewById(R.id.menuSettings).setOnClickListener(v -> toggleSettingsPanel());
        
        findViewById(R.id.menuPrivacyPolicy).setOnClickListener(v -> {
            hideCustomMenu();
            new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Privacy Policy")
                    .setMessage("Your data is stored locally on your device. We do not collect any personal information.")
                    .setPositiveButton("OK", null)
                    .show();
        });
        findViewById(R.id.menuAbout).setOnClickListener(v -> {
            hideCustomMenu();
            new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("About SAR Calendar")
                    .setMessage("SAR Calendar 2026\nVersion 1.0\nCreated by SAR")
                    .setPositiveButton("OK", null)
                    .show();
        });
        findViewById(R.id.menuExit).setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle(R.string.action_exit)
                    .setMessage(R.string.exit_confirm_msg)
                    .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                    .setNegativeButton(R.string.no, null)
                    .show();
        });

        // Settings Panel Items
        findViewById(R.id.menuChangePassword).setOnClickListener(v -> { hideCustomMenu(); showChangePasswordDialog(); });
        findViewById(R.id.menuNotificationSettings).setOnClickListener(v -> { hideCustomMenu(); openNotificationSettings(); });
        findViewById(R.id.menuToggleQuickBar).setOnClickListener(v -> { hideCustomMenu(); toggleQuickNoteBar(); });
        
        androidx.appcompat.widget.SwitchCompat switchDarkModeMenu = findViewById(R.id.switchDarkModeMenu);
        if (switchDarkModeMenu != null) {
            switchDarkModeMenu.setOnCheckedChangeListener(null);
            switchDarkModeMenu.setChecked(ThemeManager.isDarkMode(this));
            switchDarkModeMenu.setOnCheckedChangeListener((buttonView, isChecked) -> {
                hideCustomMenu();
                ThemeManager.setDarkMode(MainActivity.this, isChecked, MainActivity.this);
            });
        }
        View menuDarkMode = findViewById(R.id.menuDarkMode);
        if (menuDarkMode != null) {
            menuDarkMode.setOnClickListener(v -> {
                if (switchDarkModeMenu != null) {
                    switchDarkModeMenu.toggle();
                }
            });
        }

        findViewById(R.id.menuChangeColors).setOnClickListener(v -> { hideCustomMenu(); showChangeColorsDialog(); });
        findViewById(R.id.menuChangeFont).setOnClickListener(v -> { hideCustomMenu(); showFontDialog(); });
        findViewById(R.id.menuBackupData).setOnClickListener(v -> { hideCustomMenu(); showBackupDataDialog(); });
        findViewById(R.id.menuPrint).setOnClickListener(v -> { hideCustomMenu(); showPrintDialog(); });

        // Sorting
        findViewById(R.id.menuSortDateAsc).setOnClickListener(v -> toggleSortOrder(true));
        findViewById(R.id.menuSortDateDesc).setOnClickListener(v -> toggleSortOrder(false));
        updateSortOrderUI();
    }

    private void toggleSortOrder(boolean ascending) {
        appSettingsPrefs.edit().putString("sort_order", ascending ? "ASC" : "DESC").apply();
        updateSortOrderUI();
        updateRemarkHistory();
    }

    private void updateSortOrderUI() {
        String sortOrder = appSettingsPrefs.getString("sort_order", "DESC");
        boolean isAsc = "ASC".equals(sortOrder);

        TextView tvAsc = findViewById(R.id.tvSortDateAscState);
        TextView tvDesc = findViewById(R.id.tvSortDateDescState);

        if (tvAsc != null) {
            tvAsc.setText(isAsc ? "● ON" : "OFF");
            tvAsc.setTextColor(isAsc ? Color.GREEN : Color.WHITE);
        }
        if (tvDesc != null) {
            tvDesc.setText(!isAsc ? "● ON" : "OFF");
            tvDesc.setTextColor(!isAsc ? Color.GREEN : Color.WHITE);
        }
    }

    private void toggleCustomMenu() {
        if (customMenuContainer.getVisibility() == View.VISIBLE) {
            hideCustomMenu();
        } else {
            customMenuContainer.setVisibility(View.VISIBLE);
            menuDimmer.setVisibility(View.VISIBLE);
        }
    }

    private void hideCustomMenu() {
        customMenuContainer.setVisibility(View.GONE);
        settingsPanelContainer.setVisibility(View.GONE);
        menuDimmer.setVisibility(View.GONE);
        settingsArrow.setRotation(0);
    }

    private void toggleSettingsPanel() {
        if (settingsPanelContainer.getVisibility() == View.VISIBLE) {
            settingsPanelContainer.setVisibility(View.GONE);
            settingsArrow.setRotation(0);
        } else {
            settingsPanelContainer.setVisibility(View.VISIBLE);
            settingsArrow.setRotation(180);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        if (QuickNoteNotificationService.ACTION_VOICE.equals(intent.getAction())) {
            currentDialogInput = null;
            startVoiceRecognition();
        } else if (QuickNoteNotificationService.ACTION_NOTE.equals(intent.getAction())) {
            currentDialogInput = null;
            showNewNoteDialog("");
        } else if (QuickNoteNotificationService.ACTION_SECURE_BOX.equals(intent.getAction())) {
            launchSecureBox(false);
        } else if (QuickNoteNotificationService.ACTION_TASK.equals(intent.getAction())) {
            startActivity(new Intent(this, TaskActivity.class));
        } else if (QuickNoteNotificationService.ACTION_EXPENSES.equals(intent.getAction())) {
            launchExpenses();
        } else if (QuickNoteNotificationService.ACTION_SETTINGS.equals(intent.getAction())) {
            openNotificationSettings();
        } else if (QuickNoteNotificationService.ACTION_THEMES.equals(intent.getAction())) {
            showThemeOptionsDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Automatically scan and archive past notes every time the user enters the app
        archiveAllPastNotesSilent();
        autoCleanArchive();
        updateCalendar();
        updateRemarkHistory();
        updateAllWidgets();
    }

    private void autoCleanArchive() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        
        Calendar archiveThresholdCal = (Calendar) now.clone();
        archiveThresholdCal.add(Calendar.MONTH, -1);
        Date archiveThreshold = archiveThresholdCal.getTime();

        Calendar deleteThresholdCal = (Calendar) now.clone();
        deleteThresholdCal.add(Calendar.MONTH, -2);
        Date deleteThreshold = deleteThresholdCal.getTime();

        Map<String, ?> allArchived = archivePreferences.getAll();
        for (Map.Entry<String, ?> entry : allArchived.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && noteDate.before(archiveThreshold)) {
                    Object valObj = entry.getValue();
                    String value = valObj != null ? valObj.toString() : "";
                    if (!value.isEmpty()) {
                        String existingDeleted = deletedPreferences.getString(dateKey, "");
                        String updatedDeleted = existingDeleted.isEmpty() ? value : existingDeleted + "\n" + value;
                        deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
                        archivePreferences.edit().remove(dateKey).apply();
                    }
                }
            } catch (Exception ignored) {}
        }

        Map<String, ?> allDeleted = deletedPreferences.getAll();
        for (Map.Entry<String, ?> entry : allDeleted.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                if (noteDate != null && noteDate.before(deleteThreshold)) {
                    deletedPreferences.edit().remove(dateKey).apply();
                }
            } catch (Exception ignored) {}
        }
    }

    private void initSelectionBar() {
        selectionBar = findViewById(R.id.selectionBar);
        selectionCountText = findViewById(R.id.selectionCountText);
        
        findViewById(R.id.cancelSelectionBtn).setOnClickListener(v -> exitSelectionMode());
        
        findViewById(R.id.deleteSelectedBtn).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Selected")
                    .setMessage("Move selected notes to trash?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteSelectedNotes())
                    .setNegativeButton("No", null)
                    .show();
        });
        
        findViewById(R.id.archiveSelectedBtn).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Archive Selected")
                    .setMessage("Move selected notes to archive?")
                    .setPositiveButton("Yes", (dialog, which) -> archiveSelectedNotes())
                    .setNegativeButton("No", null)
                    .show();
        });

        findViewById(R.id.copySelectedBtn).setOnClickListener(v -> {
            showCopyDatePicker();
        });
    }

    private void showCopyDatePicker() {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date d = sdf.parse(currentDateKey);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar target = Calendar.getInstance();
            target.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String newDateKey = sdf.format(target.getTime());

            processBatchCopy(newDateKey);
            exitSelectionMode();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void processBatchCopy(String targetDateKey) {
        // We only copy from the CURRENT visible list's source prefs to the same source prefs on a new date.
        // Or actually, if multiple are selected from history, we should preserve their type.
        
        java.util.Map<String, java.util.Map<String, java.util.List<Integer>>> grouped = new java.util.HashMap<>();
        for (String id : selectedNotes) {
            String[] parts = id.split(":");
            String prefsName = parts[0];
            String dateKey = parts[1];
            int index = Integer.parseInt(parts[2]);
            
            grouped.computeIfAbsent(prefsName, k -> new java.util.HashMap<>())
                   .computeIfAbsent(dateKey, k -> new java.util.ArrayList<>())
                   .add(index);
        }

        for (Map.Entry<String, java.util.Map<String, java.util.List<Integer>>> prefsEntry : grouped.entrySet()) {
            SharedPreferences prefs = getSharedPreferences(prefsEntry.getKey(), Context.MODE_PRIVATE);
            
            // For copying, we append to the target date
            String targetText = prefs.getString(targetDateKey, "");
            java.util.List<String> targetList = new java.util.ArrayList<>();
            if (!targetText.isEmpty()) targetList.addAll(java.util.Arrays.asList(targetText.split("\n")));

            for (Map.Entry<String, java.util.List<Integer>> dateEntry : prefsEntry.getValue().entrySet()) {
                String dateKey = dateEntry.getKey();
                java.util.List<Integer> indices = dateEntry.getValue();
                
                String sourceText = prefs.getString(dateKey, "");
                if (sourceText.isEmpty()) continue;
                java.util.List<String> sourceList = java.util.Arrays.asList(sourceText.split("\n"));
                
                for (int index : indices) {
                    if (index >= 0 && index < sourceList.size()) {
                        targetList.add(sourceList.get(index));
                    }
                }
            }
            
            if (!targetList.isEmpty()) {
                prefs.edit().putString(targetDateKey, String.join("\n", targetList)).apply();
            }
        }
        
        Toast.makeText(this, "Notes copied to " + targetDateKey, Toast.LENGTH_SHORT).show();
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedNotes.clear();
        selectionBar.setVisibility(View.GONE);
        loadRemarksForSelectedDate();
        updateRemarkHistory();
    }

    private void toggleSelection(String noteId) {
        if (selectedNotes.contains(noteId)) {
            selectedNotes.remove(noteId);
        } else {
            selectedNotes.add(noteId);
        }
        
        if (selectedNotes.isEmpty()) {
            exitSelectionMode();
        } else {
            selectionCountText.setText(getString(R.string.items_selected, selectedNotes.size()));
            loadRemarksForSelectedDate();
            updateRemarkHistory();
        }
    }

    private void deleteSelectedNotes() {
        // Simplified batch delete logic:
        processBatchAction("delete");
        exitSelectionMode();
    }

    private void archiveSelectedNotes() {
        processBatchAction("archive");
        exitSelectionMode();
    }

    private void processBatchAction(String action) {
        // Group by prefs and date
        java.util.Map<String, java.util.Map<String, java.util.List<Integer>>> grouped = new java.util.HashMap<>();
        for (String id : selectedNotes) {
            String[] parts = id.split(":");
            String prefsName = parts[0];
            String dateKey = parts[1];
            int index = Integer.parseInt(parts[2]);
            
            grouped.computeIfAbsent(prefsName, k -> new java.util.HashMap<>())
                   .computeIfAbsent(dateKey, k -> new java.util.ArrayList<>())
                   .add(index);
        }

        for (Map.Entry<String, java.util.Map<String, java.util.List<Integer>>> prefsEntry : grouped.entrySet()) {
            SharedPreferences prefs = getSharedPreferences(prefsEntry.getKey(), Context.MODE_PRIVATE);
            for (Map.Entry<String, java.util.List<Integer>> dateEntry : prefsEntry.getValue().entrySet()) {
                String dateKey = dateEntry.getKey();
                java.util.List<Integer> indices = dateEntry.getValue();
                indices.sort(java.util.Collections.reverseOrder());
                
                String currentText = prefs.getString(dateKey, "");
                if (currentText.isEmpty()) continue;
                java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(currentText.split("\n")));
                
                for (int index : indices) {
                    if (index >= 0 && index < list.size()) {
                        String note = list.remove(index);
                        if (action.equals("delete")) {
                            if (!prefsEntry.getKey().equals("DeletedNotes")) {
                                String currentDeleted = deletedPreferences.getString(dateKey, "");
                                String updatedDeleted = currentDeleted.isEmpty() ? note : currentDeleted + "\n" + note;
                                deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
                            }
                        } else if (action.equals("archive")) {
                            if (!prefsEntry.getKey().equals("ArchivedNotes")) {
                                String currentArchived = archivePreferences.getString(dateKey, "");
                                String updatedArchived = currentArchived.isEmpty() ? note : currentArchived + "\n" + note;
                                archivePreferences.edit().putString(dateKey, updatedArchived).apply();
                            }
                        }
                    }
                }
                
                if (list.isEmpty()) prefs.edit().remove(dateKey).apply();
                else prefs.edit().putString(dateKey, String.join("\n", list)).apply();
            }
        }
        Toast.makeText(this, "Action completed", Toast.LENGTH_SHORT).show();
    }

    private String getPrefsName(SharedPreferences prefs) {
        if (prefs == sharedPreferences) return "CalendarNotes";
        if (prefs == archivePreferences) return "ArchivedNotes";
        if (prefs == deletedPreferences) return "DeletedNotes";
        return "";
    }

    private void showBackupDataDialog() {
        String[] options = {"Export Data (Save Backup)", "Import Data (Restore Backup)"};
        new AlertDialog.Builder(this)
                .setTitle("Backup & Restore")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startExport();
                    else startImport();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startExport() {

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "SAR_Calendar_Backup_" + timeStamp + ".json");
        exportLauncher.launch(intent);
    }

    private void performExport(android.net.Uri uri) {
        try {
            String json = BackupManager.createBackupJson(this);
            if (json == null) {
                Toast.makeText(this, "Export creation failed", Toast.LENGTH_SHORT).show();
                return;
            }
            android.os.ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd != null) {
                java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(pfd.getFileDescriptor());
                fileOutputStream.write(json.getBytes());
                fileOutputStream.close();
                pfd.close();
                Toast.makeText(this, "Data exported successfully!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startImport() {
        new AlertDialog.Builder(this)
                .setTitle("Import Data")
                .setMessage("Warning: Importing data will overwrite all current notes and settings. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    importLauncher.launch(intent);
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void performImport(android.net.Uri uri) {
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            inputStream.close();

            boolean success = BackupManager.restoreBackupJson(this, stringBuilder.toString());
            if (success) {
                Toast.makeText(this, "Data imported successfully! Restarting app...", Toast.LENGTH_LONG).show();
                // Restart activity to apply changes
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    Runtime.getRuntime().exit(0);
                }, 2000);
            } else {
                Toast.makeText(this, "Import failed: Invalid backup file", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        monthYearText.setText(sdf.format(calendar.getTime()));

        ArrayList<Date> days = new ArrayList<>();
        Calendar tempCal = (Calendar) calendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int targetMonth = tempCal.get(Calendar.MONTH);
        
        int firstDayOfWeek = (tempCal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        tempCal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

        while (days.size() < 35) {
            days.add(tempCal.getTime());
            tempCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (tempCal.get(Calendar.MONTH) == targetMonth) {
            while (days.size() < 42) {
                days.add(tempCal.getTime());
                tempCal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        int rowCount = days.size() / 7;
        float density = getResources().getDisplayMetrics().density;
        int gridHeightPx = (int) ((rowCount * 60 + rowCount - 1) * density);
        android.view.ViewGroup.LayoutParams params = calendarGrid.getLayoutParams();
        if (params != null) {
            params.height = gridHeightPx;
            calendarGrid.setLayoutParams(params);
        }

        Map<String, List<NotificationEvent>> eventMap = new java.util.HashMap<>();
        List<NotificationEvent> allEvents = TransactionDbHelper.getInstance(this).getAllActiveNotifications();
        for (NotificationEvent e : allEvents) {
            String date = e.getDate();
            if (!eventMap.containsKey(date)) {
                eventMap.put(date, new ArrayList<>());
            }
            eventMap.get(date).add(e);
        }

        adapter = new CalendarAdapter(this, days, calendar, eventMap);
        calendarGrid.setAdapter(adapter);
        
        updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
    }

    private void updateDateInfo(int year, int month, int dayOfMonth) {
        selectedDate.set(year, month, dayOfMonth);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        currentDateKey = sdf.format(selectedDate.getTime());

        SimpleDateFormat displaySdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        remarkLabel.setText(getString(R.string.remark_for, displaySdf.format(selectedDate.getTime())));

        loadRemarksForSelectedDate();
        if (!noteInput.hasFocus() && !isKeyboardModeActive) {
            noteInput.setText("");
        }
    }

    private void loadRemarksForSelectedDate() {
        dayRemarksContainer.removeAllViews();
        
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");

        if (!savedRemarks.isEmpty()) {
            String[] remarksArray = savedRemarks.split("\n");
            for (int i = 0; i < remarksArray.length; i++) {
                addRemarkView(remarksArray[i], i, sharedPreferences);
            }
        } else {
            TextView noRemarks = new TextView(this);
            noRemarks.setText(getString(R.string.no_notes_day));
            applyFontSettings(noRemarks, 16);
            noRemarks.setTextColor(Color.WHITE);
            dayRemarksContainer.addView(noRemarks);
        }
    }

    private void appendVoiceToNote(String text, int index, String dateKey, SharedPreferences prefs) {
        String currentText = prefs.getString(dateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String note = remarksList.get(index);
            remarksList.set(index, note + " " + text);
            prefs.edit().putString(dateKey, String.join("\n", remarksList)).apply();
            if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
            updateRemarkHistory();
        }
        voiceTargetIndex = -1;
        voiceTargetPrefs = null;
        voiceTargetDateKey = null;
    }

    private void addRemarkView(String remarkText, int index, SharedPreferences sourcePrefs) {
        String noteId = getPrefsName(sourcePrefs) + ":" + currentDateKey + ":" + index;
        
        LinearLayout horizontalLayout = new LinearLayout(this);
        horizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
        horizontalLayout.setGravity(Gravity.CENTER_VERTICAL);
        horizontalLayout.setPadding(0, 4, 0, 4);
        
        if (selectedNotes.contains(noteId)) {
            horizontalLayout.setBackgroundColor(Color.parseColor("#6633B5E5"));
        }

        TextView textView = createRemarkTextView(remarkText, index, sourcePrefs);
        horizontalLayout.addView(textView);

        textView.setOnLongClickListener(v -> {
            if (isSelectionMode) return false;

            android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
            popup.getMenu().add(0, 7, 0, "Select").setIcon(R.drawable.ic_menu_tx_all_color);
            popup.getMenu().add(0, 8, 0, "Voice Append").setIcon(android.R.drawable.ic_btn_speak_now);
            popup.getMenu().add(0, 1, 0, "Reminder").setIcon(R.drawable.ic_menu_reminder_color);
            popup.getMenu().add(0, 2, 0, "Edit").setIcon(R.drawable.ic_menu_edit_color);
            popup.getMenu().add(0, 3, 0, "Share").setIcon(R.drawable.ic_menu_share_color);
            popup.getMenu().add(0, 6, 0, "Move").setIcon(R.drawable.ic_menu_transfer_color);
            popup.getMenu().add(0, 4, 0, "Archive").setIcon(R.drawable.ic_menu_archive_color);
            popup.getMenu().add(0, 5, 0, "Delete").setIcon(R.drawable.ic_menu_trash_color);

            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuHelper = field.get(popup);
                Class<?> classPopupHelper = Class.forName(menuHelper.getClass().getName());
                java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                setForceIcons.invoke(menuHelper, true);
            } catch (Exception ignored) {}

            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 7) {
                    isSelectionMode = true;
                    selectionBar.setVisibility(View.VISIBLE);
                    toggleSelection(noteId);
                } else if (id == 8) {
                    voiceTargetIndex = index;
                    voiceTargetPrefs = sourcePrefs;
                    voiceTargetDateKey = currentDateKey;
                    startVoiceRecognition();
                } else if (id == 1) manageReminder(remarkText);
                else if (id == 2) showEditDialog(remarkText, index, sourcePrefs);
                else if (id == 3) {
                    String[] options = {"Share as Text", "Share as .ics File"};
                    new AlertDialog.Builder(this)
                            .setTitle("Share Note")
                            .setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    String noteBody = getNoteBody(remarkText);
                                    String shareText = "SAR CALENDAR REMINDER:\n" + noteBody + "\nDate: " + currentDateKey;
                                    String reminderKeyForShare = currentDateKey + "_" + remarkText;
                                    String savedTime = reminderPreferences.getString(reminderKeyForShare, null);
                                    String gCalUrl = getGoogleCalendarUrl(noteBody, currentDateKey, savedTime);
                                    if (!gCalUrl.isEmpty()) shareText += "\n\nAdd to Google Calendar:\n" + gCalUrl;
                                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                                    sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                                    sendIntent.setType("text/plain");
                                    startActivity(Intent.createChooser(sendIntent, "Share Note via"));
                                } else shareNoteAsIcs(remarkText, currentDateKey);
                            }).show();
                }
                else if (id == 4) archiveNote(index);
                else if (id == 5) deleteRemark(index, sourcePrefs);
                else if (id == 6) showMoveDatePicker(remarkText, index, sourcePrefs, currentDateKey);
                return true;
            });
            popup.show();
            return true;
        });

        textView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(noteId);
            } else {
                toggleNoteFinished(index, sourcePrefs);
            }
        });
        
        dayRemarksContainer.addView(horizontalLayout);
    }

    private void shareNoteAsIcs(String noteText, String dateKey) {
        String noteBody = getNoteBody(noteText);
        String reminderKey = dateKey + "_" + noteText;
        String savedTime = reminderPreferences.getString(reminderKey, null);

        String icsContent = "BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//SAR Calendar//EN\n" +
                "BEGIN:VEVENT\n" +
                "SUMMARY:" + noteBody + "\n";
        
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            if (date != null) {
                if (savedTime != null) {
                    SimpleDateFormat reminderSdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    Calendar cal = Calendar.getInstance();
                    boolean parsed = false;
                    try {
                        Date fullDate = reminderSdf.parse(savedTime);
                        if (fullDate != null) {
                            cal.setTime(fullDate);
                            parsed = true;
                        }
                    } catch (Exception e) {
                        if (savedTime.contains(":")) {
                            String[] parts = savedTime.split(":");
                            cal.setTime(date);
                            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                            cal.set(Calendar.SECOND, 0);
                            parsed = true;
                        }
                    }

                    if (parsed) {
                        SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                        String dateStr = icsSdf.format(cal.getTime());
                        icsContent += "DTSTART:" + dateStr + "\n";
                        cal.add(Calendar.MINUTE, 30);
                        String endDateStr = icsSdf.format(cal.getTime());
                        icsContent += "DTEND:" + endDateStr + "\n";
                    } else {
                        addDefaultIcsDates(icsContent, date);
                    }
                } else {
                    icsContent = addDefaultIcsDates(icsContent, date);
                }
            }
        } catch (Exception ignored) {}
        
        icsContent += "DESCRIPTION:SAR Calendar Note\\n\\nAdd to Google Calendar:\\n" + getGoogleCalendarUrl(noteBody, dateKey, savedTime) + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR";

        try {
            File cachePath = new File(getCacheDir(), "shared_notes");
            if (!cachePath.exists() && !cachePath.mkdirs()) return;
            File icsFile = new File(cachePath, "note_reminder.ics");
            FileOutputStream stream = new FileOutputStream(icsFile);
            stream.write(icsContent.getBytes());
            stream.close();

            android.net.Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", icsFile);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/calendar");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Note via"));
        } catch (Exception e) {
            Toast.makeText(this, "Error creating .ics file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getGoogleCalendarUrl(String title, String dateKey, String savedTime) {
        String baseUrl = "https://www.google.com/calendar/render?action=TEMPLATE";
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String dates = "";
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(dateKey);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                if (savedTime != null) {
                    SimpleDateFormat reminderSdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    try {
                        Date fullDate = reminderSdf.parse(savedTime);
                        if (fullDate != null) cal.setTime(fullDate);
                    } catch (Exception e) {
                        if (savedTime.contains(":")) {
                            String[] parts = savedTime.split(":");
                            cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
                            cal.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
                        }
                    }
                    SimpleDateFormat urlSdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());
                    String start = urlSdf.format(cal.getTime());
                    cal.add(Calendar.MINUTE, 30);
                    String end = urlSdf.format(cal.getTime());
                    dates = start + "/" + end;
                } else {
                    SimpleDateFormat urlSdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                    String start = urlSdf.format(cal.getTime());
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    String end = urlSdf.format(cal.getTime());
                    dates = start + "/" + end;
                }
            }
            return baseUrl + "&text=" + encodedTitle + "&dates=" + dates + "&details=Created+via+SAR+Calendar";
        } catch (Exception e) {
            return "";
        }
    }

    private String getNoteBody(String text) {
        if (text.startsWith("• ") || text.startsWith("□ ") || text.startsWith("▣ ")) {
            return text.substring(2);
        }
        return text;
    }

    private void toggleNoteFinished(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;

        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String note = remarksList.get(index);
            if (note.startsWith("▣ ")) {
                remarksList.set(index, getNoteBody(note));
            } else {
                remarksList.set(index, "▣ " + getNoteBody(note));
            }

            String updatedRemarks = String.join("\n", remarksList);
            sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            updateAllWidgets();
        }
    }

    private TextView createRemarkTextView(String remarkText, int index, SharedPreferences sourcePrefs) {
        TextView textView = new TextView(this);
        String displayText = (index + 1) + ". " + getNoteBody(remarkText);
        textView.setText(displayText);
        applyFontSettings(textView, 16);
        if (remarkText.startsWith("▣ ")) {
            textView.setTextColor(colorPrefs.getInt("color_note_checked", Color.GREEN));
        } else {
            textView.setTextColor(colorPrefs.getInt("color_note_text", Color.WHITE));
        }
        textView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Click listeners moved to addRemarkView to handle selection mode properly

        textView.setOnLongClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            String textToCopy = getNoteBody(remarkText);
            android.content.ClipData clip = android.content.ClipData.newPlainText("SAR Note", textToCopy);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Note copied to clipboard", Toast.LENGTH_SHORT).show();
            return true;
        });
        return textView;
    }

    private ImageButton createActionButton(int iconRes, LinearLayout.LayoutParams params, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setLayoutParams(params);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(4, 4, 4, 4);
        button.setOnClickListener(listener);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.light_green)));
        return button;
    }

    private void restoreSingleNote(String dateKey, int index, SharedPreferences sourcePrefs) {
        String currentNotes = sourcePrefs.getString(dateKey, "");
        if (currentNotes.isEmpty()) return;
        List<String> notesList = new ArrayList<>(Arrays.asList(currentNotes.split("\n")));
        if (index >= 0 && index < notesList.size()) {
            String noteToRestore = notesList.remove(index);
            String savedRemarks = sharedPreferences.getString(dateKey, "");
            String updatedSaved = savedRemarks.isEmpty() ? noteToRestore : savedRemarks + "\n" + noteToRestore;
            sharedPreferences.edit().putString(dateKey, updatedSaved).apply();
            String updatedSource = String.join("\n", notesList);
            if (updatedSource.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
            else sourcePrefs.edit().putString(dateKey, updatedSource).apply();
            updateRemarkHistory();
            if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
            adapter.notifyDataSetChanged();
            updateAllWidgets();
            Toast.makeText(this, "Note restored to personal notes", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleNote(String dateKey, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(dateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToDelete = remarksList.remove(index);
            String currentDeleted = deletedPreferences.getString(dateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? noteToDelete : currentDeleted + "\n" + noteToDelete;
            deletedPreferences.edit().putString(dateKey, updatedDeleted).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
            else sourcePrefs.edit().putString(dateKey, updatedRemarks).apply();
            updateRemarkHistory();
            if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
            updateAllWidgets();
            Toast.makeText(this, "Note moved to trash", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleNotePermanently(String dateKey, int index, SharedPreferences sourcePrefs) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Note")
                .setMessage("Permanently delete this note?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    String currentNotes = sourcePrefs.getString(dateKey, "");
                    if (currentNotes.isEmpty()) return;
                    List<String> notesList = new ArrayList<>(Arrays.asList(currentNotes.split("\n")));
                    if (index >= 0 && index < notesList.size()) {
                        notesList.remove(index);
                        String updatedSource = String.join("\n", notesList);
                        if (updatedSource.isEmpty()) sourcePrefs.edit().remove(dateKey).apply();
                        else sourcePrefs.edit().putString(dateKey, updatedSource).apply();
                        updateRemarkHistory();
                        if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
                        adapter.notifyDataSetChanged();
                        updateAllWidgets();
                        Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null).show();
    }

    private String addDefaultIcsDates(String icsContent, Date date) {
        SimpleDateFormat icsSdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String dateStr = icsSdf.format(date);
        return icsContent + "DTSTART;VALUE=DATE:" + dateStr + "\n" +
                "DTEND;VALUE=DATE:" + dateStr + "\n";
    }

    private void manageReminder(String noteText) {
        String reminderKey = currentDateKey + "_" + noteText;
        String savedValue = reminderPreferences.getString(reminderKey, null);
        if (savedValue != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Manage Reminder")
                    .setMessage("Currently set for: " + savedValue)
                    .setPositiveButton("Edit", (dialog, which) -> showReminderPicker(noteText))
                    .setNegativeButton("Delete", (dialog, which) -> deleteReminder(noteText))
                    .setNeutralButton("Cancel", null).show();
        } else {
            showReminderPicker(noteText);
        }
    }

    private void showReminderPicker(String noteText) {
        Calendar current = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date noteDate = sdf.parse(currentDateKey);
            if (noteDate != null) {
                current.setTime(noteDate);
                Calendar now = Calendar.getInstance();
                if (current.before(now)) {
                    current.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
                    current.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
                    current.add(Calendar.MINUTE, 5);
                }
            }
        } catch (Exception ignored) {}
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar pickedDate = Calendar.getInstance();
            pickedDate.set(year, month, day);
            new TimePickerDialog(this, (v, hour, minute) -> {
                pickedDate.set(Calendar.HOUR_OF_DAY, hour);
                pickedDate.set(Calendar.MINUTE, minute);
                pickedDate.set(Calendar.SECOND, 0);
                setReminder(pickedDate, noteText);
            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), false).show();
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setReminder(Calendar reminderTime, String noteText) {
        if (reminderTime.before(Calendar.getInstance())) {
            Toast.makeText(this, "Cannot set reminder in the past!", Toast.LENGTH_SHORT).show();
            return;
        }
        String reminderKey = currentDateKey + "_" + noteText;
        int requestCode = reminderKey.hashCode();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("noteText", noteText);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTime.getTimeInMillis(), pendingIntent);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String timeValue = sdf.format(reminderTime.getTime());
        reminderPreferences.edit().putString(reminderKey, timeValue).apply();
        loadRemarksForSelectedDate();
        Toast.makeText(this, "Reminder set for " + timeValue, Toast.LENGTH_SHORT).show();
    }

    private void deleteReminder(String noteText) {
        String reminderKey = currentDateKey + "_" + noteText;
        int requestCode = reminderKey.hashCode();
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
        reminderPreferences.edit().remove(reminderKey).apply();
        loadRemarksForSelectedDate();
        Toast.makeText(this, "Reminder deleted", Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(String currentText, int index, SharedPreferences sourcePrefs) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Note");
        final EditText input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setText(getNoteBody(currentText));
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!updatedText.isEmpty()) updateRemark(updatedText, index, sourcePrefs);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel()).show();
    }

    private void updateRemark(String newText, int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.set(index, newText);
            String updatedRemarks = String.join("\n", remarksList);
            sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            updateAllWidgets();
        }
    }

    private void archiveNote(int index) {
        new AlertDialog.Builder(this).setTitle("Archive Note").setMessage("Are you sure you want to archive this note?")
                .setPositiveButton("Yes", (dialog, which) -> performArchiveNote(index))
                .setNegativeButton("No", null).show();
    }

    private void performArchiveNote(int index) {
        String savedRemarks = sharedPreferences.getString(currentDateKey, "");
        if (savedRemarks.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(savedRemarks.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            String noteToArchive = remarksList.remove(index);
            String archivedRemarks = archivePreferences.getString(currentDateKey, "");
            String updatedArchived = archivedRemarks.isEmpty() ? noteToArchive : archivedRemarks + "\n" + noteToArchive;
            archivePreferences.edit().putString(currentDateKey, updatedArchived).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sharedPreferences.edit().remove(currentDateKey).apply();
            else sharedPreferences.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            updateAllWidgets();
            Toast.makeText(this, R.string.archive_success, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteRemark(int index, SharedPreferences sourcePrefs) {
        if (sourcePrefs == deletedPreferences) {
            new AlertDialog.Builder(this).setTitle("Delete Note").setMessage("Permanently delete this note?")
                    .setPositiveButton("Yes", (dialog, which) -> performPermanentDelete(index, sourcePrefs))
                    .setNegativeButton("No", null).show();
            return;
        }
        String[] options = {"Move to Trash", "Delete Permanently"};
        new AlertDialog.Builder(this).setTitle("Delete Note").setItems(options, (dialog, which) -> {
            if (which == 0) performDeleteRemark(index, sourcePrefs);
            else performPermanentDelete(index, sourcePrefs);
        }).setNegativeButton("Cancel", null).show();
    }

    private void performPermanentDelete(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            remarksList.remove(index);
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(currentDateKey).apply();
            else sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            updateAllWidgets();
            Toast.makeText(this, "Permanently Deleted", Toast.LENGTH_SHORT).show();
        }
    }

    private void performDeleteRemark(int index, SharedPreferences sourcePrefs) {
        String currentText = sourcePrefs.getString(currentDateKey, "");
        if (currentText.isEmpty()) return;
        List<String> remarksList = new ArrayList<>(Arrays.asList(currentText.split("\n")));
        if (index >= 0 && index < remarksList.size()) {
            lastDeletedNote = remarksList.get(index);
            lastDeletedIndex = index;
            lastDeletedDateKey = currentDateKey;
            remarksList.remove(index);
            String currentDeleted = deletedPreferences.getString(currentDateKey, "");
            String updatedDeleted = currentDeleted.isEmpty() ? lastDeletedNote : currentDeleted + "\n" + lastDeletedNote;
            deletedPreferences.edit().putString(currentDateKey, updatedDeleted).apply();
            String updatedRemarks = String.join("\n", remarksList);
            if (updatedRemarks.isEmpty()) sourcePrefs.edit().remove(currentDateKey).apply();
            else sourcePrefs.edit().putString(currentDateKey, updatedRemarks).apply();
            loadRemarksForSelectedDate();
            updateRemarkHistory();
            adapter.notifyDataSetChanged();
            deletedHistoryContainer.setVisibility(View.VISIBLE);
            showUndoSnackbar();
            updateAllWidgets();
        }
    }

    private void showUndoSnackbar() {
        Snackbar.make(findViewById(R.id.main), "Note moved to trash", Snackbar.LENGTH_LONG)
                .setAction("UNDO", v -> undoDelete())
                .show();
    }

    private void undoDelete() {
        if (lastDeletedNote == null || lastDeletedDateKey == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date noteDate = sdf.parse(lastDeletedDateKey);
            Date today = sdf.parse(todayKey);
            if (noteDate != null && today != null && noteDate.before(today)) targetPrefs = archivePreferences;
        } catch (Exception ignored) {}
        String currentRemarks = targetPrefs.getString(lastDeletedDateKey, "");
        List<String> remarksList = new ArrayList<>();
        if (!currentRemarks.isEmpty()) remarksList.addAll(Arrays.asList(currentRemarks.split("\n")));
        if (lastDeletedIndex >= 0 && lastDeletedIndex <= remarksList.size()) remarksList.add(lastDeletedIndex, lastDeletedNote);
        else remarksList.add(lastDeletedNote);
        String updatedRemarks = String.join("\n", remarksList);
        targetPrefs.edit().putString(lastDeletedDateKey, updatedRemarks).apply();
        if (Objects.equals(lastDeletedDateKey, currentDateKey)) loadRemarksForSelectedDate();
        String currentDeleted = deletedPreferences.getString(lastDeletedDateKey, "");
        if (!currentDeleted.isEmpty()) {
            List<String> deletedList = new ArrayList<>(Arrays.asList(currentDeleted.split("\n")));
            deletedList.remove(lastDeletedNote);
            if (deletedList.isEmpty()) deletedPreferences.edit().remove(lastDeletedDateKey).apply();
            else deletedPreferences.edit().putString(lastDeletedDateKey, String.join("\n", deletedList)).apply();
        }
        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        lastDeletedNote = null;
        lastDeletedDateKey = null;
        Toast.makeText(this, "Restored", Toast.LENGTH_SHORT).show();
    }

    private void saveNote() {
        String newText = noteInput.getText().toString().trim();
        if (newText.isEmpty()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show();
            return;
        }
        saveNoteForDate(currentDateKey, newText);
        noteInput.setText("");
    }

    private void saveNoteForDate(String dateKey, String noteText) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        SharedPreferences targetPrefs = sharedPreferences;
        try {
            Date selected = sdf.parse(dateKey);
            Date today = sdf.parse(todayKey);
            if (selected != null && today != null && selected.before(today)) targetPrefs = archivePreferences;
        } catch (Exception ignored) {}
        String existingRemarks = targetPrefs.getString(dateKey, "");
        String updatedRemarks = existingRemarks.isEmpty() ? noteText : existingRemarks + "\n" + noteText;
        targetPrefs.edit().putString(dateKey, updatedRemarks).apply();
        if (Objects.equals(dateKey, currentDateKey)) loadRemarksForSelectedDate();
        updateRemarkHistory();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        Toast.makeText(this, "Note added!", Toast.LENGTH_SHORT).show();
    }

    private void startVoiceRecognition() {
        startSpeechRecognitionWithSettings();
    }

    private void showSpeechTranslationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        
        // Inflate custom layout or build programmatically
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 40, 60, 40);
        root.setBackgroundColor(Color.parseColor("#1A1A1A"));

        TextView title = new TextView(this);
        title.setText("Speech & Translation");
        title.setTextColor(Color.parseColor("#8BC34A"));
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        // Auto Language
        LinearLayout autoLangLayout = new LinearLayout(this);
        autoLangLayout.setOrientation(LinearLayout.HORIZONTAL);
        autoLangLayout.setGravity(Gravity.CENTER_VERTICAL);
        autoLangLayout.setPadding(0, 40, 0, 20);
        
        TextView autoLangText = new TextView(this);
        autoLangText.setText("Auto language");
        autoLangText.setTextColor(Color.WHITE);
        autoLangText.setTextSize(16);
        autoLangText.setTypeface(null, Typeface.BOLD);
        autoLangText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        autoLangLayout.addView(autoLangText);

        androidx.appcompat.widget.SwitchCompat autoLangSwitch = new androidx.appcompat.widget.SwitchCompat(this);
        autoLangSwitch.setChecked(speechAutoLang);
        autoLangLayout.addView(autoLangSwitch);
        root.addView(autoLangLayout);

        // Speaking Language Label
        TextView speakingLabel = new TextView(this);
        speakingLabel.setText("Speaking Language:");
        speakingLabel.setTextColor(Color.WHITE);
        speakingLabel.setPadding(0, 20, 0, 10);
        root.addView(speakingLabel);

        // Speaking Language Selector
        TextView speakingSelector = new TextView(this);
        speakingSelector.setText(getLanguageName(speechSourceLang));
        speakingSelector.setTextColor(Color.WHITE);
        speakingSelector.setBackgroundResource(R.drawable.summary_border);
        speakingSelector.setPadding(30, 30, 30, 30);
        speakingSelector.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Select Speaking Language")
                    .setItems(languageNames, (dialog, which) -> {
                        speechSourceLang = languageCodes[which];
                        speakingSelector.setText(languageNames[which]);
                    }).show();
        });
        root.addView(speakingSelector);

        // Translate Toggle
        LinearLayout translateLayout = new LinearLayout(this);
        translateLayout.setOrientation(LinearLayout.HORIZONTAL);
        translateLayout.setGravity(Gravity.CENTER_VERTICAL);
        translateLayout.setPadding(0, 40, 0, 20);

        TextView translateText = new TextView(this);
        translateText.setText("Translate");
        translateText.setTextColor(Color.WHITE);
        translateText.setTextSize(16);
        translateText.setTypeface(null, Typeface.BOLD);
        translateText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        translateLayout.addView(translateText);

        androidx.appcompat.widget.SwitchCompat translateSwitch = new androidx.appcompat.widget.SwitchCompat(this);
        translateSwitch.setChecked(speechTranslateEnabled);
        translateLayout.addView(translateSwitch);
        root.addView(translateLayout);

        // Translation Container
        LinearLayout translationContainer = new LinearLayout(this);
        translationContainer.setOrientation(LinearLayout.VERTICAL);
        translationContainer.setVisibility(speechTranslateEnabled ? View.VISIBLE : View.GONE);
        
        // Translate to Label
        TextView translateToLabel = new TextView(this);
        translateToLabel.setText("Translate to:");
        translateToLabel.setTextColor(Color.WHITE);
        translateToLabel.setPadding(0, 20, 0, 10);
        translationContainer.addView(translateToLabel);

        // Translate to Selector
        TextView translateToSelector = new TextView(this);
        translateToSelector.setText(getLanguageName(speechTargetLang));
        translateToSelector.setTextColor(Color.WHITE);
        translateToSelector.setBackgroundResource(R.drawable.summary_border);
        translateToSelector.setPadding(30, 30, 30, 30);
        translateToSelector.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Select Translation Language")
                    .setItems(languageNames, (dialog, which) -> {
                        speechTargetLang = languageCodes[which];
                        translateToSelector.setText(languageNames[which]);
                    }).show();
        });
        translationContainer.addView(translateToSelector);
        root.addView(translationContainer);

        translateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            translationContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        builder.setView(root);
        builder.setPositiveButton("SAVE", (dialog, which) -> {
            speechAutoLang = autoLangSwitch.isChecked();
            speechTranslateEnabled = translateSwitch.isChecked();

            SharedPreferences.Editor editor = speechPrefs.edit();
            editor.putString("speech_source_lang", speechSourceLang);
            editor.putString("speech_target_lang", speechTargetLang);
            editor.putBoolean("speech_auto_lang", speechAutoLang);
            editor.putBoolean("speech_translate_enabled", speechTranslateEnabled);
            editor.apply();
        });

        builder.setNegativeButton("CANCEL", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();

        // Style positive button
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#8BC34A"));
    }

    private final String[] languageNames = {"English", "Arabic", "French", "Spanish", "German", "Chinese", "Italian", "Japanese", "Russian", "Portuguese"};
    private final String[] languageCodes = {"en-US", "ar", "fr", "es", "de", "zh", "it", "ja", "ru", "pt"};

    private String getLanguageName(String code) {
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(code)) return languageNames[i];
        }
        return "English";
    }

    private void startSpeechRecognitionWithSettings() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        
        if (!speechAutoLang) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechSourceLang);
        } else {
            // Auto mode: set language to current speechSourceLang as hint but allow broad detection
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechSourceLang);
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", languageCodes);
            intent.putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true);
            // Some newer versions support multi-language detection with these extras
            intent.putExtra("android.speech.extra.LANGUAGE_DETECTION_MODE", 1); 
        }
        
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, isVoiceCommandMode ? "Listening for command..." : "Speak now...");
        try {
            voiceRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
            isVoiceCommandMode = false;
        }
    }

    private void showNewNoteDialog(String initialText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Note");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final Calendar noteDate = (Calendar) selectedDate.clone();
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        final Button dateButton = new Button(this);
        dateButton.setText("Date: " + sdf.format(noteDate.getTime()));
        applyFontSettings(dateButton, 14);
        dateButton.setBackgroundTintList(ColorStateList.valueOf(colorPrefs.getInt("color_main_theme", getColor(R.color.light_green))));
        dateButton.setTextColor(Color.WHITE);
        dateButton.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                noteDate.set(year, month, dayOfMonth);
                dateButton.setText("Date: " + sdf.format(noteDate.getTime()));
            }, noteDate.get(Calendar.YEAR), noteDate.get(Calendar.MONTH), noteDate.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(dateButton);

        final EditText input = new EditText(this);
        input.setHint("Enter note here...");
        input.setText(initialText);
        applyFontSettings(input, 18);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        layout.addView(input);

        final ImageButton voiceBtn = new ImageButton(this);
        voiceBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
        voiceBtn.setBackgroundColor(Color.TRANSPARENT);
        voiceBtn.setOnClickListener(v -> {
            currentDialogInput = input;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note...");
            try {
                voiceRecognitionLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(voiceBtn);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            currentDialogInput = null;
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                saveNoteForDate(sdf.format(noteDate.getTime()), text);
            } else {
                Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show();
            }
            if (getIntent() != null && getIntent().getAction() != null) finishAndRemoveTask();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogInput = null;
            dialog.cancel();
            if (getIntent() != null && getIntent().getAction() != null) finishAndRemoveTask();
        });
        builder.show();
    }

    private void showMoveDatePicker(String remarkText, int index, SharedPreferences sourcePrefs, String oldDateKey) {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date d = sdf.parse(oldDateKey);
            if (d != null) cal.setTime(d);
        } catch (Exception ignored) {}

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar target = Calendar.getInstance();
            target.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String newDateKey = sdf.format(target.getTime());

            if (newDateKey.equals(oldDateKey)) {
                Toast.makeText(this, "Already on this date", Toast.LENGTH_SHORT).show();
                return;
            }

            performMoveNote(remarkText, index, sourcePrefs, oldDateKey, newDateKey);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void performMoveNote(String remarkText, int index, SharedPreferences sourcePrefs, String oldDateKey, String newDateKey) {
        // 1. Remove from old date
        String oldData = sourcePrefs.getString(oldDateKey, "");
        if (oldData.isEmpty()) return;
        List<String> oldList = new ArrayList<>(Arrays.asList(oldData.split("\n")));
        if (index >= 0 && index < oldList.size()) {
            oldList.remove(index);
            if (oldList.isEmpty()) sourcePrefs.edit().remove(oldDateKey).apply();
            else sourcePrefs.edit().putString(oldDateKey, String.join("\n", oldList)).apply();
        }

        // 2. Add to new date
        String newData = sourcePrefs.getString(newDateKey, "");
        String updatedNewData = newData.isEmpty() ? remarkText : newData + "\n" + remarkText;
        sourcePrefs.edit().putString(newDateKey, updatedNewData).apply();

        // 3. Migrate Reminder if exists
        String oldReminderKey = oldDateKey + "_" + remarkText;
        String savedTime = reminderPreferences.getString(oldReminderKey, null);
        if (savedTime != null) {
            // Cancel old
            int oldRequestCode = oldReminderKey.hashCode();
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, oldRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            reminderPreferences.edit().remove(oldReminderKey).apply();

            // Calculate new time
            try {
                SimpleDateFormat sdfFull = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date oldDateTime = sdfFull.parse(savedTime);
                if (oldDateTime != null) {
                    Calendar oldCal = Calendar.getInstance();
                    oldCal.setTime(oldDateTime);

                    SimpleDateFormat sdfDay = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    Date newDate = sdfDay.parse(newDateKey);
                    if (newDate != null) {
                        Calendar newCal = Calendar.getInstance();
                        newCal.setTime(newDate);
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY));
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE));
                        newCal.set(Calendar.SECOND, 0);

                        setReminder(newCal, remarkText); // This handles the new date key internally
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. Refresh UI
        loadRemarksForSelectedDate();
        updateRemarkHistory();
        if (sourcePrefs == archivePreferences) archiveHistoryContainer.removeAllViews(); // Will be reloaded if expanded
        if (sourcePrefs == deletedPreferences) deletedHistoryContainer.removeAllViews();
        
        // Full refresh of history if they are expanded
        if (remarkHistoryContainer.getVisibility() == View.VISIBLE) {
            remarkHistoryContainer.removeAllViews();
            loadHistoryFromPrefs(sharedPreferences, remarkHistoryContainer, R.string.no_notes_saved, colorPrefs.getInt("color_main_theme", getColor(R.color.light_green)));
        }
        if (archiveHistoryContainer.getVisibility() == View.VISIBLE) {
            archiveHistoryContainer.removeAllViews();
            loadHistoryFromPrefs(archivePreferences, archiveHistoryContainer, R.string.no_archived_notes, colorPrefs.getInt("color_archive", Color.YELLOW));
        }
        if (deletedHistoryContainer.getVisibility() == View.VISIBLE) {
            deletedHistoryContainer.removeAllViews();
            loadHistoryFromPrefs(deletedPreferences, deletedHistoryContainer, R.string.no_deleted_notes, colorPrefs.getInt("color_deleted", getColor(R.color.chili_red)));
        }

        adapter.notifyDataSetChanged();
        updateAllWidgets();
        Toast.makeText(this, "Moved to " + newDateKey, Toast.LENGTH_SHORT).show();
    }

    private void updateRemarkHistory() {
        remarkHistoryContainer.removeAllViews();
        archiveHistoryContainer.removeAllViews();
        deletedHistoryContainer.removeAllViews();

        int mainTheme = colorPrefs.getInt("color_main_theme", getColor(R.color.light_green));
        int archiveColor = colorPrefs.getInt("color_archive", Color.YELLOW);
        int deletedColor = colorPrefs.getInt("color_deleted", getColor(R.color.chili_red));

        int activeCount = countTotalNotes(sharedPreferences);
        TextView activeTitle = findViewById(R.id.remarkHistoryTitle);
        if (activeTitle != null) {
            String countText = getString(R.string.all_personal_notes) + " (" + activeCount + ")";
            activeTitle.setText(countText);
            activeTitle.setTextColor(mainTheme);
            applyFontSettings(activeTitle, 18);
        }
        loadHistoryFromPrefs(sharedPreferences, remarkHistoryContainer, R.string.no_notes_saved, mainTheme);
        int archiveCount = countTotalNotes(archivePreferences);
        TextView archiveTitle = findViewById(R.id.archiveHistoryTitle);
        if (archiveTitle != null) {
            String countText = getString(R.string.archive_folder) + " (" + archiveCount + ")";
            archiveTitle.setText(countText);
            archiveTitle.setTextColor(archiveColor);
            applyFontSettings(archiveTitle, 18);
        }
        loadHistoryFromPrefs(archivePreferences, archiveHistoryContainer, R.string.no_archived_notes, archiveColor);
        int deletedCount = countTotalNotes(deletedPreferences);
        TextView deletedTitle = findViewById(R.id.deletedHistoryTitle);
        if (deletedTitle != null) {
            String countText = getString(R.string.deleted_notes_title) + " (" + deletedCount + ")";
            deletedTitle.setText(countText);
            deletedTitle.setTextColor(deletedColor);
            applyFontSettings(deletedTitle, 18);
        }
        loadHistoryFromPrefs(deletedPreferences, deletedHistoryContainer, R.string.no_deleted_notes, deletedColor);
    }

    private int countTotalNotes(SharedPreferences prefs) {
        int count = 0;
        Map<String, ?> allEntries = prefs.getAll();
        for (Object entry : allEntries.values()) {
            if (entry != null) {
                String val = entry.toString();
                if (!val.isEmpty()) count += val.split("\n").length;
            }
        }
        return count;
    }

    private void archiveAllPastNotes() {
        int movedCount = archiveAllPastNotesSilent();
        updateRemarkHistory();
        loadRemarksForSelectedDate();
        adapter.notifyDataSetChanged();
        updateAllWidgets();
        if (movedCount > 0) {
            archiveHistoryContainer.setVisibility(View.VISIBLE);
            Toast.makeText(this, movedCount + " dates moved to archive", Toast.LENGTH_SHORT).show();
        } else Toast.makeText(this, "No old notes to archive", Toast.LENGTH_SHORT).show();
        mainScrollView.post(() -> {
            View title = findViewById(R.id.archiveHistoryTitle);
            if (title != null) mainScrollView.smoothScrollTo(0, title.getTop());
        });
    }

    private int archiveAllPastNotesSilent() {
        Map<String, ?> allEntries = sharedPreferences.getAll();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        int movedCount = 0;
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String dateKey = entry.getKey();
            try {
                Date noteDate = sdf.parse(dateKey);
                Date todayDate = sdf.parse(todayKey);
                if (noteDate != null && todayDate != null && noteDate.before(todayDate)) {
                    String value = entry.getValue().toString();
                    if (!value.isEmpty()) {
                        String existingArchived = archivePreferences.getString(dateKey, "");
                        String updatedArchived = existingArchived.isEmpty() ? value : existingArchived + "\n" + value;
                        archivePreferences.edit().putString(dateKey, updatedArchived).apply();
                        sharedPreferences.edit().remove(dateKey).apply();
                        movedCount++;
                    }
                }
            } catch (Exception ignored) {}
        }
        return movedCount;
    }

    private void loadHistoryFromPrefs(SharedPreferences prefs, LinearLayout container, int emptyTextRes, int dateColor) {
        Map<String, ?> allEntries = prefs.getAll();
        if (allEntries.isEmpty()) {
            TextView noHistory = new TextView(this);
            noHistory.setText(getString(emptyTextRes));
            noHistory.setTextColor(Color.WHITE);
            container.addView(noHistory);
            return;
        }
        List<String> sortedKeys = new ArrayList<>(allEntries.keySet());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayKey = sdf.format(Calendar.getInstance().getTime());
        String sortOrder = appSettingsPrefs.getString("sort_order", "DESC");
        
        sortedKeys.sort((o1, o2) -> {
            try {
                Date d1 = sdf.parse(o1);
                Date d2 = sdf.parse(o2);
                if (d1 != null && d2 != null) {
                    int cmp = d1.compareTo(d2);
                    return "ASC".equals(sortOrder) ? cmp : -cmp;
                }
            } catch (Exception ignored) {}
            int cmp = o1.compareTo(o2);
            return "ASC".equals(sortOrder) ? cmp : -cmp;
        });
        for (String dateKey : sortedKeys) {
            Object valObj = allEntries.get(dateKey);
            String value = valObj != null ? valObj.toString() : "";
            if (value.isEmpty()) continue;
            String[] notes = value.split("\n");
            TextView dateHeader = new TextView(this);
            String label = Objects.equals(dateKey, todayKey) ? "TODAY: " + dateKey : "Date: " + dateKey;
            dateHeader.setText(label);
            applyFontSettings(dateHeader, 13);
            dateHeader.setTextColor(dateColor);
            dateHeader.setPadding(0, 16, 0, 4);
            dateHeader.setOnClickListener(v -> jumpToDate(dateKey, sdf));
            container.addView(dateHeader);
            for (int i = 0; i < notes.length; i++) {
                final int index = i;
                final String noteText = notes[i];
                final String currentLoopDateKey = dateKey;
                String noteId = getPrefsName(prefs) + ":" + currentLoopDateKey + ":" + index;

                LinearLayout noteLayout = new LinearLayout(this);
                noteLayout.setOrientation(LinearLayout.HORIZONTAL);
                noteLayout.setGravity(Gravity.CENTER_VERTICAL);
                noteLayout.setPadding(32, 4, 0, 4);

                if (selectedNotes.contains(noteId)) {
                    noteLayout.setBackgroundColor(Color.parseColor("#6633B5E5"));
                }

                TextView tv = new TextView(this);
                String displayText = (index + 1) + ". " + getNoteBody(noteText);
                tv.setText(displayText);
                applyFontSettings(tv, 14);
                if (noteText.startsWith("▣ ")) {
                    tv.setTextColor(colorPrefs.getInt("color_note_checked", Color.GREEN));
                } else if (container == deletedHistoryContainer) {
                    tv.setTextColor(Color.WHITE); // Keep deleted text white, header is red
                } else {
                    tv.setTextColor(colorPrefs.getInt("color_note_text", Color.WHITE));
                }
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                noteLayout.addView(tv);

                tv.setOnClickListener(v -> {
                    if (isSelectionMode) {
                        toggleSelection(noteId);
                    }
                });

                tv.setOnLongClickListener(v -> {
                    if (isSelectionMode) return false;

                    android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                    popup.getMenu().add(0, 7, 0, "Select").setIcon(R.drawable.ic_menu_tx_all_color);
                    popup.getMenu().add(0, 8, 0, "Voice Append").setIcon(android.R.drawable.ic_btn_speak_now);
                    popup.getMenu().add(0, 1, 0, "Share").setIcon(R.drawable.ic_menu_share_color);
                    popup.getMenu().add(0, 6, 0, "Move").setIcon(R.drawable.ic_menu_transfer_color);
                    
                    if (container == archiveHistoryContainer || container == deletedHistoryContainer) {
                        popup.getMenu().add(0, 2, 0, "Restore").setIcon(R.drawable.ic_menu_restore_color);
                        popup.getMenu().add(0, 3, 0, "Delete Permanently").setIcon(R.drawable.ic_menu_trash_color);
                    } else {
                        popup.getMenu().add(0, 4, 0, "Archive").setIcon(R.drawable.ic_menu_archive_color);
                        popup.getMenu().add(0, 5, 0, "Delete").setIcon(R.drawable.ic_menu_trash_color);
                    }

                    try {
                        java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                        field.setAccessible(true);
                        Object menuHelper = field.get(popup);
                        Class<?> classPopupHelper = Class.forName(menuHelper.getClass().getName());
                        java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuHelper, true);
                    } catch (Exception ignored) {}

                    popup.setOnMenuItemClickListener(item -> {
                        int itemId = item.getItemId();
                        if (itemId == 7) {
                            isSelectionMode = true;
                            selectionBar.setVisibility(View.VISIBLE);
                            toggleSelection(noteId);
                        } else if (itemId == 8) {
                            voiceTargetIndex = index;
                            voiceTargetPrefs = prefs;
                            voiceTargetDateKey = currentLoopDateKey;
                            startVoiceRecognition();
                        } else if (itemId == 1) {
                            String[] options = {"Share as Text", "Share as .ics File"};
                            new AlertDialog.Builder(this).setTitle("Share Note").setItems(options, (dialog, which) -> {
                                if (which == 0) {
                                    String textToShare = getNoteBody(noteText);
                                    Intent sendIntent = new Intent();
                                    sendIntent.setAction(Intent.ACTION_SEND);
                                    sendIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
                                    sendIntent.setType("text/plain");
                                    startActivity(Intent.createChooser(sendIntent, "Share Note via"));
                                } else shareNoteAsIcs(noteText, currentLoopDateKey);
                            }).show();
                        } else if (itemId == 6) {
                            showMoveDatePicker(noteText, index, prefs, currentLoopDateKey);
                        } else if (itemId == 2) {
                            restoreSingleNote(currentLoopDateKey, index, prefs);
                        } else if (itemId == 3) {
                            deleteSingleNotePermanently(currentLoopDateKey, index, prefs);
                        } else if (itemId == 4) {
                            // Archive logic for history
                            SharedPreferences sharedPrefs = getSharedPreferences("CalendarNotes", Context.MODE_PRIVATE);
                            SharedPreferences archivePrefs = getSharedPreferences("ArchivedNotes", Context.MODE_PRIVATE);
                            
                            String currentNotes = prefs.getString(currentLoopDateKey, "");
                            List<String> list = new ArrayList<>(Arrays.asList(currentNotes.split("\n")));
                            if (index >= 0 && index < list.size()) {
                                String noteToArchive = list.remove(index);
                                String archived = archivePrefs.getString(currentLoopDateKey, "");
                                archivePrefs.edit().putString(currentLoopDateKey, archived.isEmpty() ? noteToArchive : archived + "\n" + noteToArchive).apply();
                                if (list.isEmpty()) prefs.edit().remove(currentLoopDateKey).apply();
                                else prefs.edit().putString(currentLoopDateKey, String.join("\n", list)).apply();
                                updateRemarkHistory();
                                if (Objects.equals(currentLoopDateKey, currentDateKey)) loadRemarksForSelectedDate();
                                Toast.makeText(this, "Note archived", Toast.LENGTH_SHORT).show();
                            }
                        } else if (itemId == 5) {
                            deleteSingleNote(currentLoopDateKey, index, prefs);
                        }
                        return true;
                    });
                    popup.show();
                    return true;
                });
                container.addView(noteLayout);
            }
        }
    }

    private void jumpToDate(String dateKey, SimpleDateFormat sdf) {
        try {
            Date date = sdf.parse(dateKey);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                calendar.set(Calendar.MONTH, cal.get(Calendar.MONTH));
                calendar.set(Calendar.YEAR, cal.get(Calendar.YEAR));
                updateCalendar();
                updateDateInfo(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
                adapter.notifyDataSetChanged();
                mainScrollView.smoothScrollTo(0, 0);
            }
        } catch (Exception ignored) {}
    }

    private void updateAllWidgets() {
        WidgetUtils.updateAllWidgets(getApplicationContext());
    }

    private void launchSecureBox(boolean openNewNote) {
        boolean passwordDisabled = securityPrefs.getBoolean("sb_password_disabled", false);
        if (passwordDisabled) {
            Intent intent = new Intent(this, SecureBoxActivity.class);
            if (openNewNote) intent.putExtra("action", "new_note");
            startActivity(intent);
            return;
        }

        String customPass = securityPrefs.getString("custom_password", null);

        if (customPass != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Secure Box Access");
            builder.setMessage("Enter your custom password:");

            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);

            builder.setPositiveButton("Access", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (entered.equals(customPass)) {
                    Intent intent = new Intent(this, SecureBoxActivity.class);
                    if (openNewNote) intent.putExtra("action", "new_note");
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this,
                    executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    Intent intent = new Intent(MainActivity.this, SecureBoxActivity.class);
                    if (openNewNote) intent.putExtra("action", "new_note");
                    startActivity(intent);
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
                }
            });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Secure Box Access")
                    .setSubtitle("Use your phone's PIN, Pattern, or Biometrics")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void launchExpenses() {
        boolean passwordDisabled = securityPrefs.getBoolean("exp_password_disabled", false);
        if (passwordDisabled) {
            startActivity(new Intent(this, ExpensesActivity.class));
            return;
        }

        String customPass = securityPrefs.getString("custom_password", null);

        if (customPass != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Expenses Access");
            builder.setMessage("Enter your custom password:");

            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);

            builder.setPositiveButton("Access", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (entered.equals(customPass)) {
                    startActivity(new Intent(this, ExpensesActivity.class));
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
            androidx.biometric.BiometricPrompt biometricPrompt = new androidx.biometric.BiometricPrompt(MainActivity.this,
                    executor, new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED && 
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_CANCELED) {
                        Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    startActivity(new Intent(MainActivity.this, ExpensesActivity.class));
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
                }
            });

            androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Expenses Access")
                    .setSubtitle("Use your phone's PIN, Pattern, or Biometrics")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();

            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void showChangePasswordDialog() {
        String[] options = {"Use Phone Lock Screen (Fingerprint/PIN)", "Set a New Custom Password", "Disable Password"};
        new AlertDialog.Builder(this)
                .setTitle("Secure Box Access Type")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        securityPrefs.edit().remove("custom_password")
                                .putBoolean("password_disabled", false)
                                .putBoolean("sb_password_disabled", false)
                                .putBoolean("exp_password_disabled", false).apply();
                        Toast.makeText(this, "Security enabled for all features (Sync with phone lock).", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        showSetCustomPasswordDialog();
                    } else if (which == 2) {
                        confirmDisablePassword();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDisablePassword() {
        new AlertDialog.Builder(this)
                .setTitle("Disable Password")
                .setMessage("Are you sure you want to disable the password entirely? Anyone will be able to open the Secure Box and Expenses.")
                .setPositiveButton("Yes", (dialog, which) -> {
                    verifyThenDisablePassword("Global", "password_disabled");
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void showSetCustomPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set New Password");
        builder.setMessage("Enter the custom password you want for the Secure Box:");

        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPass = input.getText().toString().trim();
            if (!newPass.isEmpty()) {
                securityPrefs.edit().putString("custom_password", newPass)
                        .putBoolean("password_disabled", false)
                        .putBoolean("sb_password_disabled", false)
                        .putBoolean("exp_password_disabled", false).apply();
                Toast.makeText(this, "Custom password saved and enabled for all features!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            currentDialogInput = null;
            dialog.cancel();
        });
        builder.show();
    }

    private void showSecurityToggleDialog(String featureName, String prefKey) {
        String[] options = {"Yes (Require Password)", "No (No Password)"};
        new AlertDialog.Builder(this)
                .setTitle("Require password for " + featureName + "?")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        securityPrefs.edit().putBoolean(prefKey, false).apply();
                        Toast.makeText(this, featureName + " now requires a password.", Toast.LENGTH_SHORT).show();
                    } else {
                        verifyThenDisablePassword(featureName, prefKey);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showThemeOptionsDialog() {
        String[] options = {"Dark Mode", "Light Mode", "Other (Custom Colors)"};
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Select Theme")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else if (which == 1) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    } else if (which == 2) {
                        showColorPicker(0);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void verifyThenDisablePassword(String featureName, String prefKey) {
        String customPass = securityPrefs.getString("custom_password", null);
        if (customPass != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Verify Identity");
            builder.setMessage("Enter password to disable security for " + featureName + ":");
            final EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            builder.setView(input);
            builder.setPositiveButton("Verify", (dialog, which) -> {
                if (input.getText().toString().trim().equals(customPass)) {
                    disableFeatureSecurity(featureName, prefKey);
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } else {
            Executor executor = ContextCompat.getMainExecutor(this);
            BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            disableFeatureSecurity(featureName, prefKey);
                        }
                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && 
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_CANCELED) {
                                Toast.makeText(MainActivity.this, "Auth error: " + errString, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Disable " + featureName + " Security")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            biometricPrompt.authenticate(promptInfo);
        }
    }

    private void disableFeatureSecurity(String featureName, String prefKey) {
        SharedPreferences.Editor editor = securityPrefs.edit();
        if (prefKey.equals("password_disabled")) {
            editor.putBoolean("password_disabled", true);
            editor.putBoolean("sb_password_disabled", true);
            editor.putBoolean("exp_password_disabled", true);
        } else {
            editor.putBoolean(prefKey, true);
        }
        editor.apply();
        Toast.makeText(this, featureName + " protection disabled.", Toast.LENGTH_SHORT).show();
    }

    private void showChangeColorsDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 16);

        // Color Options
        String[] options = {
                "Main Theme (Buttons/Title)",
                "Archive Folder Color",
                "Deleted Folder Color",
                "App Background Color",
                "Reset All Colors"
        };

        TextView titleView = new TextView(this);
        titleView.setText("Change Colors & Theme");
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white));
        titleView.setPadding(48, 40, 48, 12);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setCustomTitle(titleView)
                .setView(container)
                .setNegativeButton("Close", null)
                .create();

        ListView listView = new ListView(this);
        listView.setDivider(null);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                tv.setTextSize(14);
                return view;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            if (which == options.length - 1) {
                resetColors();
            } else {
                int category;
                switch (which) {
                    case 0: category = 0; break;  // Main Theme (Buttons/Title)
                    case 1: category = 3; break;  // Archive Folder Color
                    case 2: category = 4; break;  // Deleted Folder Color
                    case 3: category = 10; break; // App Background Color
                    default: return;
                }
                showColorPicker(category);
            }
        });

        container.addView(listView);
        dialog.show();
        ThemeManager.styleDialogButtons(dialog, this);
    }

    private void showColorPicker(int category) {
        String[] colorNames = {"Green", "Light Green", "Blue", "Red", "Chili Red", "Orange", "Purple", "Gold", "Unmellow Yellow", "Honey", "Teal", "White", "Black"};
        int[] colorValues = {
                0xFF4CAF50, 0xFF8BC34A, 0xFF2196F3, 0xFFFF0000, 0xFFC21807,
                0xFFFF9800, 0xFF9C27B0, 0xFFFFD700, 0xFFFFFF66, 0xFFFFC30B,
                0xFF008080, 0xFFFFFFFF, 0xFF000000
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Color")
                .setItems(colorNames, (dialog, which) -> {
                    int selectedColor = colorValues[which];
                    saveColor(category, selectedColor);
                })
                .show();
    }

    private void saveColor(int category, int color) {
        String key;
        switch (category) {
            case 0: key = "color_main_theme"; break;
            case 1: key = "color_note_text"; break;
            case 2: key = "color_note_checked"; break;
            case 3: key = "color_archive"; break;
            case 4: key = "color_deleted"; break;
            case 5: key = "color_sb_personal"; break;
            case 6: key = "color_sb_password"; break;
            case 7: key = "color_sb_family"; break;
            case 8: key = "color_sb_work"; break;
            case 9: key = "color_sb_others"; break;
            case 10: key = "color_app_background"; break;
            default: return;
        }
        colorPrefs.edit().putInt(key, color).apply();
        refreshUIColors();
        Toast.makeText(this, "Color updated!", Toast.LENGTH_SHORT).show();
    }

    private void resetColors() {
        colorPrefs.edit().clear().apply();
        refreshUIColors();
        Toast.makeText(this, "Colors reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showFontDialog() {
        String[] options = {"Font Style", "Font Size", "Reset Font Settings"};
        new AlertDialog.Builder(this)
                .setTitle("Font Settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showFontStylePicker();
                    } else if (which == 1) {
                        showFontSizePicker();
                    } else {
                        resetFontSettings();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFontStylePicker() {
        String[] fontNames = {"Default", "Sans Serif", "Serif", "Monospace"};
        new AlertDialog.Builder(this)
                .setTitle("Select Font Style")
                .setItems(fontNames, (dialog, which) -> {
                    fontPrefs.edit().putInt("font_style", which).apply();
                    refreshUI();
                    Toast.makeText(this, "Font style updated!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showFontSizePicker() {
        String[] sizeNames = {"Small", "Normal", "Large", "Extra Large"};
        new AlertDialog.Builder(this)
                .setTitle("Select Font Size")
                .setItems(sizeNames, (dialog, which) -> {
                    fontPrefs.edit().putInt("font_size_index", which).apply();
                    refreshUI();
                    Toast.makeText(this, "Font size updated!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void resetFontSettings() {
        fontPrefs.edit().clear().apply();
        refreshUI();
        Toast.makeText(this, "Font settings reset", Toast.LENGTH_SHORT).show();
    }

    private void refreshUI() {
        refreshUIColors();
        // Fonts are refreshed inside individual component loaders and in adapter
    }

    private void applyFontSettings(TextView textView, float baseSize) {
        int styleIndex = fontPrefs.getInt("font_style", 0);
        Typeface tf = Typeface.DEFAULT;
        switch (styleIndex) {
            case 1: tf = Typeface.SANS_SERIF; break;
            case 2: tf = Typeface.SERIF; break;
            case 3: tf = Typeface.MONOSPACE; break;
        }
        textView.setTypeface(tf);

        int sizeIndex = fontPrefs.getInt("font_size_index", 1); // Default to Normal
        float multiplier = 1.0f;
        switch (sizeIndex) {
            case 0: multiplier = 0.8f; break;
            case 2: multiplier = 1.3f; break;
            case 3: multiplier = 1.6f; break;
        }
        textView.setTextSize(baseSize * multiplier);
    }

    private void refreshUIColors() {
        int mainTheme = colorPrefs.getInt("color_main_theme", getColor(R.color.light_green));
        int bgColor = colorPrefs.getInt("color_app_background", Color.BLACK);

        // Root Background
        View root = findViewById(R.id.main);
        if (root != null) root.setBackgroundColor(bgColor);

        View bottomSticky = findViewById(R.id.bottomStickyContainer);
        if (bottomSticky != null) bottomSticky.setBackgroundColor(bgColor);

        // Header
        TextView title = findViewById(R.id.titleTextView);
        if (title != null) {
            String fullText = getString(R.string.title_calendar_2026);
            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(fullText);
            int spaceIndex = fullText.indexOf(" ");
            if (spaceIndex != -1) {
                ssb.setSpan(new android.text.style.ForegroundColorSpan(mainTheme), 0, spaceIndex, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new android.text.style.ForegroundColorSpan(Color.WHITE), spaceIndex, fullText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                // Make "Calendar" smaller than "SAR"
                // Original: 22 * 0.8 = 17.6. New base: 24. 17.6 / 24 = 0.733f
                ssb.setSpan(new android.text.style.RelativeSizeSpan(0.733f), spaceIndex, fullText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                ssb.setSpan(new android.text.style.ForegroundColorSpan(mainTheme), 0, fullText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            title.setText(ssb);
            applyFontSettings(title, 24);
        }
        TextView clock = findViewById(R.id.textClockDate);
        if (clock != null) {
            clock.setTextColor(mainTheme);
            applyFontSettings(clock, 14);
        }
        TextView remarkLbl = findViewById(R.id.remarkLabel);
        if (remarkLbl != null) {
            remarkLbl.setTextColor(mainTheme);
            applyFontSettings(remarkLbl, 18);
        }
        TextView monthYear = findViewById(R.id.monthYearText);
        if (monthYear != null) {
            applyFontSettings(monthYear, 16);
        }

        // Weekday labels
        ViewGroup weekdayLayout = findViewById(R.id.weekdayLayout);
        if (weekdayLayout != null) {
            for (int i = 0; i < weekdayLayout.getChildCount(); i++) {
                View child = weekdayLayout.getChildAt(i);
                if (child instanceof TextView) {
                    TextView tv = (TextView) child;
                    tv.setTextColor(mainTheme);
                    applyFontSettings(tv, 11);
                }
            }
        }

        // Input
        EditText input = findViewById(R.id.noteInput);
        if (input != null) {
            applyFontSettings(input, 14);
        }

        ImageButton notifyBtn = findViewById(R.id.notificationSettingsButton);
        if (notifyBtn != null) {
            notifyBtn.setImageTintList(null);
        }
        int neutral40 = Color.parseColor("#5E5E5E"); // material_dynamic_neutral40
        ImageButton aiBtn = findViewById(R.id.aiAssistantButton);
        if (aiBtn != null) {
            aiBtn.setImageTintList(null);
        }
        ImageButton menuBtn = findViewById(R.id.mainMenuButton);
        if (menuBtn != null) menuBtn.setImageTintList(null);
        ImageButton voiceBtn = findViewById(R.id.voiceNoteButton);
        if (voiceBtn != null) voiceBtn.setImageTintList(null);
        ImageButton addNoteBtn = findViewById(R.id.addNoteIconButton);
        if (addNoteBtn != null) addNoteBtn.setImageTintList(null);

        updateSortOrderUI();

        // History
        updateRemarkHistory(); // This will use the new colors/fonts during redraw
        loadRemarksForSelectedDate();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showEventsPopup(String date, List<NotificationEvent> events) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle("Events for " + date);

        String[] eventTitles = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            String title = events.get(i).getTitle();
            String time = events.get(i).getStartTime();
            eventTitles[i] = (time != null ? time + ": " : "") + title;
        }

        builder.setItems(eventTitles, (dialog, which) -> {
            NotificationEvent selectedEvent = events.get(which);
            Intent intent = new Intent(MainActivity.this, NotificationDetailsActivity.class);
            intent.putExtra("mode", "view");
            intent.putExtra("eventId", selectedEvent.getId());
            startActivity(intent);
        });

        builder.setPositiveButton("Add New", (dialog, which) -> {
            Intent intent = new Intent(MainActivity.this, NotificationDetailsActivity.class);
            intent.putExtra("mode", "add");
            intent.putExtra("date", date);
            startActivity(intent);
        });

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void showPrintDialog() {
        String[] options = {"Selected Date's Notes", "All Personal Notes", "All Archived Notes", "All Deleted Notes"};
        new AlertDialog.Builder(this)
                .setTitle("Print Notes")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: printNotes(currentDateKey, sharedPreferences); break;
                        case 1: printAllNotes(sharedPreferences, "All Personal Notes"); break;
                        case 2: printAllNotes(archivePreferences, "All Archived Notes"); break;
                        case 3: printAllNotes(deletedPreferences, "All Deleted Notes"); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void printNotes(String dateKey, SharedPreferences prefs) {
        String notes = prefs.getString(dateKey, "");
        if (notes.isEmpty()) {
            Toast.makeText(this, "No notes to print for " + dateKey, Toast.LENGTH_SHORT).show();
            return;
        }
        
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>Notes for ").append(dateKey).append("</h1>");
        html.append("<ul>");
        for (String note : notes.split("\n")) {
            html.append("<li>").append(note).append("</li>");
        }
        html.append("</ul></body></html>");
        
        doPrint(html.toString(), "Notes_" + dateKey.replace("/", "_"));
    }

    private void printAllNotes(SharedPreferences prefs, String title) {
        Map<String, ?> allEntries = prefs.getAll();
        if (allEntries.isEmpty()) {
            Toast.makeText(this, "No notes found in " + title, Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>").append(title).append("</h1>");

        List<String> sortedKeys = new ArrayList<>(allEntries.keySet());
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        sortedKeys.sort((o1, o2) -> {
            try {
                Date d1 = sdf.parse(o1);
                Date d2 = sdf.parse(o2);
                if (d1 != null && d2 != null) return d1.compareTo(d2);
            } catch (Exception ignored) {}
            return o1.compareTo(o2);
        });

        for (String key : sortedKeys) {
            Object val = allEntries.get(key);
            String notes = val != null ? val.toString() : "";
            if (!notes.isEmpty()) {
                html.append("<h3>Date: ").append(key).append("</h3>");
                html.append("<ul>");
                for (String note : notes.split("\n")) {
                    html.append("<li>").append(note).append("</li>");
                }
                html.append("</ul>");
            }
        }
        html.append("</body></html>");

        doPrint(html.toString(), title.replace(" ", "_"));
    }

    private void doPrint(String htmlContent, String jobName) {
        WebView webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(jobName);
                printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
            }
        });
        webView.loadDataWithBaseURL(null, htmlContent, "text/HTML", "UTF-8", null);
    }

    private void toggleQuickNoteBar() {
        boolean isEnabled = securityPrefs.getBoolean("quick_note_bar_enabled", false);
        isEnabled = !isEnabled;
        securityPrefs.edit().putBoolean("quick_note_bar_enabled", isEnabled).apply();

        Intent serviceIntent = new Intent(this, QuickNoteNotificationService.class);
        if (isEnabled) {
            startForegroundService(serviceIntent);
            Toast.makeText(this, "Quick Note Bar Enabled", Toast.LENGTH_SHORT).show();
        } else {
            stopService(serviceIntent);
            Toast.makeText(this, "Quick Note Bar Disabled", Toast.LENGTH_SHORT).show();
        }
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "calendar_reminder_channel";

        NotificationChannel channel = new NotificationChannel(channelId, "Calendar Reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Reminders for your calendar notes");
        channel.enableVibration(true);
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null);
        
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private class CalendarAdapter extends BaseAdapter {
        private final ArrayList<Date> days;
        private final Calendar currentMonth;
        private final LayoutInflater inflater;
        private final Map<String, List<NotificationEvent>> eventMap;
        private long lastClickTime = 0;
        private int lastClickPos = -1;

        public CalendarAdapter(Context context, ArrayList<Date> days, Calendar currentMonth, Map<String, List<NotificationEvent>> eventMap) {
            this.days = days;
            this.currentMonth = currentMonth;
            this.inflater = LayoutInflater.from(context);
            this.eventMap = eventMap;
        }

        @Override
        public int getCount() { return days.size(); }
        @Override
        public Object getItem(int position) { return days.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View itemView = convertView;
            if (itemView == null) {
                itemView = inflater.inflate(R.layout.calendar_day_item, parent, false);
            }

            TextView dayText = itemView.findViewById(R.id.dayNumber);
            applyFontSettings(dayText, 14);
            dayText.setTypeface(dayText.getTypeface(), Typeface.BOLD);
            ImageView flag = itemView.findViewById(R.id.noteFlag);
            LinearLayout eventContainer = itemView.findViewById(R.id.eventContainer);
            eventContainer.removeAllViews();

            Date date = days.get(position);
            Calendar cellCal = Calendar.getInstance();
            cellCal.setTime(date);

            dayText.setText(String.format(Locale.getDefault(), "%d", cellCal.get(Calendar.DAY_OF_MONTH)));

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            String key = sdf.format(date);
            String savedNotes = sharedPreferences.getString(key, "");
            String archivedNotes = archivePreferences.getString(key, "");
            boolean hasPersonalNotes = !savedNotes.isEmpty();
            boolean hasArchivedNotes = !archivedNotes.isEmpty();
            boolean hasNotes = hasPersonalNotes || hasArchivedNotes;

            flag.setVisibility(hasNotes ? View.VISIBLE : View.GONE);

            if (hasPersonalNotes) {
                flag.setImageTintList(ColorStateList.valueOf(colorPrefs.getInt("color_main_theme", getColor(R.color.light_green))));
            } else if (hasArchivedNotes) {
                flag.setImageTintList(ColorStateList.valueOf(colorPrefs.getInt("color_archive", Color.YELLOW)));
            }

            // Display Events
            List<NotificationEvent> events = eventMap.get(key);
            if (events != null) {
                for (NotificationEvent e : events) {
                    View eventLine = new View(itemView.getContext());
                    float density = itemView.getContext().getResources().getDisplayMetrics().density;
                    int height = (int) (3 * density);
                    
                    int width = LinearLayout.LayoutParams.MATCH_PARENT;
                    int priorityColor = Color.GREEN; // Low
                    
                    if ("High".equalsIgnoreCase(e.getPriority())) {
                        priorityColor = Color.RED;
                        width = (int) (40 * density);
                    } else if ("Medium".equalsIgnoreCase(e.getPriority())) {
                        priorityColor = Color.YELLOW;
                        width = (int) (30 * density);
                    } else {
                        priorityColor = Color.GREEN;
                        width = (int) (15 * density);
                    }

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
                    lp.setMargins(0, (int) (1 * density), 0, (int) (1 * density));
                    eventLine.setLayoutParams(lp);
                    eventLine.setBackgroundColor(priorityColor);
                    eventContainer.addView(eventLine);
                }
            }

            int currentMonthDatesColor = colorPrefs.getInt("color_note_text", Color.WHITE);
            Calendar today = Calendar.getInstance();
            boolean isToday = cellCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cellCal.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    cellCal.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);

            if (isToday) {
                dayText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.light_green));
                if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                    itemView.setBackgroundColor(Color.BLACK);
                } else {
                    itemView.setBackgroundColor(Color.parseColor("#1A1A1A"));
                }
            } else if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                dayText.setTextColor(Color.WHITE);
                itemView.setBackgroundColor(Color.BLACK);
            } else {
                dayText.setTextColor(currentMonthDatesColor);
                itemView.setBackgroundColor(Color.parseColor("#1A1A1A"));
            }

            dayText.setBackgroundResource(0);
            if (isToday) {
                dayText.setGravity(Gravity.CENTER);
            } else if (cellCal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                cellCal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                cellCal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH)) {
                itemView.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            }

            itemView.setOnClickListener(v -> {
                long clickTime = System.currentTimeMillis();
                boolean isDoubleClick = (position == lastClickPos && (clickTime - lastClickTime) < 500);
                
                lastClickTime = clickTime;
                lastClickPos = position;

                selectedDate.set(cellCal.get(Calendar.YEAR), cellCal.get(Calendar.MONTH), cellCal.get(Calendar.DAY_OF_MONTH));
                
                if (cellCal.get(Calendar.MONTH) != currentMonth.get(Calendar.MONTH)) {
                    calendar.set(Calendar.MONTH, cellCal.get(Calendar.MONTH));
                    calendar.set(Calendar.YEAR, cellCal.get(Calendar.YEAR));
                    updateCalendar();
                } else {
                    updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
                    notifyDataSetChanged();
                }

                if (isDoubleClick) {
                    noteInput.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(noteInput, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                selectedDate.set(cellCal.get(Calendar.YEAR), cellCal.get(Calendar.MONTH), cellCal.get(Calendar.DAY_OF_MONTH));
                updateDateInfo(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH));
                notifyDataSetChanged();

                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String dateStr = sdfDate.format(cellCal.getTime());

                List<NotificationEvent> dayEvents = eventMap.get(dateStr);
                if (dayEvents != null && !dayEvents.isEmpty()) {
                    showEventsPopup(dateStr, dayEvents);
                } else {
                    Intent intent = new Intent(MainActivity.this, NotificationDetailsActivity.class);
                    intent.putExtra("mode", "add");
                    intent.putExtra("date", dateStr);
                    startActivity(intent);
                }
                return true;
            });

            return itemView;
        }
    }
}
