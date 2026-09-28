package com.example.mycalendar2026sar;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.speech.RecognizerIntent;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.common.model.DownloadConditions;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TaskActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "TaskPrefs";
    private static final String KEY_TASKS = "tasks";

    private EditText taskInput;
    private RecyclerView recyclerView;
    private TaskAdapter adapter;
    private List<TaskItem> taskList = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    private View selectionBar;
    private TextView selectionCountText;
    private boolean isSelectionMode = false;
    private final List<Integer> selectedIndices = new ArrayList<>();

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
        String existingText = taskInput.getText().toString();
        taskInput.setText(existingText.isEmpty() ? text : existingText + " " + text);
        taskInput.setSelection(taskInput.getText().length());
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
        setContentView(R.layout.activity_task);

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navTaskButton);
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        selectionBar = findViewById(R.id.selectionBar);
        selectionCountText = findViewById(R.id.selectionCountText);

        findViewById(R.id.cancelSelectionBtn).setOnClickListener(v -> exitSelectionMode());
        findViewById(R.id.shareSelectedBtn).setOnClickListener(v -> shareSelectedTasks());
        findViewById(R.id.deleteSelectedBtn).setOnClickListener(v -> deleteSelectedTasks());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSelectionMode) {
                    exitSelectionMode();
                    return;
                }
                AlertDialog dialog = new AlertDialog.Builder(TaskActivity.this, R.style.CustomAlertDialogTheme)
                        .setTitle("Leave Page")
                        .setMessage("Are you sure you want to leave this page?")
                        .setPositiveButton("Yes", (d, which) -> {
                            Intent intent = new Intent(TaskActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("No", null)
                        .show();
                ThemeManager.styleDialogButtons(dialog, TaskActivity.this);
            }
        });

        taskInput = findViewById(R.id.taskInput);
        recyclerView = findViewById(R.id.taskRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false);
        recyclerView.setLayoutManager(layoutManager);

        findViewById(R.id.taskBackButton).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.voiceTaskButton).setOnClickListener(v -> startVoiceRecognition());
        findViewById(R.id.addTaskButton).setOnClickListener(v -> addTask());

        findViewById(R.id.taskMenuButton).setOnClickListener(this::showTaskMenu);

        loadTasks();
        adapter = new TaskAdapter(taskList);
        recyclerView.setAdapter(adapter);

        if (getIntent().getBooleanExtra("fromNotification", false)) {
            String title = getIntent().getStringExtra("title");
            String notes = getIntent().getStringExtra("notes");
            String taskText = title + (notes != null && !notes.isEmpty() ? ": " + notes : "");
            taskInput.setText(taskText);
            addTask();
        }

        setupDragAndDrop();
        applyColors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private void applyColors() {
        int accent = ThemeManager.getMainAccentColor(this);
        TextView titleTv = findViewById(R.id.taskTitle);
        if (titleTv != null) {
            titleTv.setTextColor(accent);
        }
        android.widget.ImageView titleIcon = findViewById(R.id.taskTitleIcon);
        if (titleIcon != null) {
            titleIcon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        android.widget.ImageButton addBtn = findViewById(R.id.addTaskButton);
        if (addBtn != null) {
            addBtn.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        android.widget.ImageButton voiceBtn = findViewById(R.id.voiceTaskButton);
        if (voiceBtn != null) {
            voiceBtn.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        BottomNavigationHelper.setupBottomNavigation(this, R.id.navTaskButton);
    }

    private void showTaskMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "Share").setIcon(R.drawable.ic_menu_share_color);
        popup.getMenu().add(0, 2, 0, "Clear Completed").setIcon(R.drawable.ic_menu_trash_color);

        try {
            java.lang.reflect.Field field = popup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuHelper = field.get(popup);
            java.lang.reflect.Method setForceIcons = menuHelper.getClass().getDeclaredMethod("setForceShowIcon", boolean.class);
            setForceIcons.invoke(menuHelper, true);
        } catch (Exception ignored) {}

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) shareTasks();
            else if (item.getItemId() == 2) showClearCompletedConfirm();
            return true;
        });
        popup.show();
    }

    private void shareTasks() {
        if (taskList.isEmpty()) {
            Toast.makeText(this, "No tasks to share", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("SAR Tasks List:\n");
        for (int i = 0; i < taskList.size(); i++) {
            TaskItem item = taskList.get(i);
            sb.append(i + 1).append(". ").append(item.completed ? "[Done] " : "").append(item.text).append("\n");
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Tasks via"));
    }

    private void toggleSelection(int position) {
        if (selectedIndices.contains(position)) {
            selectedIndices.remove((Integer) position);
        } else {
            selectedIndices.add(position);
        }
        if (selectedIndices.isEmpty()) {
            exitSelectionMode();
        } else {
            if (selectionCountText != null) {
                selectionCountText.setText(selectedIndices.size() + " selected");
            }
            if (adapter != null) {
                adapter.notifyItemChanged(position);
            }
        }
    }

    private void exitSelectionMode() {
        isSelectionMode = false;
        selectedIndices.clear();
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void shareSelectedTasks() {
        if (selectedIndices.isEmpty()) return;
        StringBuilder sb = new StringBuilder("SAR Tasks List:\n");
        int count = 1;
        List<Integer> sorted = new ArrayList<>(selectedIndices);
        Collections.sort(sorted);
        for (int index : sorted) {
            if (index >= 0 && index < taskList.size()) {
                TaskItem item = taskList.get(index);
                sb.append(count++).append(". ").append(item.completed ? "[Done] " : "").append(item.text).append("\n");
            }
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, "Share Tasks via"));
        exitSelectionMode();
    }

    private void deleteSelectedTasks() {
        if (selectedIndices.isEmpty()) return;
        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Delete Selected Tasks")
                .setMessage("Are you sure you want to delete the selected tasks?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    List<Integer> sorted = new ArrayList<>(selectedIndices);
                    Collections.sort(sorted, Collections.reverseOrder());
                    for (int index : sorted) {
                        if (index >= 0 && index < taskList.size()) {
                            taskList.remove(index);
                        }
                    }
                    saveTasks();
                    exitSelectionMode();
                    Toast.makeText(this, "Selected tasks deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null), this);
    }

    private void showTaskBottomSheet(int position) {
        if (position < 0 || position >= taskList.size()) return;
        TaskItem item = taskList.get(position);

        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_task_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        view.findViewById(R.id.bsSelectTask).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            isSelectionMode = true;
            selectedIndices.clear();
            selectedIndices.add(position);
            if (selectionBar != null) {
                selectionBar.setVisibility(View.VISIBLE);
            }
            if (selectionCountText != null) {
                selectionCountText.setText("1 selected");
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        });

        view.findViewById(R.id.bsEditTask).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showEditTaskDialog(position);
        });

        view.findViewById(R.id.bsShareTask).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            StringBuilder sb = new StringBuilder("SAR Task:\n");
            sb.append(item.completed ? "[Done] " : "").append(item.text);
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share Task via"));
        });

        view.findViewById(R.id.bsDeleteTask).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                    .setTitle("Delete Task")
                    .setMessage("Are you sure you want to delete this task?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        taskList.remove(position);
                        saveTasks();
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null), this);
        });

        bottomSheetDialog.show();
    }

    private void showEditTaskDialog(int position) {
        if (position < 0 || position >= taskList.size()) return;
        TaskItem item = taskList.get(position);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle("Edit Task");

        final EditText input = new EditText(this);
        input.setText(item.text);
        input.setTextColor(android.graphics.Color.WHITE);
        
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = padding;
        params.rightMargin = padding;
        params.topMargin = padding;
        params.bottomMargin = padding;
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        // Position cursor at end of text
        input.setSelection(input.getText().length());

        builder.setPositiveButton("Save", (dialog, which) -> {
            String updatedText = input.getText().toString().trim();
            if (!TextUtils.isEmpty(updatedText)) {
                item.text = updatedText;
                saveTasks();
                if (adapter != null) {
                    adapter.notifyItemChanged(position);
                }
                Toast.makeText(this, "Task updated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Task text cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, this);
    }

    private void showClearCompletedConfirm() {
        boolean hasCompleted = false;
        for (TaskItem item : taskList) {
            if (item.completed) {
                hasCompleted = true;
                break;
            }
        }
        if (!hasCompleted) {
            Toast.makeText(this, "No completed tasks to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        ThemeManager.showDialog(new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Clear Completed")
                .setMessage("Delete all tasks that are checked?")
                .setPositiveButton("Clear", (dialog, which) -> {
                    List<TaskItem> remaining = new ArrayList<>();
                    for (TaskItem item : taskList) {
                        if (!item.completed) remaining.add(item);
                    }
                    taskList.clear();
                    taskList.addAll(remaining);
                    saveTasks();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Completed tasks cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null), this);
    }

    private void setupDragAndDrop() {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    Collections.swap(taskList, fromPosition, toPosition);
                    adapter.notifyItemMoved(fromPosition, toPosition);
                    saveTasks();
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
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
        
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your task...");
        try {
            voiceRecognitionLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }


    private void addTask() {
        String text = taskInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Please enter a task", Toast.LENGTH_SHORT).show();
            return;
        }

        TaskItem newItem = new TaskItem(text, false);
        taskList.add(newItem);
        saveTasks();
        adapter.notifyItemInserted(taskList.size() - 1);
        taskInput.setText("");
    }

    private void loadTasks() {
        taskList.clear();
        String json = sharedPreferences.getString(KEY_TASKS, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    taskList.add(new TaskItem(
                            obj.getString("text"),
                            obj.getBoolean("completed")
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveTasks() {
        try {
            JSONArray array = new JSONArray();
            for (TaskItem item : taskList) {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("completed", item.completed);
                array.put(obj);
            }
            sharedPreferences.edit().putString(KEY_TASKS, array.toString()).apply();
            WidgetUtils.updateAllWidgets(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class TaskItem {
        String text;
        boolean completed;

        TaskItem(String text, boolean completed) {
            this.text = text;
            this.completed = completed;
        }
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
        private List<TaskItem> items;

        TaskAdapter(List<TaskItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TaskItem item = items.get(position);
            holder.taskText.setText(item.text);
            holder.checkBox.setOnCheckedChangeListener(null); // Clear listener before setting checked state
            holder.checkBox.setChecked(item.completed);
            
            int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
            holder.checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));

            updateStrikethrough(holder.taskText, item.completed);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.completed = isChecked;
                updateStrikethrough(holder.taskText, isChecked);
                saveTasks();
            });

            holder.checkBox.setClickable(!isSelectionMode);
            holder.checkBox.setFocusable(!isSelectionMode);

            if (isSelectionMode) {
                holder.itemView.setBackgroundColor(selectedIndices.contains(position) ? android.graphics.Color.parseColor("#338BC34A") : android.graphics.Color.TRANSPARENT);
            } else {
                holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            }

            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(holder.getAdapterPosition());
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (!isSelectionMode) {
                    showTaskBottomSheet(holder.getAdapterPosition());
                }
                return true;
            });
        }

        private void updateStrikethrough(TextView textView, boolean completed) {
            if (completed) {
                textView.setPaintFlags(textView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                textView.setTextColor(0xFFAAAAAA); // Muted color for completed tasks
            } else {
                textView.setPaintFlags(textView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                textView.setTextColor(0xFFFFFFFF); // White for active tasks
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView taskText;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.taskCheckBox);
                taskText = itemView.findViewById(R.id.taskText);
            }
        }
    }
}
