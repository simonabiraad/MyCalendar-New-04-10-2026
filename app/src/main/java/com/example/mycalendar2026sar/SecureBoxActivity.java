package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;

public class SecureBoxActivity extends AppCompatActivity {

    private RecyclerView categoryRecyclerView, notesRecyclerView;
    private EditText noteTitleInput, noteContentInput;
    private View noteInputArea;
    private SharedPreferences securePrefs, colorPrefs, fontPrefs, categoryPrefs, securityPrefs;
    
    private boolean isSelectionMode = false;
    private final java.util.HashSet<Integer> selectedIndices = new java.util.HashSet<>();
    private View selectionBar;
    private TextView selectionCountText;

    private String activeCategoryKey = "all_notes";
    private int activeCategoryColor = Color.GRAY;

    private CategoryAdapter categoryAdapter;
    private NoteAdapter noteAdapter;
    private List<CategoryItem> categoryList = new ArrayList<>();
    private List<NoteItem> noteList = new ArrayList<>();

    private Uri cameraImageUri;

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) launchImageEditor(cameraImageUri);
            });

    private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) launchImageEditor(uri);
            });

    private final ActivityResultLauncher<String[]> pdfLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) convertPdfToBitmap(uri);
            });

    private final ActivityResultLauncher<Intent> imageEditorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String path = result.getData().getStringExtra("resultPath");
                    if (path != null) saveImageNote(path);
                }
            });

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
        String existingText = noteContentInput.getText().toString();
        noteContentInput.setText(existingText.isEmpty() ? text : existingText + " " + text);
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


    private static final String SEPARATOR = "###NOTE_SEP###";
    private static final String TITLE_SEP = "###TITLE_SEP###";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_secure_box);

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navSecureBoxButton);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        colorPrefs = getSharedPreferences("AppColors", Context.MODE_PRIVATE);
        securePrefs = getSharedPreferences("SecureBoxNotes", Context.MODE_PRIVATE);
        fontPrefs = getSharedPreferences("AppFonts", Context.MODE_PRIVATE);
        categoryPrefs = getSharedPreferences("SecureBoxCategories", Context.MODE_PRIVATE);
        securityPrefs = getSharedPreferences("SecuritySettings", Context.MODE_PRIVATE);

        initViews();
        loadCategories();
        setupReordering();

        performSelectCategory("all_notes", Color.GRAY);
        refreshColors();

        setupSwipeGesture();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode();
                } else {
                    Intent intent = new Intent(SecureBoxActivity.this, TaskActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }
            }
        });
    }

    private void initViews() {
        categoryRecyclerView = findViewById(R.id.categoryRecyclerView);
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        noteTitleInput = findViewById(R.id.noteTitleInput);
        noteContentInput = findViewById(R.id.noteContentInput);
        noteInputArea = findViewById(R.id.noteInputArea);
        selectionBar = findViewById(R.id.selectionBar);
        selectionCountText = findViewById(R.id.selectionCountText);

        categoryRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        notesRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        findViewById(R.id.backButton).setOnClickListener(v -> {
            if (isSelectionMode) {
                exitSelectionMode();
            } else {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        findViewById(R.id.saveStickyNoteButton).setOnClickListener(v -> saveNote());
        findViewById(R.id.sbVoiceNoteButton).setOnClickListener(v -> startVoiceRecognition());
        findViewById(R.id.sbCameraNoteButton).setOnClickListener(v -> showSourceOptionsDialog());
        findViewById(R.id.addCategoryHeaderButton).setOnClickListener(v -> showAddCategoryDialog());
        
        findViewById(R.id.cancelSelectionBtn).setOnClickListener(v -> exitSelectionMode());
        findViewById(R.id.deleteSelectedBtn).setOnClickListener(v -> {
            ThemeManager.showDialog(new AlertDialog.Builder(this)
                    .setTitle("Delete Selected")
                    .setMessage("Permanently delete selected sticky notes?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteSelectedNotes())
                    .setNegativeButton("No", null), this);
        });
        findViewById(R.id.moveSelectedBtn).setOnClickListener(v -> showMoveSelectedDialog());
    }

    private void setupReordering() {
        ItemTouchHelper catHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == 0 || to == 0) return false;
                Collections.swap(categoryList, from, to);
                categoryAdapter.notifyItemMoved(from, to);
                saveCategoryOrder();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        });
        catHelper.attachToRecyclerView(categoryRecyclerView);

        ItemTouchHelper noteHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                if (activeCategoryKey.equals("all_notes")) return false;
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(noteList, from, to);
                noteAdapter.notifyItemMoved(from, to);
                saveNoteOrder();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        });
        noteHelper.attachToRecyclerView(notesRecyclerView);
    }

    private void loadCategories() {
        categoryList.clear();
        int allNotesColor = colorPrefs.getInt("color_sb_all_notes", Color.GRAY);
        categoryList.add(new CategoryItem("all_notes", "All Notes", allNotesColor));
        if (categoryPrefs.getAll().isEmpty()) {
            categoryPrefs.edit().putString("cats_order", "personal_notes,password_notes,family_notes,work_notes,others_notes").apply();
        }
        String o = categoryPrefs.getString("cats_order", "");
        if (!o.isEmpty()) {
            for (String k : o.split(",")) {
                String n = categoryPrefs.getString(k, k.substring(0,1).toUpperCase() + k.substring(1).replace("_notes", ""));
                int def;
                if (k.equals("password_notes")) def = getColor(R.color.unmellow_yellow);
                else if (k.equals("family_notes")) def = getColor(R.color.blue);
                else if (k.equals("work_notes")) def = getColor(R.color.honey);
                else if (k.equals("others_notes")) def = getColor(R.color.teal_200);
                else def = getColor(R.color.light_green);
                
                int c = colorPrefs.getInt("color_sb_" + k.replace("_notes", ""), def);
                categoryList.add(new CategoryItem(k, n, c));
            }
        }
        categoryAdapter = new CategoryAdapter();
        categoryRecyclerView.setAdapter(categoryAdapter);
    }

    private void saveCategoryOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < categoryList.size(); i++) {
            if (i > 1) sb.append(","); sb.append(categoryList.get(i).key);
        }
        categoryPrefs.edit().putString("cats_order", sb.toString()).apply();
        WidgetUtils.updateAllWidgets(this);
    }

    private void loadNotes(String key) {
        noteList.clear();
        if (key.equals("all_notes")) {
            int allNotesColor = categoryList.isEmpty() ? Color.GRAY : categoryList.get(0).color;
            for (CategoryItem cat : categoryList) {
                if (cat.key.equals("all_notes")) continue;
                // Skip protected categories in "All Notes" view
                if (securityPrefs.getBoolean("cat_protected_" + cat.key, false)) continue;
                
                String s = securePrefs.getString(cat.key, "");
                if (!s.isEmpty()) {
                    for (String str : s.split(SEPARATOR)) if (!str.trim().isEmpty()) noteList.add(new NoteItem(cat.key, str, allNotesColor));
                }
            }
        } else {
            String s = securePrefs.getString(key, "");
            if (!s.isEmpty()) {
                for (String str : s.split(SEPARATOR)) if (!str.trim().isEmpty()) noteList.add(new NoteItem(key, str, activeCategoryColor));
            }
        }
        noteAdapter = new NoteAdapter();
        notesRecyclerView.setAdapter(noteAdapter);
    }

    private void saveNoteOrder() {
        if (activeCategoryKey.equals("all_notes")) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noteList.size(); i++) {
            if (i > 0) sb.append(SEPARATOR); sb.append(noteList.get(i).rawContent);
        }
        securePrefs.edit().putString(activeCategoryKey, sb.toString()).apply();
        WidgetUtils.updateAllWidgets(this);
    }

    private void selectCategory(String k, int c) {
        if (k.equals("all_notes")) performSelectCategory(k, c);
        else if (securityPrefs.getBoolean("cat_protected_" + k, false)) verifyCatAccess(k, c);
        else performSelectCategory(k, c);
    }

    private void performSelectCategory(String k, int c) {
        activeCategoryKey = k; activeCategoryColor = c; categoryAdapter.notifyDataSetChanged();
        if (noteInputArea != null) {
            noteInputArea.setVisibility(k.equals("all_notes") ? View.GONE : View.VISIBLE);
        }
        loadNotes(k);
    }

    private void saveNote() {
        if (activeCategoryKey.equals("all_notes")) { Toast.makeText(this, "Select a category first", Toast.LENGTH_SHORT).show(); return; }
        String t = noteTitleInput.getText().toString().trim(), c = noteContentInput.getText().toString().trim();
        if (c.isEmpty()) { Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show(); return; }
        String fn = (t.isEmpty() ? "No Name" : t) + TITLE_SEP + c;
        String ex = securePrefs.getString(activeCategoryKey, "");
        securePrefs.edit().putString(activeCategoryKey, ex.isEmpty() ? fn : ex + SEPARATOR + fn).apply();
        noteTitleInput.setText(""); noteContentInput.setText(""); loadNotes(activeCategoryKey);
        WidgetUtils.updateAllWidgets(this);
    }

    private void deleteSelectedNotes() {
        for (int i : new ArrayList<>(selectedIndices)) {
            if (i < noteList.size()) {
                NoteItem itm = noteList.get(i);
                String s = securePrefs.getString(itm.categoryKey, "");
                List<String> l = new ArrayList<>(Arrays.asList(s.split(SEPARATOR)));
                l.remove(itm.rawContent);
                securePrefs.edit().putString(itm.categoryKey, String.join(SEPARATOR, l)).apply();
            }
        }
        exitSelectionMode(); loadNotes(activeCategoryKey);
        WidgetUtils.updateAllWidgets(this);
    }

    private void exitSelectionMode() {
        isSelectionMode = false; selectedIndices.clear(); selectionBar.setVisibility(View.GONE); noteAdapter.notifyDataSetChanged();
    }

    private void showMoveSelectedDialog() {
        List<String> names = new ArrayList<>(), keys = new ArrayList<>();
        for (CategoryItem cat : categoryList) if (!cat.key.equals("all_notes") && !cat.key.equals(activeCategoryKey)) { names.add(cat.name); keys.add(cat.key); }
        if (names.isEmpty()) return;
        ThemeManager.showDialog(new AlertDialog.Builder(this).setTitle("Move to Category").setItems(names.toArray(new String[0]), (d, w) -> moveSelectedToCategory(keys.get(w))), this);
    }

    private void moveSelectedToCategory(String tk) {
        for (int i : selectedIndices) {
            NoteItem itm = noteList.get(i);
            String s = securePrefs.getString(itm.categoryKey, "");
            List<String> sl = new ArrayList<>(Arrays.asList(s.split(SEPARATOR))); sl.remove(itm.rawContent);
            securePrefs.edit().putString(itm.categoryKey, String.join(SEPARATOR, sl)).apply();
            String t = securePrefs.getString(tk, "");
            securePrefs.edit().putString(tk, t.isEmpty() ? itm.rawContent : t + SEPARATOR + itm.rawContent).apply();
        }
        exitSelectionMode(); loadNotes(activeCategoryKey);
        WidgetUtils.updateAllWidgets(this);
    }

    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(p.getContext());
            GridLayoutManager.LayoutParams lp = new GridLayoutManager.LayoutParams(-1, -2);
            int margin = (int) (4 * p.getContext().getResources().getDisplayMetrics().density);
            lp.setMargins(margin, margin, margin, margin);
            b.setLayoutParams(lp);
            int padHoriz = (int) (4 * p.getContext().getResources().getDisplayMetrics().density);
            int padVert = (int) (8 * p.getContext().getResources().getDisplayMetrics().density);
            b.setPadding(padHoriz, padVert, padHoriz, padVert);
            b.setSingleLine(true);
            b.setMaxLines(1);
            int cornerRadius = (int) (20 * p.getContext().getResources().getDisplayMetrics().density);
            b.setCornerRadius(cornerRadius);
            return new VH(b);
        }

        private int getIconForCategoryKey(String key) {
            if (key == null) return R.drawable.ic_menu_sticky_note_color;
            switch (key.toLowerCase(Locale.US)) {
                case "all_notes":
                    return R.drawable.ic_menu_notebook_color;
                case "password_notes":
                case "password":
                    return R.drawable.ic_menu_password_color;
                case "family_notes":
                case "family":
                    return R.drawable.ic_menu_profile_color;
                case "work_notes":
                case "work":
                    return R.drawable.ic_cat_business_color;
                default:
                    return R.drawable.ic_menu_sticky_note_color;
            }
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            com.google.android.material.button.MaterialButton b = (com.google.android.material.button.MaterialButton) h.itemView;
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setSingleLine(true);
            b.setMaxLines(1);
            applyFontSettings(b, 15);

            CategoryItem itm = categoryList.get(pos);
            b.setText(itm.name);

            int bgColor = itm.color;
            if (!activeCategoryKey.equals(itm.key)) {
                int alphaBg = (bgColor & 0x00FFFFFF) | 0x99000000;
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(alphaBg));
            } else {
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
            }
            b.setAlpha(1.0f);

            int iconRes = getIconForCategoryKey(itm.key);
            b.setIconResource(iconRes);
            b.setIconTint(android.content.res.ColorStateList.valueOf(Color.WHITE));
            b.setIconSize((int) (18 * h.itemView.getContext().getResources().getDisplayMetrics().density));
            b.setIconPadding((int) (6 * h.itemView.getContext().getResources().getDisplayMetrics().density));
            b.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);

            b.setOnClickListener(v -> selectCategory(itm.key, itm.color));
            b.setOnLongClickListener(v -> { showCategoryOptionsDialog(itm.key, b); return true; });
        }

        @Override public int getItemCount() { return categoryList.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    private class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.sticky_note_item, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            NoteItem itm = noteList.get(pos);
            String t = "Note", c = itm.rawContent;
            if (itm.rawContent.contains(TITLE_SEP)) { String[] pts = itm.rawContent.split(TITLE_SEP); t = pts[0]; c = pts.length > 1 ? pts[1] : ""; }
            TextView tt = h.itemView.findViewById(R.id.noteTitle), ct = h.itemView.findViewById(R.id.noteText);
            MaterialCardView cv = h.itemView.findViewById(R.id.cardView); ImageView iv = h.itemView.findViewById(R.id.noteImage);
            tt.setText(t); applyFontSettings(tt, 14);
            if (c.startsWith("[IMG:")) { ct.setVisibility(View.GONE); iv.setVisibility(View.VISIBLE); iv.setImageURI(Uri.fromFile(new File(c.substring(5, c.length() - 1)))); }
            else { ct.setText(c); ct.setVisibility(View.VISIBLE); iv.setVisibility(View.GONE); applyFontSettings(ct, 12); }
            cv.setCardBackgroundColor(itm.color); cv.setStrokeWidth(selectedIndices.contains(pos) ? 8 : 0); cv.setStrokeColor(Color.WHITE);
            final String ft = t, fc = c;
            h.itemView.setOnClickListener(v -> { if (isSelectionMode) toggleSelection(pos); else showEditFullPage(itm.categoryKey, pos, ft, fc, null); });
            h.itemView.setOnLongClickListener(v -> { if (!isSelectionMode) { isSelectionMode = true; selectionBar.setVisibility(View.VISIBLE); } toggleSelection(pos); return true; });
        }
        private void toggleSelection(int pos) {
            if (selectedIndices.contains(pos)) selectedIndices.remove(pos); else selectedIndices.add(pos);
            if (selectedIndices.isEmpty()) exitSelectionMode(); else { selectionCountText.setText(selectedIndices.size() + " selected"); notifyItemChanged(pos); }
        }
        @Override public int getItemCount() { return noteList.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    private static class CategoryItem { String key, name; int color; CategoryItem(String k, String n, int c) { key = k; name = n; color = c; } }
    private static class NoteItem { String categoryKey, rawContent; int color; NoteItem(String k, String r, int c) { categoryKey = k; rawContent = r; color = c; } }

    private void showSourceOptionsDialog() {
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_media_picker, null);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Attach Media").setView(dv).create();
        dv.findViewById(R.id.optionCamera).setOnClickListener(v -> { d.dismiss(); File f = new File(getCacheDir(), "camera_secure.jpg"); cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f); cameraLauncher.launch(cameraImageUri); });
        dv.findViewById(R.id.optionGallery).setOnClickListener(v -> { d.dismiss(); galleryLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()); });
        dv.findViewById(R.id.optionPdf).setOnClickListener(v -> { d.dismiss(); pdfLauncher.launch(new String[]{"application/pdf"}); });
        d.show();
    }

    private void launchImageEditor(Uri u) { Intent i = new Intent(this, ImageEditorActivity.class); i.putExtra("imageUri", u.toString()); imageEditorLauncher.launch(i); }

    private void convertPdfToBitmap(Uri u) {
        try {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(u, "r");
            if (pfd != null) {
                PdfRenderer r = new PdfRenderer(pfd);
                if (r.getPageCount() > 0) {
                    PdfRenderer.Page p = r.openPage(0); Bitmap b = Bitmap.createBitmap(p.getWidth() * 2, p.getHeight() * 2, Bitmap.Config.ARGB_8888);
                    new Canvas(b).drawColor(Color.WHITE); p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close();
                    File f = new File(getCacheDir(), "pdf_page_temp.jpg"); FileOutputStream fos = new FileOutputStream(f); b.compress(Bitmap.CompressFormat.JPEG, 90, fos); fos.close(); launchImageEditor(Uri.fromFile(f));
                }
                r.close(); pfd.close();
            }
        } catch (Exception e) { Toast.makeText(this, "PDF Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void saveImageNote(String p) {
        if (activeCategoryKey.equals("all_notes")) { Toast.makeText(this, "Select a category first", Toast.LENGTH_SHORT).show(); return; }
        String n = "Image" + TITLE_SEP + "[IMG:" + p + "]"; String ex = securePrefs.getString(activeCategoryKey, "");
        securePrefs.edit().putString(activeCategoryKey, ex.isEmpty() ? n : ex + SEPARATOR + n).apply(); loadNotes(activeCategoryKey);
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


    private void verifyCatAccess(String k, int c) {
        String p = securityPrefs.getString("custom_password", null);
        if (p != null) {
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Locked"); EditText in = new EditText(this); in.setInputType(129); b.setView(in);
            b.setPositiveButton("Access", (d, w) -> { if (in.getText().toString().equals(p)) performSelectCategory(k, c); else Toast.makeText(this, "Incorrect", Toast.LENGTH_SHORT).show(); });
            ThemeManager.showDialog(b, this);
        } else {
            Executor ex = ContextCompat.getMainExecutor(this);
            new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult r) { performSelectCategory(k, c); }
            }).authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Unlock").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build());
        }
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("New Category"); EditText in = new EditText(this); in.setHint("Name"); b.setView(in);
        b.setPositiveButton("Choose Color", (d, w) -> { if (!in.getText().toString().isEmpty()) showColorPickerForCategory(in.getText().toString().trim()); });
        ThemeManager.showDialog(b, this);
    }

    private void showColorPickerForCategory(String n) {
        int initialColor = 0xFF4CAF50;
        new ColorPickerDialog(this, initialColor, selectedColor -> {
            String k = n.toLowerCase().replace(" ", "_") + "_notes_" + System.currentTimeMillis();
            categoryPrefs.edit().putString(k, n).apply();
            String o = categoryPrefs.getString("cats_order", "");
            categoryPrefs.edit().putString("cats_order", o.isEmpty() ? k : o + "," + k).apply();
            colorPrefs.edit().putInt("color_sb_" + k.replace("_notes", ""), selectedColor).apply();
            loadCategories();
            selectCategory(k, selectedColor);
        }).show();
    }

    private void showCategoryOptionsDialog(String k, Button b) {
        ThemeManager.showDialog(new AlertDialog.Builder(this).setTitle("Options").setItems(new String[]{"Rename", "Change Color", "Set Password", "Delete Category"}, (d, w) -> {
            if (w == 0) showRenameDialog(k, b); else if (w == 1) showColorPickerForExisting(k, b); else if (w == 2) showCategorySecurityToggleDialog(k, b.getText().toString()); else if (w == 3) showDeleteCategoryConfirm(k);
        }), this);
    }

    private void showCategorySecurityToggleDialog(String k, String n) {
        ThemeManager.showDialog(new AlertDialog.Builder(this).setTitle("Password for " + n + "?").setItems(new String[]{"Yes", "No"}, (d, w) -> { if (w == 0) securityPrefs.edit().putBoolean("cat_protected_" + k, true).apply(); else verifyThenDisableCatPassword(n, "cat_protected_" + k); }), this);
    }

    private void verifyThenDisableCatPassword(String n, String pk) {
        String p = securityPrefs.getString("custom_password", null);
        if (p != null) {
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Verify"); EditText in = new EditText(this); in.setInputType(129); b.setView(in);
            b.setPositiveButton("Verify", (d, w) -> { if (in.getText().toString().equals(p)) securityPrefs.edit().putBoolean(pk, false).apply(); });
            ThemeManager.showDialog(b, this);
        } else {
            Executor ex = ContextCompat.getMainExecutor(this);
            new BiometricPrompt(this, ex, new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult r) { securityPrefs.edit().putBoolean(pk, false).apply(); }
            }).authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Disable").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL).build());
        }
    }

    private void showRenameDialog(String k, Button b) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Rename"); EditText in = new EditText(this); in.setText(b.getText().toString()); builder.setView(in);
        builder.setPositiveButton("OK", (d, w) -> { if (!in.getText().toString().isEmpty()) { categoryPrefs.edit().putString(k, in.getText().toString()).apply(); b.setText(in.getText().toString()); } });
        ThemeManager.showDialog(builder, this);
    }

    private void showColorPickerForExisting(String k, Button b) {
        int initialColor = colorPrefs.getInt("color_sb_" + k.replace("_notes", ""), 0xFF4CAF50);
        new ColorPickerDialog(this, initialColor, selectedColor -> {
            colorPrefs.edit().putInt("color_sb_" + k.replace("_notes", ""), selectedColor).apply();
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selectedColor));
            if (activeCategoryKey.equals(k)) {
                selectCategory(k, selectedColor);
            }
        }).show();
    }

    private void showDeleteCategoryConfirm(String k) {
        ThemeManager.showDialog(new AlertDialog.Builder(this).setTitle("Delete?").setMessage("Are you sure?").setPositiveButton("Yes", (d, w) -> {
            categoryPrefs.edit().remove(k).apply(); securePrefs.edit().remove(k).apply(); String o = categoryPrefs.getString("cats_order", "");
            if (!o.isEmpty()) { List<String> l = new ArrayList<>(Arrays.asList(o.split(","))); l.remove(k); categoryPrefs.edit().putString("cats_order", String.join(",", l)).apply(); }
            if (activeCategoryKey.equals(k)) performSelectCategory("all_notes", Color.GRAY); loadCategories();
        }).setNegativeButton("No", null), this);
    }

    private void showEditFullPage(String k, int idx, String t, String c, ViewGroup ignore) {
        android.app.Dialog d = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen); ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(activeCategoryColor);
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(32, 32, 32, 32); EditText te = new EditText(this); te.setText(t); applyFontSettings(te, 22); l.addView(te);
        EditText ce = null;
        if (c.startsWith("[IMG:")) { ImageView iv = new ImageView(this); iv.setImageURI(Uri.fromFile(new File(c.substring(5, c.length() - 1)))); iv.setScaleType(ImageView.ScaleType.FIT_CENTER); l.addView(iv); }
        else { ce = new EditText(this); ce.setText(c); applyFontSettings(ce, 18); ce.setGravity(48); ce.setBackground(null); l.addView(ce); }
        LinearLayout bl = new LinearLayout(this); bl.setGravity(android.view.Gravity.END); Button can = new Button(this); can.setText("Cancel"); applyFontSettings(can, 16); can.setOnClickListener(v -> d.dismiss()); bl.addView(can);
        final EditText fce = ce;
        Button sh = new Button(this); sh.setText("Share"); applyFontSettings(sh, 16);
        sh.setOnClickListener(v -> {
            String nt = te.getText().toString().trim();
            String nc = fce != null ? fce.getText().toString().trim() : (c.startsWith("[IMG:") ? "[Image content]" : c);
            String shareText = "*" + nt + "*\n\n" + nc;
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            sendIntent.setPackage("com.whatsapp");
            try {
                startActivity(sendIntent);
            } catch (android.content.ActivityNotFoundException ex) {
                sendIntent.setPackage(null);
                startActivity(Intent.createChooser(sendIntent, "Share via"));
            }
        });
        bl.addView(sh);
        Button pr = new Button(this); pr.setText("Print"); applyFontSettings(pr, 16); pr.setOnClickListener(v -> { String p = te.getText().toString() + (fce != null ? "\n\n" + fce.getText().toString() : "\n\n[Image]"); printSingleNote(p); }); bl.addView(pr);
        Button sa = new Button(this); sa.setText("Save"); applyFontSettings(sa, 16); sa.setOnClickListener(v -> { String nt = te.getText().toString().trim(), nc = fce != null ? fce.getText().toString().trim() : c; if (fce != null && nc.isEmpty()) Toast.makeText(this, "Empty", Toast.LENGTH_SHORT).show(); else { updateNote(k, idx, (nt.isEmpty() ? "No Name" : nt) + TITLE_SEP + nc); d.dismiss(); } }); bl.addView(sa);
        l.addView(bl); s.addView(l); d.setContentView(s); d.show();
        if (d.getWindow() != null && d.getWindow().getDecorView() != null) {
            ThemeManager.applyBlackCursorToAllEditTexts(d.getWindow().getDecorView());
        }
    }

    private void updateNote(String k, int idx, String fn) {
        String s = securePrefs.getString(k, ""); if (s.isEmpty()) return; List<String> l = new ArrayList<>(Arrays.asList(s.split(SEPARATOR)));
        if (idx < l.size()) { l.set(idx, fn); securePrefs.edit().putString(k, String.join(SEPARATOR, l)).apply(); loadNotes(activeCategoryKey); WidgetUtils.updateAllWidgets(this); }
    }

    private void printSingleNote(String n) {
        String h = "<html><body><h1>Secure Note</h1><p>" + n.replace("\n", "<br>") + "</p></body></html>"; WebView w = new WebView(this);
        w.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView v, String u) {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                pm.print("Secure_Note", v.createPrintDocumentAdapter("Secure_Note"), new PrintAttributes.Builder().build());
            }
        });
        w.loadDataWithBaseURL(null, h, "text/HTML", "UTF-8", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshColors();
        BottomNavigationHelper.setupBottomNavigation(this, R.id.navSecureBoxButton);
    }

    private void refreshColors() {
        int m = ThemeManager.getMainAccentColor(this);
        View mainView = findViewById(R.id.main);
        if (mainView != null) mainView.setBackgroundColor(colorPrefs.getInt("color_app_background", Color.BLACK));
        TextView t = findViewById(R.id.secureBoxTitle); if (t != null) { t.setTextColor(m); applyFontSettings(t, 24); }
        ImageView titleIcon = findViewById(R.id.secureBoxTitleIcon); if (titleIcon != null) { titleIcon.setImageTintList(ColorStateList.valueOf(m)); }
        if (noteTitleInput != null) applyFontSettings(noteTitleInput, 18); if (noteContentInput != null) applyFontSettings(noteContentInput, 18);

        View addCatBtn = findViewById(R.id.addCategoryHeaderButton);
        if (addCatBtn instanceof ImageButton) ((ImageButton) addCatBtn).setImageTintList(ColorStateList.valueOf(m));
        View voiceBtn = findViewById(R.id.sbVoiceNoteButton);
        if (voiceBtn instanceof ImageButton) ((ImageButton) voiceBtn).setImageTintList(ColorStateList.valueOf(m));
        View camBtn = findViewById(R.id.sbCameraNoteButton);
        if (camBtn instanceof ImageButton) ((ImageButton) camBtn).setImageTintList(ColorStateList.valueOf(m));
        View saveBtn = findViewById(R.id.saveStickyNoteButton);
        if (saveBtn instanceof ImageButton) ((ImageButton) saveBtn).setImageTintList(ColorStateList.valueOf(m));

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navSecureBoxButton);
    }

    private void applyFontSettings(TextView tv, float b) {
        int si = fontPrefs.getInt("font_style", 0); Typeface tf = Typeface.DEFAULT; if (si == 1) tf = Typeface.SANS_SERIF; else if (si == 2) tf = Typeface.SERIF; else if (si == 3) tf = Typeface.MONOSPACE;
        tv.setTypeface(tf); int sz = fontPrefs.getInt("font_size_index", 1); float m = 1.0f; if (sz == 0) m = 0.8f; else if (sz == 2) m = 1.3f; else if (sz == 3) m = 1.6f;
        tv.setTextSize(b * m);
    }

    private GestureDetector gestureDetector;

    private void setupSwipeGesture() {
        gestureDetector = SwipeGestureHelper.createSwipeDetector(this,
            () -> {
                Intent intent = new Intent(SecureBoxActivity.this, TaskActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            },
            () -> {
                Intent intent = new Intent(SecureBoxActivity.this, ExpensesActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
        );
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }
}
