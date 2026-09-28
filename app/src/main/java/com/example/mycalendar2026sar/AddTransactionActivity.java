package com.example.mycalendar2026sar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import android.content.SharedPreferences;

public class AddTransactionActivity extends AppCompatActivity {

    private TextView titleView, txtDate, txtTime, txtCurrency;
    private Button btnCashIn, btnCashOut, btnSaveExit, btnSaveContinue, btnDelete;
    private EditText editAmount, editItems, editNotes;
    private ImageView btnCalculator, btnVoice, btnSelectCategory;
    
    private String currentType = Transaction.TYPE_CASH_IN;
    private String currentCurrency = "USD";
    private long editTransactionId = -1;
    private double originalAmount = 0;
    private String originalType = "";
    private String originalAccount = "";

    private Calendar selectedDateTime = Calendar.getInstance();
    private SimpleDateFormat dateSdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private boolean isFormattingAmount = false;

    private final android.text.TextWatcher amountTextWatcher = new android.text.TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(android.text.Editable s) {
            if (isFormattingAmount) return;

            String originalText = s.toString();
            if (originalText.isEmpty()) return;

            int cursorPosition = editAmount.getSelectionStart();
            boolean isLbp = "LBP".equalsIgnoreCase(currentCurrency);

            int significantCountBefore = 0;
            for (int i = 0; i < Math.min(cursorPosition, originalText.length()); i++) {
                char c = originalText.charAt(i);
                if (isLbp ? Character.isDigit(c) : (Character.isDigit(c) || c == '.')) {
                    significantCountBefore++;
                }
            }

            String formatted = CurrencyFormatter.formatLiveText(originalText, currentCurrency);

            if (formatted.equals(originalText)) return;

            isFormattingAmount = true;
            editAmount.setText(formatted);

            int newCursorPosition = 0;
            if (significantCountBefore > 0) {
                int currentSignificant = 0;
                for (int i = 0; i < formatted.length(); i++) {
                    char c = formatted.charAt(i);
                    if (isLbp ? Character.isDigit(c) : (Character.isDigit(c) || c == '.')) {
                        currentSignificant++;
                        if (currentSignificant == significantCountBefore) {
                            newCursorPosition = i + 1;
                            break;
                        }
                    }
                }
                if (currentSignificant < significantCountBefore) {
                    newCursorPosition = formatted.length();
                }
            }

            editAmount.setSelection(Math.min(newCursorPosition, formatted.length()));
            isFormattingAmount = false;
        }
    };

    private final ActivityResultLauncher<Intent> categoryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String category = result.getData().getStringExtra("category");
                    if (category != null) {
                        editItems.setText(category);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> voiceRecognitionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        SharedPreferences speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
                        boolean translateEnabled = speechPrefs.getBoolean("speech_translate_enabled", true);
                        if (translateEnabled) {
                            translateText(spokenText);
                        } else {
                            handleRecognizedText(spokenText);
                        }
                    }
                }
            });

    private void handleRecognizedText(String text) {
        String existingText = editNotes.getText().toString();
        editNotes.setText(existingText.isEmpty() ? text : existingText + " " + text);
    }

    private void translateText(final String text) {
        SharedPreferences speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
        boolean autoLang = speechPrefs.getBoolean("speech_auto_lang", true);
        String sourceLang = speechPrefs.getString("speech_source_lang", "en-US");

        if (autoLang) {
            LanguageIdentifier languageIdentifier = LanguageIdentification.getClient();
            languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener(languageCode -> {
                        if (languageCode.equals("und")) {
                            performTranslation(text, getMLKitCode(sourceLang));
                        } else {
                            performTranslation(text, languageCode);
                        }
                    })
                    .addOnFailureListener(e -> performTranslation(text, getMLKitCode(sourceLang)));
        } else {
            performTranslation(text, getMLKitCode(sourceLang));
        }
    }

    private void performTranslation(final String text, String sourceCode) {
        SharedPreferences speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
        String targetLang = speechPrefs.getString("speech_target_lang", "ar");
        String targetCode = getMLKitCode(targetLang);

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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_transaction);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.addTransactionMain), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        titleView = findViewById(R.id.addTransactionTitle);
        btnCashIn = findViewById(R.id.btnCashIn);
        btnCashOut = findViewById(R.id.btnCashOut);
        txtDate = findViewById(R.id.txtDate);
        txtTime = findViewById(R.id.txtTime);
        txtCurrency = findViewById(R.id.txtCurrency);
        editAmount = findViewById(R.id.editAmount);
        editAmount.addTextChangedListener(amountTextWatcher);
        editItems = findViewById(R.id.editItems);
        editNotes = findViewById(R.id.editNotes);
        btnCalculator = findViewById(R.id.btnCalculator);
        btnVoice = findViewById(R.id.btnVoice);
        btnSelectCategory = findViewById(R.id.btnSelectCategory);
        btnDelete = findViewById(R.id.btnDelete);
        btnSaveExit = findViewById(R.id.btnSaveExit);
        btnSaveContinue = findViewById(R.id.btnSaveContinue);

        editTransactionId = getIntent().getLongExtra("transaction_id", -1);
        if (editTransactionId != -1) {
            setupEditMode();
        } else {
            String initialType = getIntent().getStringExtra("type");
            if (Transaction.TYPE_CASH_OUT.equals(initialType)) {
                setMode(Transaction.TYPE_CASH_OUT);
            } else {
                setMode(Transaction.TYPE_CASH_IN);
            }

            long initialTimestamp = getIntent().getLongExtra("timestamp", -1);
            if (initialTimestamp != -1) {
                selectedDateTime.setTimeInMillis(initialTimestamp);
            }

            // Default currency from active account
            String activeAccountName = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE).getString("ActiveAccount", "Expenses");
            if (!activeAccountName.equals("Expenses")) {
                List<Account> accounts = BalanceManager.loadAccounts(this);
                for (Account a : accounts) {
                    if (a.getName().equals(activeAccountName)) {
                        currentCurrency = a.getCurrency();
                        break;
                    }
                }
            }
            txtCurrency.setText("Currency: " + currentCurrency);
        }

        updateDateTimeLabels();

        btnCashIn.setOnClickListener(v -> setMode(Transaction.TYPE_CASH_IN));
        btnCashOut.setOnClickListener(v -> setMode(Transaction.TYPE_CASH_OUT));

        findViewById(R.id.datePickerBox).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.timePickerBox).setOnClickListener(v -> showTimePicker());
        findViewById(R.id.currencyContainer).setOnClickListener(v -> showCurrencyPicker());

        btnCalculator.setOnClickListener(v -> {
            CalculatorDialogFragment calc = CalculatorDialogFragment.newInstance(result -> editAmount.setText(result));
            calc.show(getSupportFragmentManager(), "calculator");
        });

        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        int mainAccent = ThemeManager.getMainAccentColor(this);
        btnVoice.setImageTintList(ColorStateList.valueOf(mainAccent));

        findViewById(R.id.btnSelectCategory).setOnClickListener(v -> {
            Intent intent = new Intent(this, CategoryActivity.class);
            intent.putExtra("selection_mode", true);
            intent.putExtra("is_expense", Transaction.TYPE_CASH_OUT.equals(currentType));
            categoryLauncher.launch(intent);
        });

        findViewById(R.id.btnAddBills).setOnClickListener(v -> showBillsOptions());

        btnDelete.setOnClickListener(v -> {
            ThemeManager.showDialog(new AlertDialog.Builder(this)
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction?")
                    .setPositiveButton("Delete", (d, w) -> {
                        performDelete();
                        finish();
                    })
                    .setNegativeButton("Cancel", null), this);
        });

        btnSaveExit.setOnClickListener(v -> {
            if (saveTransaction()) {
                finish();
            }
        });

        btnSaveContinue.setOnClickListener(v -> {
            if (saveTransaction()) {
                editAmount.setText("");
                editItems.setText("");
                editNotes.setText("");
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setMode(String type) {
        currentType = type;
        int activeColor = ContextCompat.getColor(this, R.color.light_green);
        int inactiveColor = ContextCompat.getColor(this, R.color.gray);

        if (Transaction.TYPE_CASH_IN.equals(type)) {
            titleView.setText("Cash in");
            btnCashIn.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            btnCashOut.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
            editAmount.setHint("Cash in amount");
        } else {
            titleView.setText("Cash out");
            btnCashIn.setBackgroundTintList(ColorStateList.valueOf(inactiveColor));
            btnCashOut.setBackgroundTintList(ColorStateList.valueOf(activeColor));
            editAmount.setHint("Cash out amount");
        }
    }

    private void setupEditMode() {
        Transaction t = TransactionDbHelper.getInstance(this).getTransactionById(editTransactionId);
        if (t != null) {
            setMode(t.getType());
            currentCurrency = t.getCurrency();
            txtCurrency.setText("Currency: " + currentCurrency);
            if ("LBP".equalsIgnoreCase(t.getCurrency())) {
                editAmount.setText(CurrencyFormatter.formatLbpAmount(t.getAmount()));
            } else {
                editAmount.setText(String.format(Locale.US, "%,.2f", t.getAmount()));
            }
            editItems.setText(t.getTitle());
            editNotes.setText(t.getNotes());
            selectedDateTime.setTimeInMillis(t.getTimestamp());
            updateDateTimeLabels();

            btnDelete.setVisibility(View.VISIBLE);
            btnSaveContinue.setVisibility(View.GONE); // Usually not needed in edit mode

            // Store original values for balance correction
            originalAmount = t.getAmount();
            originalType = t.getType();
            originalAccount = t.getAccount();
        }
    }

    private void performDelete() {
        // 1. Reverse balance
        double delta = originalType.equals(Transaction.TYPE_CASH_IN) ? -originalAmount : originalAmount;
        BalanceManager.updateAccountBalance(this, originalAccount, delta);

        // 2. Delete from DB
        TransactionDbHelper.getInstance(this).deleteTransaction(editTransactionId);
    }

    private void updateDateTimeLabels() {
        txtDate.setText(dateSdf.format(selectedDateTime.getTime()));
        txtTime.setText(timeSdf.format(selectedDateTime.getTime()));
    }

    private void showDatePicker() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDateTime.set(Calendar.YEAR, year);
            selectedDateTime.set(Calendar.MONTH, month);
            selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateTimeLabels();
        }, selectedDateTime.get(Calendar.YEAR), selectedDateTime.get(Calendar.MONTH), selectedDateTime.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedDateTime.set(Calendar.MINUTE, minute);
            updateDateTimeLabels();
        }, selectedDateTime.get(Calendar.HOUR_OF_DAY), selectedDateTime.get(Calendar.MINUTE), false).show();
    }

    private void showCurrencyPicker() {
        List<CountryManager.Country> countries = CountryManager.getCountries();
        String[] items = new String[countries.size()];
        for (int i = 0; i < countries.size(); i++) {
            items[i] = countries.get(i).currency + " (" + countries.get(i).name + ")";
        }

        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Select Currency")
                .setItems(items, (dialog, which) -> {
                    currentCurrency = countries.get(which).currency;
                    txtCurrency.setText("Currency: " + currentCurrency);
                    editAmount.setText(editAmount.getText().toString());
                }), this);
    }

    private void startVoiceRecognition() {
        SharedPreferences speechPrefs = getSharedPreferences("SpeechSettings", Context.MODE_PRIVATE);
        boolean autoLang = speechPrefs.getBoolean("speech_auto_lang", true);
        String sourceLang = speechPrefs.getString("speech_source_lang", "en-US");
        String[] languageCodes = {"en-US", "ar", "fr", "es", "de", "zh", "it", "ja", "ru", "pt"};

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        
        if (!autoLang) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, sourceLang);
        } else {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", languageCodes);
        }
        
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note...");
        try {
            voiceRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }


    private void showBillsOptions() {
        String[] options = {"Camera", "Gallery", "PDF"};
        int[] icons = {
                R.drawable.ic_notif_camera_color,
                R.drawable.ic_notif_gallery_color,
                R.drawable.ic_pdf_logo
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Add Bills");

        android.widget.ListAdapter adapter = new android.widget.ArrayAdapter<String>(this, R.layout.dialog_item_with_icon, R.id.itemText, options) {
            @androidx.annotation.NonNull
            @Override
            public android.view.View getView(int position, android.view.View convertView, @androidx.annotation.NonNull android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                ImageView icon = view.findViewById(R.id.itemIcon);
                icon.setImageResource(icons[position]);
                icon.setImageTintList(null); // Remove default tinting
                return view;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            switch (which) {
                case 0: // Camera
                    Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivity(takePicture);
                    break;
                case 1: // Gallery
                    Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivity(pickPhoto);
                    break;
                case 2: // PDF
                    Intent pickPdf = new Intent(Intent.ACTION_GET_CONTENT);
                    pickPdf.setType("application/pdf");
                    startActivity(pickPdf);
                    break;
            }
        });
        ThemeManager.showDialog(builder, this);
    }

    private boolean saveTransaction() {
        String amountStr = editAmount.getText().toString().trim();
        String itemTitle = editItems.getText().toString().trim();
        String notes = editNotes.getText().toString().trim();
        
        if (amountStr.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (itemTitle.isEmpty()) {
            itemTitle = currentType.equals(Transaction.TYPE_CASH_IN) ? "Cash In" : "Cash Out";
        }

        double amount;
        try {
            if ("LBP".equalsIgnoreCase(currentCurrency)) {
                String cleanStr = amountStr.replace(",", "");
                if (cleanStr.contains(".") && cleanStr.matches(".*\\d+\\.\\d{3}(\\.\\d{3})*")) {
                    cleanStr = cleanStr.replace(".", "");
                }
                amount = Double.parseDouble(cleanStr);
            } else {
                amount = Double.parseDouble(amountStr.replace(",", ""));
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            return false;
        }

        // PRIORITIZE intent context (from Calendar) if available
        String account = getIntent().getStringExtra("account_context");
        if (account == null) {
            account = getSharedPreferences("ExpensesPrefs", MODE_PRIVATE)
                    .getString("ActiveAccount", "Expenses");
        }
        
        String type = currentType;
        
        if (editTransactionId != -1) {
            // EDIT MODE logic
            
            // 1. Reverse original balance
            double reverseDelta = originalType.equals(Transaction.TYPE_CASH_IN) ? -originalAmount : originalAmount;
            BalanceManager.updateAccountBalance(this, originalAccount, reverseDelta);
            
            // 2. Update DB record
            TransactionDbHelper.getInstance(this).updateTransaction(
                    editTransactionId,
                    itemTitle,
                    amount,
                    currentCurrency,
                    type,
                    selectedDateTime.getTimeInMillis(),
                    account,
                    notes,
                    "", // voice path
                    ""  // bills
            );
        } else {
            // NEW RECORD logic
            TransactionDbHelper.getInstance(this).addTransaction(
                    itemTitle,
                    amount,
                    currentCurrency,
                    type,
                    selectedDateTime.getTimeInMillis(),
                    account,
                    notes,
                    "", // voice path placeholder
                    ""  // bills placeholder
            );
        }

        // Apply NEW balance (Shared logic for both new/edit)

        // 1. Sync with Account Balance
        double delta = type.equals(Transaction.TYPE_CASH_IN) ? amount : -amount;
        BalanceManager.updateAccountBalance(this, account, delta);

        return true;
    }
}
