package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.widget.SearchView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.mlkit.common.model.DownloadConditions;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.android.material.datepicker.MaterialDatePicker;
import androidx.core.util.Pair;

public class ExpensesActivity extends AppCompatActivity {

    // Filter modes for the transaction list
    private static final int FILTER_ALL = 0;
    private static final int FILTER_DAILY = 1;
    private static final int FILTER_WEEKLY = 2;
    private static final int FILTER_MONTHLY = 3;
    private static final int FILTER_YEARLY = 4;
    private static final int FILTER_CUSTOM_RANGE = 5;

    private DrawerLayout drawerLayout;
    private List<Account> accountList = new ArrayList<>();
    private AccountAdapter adapter;
    private Button topExpensesButton, allButton, dailyButton, weeklyButton, monthlyButton, yearlyButton;

    private TransactionDbHelper transactionDbHelper;
    private TransactionAdapter transactionAdapter;
    private RecyclerView transactionsRecyclerView;
    private View emptyStateText;
    private TextView cashInTotalText;
    private TextView cashOutTotalText;
    private TextView balanceTotalText;
    private TextView previousBalanceTotalText;
    private TextView finalBalanceTotalText;
    private View previousBalanceRow, finalBalanceRow;

    private int currentFilter = FILTER_ALL;
    private boolean isSortAscending = false;
    private boolean filterOnlyCashIn = false;
    private boolean filterOnlyCashOut = false;
    private long customStartDate = -1;
    private long customEndDate = -1;

    private final ActivityResultLauncher<Intent> backupExportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) performBackupExport(uri);
                }
            });

    private final ActivityResultLauncher<Intent> backupImportLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) performBackupImport(uri);
                }
            });
    private String currentSearchQuery = "";
    private boolean isVoiceCommandMode = false;
    
    private SharedPreferences speechPrefs;
    private String speechSourceLang = "en-US";
    private String speechTargetLang = "ar";
    private boolean speechAutoLang = true;
    private boolean speechTranslateEnabled = true;

    private final ActivityResultLauncher<Intent> voiceRecognitionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        if (speechTranslateEnabled) {
                            translateText(spokenText);
                        } else {
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
                            performTranslation(text, getMLKitCode(speechSourceLang));
                        } else {
                            performTranslation(text, languageCode);
                        }
                    })
                    .addOnFailureListener(e -> performTranslation(text, getMLKitCode(speechSourceLang)));
        } else {
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
                .requireWifi()
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
        if (isVoiceCommandMode) {
            processVoiceCommand(text);
            isVoiceCommandMode = false;
        } else {
            Toast.makeText(this, "Recognized: " + text, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expenses);

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navExpensesButton);
        
        loadAccounts();
        if (accountList.isEmpty()) {
            accountList.add(new Account("Expenses", 0.00));
            saveAccounts();
        } else {
            // Clean up old default accounts if they still exist
            boolean removed = accountList.removeIf(a -> a.getName().equalsIgnoreCase("Cash") || a.getName().equalsIgnoreCase("Bank"));
            if (removed) {
                saveAccounts();
            }
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        topExpensesButton = findViewById(R.id.topExpensesButton);
        allButton = findViewById(R.id.allButton);
        dailyButton = findViewById(R.id.dailyButton);
        weeklyButton = findViewById(R.id.weeklyButton);
        monthlyButton = findViewById(R.id.monthlyButton);
        yearlyButton = findViewById(R.id.yearlyButton);
        NavigationView navigationView = findViewById(R.id.expensesNavigationView);
        
        // Disable icon tinting to show real colors
        navigationView.setItemIconTintList(null);

        // --- Transaction list setup ---
        transactionDbHelper = TransactionDbHelper.getInstance(this);
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        cashInTotalText = findViewById(R.id.cashInTotalText);
        cashOutTotalText = findViewById(R.id.cashOutTotalText);
        balanceTotalText = findViewById(R.id.balanceTotalText);
        
        speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
        speechSourceLang = speechPrefs.getString("speech_source_lang", "en-US");
        speechTargetLang = speechPrefs.getString("speech_target_lang", "ar");
        speechAutoLang = speechPrefs.getBoolean("speech_auto_lang", true);
        speechTranslateEnabled = speechPrefs.getBoolean("speech_translate_enabled", true);

        previousBalanceTotalText = findViewById(R.id.previousBalanceTotalText);
        finalBalanceTotalText = findViewById(R.id.finalBalanceTotalText);
        previousBalanceRow = findViewById(R.id.previousBalanceRow);
        finalBalanceRow = findViewById(R.id.finalBalanceRow);

        transactionAdapter = new TransactionAdapter(this::confirmDeleteTransaction);
        transactionAdapter.setOnTransactionClickListener(this::showTransactionNotePopup);
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(transactionAdapter);

        // Handle Active Account context from Intent
        String passedAccount = getIntent().getStringExtra("active_account");
        if (passedAccount != null) {
            topExpensesButton.setText(passedAccount);
            saveActiveAccount(passedAccount);
        }

        refreshTransactionsList();
        
        // Persist default account if needed
        saveActiveAccount(topExpensesButton.getText().toString());

        findViewById(R.id.expensesMenuButton).setOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });

        findViewById(R.id.expensesExportButton).setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, v);
            popup.getMenuInflater().inflate(R.menu.menu_expenses_export, popup.getMenu());

            try {
                java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
                field.setAccessible(true);
                Object menuHelper = field.get(popup);
                if (menuHelper != null) {
                    Class<?> classPopupHelper = menuHelper.getClass();
                    java.lang.reflect.Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                    setForceIcons.invoke(menuHelper, true);
                }
            } catch (Exception ignored) {}

            popup.setOnMenuItemClickListener(item -> {
                String title = String.valueOf(item.getTitle());
                Toast.makeText(this, "Exporting to " + title + "...", Toast.LENGTH_SHORT).show();
                return true;
            });
            popup.show();
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_remove_ads) {
                Toast.makeText(this, "Remove Ads feature coming soon", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_summary) {
                startActivity(new Intent(this, SummaryActivity.class));
            } else if (id == R.id.nav_account_summary) {
                startActivity(new Intent(this, AccountSummaryActivity.class));
            } else if (id == R.id.nav_transaction_all) {
                startActivity(new Intent(this, TransactionsAllAccountsActivity.class));
            } else if (id == R.id.nav_accounts) {
                showAccountsDialog();
            } else if (id == R.id.nav_transfer) {
                startActivity(new Intent(this, TransferActivity.class));
            } else if (id == R.id.nav_report_all) {
                startActivity(new Intent(this, ReportAllActivity.class));
            } else if (id == R.id.nav_transaction_names) {
                startActivity(new Intent(this, TransactionNamesActivity.class));
            } else if (id == R.id.nav_notebook) {
                startActivity(new Intent(this, NotebookActivity.class));
            } else if (id == R.id.nav_calendar) {
                startActivity(new Intent(this, ExpensesCalendarActivity.class));
            } else if (id == R.id.nav_cash_calculator) {
                startActivity(new Intent(this, CashCalculatorActivity.class));
            } else if (id == R.id.nav_backup_restore) {
                showBackupRestoreDialog();
            } else if (id == R.id.nav_setting) {
                startActivity(new Intent(this, ExpensesSettingsActivity.class));
            } else if (id == R.id.nav_deleted_transactions) {
                startActivity(new Intent(this, DeletedTransactionsActivity.class));
            } else if (id == R.id.nav_rate_us) {
                startActivity(new Intent(this, RatesActivity.class));
            } else if (id == R.id.nav_recommend) {
                startActivity(new Intent(this, RecommendActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.expenses_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.dailyButton).setOnClickListener(v -> {
            View subDaily = findViewById(R.id.subDailyContainer);
            View subAll = findViewById(R.id.subExpensesContainer);
            View subWeekly = findViewById(R.id.subWeeklyContainer);
            View subMonthly = findViewById(R.id.subMonthlyContainer);
            View subYearly = findViewById(R.id.subYearlyContainer);
            subAll.setVisibility(View.GONE);
            subWeekly.setVisibility(View.GONE);
            subMonthly.setVisibility(View.GONE);
            subYearly.setVisibility(View.GONE);
            if (subDaily.getVisibility() == View.VISIBLE) {
                subDaily.setVisibility(View.GONE);
            } else {
                subDaily.setVisibility(View.VISIBLE);
            }
            currentFilter = FILTER_DAILY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        Button subTodayButton = findViewById(R.id.subTodayButton);
        SimpleDateFormat dateSdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String todayDate = dateSdf.format(new java.util.Date());
        subTodayButton.setText(todayDate);
        subTodayButton.setOnClickListener(v -> {
            if (subTodayButton.getText().toString().equals(todayDate)) {
                subTodayButton.setText("Today");
            } else {
                subTodayButton.setText(todayDate);
            }
            currentFilter = FILTER_DAILY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        findViewById(R.id.weeklyButton).setOnClickListener(v -> {
            View subWeekly = findViewById(R.id.subWeeklyContainer);
            View subAll = findViewById(R.id.subExpensesContainer);
            View subDaily = findViewById(R.id.subDailyContainer);
            View subMonthly = findViewById(R.id.subMonthlyContainer);
            View subYearly = findViewById(R.id.subYearlyContainer);
            subAll.setVisibility(View.GONE);
            subDaily.setVisibility(View.GONE);
            subMonthly.setVisibility(View.GONE);
            subYearly.setVisibility(View.GONE);
            if (subWeekly.getVisibility() == View.VISIBLE) {
                subWeekly.setVisibility(View.GONE);
            } else {
                subWeekly.setVisibility(View.VISIBLE);
            }
            currentFilter = FILTER_WEEKLY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        Button subWeeklyRangeButton = findViewById(R.id.subWeeklyRangeButton);
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String startWeekly = sdf.format(calendar.getTime());
        calendar.add(Calendar.DATE, 6);
        String endWeekly = sdf.format(calendar.getTime());
        String weeklyRange = startWeekly + " to " + endWeekly;
        subWeeklyRangeButton.setText(weeklyRange);
        subWeeklyRangeButton.setOnClickListener(v -> {
            if (subWeeklyRangeButton.getText().toString().equals(weeklyRange)) {
                subWeeklyRangeButton.setText("Weekly");
            } else {
                subWeeklyRangeButton.setText(weeklyRange);
            }
            currentFilter = FILTER_WEEKLY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        findViewById(R.id.monthlyButton).setOnClickListener(v -> {
            View subMonthly = findViewById(R.id.subMonthlyContainer);
            View subAll = findViewById(R.id.subExpensesContainer);
            View subDaily = findViewById(R.id.subDailyContainer);
            View subWeekly = findViewById(R.id.subWeeklyContainer);
            View subYearly = findViewById(R.id.subYearlyContainer);
            subAll.setVisibility(View.GONE);
            subDaily.setVisibility(View.GONE);
            subWeekly.setVisibility(View.GONE);
            subYearly.setVisibility(View.GONE);
            if (subMonthly.getVisibility() == View.VISIBLE) {
                subMonthly.setVisibility(View.GONE);
            } else {
                subMonthly.setVisibility(View.VISIBLE);
            }
            currentFilter = FILTER_MONTHLY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        Button subMonthlyRangeButton = findViewById(R.id.subMonthlyRangeButton);
        Calendar monthCal = Calendar.getInstance();
        monthCal.set(Calendar.DAY_OF_MONTH, 1);
        String startMonth = sdf.format(monthCal.getTime());
        monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String endMonth = sdf.format(monthCal.getTime());
        String monthlyRange = startMonth + " to " + endMonth;
        subMonthlyRangeButton.setText(monthlyRange);
        subMonthlyRangeButton.setOnClickListener(v -> {
            if (subMonthlyRangeButton.getText().toString().equals(monthlyRange)) {
                subMonthlyRangeButton.setText("Monthly");
            } else {
                subMonthlyRangeButton.setText(monthlyRange);
            }
            currentFilter = FILTER_MONTHLY;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        findViewById(R.id.yearlyButton).setOnClickListener(v -> {
            View subYearly = findViewById(R.id.subYearlyContainer);
            View subAll = findViewById(R.id.subExpensesContainer);
            View subDaily = findViewById(R.id.subDailyContainer);
            View subWeekly = findViewById(R.id.subWeeklyContainer);
            View subMonthly = findViewById(R.id.subMonthlyContainer);
            subAll.setVisibility(View.GONE);
            subDaily.setVisibility(View.GONE);
            subWeekly.setVisibility(View.GONE);
            subMonthly.setVisibility(View.GONE);
            if (subYearly.getVisibility() == View.VISIBLE) {
                subYearly.setVisibility(View.GONE);
            } else {
                subYearly.setVisibility(View.VISIBLE);
            }
            currentFilter = FILTER_YEARLY;
            updateFilterButtonsUI();
        });

        Button subYearlyRangeButton = findViewById(R.id.subYearlyRangeButton);
        Calendar yearCal = Calendar.getInstance();
        yearCal.set(Calendar.DAY_OF_YEAR, 1);
        String startYear = sdf.format(yearCal.getTime());
        yearCal.set(Calendar.DAY_OF_YEAR, yearCal.getActualMaximum(Calendar.DAY_OF_YEAR));
        String endYear = sdf.format(yearCal.getTime());
        String yearlyRange = startYear + " to " + endYear;
        subYearlyRangeButton.setText(yearlyRange);
        subYearlyRangeButton.setOnClickListener(v -> {
            if (subYearlyRangeButton.getText().toString().equals(yearlyRange)) {
                subYearlyRangeButton.setText("Yearly");
            } else {
                subYearlyRangeButton.setText(yearlyRange);
            }
            currentFilter = FILTER_YEARLY;
            updateFilterButtonsUI();
            Toast.makeText(this, "Yearly Expenses view coming soon", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.allButton).setOnClickListener(v -> {
            View subAll = findViewById(R.id.subExpensesContainer);
            View subDaily = findViewById(R.id.subDailyContainer);
            View subWeekly = findViewById(R.id.subWeeklyContainer);
            View subMonthly = findViewById(R.id.subMonthlyContainer);
            View subYearly = findViewById(R.id.subYearlyContainer);
            subDaily.setVisibility(View.GONE);
            subWeekly.setVisibility(View.GONE);
            subMonthly.setVisibility(View.GONE);
            subYearly.setVisibility(View.GONE);
            if (subAll.getVisibility() == View.VISIBLE) {
                subAll.setVisibility(View.GONE);
            } else {
                subAll.setVisibility(View.VISIBLE);
            }
            currentFilter = FILTER_ALL;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        findViewById(R.id.subAllButton).setOnClickListener(v -> {
            currentFilter = FILTER_ALL;
            updateFilterButtonsUI();
            refreshTransactionsList();
        });

        findViewById(R.id.expensesChartButton).setOnClickListener(v -> {
            startActivity(new Intent(this, ChartActivity.class));
        });

        findViewById(R.id.cashInButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra("type", Transaction.TYPE_CASH_IN);
            startActivity(intent);
        });

        findViewById(R.id.cashOutButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra("type", Transaction.TYPE_CASH_OUT);
            startActivity(intent);
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                String activeAccount = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString("ActiveAccount", "Expenses");
                if (!activeAccount.equals("Expenses")) {
                    // Inside an account: Return to Summary Mode immediately
                    saveActiveAccount("Expenses");
                    topExpensesButton.setText("Expenses");
                    refreshTransactionsList();
                } else {
                    // Already in Summary Mode: Show confirmation dialog
                    androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(ExpensesActivity.this, R.style.CustomAlertDialogTheme)
                            .setTitle("Leave Expenses")
                            .setMessage("Do you want to leave Expenses?")
                            .setPositiveButton("Yes", (d, which) -> {
                                // Redirect to SAR Calendar (MainActivity)
                                Intent intent = new Intent(ExpensesActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            })
                            .setNegativeButton("No", null)
                            .show();
                    ThemeManager.styleDialogButtons(dialog, ExpensesActivity.this);
                }
            }
        });

        findViewById(R.id.aiAssistantButton).setOnClickListener(v -> showSpeechTranslationDialog());

        topExpensesButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AccountsOverviewActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.expensesOverflowButton).setOnClickListener(this::showExpensesOverflowMenu);

        updateFilterButtonsUI();

        // Expandable / Hideable totals panel setup
        View totalsFooterContainer = findViewById(R.id.totalsFooterContainer);
        android.widget.ImageButton toggleFooterButton = findViewById(R.id.toggleFooterButton);
        
        toggleFooterButton.setOnClickListener(v -> {
            if (totalsFooterContainer.getVisibility() == View.VISIBLE) {
                totalsFooterContainer.setVisibility(View.GONE);
                toggleFooterButton.setImageResource(R.drawable.ic_arrow_down);
            } else {
                totalsFooterContainer.setVisibility(View.VISIBLE);
                toggleFooterButton.setImageResource(android.R.drawable.arrow_up_float);
            }
        });

        // Hide when tapping outside
        findViewById(R.id.expenses_main).setOnTouchListener((v, event) -> {
            if (totalsFooterContainer.getVisibility() == View.VISIBLE) {
                totalsFooterContainer.setVisibility(View.GONE);
                toggleFooterButton.setImageResource(R.drawable.ic_arrow_down);
            }
            return false;
        });
    }

    private void showExpensesOverflowMenu(View anchor) {
        View popupView = getLayoutInflater().inflate(R.layout.layout_expenses_overflow_menu, null);
        final PopupWindow popupWindow = new PopupWindow(popupView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        
        popupWindow.setElevation(20);
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        // Sorting toggles
        SwitchCompat switchAsc = popupView.findViewById(R.id.switch_asc);
        SwitchCompat switchDesc = popupView.findViewById(R.id.switch_desc);

        // Remove listeners temporarily to set initial state without triggering refresh
        switchAsc.setOnCheckedChangeListener(null);
        switchDesc.setOnCheckedChangeListener(null);
        switchAsc.setChecked(isSortAscending);
        switchDesc.setChecked(!isSortAscending);

        switchAsc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isSortAscending = true;
                if (switchDesc.isChecked()) switchDesc.setChecked(false);
                refreshTransactionsList();
            } else {
                if (!switchDesc.isChecked()) switchAsc.setChecked(true); // Always keep one ON
            }
        });

        switchDesc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                isSortAscending = false;
                if (switchAsc.isChecked()) switchAsc.setChecked(false);
                refreshTransactionsList();
            } else {
                if (!switchAsc.isChecked()) switchDesc.setChecked(true); // Always keep one ON
            }
        });

        // Clicks for items
        popupView.findViewById(R.id.menu_category).setOnClickListener(v -> { startActivity(new Intent(this, CategoryActivity.class)); popupWindow.dismiss(); });
        popupView.findViewById(R.id.menu_notes).setOnClickListener(v -> { startActivity(new Intent(this, NotebookActivity.class)); popupWindow.dismiss(); });
        popupView.findViewById(R.id.menu_date).setOnClickListener(v -> { showDatePicker(); popupWindow.dismiss(); });
        popupView.findViewById(R.id.menu_date_range).setOnClickListener(v -> { showDateRangePicker(); popupWindow.dismiss(); });
        
        // Containers for switches should also toggle
        popupView.findViewById(R.id.menu_asc_container).setOnClickListener(v -> switchAsc.toggle());
        popupView.findViewById(R.id.menu_desc_container).setOnClickListener(v -> switchDesc.toggle());

        popupView.findViewById(R.id.menu_cash_in).setOnClickListener(v -> { 
            filterOnlyCashIn = !filterOnlyCashIn; 
            filterOnlyCashOut = false; 
            refreshTransactionsList(); 
            popupWindow.dismiss(); 
        });
        popupView.findViewById(R.id.menu_cash_out).setOnClickListener(v -> { 
            filterOnlyCashOut = !filterOnlyCashOut; 
            filterOnlyCashIn = false; 
            refreshTransactionsList(); 
            popupWindow.dismiss(); 
        });
        popupView.findViewById(R.id.menu_print).setOnClickListener(v -> { 
            findViewById(R.id.expensesExportButton).performClick(); 
            popupWindow.dismiss(); 
        });
        popupView.findViewById(R.id.menu_name).setOnClickListener(v -> { 
            showUserInfoDialog("Name", "UserName"); 
            popupWindow.dismiss(); 
        });
        popupView.findViewById(R.id.menu_address).setOnClickListener(v -> { 
            showUserInfoDialog("Address", "UserAddress"); 
            popupWindow.dismiss(); 
        });

        popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    private void showSpeechTranslationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        
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

        SwitchCompat autoLangSwitch = new SwitchCompat(this);
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

        SwitchCompat translateSwitch = new SwitchCompat(this);
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
        dialog.setOnShowListener(d -> ThemeManager.styleDialogButtons(dialog, this));
        dialog.show();
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
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", languageCodes);
        }
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, isVoiceCommandMode ? "Listening for command..." : "Speak now...");
        try {
            voiceRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
            isVoiceCommandMode = false;
        }
    }

    private void updateFilterButtonsUI() {
        int activeColor = ThemeManager.getMainAccentColor(this);
        int inactiveColor = ContextCompat.getColor(this, R.color.gray);

        allButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentFilter == FILTER_ALL ? activeColor : inactiveColor));
        dailyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentFilter == FILTER_DAILY ? activeColor : inactiveColor));
        weeklyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentFilter == FILTER_WEEKLY ? activeColor : inactiveColor));
        monthlyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentFilter == FILTER_MONTHLY ? activeColor : inactiveColor));
        yearlyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(currentFilter == FILTER_YEARLY ? activeColor : inactiveColor));

        if (currentFilter == FILTER_CUSTOM_RANGE) {
            // If custom range is active, we might want to show a toast or update a label
            String start = new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new java.util.Date(customStartDate));
            String end = new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new java.util.Date(customEndDate));
            Toast.makeText(this, "Range: " + start + " - " + end, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAccountsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_accounts, null);
        int mainAccent = ThemeManager.getMainAccentColor(this);
        View editBtn = dialogView.findViewById(R.id.editAccountsButton);
        if (editBtn instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) editBtn;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    ((android.widget.TextView) child).setTextColor(mainAccent);
                } else if (child instanceof android.widget.ImageView) {
                    ((android.widget.ImageView) child).setImageTintList(android.content.res.ColorStateList.valueOf(mainAccent));
                }
            }
        }
        View addBtn = dialogView.findViewById(R.id.addAccountButton);
        if (addBtn != null) {
            addBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mainAccent));
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(dialogView)
                .create();

        androidx.recyclerview.widget.RecyclerView recyclerView = dialogView.findViewById(R.id.accountsRecyclerView);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        
        adapter = new AccountAdapter(accountList, new AccountAdapter.OnAccountClickListener() {
            @Override
            public void onAccountClick(Account account) {
                topExpensesButton.setText(account.getName());
                saveActiveAccount(account.getName());
                dialog.dismiss();
            }

            @Override
            public void onDeleteClick(Account account, int position) {
                new androidx.appcompat.app.AlertDialog.Builder(ExpensesActivity.this, R.style.CustomAlertDialogTheme)
                        .setTitle("Delete Account")
                        .setMessage("Are you sure you want to delete " + account.getName() + "?\n\nAll transactions associated with this account will also be removed.")
                        .setPositiveButton("Delete", (d, w) -> {
                            String accountName = account.getName();
                            
                            // 1. Delete associated transactions from DB (Soft Delete)
                            transactionDbHelper.deleteTransactionsByAccount(accountName);
                            
                            // 2. Remove account from list and save
                            accountList.remove(account);
                            adapter.updateList(accountList);
                            saveAccounts();
                            
                            // 3. Reset active account if it was deleted
                            String activeAccount = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString("ActiveAccount", "Expenses");
                            if (activeAccount.equals(accountName)) {
                                topExpensesButton.setText("Expenses");
                                saveActiveAccount("Expenses");
                            }
                            
                            // 4. Update UI immediately
                            refreshTransactionsList();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onEditClick(Account account, int position) {
                View editView = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
                android.widget.EditText nameInput = editView.findViewById(R.id.editAccountName);
                android.widget.EditText balanceInput = editView.findViewById(R.id.editAccountBalance);
                
                nameInput.setText(account.getName());
                balanceInput.setText(String.format(Locale.US, "%,.2f", account.getBalance()));
                
                editView.findViewById(R.id.accountCurrencyPicker).setVisibility(View.GONE);
            
            new androidx.appcompat.app.AlertDialog.Builder(ExpensesActivity.this, R.style.CustomAlertDialogTheme)
                        .setTitle("Edit Account")
                        .setView(editView)
                        .setPositiveButton("Save", (d, w) -> {
                            String newName = nameInput.getText().toString();
                            String balanceStr = balanceInput.getText().toString();
                            if (!newName.isEmpty()) {
                                account.setName(newName);
                                if (!balanceStr.isEmpty()) {
                                    try {
                                        account.setBalance(Double.parseDouble(balanceStr.replace(",", "")));
                                    } catch (NumberFormatException ignored) {}
                                }
                                adapter.notifyItemChanged(position);
                                if (topExpensesButton.getText().toString().equals(account.getName())) {
                                    topExpensesButton.setText(newName);
                                }
                                saveAccounts();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }

            @Override
            public void onListChanged() {
                saveAccounts();
            }
        });
        recyclerView.setAdapter(adapter);

        dialogView.findViewById(R.id.editAccountsButton).setOnClickListener(v -> {
            adapter.setEditMode(!adapter.isEditMode());
        });

        androidx.appcompat.widget.SearchView searchView = dialogView.findViewById(R.id.accountsSearchView);
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });

        dialogView.findViewById(R.id.addAccountButton).setOnClickListener(v -> {
            View addView = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
            android.widget.EditText nameInput = addView.findViewById(R.id.editAccountName);
            android.widget.EditText balanceInput = addView.findViewById(R.id.editAccountBalance);
            android.widget.TextView txtAccountCurrency = addView.findViewById(R.id.txtAccountCurrency);
            android.widget.TextView txtAccountDate = addView.findViewById(R.id.txtAccountDate);
            android.view.View indicatorPlus = addView.findViewById(R.id.indicatorPlus);
            android.view.View indicatorMinus = addView.findViewById(R.id.indicatorMinus);

            final String[] selectedCurrency = {"USD"};

            addView.findViewById(R.id.accountCurrencyPicker).setOnClickListener(v1 -> {
                List<CountryManager.Country> countries = CountryManager.getCountries();
                String[] items = new String[countries.size()];
                for (int i = 0; i < countries.size(); i++) {
                    items[i] = countries.get(i).currency + " (" + countries.get(i).name + ")";
                }
                new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Select Currency")
                        .setItems(items, (dialog1, which) -> {
                            selectedCurrency[0] = countries.get(which).currency;
                            txtAccountCurrency.setText("Currency: " + selectedCurrency[0]);
                        }).show();
            });

            final java.util.Calendar selectedCal = java.util.Calendar.getInstance();
            final java.text.SimpleDateFormat dialogSdf = new java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault());
            txtAccountDate.setText(dialogSdf.format(selectedCal.getTime()));

            final boolean[] isPositive = {true};
            int accentColor = ThemeManager.getMainAccentColor(this);
            indicatorPlus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accentColor));
            indicatorMinus.setBackgroundTintList(null);

            TextView btnCancelAcc = addView.findViewById(R.id.btnCancelAccount);
            if (btnCancelAcc != null) btnCancelAcc.setTextColor(accentColor);
            TextView btnSaveAcc = addView.findViewById(R.id.btnSaveAccount);
            if (btnSaveAcc != null) btnSaveAcc.setTextColor(accentColor);

            androidx.appcompat.app.AlertDialog addDialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setView(addView)
                    .create();

            if (addDialog.getWindow() != null) {
                addDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            }

            addView.findViewById(R.id.typePlusContainer).setOnClickListener(v1 -> {
                isPositive[0] = true;
                indicatorPlus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ThemeManager.getMainAccentColor(this)));
                indicatorMinus.setBackgroundTintList(null);
            });

            addView.findViewById(R.id.typeMinusContainer).setOnClickListener(v1 -> {
                isPositive[0] = false;
                indicatorMinus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.expense_red)));
                indicatorPlus.setBackgroundTintList(null);
            });

            addView.findViewById(R.id.dateSelectionBox).setOnClickListener(v1 -> {
                new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                    selectedCal.set(year, month, dayOfMonth);
                    txtAccountDate.setText(dialogSdf.format(selectedCal.getTime()));
                }, selectedCal.get(java.util.Calendar.YEAR), selectedCal.get(java.util.Calendar.MONTH), selectedCal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });

            addView.findViewById(R.id.btnCancelAccount).setOnClickListener(v1 -> addDialog.dismiss());

            addView.findViewById(R.id.btnSaveAccount).setOnClickListener(v1 -> {
                String name = nameInput.getText().toString().trim();
                String balanceStr = balanceInput.getText().toString().trim();

                if (!name.isEmpty()) {
                    double balance = 0.0;
                    if (!balanceStr.isEmpty()) {
                        try {
                            balance = Double.parseDouble(balanceStr.replace(",", ""));
                        } catch (NumberFormatException ignored) {}
                    }

                    // Add account with 0 balance first
                    accountList.add(new Account(name, 0.00, selectedCurrency[0]));
                    saveAccounts();

                    if (balance > 0) {
                        String type = isPositive[0] ? Transaction.TYPE_CASH_IN : Transaction.TYPE_CASH_OUT;
                        double finalBalanceDelta = isPositive[0] ? balance : -balance;

                        transactionDbHelper.addTransaction("Income", balance, selectedCurrency[0], type, selectedCal.getTimeInMillis(), name, "", "", "");
                        BalanceManager.updateAccountBalance(this, name, finalBalanceDelta);
                        loadAccounts(); // Reload
                    }

                    adapter.updateList(accountList);
                    addDialog.dismiss();
                } else {
                    android.widget.Toast.makeText(this, "Please enter a name", android.widget.Toast.LENGTH_SHORT).show();
                }
            });

            addDialog.show();
        });

        dialog.show();
    }

    private void confirmDeleteTransaction(Transaction transaction) {
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Delete Transaction")
                .setMessage("Move \"" + transaction.getTitle() + "\" to Deleted Transactions?")
                .setPositiveButton("Delete", (d, w) -> {
                    // Sync Balance back
                    double delta = transaction.isCashIn() ? -transaction.getAmount() : transaction.getAmount();
                    BalanceManager.updateAccountBalance(this, transaction.getAccount(), delta);

                    transactionDbHelper.deleteTransaction(transaction.getId());
                    refreshTransactionsList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBackupRestoreDialog() {
        String[] options = {"Export Data (Save Backup)", "Import Data (Restore Backup)"};
        ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Backup & Restore")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startBackupExport();
                    else startBackupImport();
                })
                .setNegativeButton("Cancel", null), this);
    }

    private void startBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new java.util.Date());
        intent.putExtra(Intent.EXTRA_TITLE, "SAR_Calendar_Backup_" + timeStamp + ".json");
        backupExportLauncher.launch(intent);
    }

    private void performBackupExport(android.net.Uri uri) {
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

    private void startBackupImport() {
        ThemeManager.showDialog(new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Import Data")
                .setMessage("Warning: Importing data will overwrite all current notes and settings. Continue?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    backupImportLauncher.launch(intent);
                })
                .setNegativeButton("No", null), this);
    }

    private void performBackupImport(android.net.Uri uri) {
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

    private void showTransactionNotePopup(Transaction transaction) {
        String note = transaction.getNotes();
        if (note == null || note.trim().isEmpty()) {
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction_note, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView messageTv = dialogView.findViewById(R.id.dialogMessage);
        messageTv.setText(note);

        dialogView.findViewById(R.id.btnEdit).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, AddTransactionActivity.class);
            intent.putExtra("transaction_id", transaction.getId());
            startActivity(intent);
        });

        dialogView.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            dialog.dismiss();
            confirmDeleteTransaction(transaction);
        });

        dialogView.findViewById(R.id.btnUpdate).setOnClickListener(v -> {
            dialog.dismiss();
            showQuickUpdateNoteDialog(transaction);
        });

        dialog.show();
    }

    private void showQuickUpdateNoteDialog(Transaction transaction) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction_note, null);
        TextView titleTv = dialogView.findViewById(R.id.dialogTitle);
        TextView messageTv = dialogView.findViewById(R.id.dialogMessage);
        Button btnUpdate = dialogView.findViewById(R.id.btnUpdate);
        Button btnDelete = dialogView.findViewById(R.id.btnDelete);
        Button btnEdit = dialogView.findViewById(R.id.btnEdit);

        titleTv.setText("Update Note");
        messageTv.setVisibility(View.GONE);

        EditText input = new EditText(this);
        input.setText(transaction.getNotes());
        input.setTextColor(Color.WHITE);
        input.setPadding(0, 20, 0, 20);
        ((LinearLayout)dialogView).addView(input, 2); // Insert after title

        btnUpdate.setText("SAVE");
        btnDelete.setText("CANCEL");
        btnEdit.setVisibility(View.GONE);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnDelete.setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            String newNote = input.getText().toString().trim();
            transactionDbHelper.updateTransactionNote(transaction.getId(), newNote);
            refreshTransactionsList();
            Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        loadAccounts();
        refreshTransactionsList();
    }

    private void applyColors() {
        if (getWindow() != null) {
            getWindow().setStatusBarColor(Color.BLACK);
        }
        int accent = ThemeManager.getMainAccentColor(this);
        if (topExpensesButton != null) {
            topExpensesButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        View cashInBtn = findViewById(R.id.cashInButton);
        if (cashInBtn != null) {
            cashInBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        View toggleFooterBtn = findViewById(R.id.toggleFooterButton);
        if (toggleFooterBtn instanceof ImageButton) {
            ((ImageButton) toggleFooterBtn).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        View subTodayBtn = findViewById(R.id.subTodayButton);
        if (subTodayBtn != null) {
            subTodayBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        TextView cashInTotal = findViewById(R.id.cashInTotalText);
        if (cashInTotal != null) {
            cashInTotal.setTextColor(accent);
        }
        View prevRow = findViewById(R.id.previousBalanceRow);
        if (prevRow instanceof ViewGroup) {
            TextView tv = (TextView) ((ViewGroup) prevRow).getChildAt(0);
            if (tv != null) tv.setTextColor(accent);
        }
        View finalRow = findViewById(R.id.finalBalanceRow);
        if (finalRow instanceof ViewGroup) {
            TextView tv = (TextView) ((ViewGroup) finalRow).getChildAt(0);
            if (tv != null) tv.setTextColor(accent);
        }

        com.google.android.material.navigation.NavigationView navView = findViewById(R.id.expensesNavigationView);
        if (navView != null && navView.getHeaderCount() > 0) {
            View headerView = navView.getHeaderView(0);
            if (headerView instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) headerView;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(accent);
                    }
                }
            }
        }

        updateFilterButtonsUI();
        BottomNavigationHelper.setupBottomNavigation(this, R.id.navExpensesButton);
    }

    /**
     * Reloads transactions from the database, applies the current date filter and
     * search query, groups the results by date, and refreshes the totals footer.
     */
    private void refreshTransactionsList() {
        List<Transaction> allAscending = transactionDbHelper.getAllTransactionsAscending();

        // Step 1: Sync with Account Balances
        // We need to re-calculate all account balances from scratch to ensure accuracy with multiple currencies
        List<Account> accounts = BalanceManager.loadAccounts(this);
        java.util.Map<String, Double> accountBalances = new java.util.HashMap<>();
        for (Account a : accounts) accountBalances.put(a.getName(), 0.0);

        for (Transaction t : allAscending) {
            if (accountBalances.containsKey(t.getAccount())) {
                accountBalances.put(t.getAccount(), accountBalances.get(t.getAccount()) + t.getSignedAmount());
            }
        }
        for (Account a : accounts) {
            if (accountBalances.containsKey(a.getName())) {
                a.setBalance(accountBalances.get(a.getName()));
            }
        }
        BalanceManager.saveAccounts(this, accounts);

        // Running balance logic (Respecting Currency)
        java.util.Map<Long, Double> balanceAfterById = new java.util.HashMap<>();
        String activeAccount = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString("ActiveAccount", "Expenses");
        boolean isSummaryMode = activeAccount.equals("Expenses");
        
        java.util.Map<String, Double> runningMap = new java.util.HashMap<>(); // Currency -> Running Balance
        
        for (Transaction t : allAscending) {
            if (!isSummaryMode && (t.getAccount() == null || !t.getAccount().equals(activeAccount))) continue;
            if (isSummaryMode && "Income".equalsIgnoreCase(t.getTitle())) continue;
            if (!isSummaryMode && "Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            String curr = t.getCurrency();
            double r = runningMap.getOrDefault(curr, 0.0);
            r += t.getSignedAmount();
            runningMap.put(curr, r);
            balanceAfterById.put(t.getId(), r);
        }

        // Apply filters
        List<Transaction> filtered = new ArrayList<>();
        List<Transaction> monthlyIncomes = new ArrayList<>();
        
        int count = allAscending.size();
        for (int i = 0; i < count; i++) {
            int index = isSortAscending ? i : (count - 1 - i);
            Transaction t = allAscending.get(index);
            
            if (!isSummaryMode && (t.getAccount() == null || !t.getAccount().equals(activeAccount))) continue;
            if (isSummaryMode && "Income".equalsIgnoreCase(t.getTitle())) continue;
            if (!isSummaryMode && "Monthly Income".equalsIgnoreCase(t.getTitle())) continue;

            if (matchesFilter(t) && matchesSearch(t)) {
                if ("Monthly Income".equalsIgnoreCase(t.getTitle())) {
                    monthlyIncomes.add(t);
                } else {
                    filtered.add(t);
                }
            }
        }

        if (isSummaryMode) {
            filtered.addAll(0, monthlyIncomes);
        }

        // Grouping and Totals calculation
        List<TransactionListItem> grouped = new ArrayList<>();
        String lastGroupLabel = null;
        
        java.util.Map<String, Double> cashInMap = new java.util.HashMap<>();
        java.util.Map<String, Double> cashOutMap = new java.util.HashMap<>();

        for (Transaction t : filtered) {
            String label = getDateGroupLabel(t.getTimestamp());
            if (!label.equals(lastGroupLabel)) {
                grouped.add(TransactionListItem.header(label));
                lastGroupLabel = label;
            }
            Double balanceAfter = balanceAfterById.get(t.getId());
            grouped.add(TransactionListItem.transaction(t, balanceAfter != null ? balanceAfter : 0.0));
            
            String curr = t.getCurrency();
            if (t.isCashIn()) {
                cashInMap.put(curr, cashInMap.getOrDefault(curr, 0.0) + t.getAmount());
            } else {
                cashOutMap.put(curr, cashOutMap.getOrDefault(curr, 0.0) + t.getAmount());
            }
        }

        transactionAdapter.updateItems(grouped);
        transactionsRecyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        emptyStateText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);

        updateTotalsUI(cashInMap, cashOutMap);
    }

    private void updateTotalsUI(java.util.Map<String, Double> cashInMap, java.util.Map<String, Double> cashOutMap) {
        LinearLayout footerContainer = findViewById(R.id.totalsFooterContainer);
        footerContainer.removeAllViews();

        java.util.Set<String> allCurrencies = new java.util.TreeSet<>(cashInMap.keySet());
        allCurrencies.addAll(cashOutMap.keySet());

        if (allCurrencies.isEmpty()) {
            allCurrencies.add("USD"); // Default
        }

        for (String curr : allCurrencies) {
            double in = cashInMap.getOrDefault(curr, 0.0);
            double out = cashOutMap.getOrDefault(curr, 0.0);
            
            View row = getLayoutInflater().inflate(R.layout.item_summary_stat_row, footerContainer, false);
            TextView titleTv = row.findViewById(R.id.statTitle);
            TextView inTv = row.findViewById(R.id.statIn);
            TextView outTv = row.findViewById(R.id.statOut);
            TextView balTv = row.findViewById(R.id.statBalance);

            titleTv.setText(curr);
            if ("LBP".equalsIgnoreCase(curr)) {
                inTv.setText(CurrencyFormatter.formatLbpAmount(in));
                outTv.setText(CurrencyFormatter.formatLbpAmount(out));
                balTv.setText(CurrencyFormatter.formatLbpAmount(in - out));
            } else {
                inTv.setText(String.format(Locale.US, "%,.2f", in));
                outTv.setText(String.format(Locale.US, "%,.2f", out));
                balTv.setText(String.format(Locale.US, "%,.2f", in - out));
            }
            
            footerContainer.addView(row);
        }

        // Handle period balance if not FILTER_ALL
        if (currentFilter != FILTER_ALL) {
            // Add a small empty vertical space/blank line between the USD Cash In section and the Preview sections
            View space = new View(this);
            LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (8 * getResources().getDisplayMetrics().density));
            space.setLayoutParams(spaceParams);
            footerContainer.addView(space);

            long periodStart = getPeriodStartMillis();
            List<Transaction> all = transactionDbHelper.getAllTransactionsAscending();
            String activeAccount = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString("ActiveAccount", "Expenses");
            boolean isSummary = activeAccount.equals("Expenses");

            java.util.Map<String, Double> prevBalMap = new java.util.HashMap<>();
            java.util.Map<String, Double> finalBalMap = new java.util.HashMap<>();

            for (Transaction t : all) {
                if (!isSummary && !t.getAccount().equals(activeAccount)) continue;
                String curr = t.getCurrency();
                finalBalMap.put(curr, finalBalMap.getOrDefault(curr, 0.0) + t.getSignedAmount());
                if (t.getTimestamp() < periodStart) {
                    prevBalMap.put(curr, prevBalMap.getOrDefault(curr, 0.0) + t.getSignedAmount());
                }
            }

            for (String curr : allCurrencies) {
                android.widget.RelativeLayout balRow = new android.widget.RelativeLayout(this);
                balRow.setPadding(0, 2, 0, 2);

                TextView prevTv = new TextView(this);
                prevTv.setTextColor(Color.parseColor("#8BC34A"));
                prevTv.setTextSize(12);
                String prevValStr = "LBP".equalsIgnoreCase(curr)
                        ? CurrencyFormatter.formatLbpAmount(prevBalMap.getOrDefault(curr, 0.0))
                        : String.format(Locale.US, "%,.2f", prevBalMap.getOrDefault(curr, 0.0));
                prevTv.setText(curr + " Previous: " + prevValStr);
                
                android.widget.RelativeLayout.LayoutParams lpPrev = new android.widget.RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpPrev.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
                prevTv.setLayoutParams(lpPrev);
                balRow.addView(prevTv);

                TextView finalTv = new TextView(this);
                finalTv.setTextColor(Color.WHITE);
                finalTv.setTextSize(12);
                String finalValStr = "LBP".equalsIgnoreCase(curr)
                        ? CurrencyFormatter.formatLbpAmount(finalBalMap.getOrDefault(curr, 0.0))
                        : String.format(Locale.US, "%,.2f", finalBalMap.getOrDefault(curr, 0.0));
                finalTv.setText(curr + " Final: " + finalValStr);
                
                android.widget.RelativeLayout.LayoutParams lpFinal = new android.widget.RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpFinal.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
                finalTv.setLayoutParams(lpFinal);
                balRow.addView(finalTv);

                footerContainer.addView(balRow);
            }
        }
    }

    private boolean matchesFilter(Transaction t) {
        // Type filtering
        if (filterOnlyCashIn && !t.isCashIn()) return false;
        if (filterOnlyCashOut && t.isCashIn()) return false;

        if (currentFilter == FILTER_ALL) return true;

        Calendar now = Calendar.getInstance();
        Calendar txCal = Calendar.getInstance();
        txCal.setTimeInMillis(t.getTimestamp());

        switch (currentFilter) {
            case FILTER_DAILY:
                return isSameDay(now, txCal);
            case FILTER_WEEKLY:
                return isSameWeek(now, txCal);
            case FILTER_MONTHLY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR)
                        && now.get(Calendar.MONTH) == txCal.get(Calendar.MONTH);
            case FILTER_YEARLY:
                return now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR);
            case FILTER_CUSTOM_RANGE:
                long ts = t.getTimestamp();
                if (customStartDate != -1 && ts < customStartDate) return false;
                if (customEndDate != -1 && ts > customEndDate) return false;
                return true;
            default:
                return true;
        }
    }

    private boolean matchesSearch(Transaction t) {
        if (currentSearchQuery == null || currentSearchQuery.trim().isEmpty()) return true;
        return t.getTitle().toLowerCase(Locale.getDefault())
                .contains(currentSearchQuery.trim().toLowerCase(Locale.getDefault()));
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private boolean isSameWeek(Calendar a, Calendar b) {
        Calendar startOfWeek = (Calendar) a.clone();
        startOfWeek.setFirstDayOfWeek(Calendar.MONDAY);
        startOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0);
        startOfWeek.set(Calendar.MINUTE, 0);
        startOfWeek.set(Calendar.SECOND, 0);
        startOfWeek.set(Calendar.MILLISECOND, 0);

        Calendar endOfWeek = (Calendar) startOfWeek.clone();
        endOfWeek.add(Calendar.DATE, 7);

        long time = b.getTimeInMillis();
        return time >= startOfWeek.getTimeInMillis() && time < endOfWeek.getTimeInMillis();
    }

    private String getDateGroupLabel(long timestamp) {
        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        Calendar txCal = Calendar.getInstance();
        txCal.setTimeInMillis(timestamp);

        if (isSameDay(today, txCal)) {
            return "Today";
        } else if (isSameDay(yesterday, txCal)) {
            return "Yesterday";
        } else {
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(txCal.getTime());
        }
    }

    private void saveAccounts() {
        try {
            JSONArray array = new JSONArray();
            for (Account account : accountList) {
                JSONObject obj = new JSONObject();
                obj.put("name", account.getName());
                obj.put("balance", account.getBalance());
                obj.put("currency", account.getCurrency());
                array.put(obj);
            }
            getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                    .edit()
                    .putString("AccountList", array.toString())
                    .apply();
            
            refreshTransactionsList();
            
        } catch (Exception e) {
            Log.e("ExpensesActivity", "Error calculating balances", e);
        }
    }

    private void loadAccounts() {
        try {
            String json = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                    .getString("AccountList", null);
            if (json != null) {
                JSONArray array = new JSONArray(json);
                accountList.clear();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    accountList.add(new Account(
                            obj.getString("name"),
                            obj.getDouble("balance"),
                            obj.optString("currency", "USD")
                    ));
                }
            }
        } catch (Exception e) {
            Log.e("ExpensesActivity", "Error calculating balances", e);
        }
    }

    private long getPeriodStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        switch (currentFilter) {
            case FILTER_DAILY:
                // Today 00:00:00
                break;
            case FILTER_WEEKLY:
                cal.setFirstDayOfWeek(Calendar.MONDAY);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                break;
            case FILTER_MONTHLY:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                break;
            case FILTER_YEARLY:
                cal.set(Calendar.DAY_OF_YEAR, 1);
                break;
        }
        return cal.getTimeInMillis();
    }

    private void saveActiveAccount(String name) {
        getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                .edit()
                .putString("ActiveAccount", name)
                .apply();
    }

    private void startVoiceRecognition() {
        startSpeechRecognitionWithSettings();
    }

    private void processVoiceCommand(String command) {
        String cmd = command.toLowerCase().trim();
        Toast.makeText(this, "Command: " + command, Toast.LENGTH_SHORT).show();

        if (cmd.contains("cash in") || cmd.contains("add income")) {
            findViewById(R.id.cashInButton).performClick();
        } else if (cmd.contains("cash out") || cmd.contains("add expense")) {
            findViewById(R.id.cashOutButton).performClick();
        } else if (cmd.contains("category") || cmd.contains("categories")) {
            startActivity(new Intent(this, CategoryActivity.class));
        } else if (cmd.contains("account")) {
            showAccountsDialog();
        } else if (cmd.contains("transfer")) {
            startActivity(new Intent(this, TransferActivity.class));
        } else if (cmd.contains("back") || cmd.contains("calendar")) {
            finish();
        } else if (cmd.contains("all")) {
            findViewById(R.id.allButton).performClick();
        } else if (cmd.contains("today") || cmd.contains("daily")) {
            findViewById(R.id.dailyButton).performClick();
        } else if (cmd.contains("weekly")) {
            findViewById(R.id.weeklyButton).performClick();
        } else if (cmd.contains("monthly")) {
            findViewById(R.id.monthlyButton).performClick();
        } else if (cmd.contains("print")) {
            Toast.makeText(this, "Opening Print options", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Command not recognized: " + command, Toast.LENGTH_LONG).show();
        }
    }

    private String getCategoryEmoji(String categoryName) {
        String lower = categoryName.toLowerCase();
        
        // Income
        if (lower.contains("salary")) return "💰";
        if (lower.contains("bonus")) return "🎁";
        if (lower.contains("business")) return "💼";
        if (lower.contains("investment")) return "📈";
        if (lower.contains("income")) return "💵";
        
        // Expenses
        if (lower.contains("air tickets") || lower.contains("flight")) return "✈️";
        if (lower.contains("auto rickshaw")) return "🛺";
        if (lower.contains("bike")) return "🏍️";
        if (lower.contains("bills")) return "🧾";
        if (lower.contains("cable")) return "📺";
        if (lower.contains("car insurance")) return "🛡️";
        if (lower.contains("car")) return "🚗";
        if (lower.contains("card fee")) return "💳";
        if (lower.contains("cigarette")) return "🚬";
        if (lower.contains("clothes")) return "👕";
        if (lower.contains("drinks")) return "🍺";
        if (lower.contains("driver")) return "👨‍✈️";
        if (lower.contains("durables")) return "📺";
        if (lower.contains("education")) return "📚";
        if (lower.contains("electricity")) return "💡";
        if (lower.contains("emi")) return "💸";
        if (lower.contains("entertainment")) return "🎬";
        if (lower.contains("fast food")) return "🍕";
        if (lower.contains("festivals")) return "🏮";
        if (lower.contains("fitness")) return "🏋️";
        if (lower.contains("fruits")) return "🍎";
        if (lower.contains("fuel")) return "⛽";
        if (lower.contains("furniture")) return "🛋️";
        if (lower.contains("gas")) return "🔥";
        if (lower.contains("gifts")) return "🎁";
        if (lower.contains("groceries")) return "🛒";
        if (lower.contains("health insurance")) return "🏥";
        if (lower.contains("health")) return "💊";
        if (lower.contains("hobby")) return "🎨";
        if (lower.contains("home insurance")) return "🏡";
        if (lower.contains("house hold")) return "🏠";
        if (lower.contains("insurance")) return "🛡️";
        if (lower.contains("internet")) return "🌐";
        if (lower.contains("kids")) return "👶";
        if (lower.contains("laundry")) return "🧺";
        if (lower.contains("maid")) return "🧹";
        if (lower.contains("medicine")) return "💊";
        if (lower.contains("milk")) return "🥛";
        if (lower.contains("mobile")) return "📱";
        if (lower.contains("parking")) return "🅿️";
        if (lower.contains("party")) return "🥳";
        if (lower.contains("grooming")) return "✂️";
        if (lower.contains("pet")) return "🐾";
        if (lower.contains("rent")) return "🔑";
        if (lower.contains("repair")) return "🛠️";
        if (lower.contains("restaurant") || lower.contains("food")) return "🍔";
        if (lower.contains("savings")) return "🐷";
        if (lower.contains("shopping")) return "🛍️";
        if (lower.contains("social")) return "🤝";
        if (lower.contains("stationery")) return "✏️";
        if (lower.contains("taxes")) return "🏛️";
        if (lower.contains("taxi")) return "🚕";
        if (lower.contains("toiletries")) return "🧻";
        if (lower.contains("toll")) return "🛣️";
        if (lower.contains("toys")) return "🧸";
        if (lower.contains("transport")) return "🚌";
        if (lower.contains("vacation")) return "🌴";
        if (lower.contains("water")) return "💧";
        
        return "📝";
    }

    private void showDateRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setTheme(R.style.CustomDatePickerTheme)
                .build();
        builder.addOnPositiveButtonClickListener(selection -> {
            if (selection.first != null && selection.second != null) {
                // Adjust to cover the full end day
                Calendar end = Calendar.getInstance();
                end.setTimeInMillis(selection.second);
                end.set(Calendar.HOUR_OF_DAY, 23);
                end.set(Calendar.MINUTE, 59);
                end.set(Calendar.SECOND, 59);
                
                customStartDate = selection.first;
                customEndDate = end.getTimeInMillis();
                currentFilter = FILTER_CUSTOM_RANGE;
                updateFilterButtonsUI();
                refreshTransactionsList();
            }
        });
        builder.show(getSupportFragmentManager(), "date_range_picker");
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new android.app.DatePickerDialog(this, (view, year, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(year, month, day);
            selected.set(Calendar.HOUR_OF_DAY, 0);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
            customStartDate = selected.getTimeInMillis();
            
            selected.set(Calendar.HOUR_OF_DAY, 23);
            selected.set(Calendar.MINUTE, 59);
            selected.set(Calendar.SECOND, 59);
            selected.set(Calendar.MILLISECOND, 999);
            customEndDate = selected.getTimeInMillis();
            
            currentFilter = FILTER_CUSTOM_RANGE;
            updateFilterButtonsUI();
            refreshTransactionsList();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showUserInfoDialog(String title, String prefKey) {
        EditText input = new EditText(this);
        input.setText(getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString(prefKey, ""));
        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Edit " + title)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString().trim();
                    getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).edit().putString(prefKey, val).apply();
                    Toast.makeText(this, title + " saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
