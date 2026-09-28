package com.example.mycalendar2026sar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import java.io.File;
import java.io.IOException;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import java.util.Date;

public class NotificationDetailsActivity extends AppCompatActivity {

    private NotificationEvent currentEvent;
    private long eventId = -1;
    private String mode = "view"; // "view", "edit", "add"
    private TransactionDbHelper dbHelper;

    private TextView topTitle, detailTitle, detailStatus, detailDate, detailTime, detailPriority, detailRepeat, detailReminder, detailLocation, detailNotes;
    private ImageButton backButton, editTopButton, moreOptionsButton, playVoiceBtn, deleteVoiceBtn;
    private Button completeAction, snoozeAction, editAction, deleteAction;
    private android.widget.LinearLayout historyContainer, voiceNoteContainer;
    private RecyclerView attachmentsRecyclerView;
    private AttachmentAdapter attachmentAdapter;
    private List<String> attachmentList = new ArrayList<>();
    
    private MediaRecorder recorder;
    private MediaPlayer player;
    private String voicePath = "";
    private boolean isRecording = false;

    private final androidx.activity.result.ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        addAttachment(uri.toString());
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_details);

        dbHelper = TransactionDbHelper.getInstance(this);
        eventId = getIntent().getLongExtra("eventId", -1);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) mode = "view";

        initViews();
        setupClickListeners();

        if ("add".equals(mode)) {
            String date = getIntent().getStringExtra("date");
            currentEvent = new NotificationEvent(-1, "", "", date, "00:00 AM", "00:00 AM", "Medium", "Pending", "None", "None", "", "Other", "None", false, "[]", "", "[]");
            setupEditUI();
        } else {
            loadEvent();
        }
    }

    private void setupEditUI() {
        setContentView(R.layout.activity_notification_edit);

        View editRoot = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(editRoot, (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (!isKeyboardVisible) {
                // Force a layout pass when keyboard is hidden to ensure everything returns to normal
                v.post(v::requestLayout);
            }
            return insets;
        });
        
        TextView editDate = findViewById(R.id.editDate);
        TextView editStartTime = findViewById(R.id.editStartTime);
        TextView editEndTime = findViewById(R.id.editEndTime);
        android.widget.EditText editTitle = findViewById(R.id.editTitle);
        android.widget.EditText editNotesField = findViewById(R.id.editNotes);
        android.widget.EditText editLocationField = findViewById(R.id.editLocation);
        TextView tvPriorityValue = findViewById(R.id.tvPriorityValue);
        android.widget.ImageView btnPriorityArrow = findViewById(R.id.btnPriorityArrow);
        TextView tvRepeatValue = findViewById(R.id.tvRepeatValue);
        android.widget.ImageView btnRepeatArrow = findViewById(R.id.btnRepeatArrow);
        TextView tvReminderValue = findViewById(R.id.tvReminderValue);
        android.widget.ImageView btnReminderArrow = findViewById(R.id.btnReminderArrow);
        TextView tvCategoryValue = findViewById(R.id.tvCategoryValue);
        android.widget.ImageView btnCategoryArrow = findViewById(R.id.btnCategoryArrow);
        TextView tvStatusValue = findViewById(R.id.tvStatusValue);
        android.widget.ImageView btnStatusArrow = findViewById(R.id.btnStatusArrow);
        TextView tvEventColorValue = findViewById(R.id.tvEventColorValue);
        android.widget.ImageView btnEventColorArrow = findViewById(R.id.btnEventColorArrow);
        View viewStatusDot = findViewById(R.id.viewStatusDot);
        View viewEventColorDot = findViewById(R.id.viewEventColorDot);

        androidx.appcompat.widget.SwitchCompat switchAllDay = findViewById(R.id.switchAllDay);
        TextView tvAllDayStatus = findViewById(R.id.tvAllDayStatus);

        View dateBox = findViewById(R.id.dateBox);
        View startTimeBox = findViewById(R.id.startTimeBox);
        View endTimeBox = findViewById(R.id.endTimeBox);
        View reminderBox = findViewById(R.id.reminderBox);
        View repeatBox = findViewById(R.id.repeatBox);
        View categoryBox = findViewById(R.id.categoryBox);
        View priorityBox = findViewById(R.id.priorityBox);
        View statusBox = findViewById(R.id.statusBox);
        View colorBox = findViewById(R.id.colorBox);
        View allDayStatusBox = findViewById(R.id.allDayStatusBox);

        Button btnAddAttachment = findViewById(R.id.btnAddAttachment);
        Button btnRecordVoice = findViewById(R.id.btnRecordVoice);
        TextView btnCancel = findViewById(R.id.btnCancelEdit);
        TextView btnSave = findViewById(R.id.btnSaveEdit);
        TextView headerTitle = findViewById(R.id.editTitleHeader);
        ImageButton btnBack = findViewById(R.id.btnBackEdit);

        headerTitle.setText(currentEvent.getId() == -1 ? "New Event" : "Edit Event");

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (currentEvent.getId() == -1) {
                    finish();
                } else {
                    initViews();
                    setupClickListeners();
                    bindData();
                }
            });
        }

        btnRecordVoice.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
                btnRecordVoice.setText("Stop Recording");
                isRecording = true;
            } else {
                stopRecording();
                btnRecordVoice.setText("Record Voice Note");
                isRecording = false;
            }
        });

        btnAddAttachment.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            filePickerLauncher.launch(intent);
        });

        btnPriorityArrow.setOnClickListener(v -> showListPopupWindow(priorityBox, R.array.priority_options, tvPriorityValue));
        priorityBox.setOnClickListener(v -> btnPriorityArrow.performClick());

        btnRepeatArrow.setOnClickListener(v -> showListPopupWindow(repeatBox, R.array.repeat_options, tvRepeatValue));
        repeatBox.setOnClickListener(v -> btnRepeatArrow.performClick());

        btnReminderArrow.setOnClickListener(v -> showListPopupWindow(reminderBox, R.array.reminder_options, tvReminderValue));
        reminderBox.setOnClickListener(v -> btnReminderArrow.performClick());

        btnCategoryArrow.setOnClickListener(v -> showListPopupWindow(categoryBox, R.array.category_options, tvCategoryValue));
        categoryBox.setOnClickListener(v -> btnCategoryArrow.performClick());

        btnStatusArrow.setOnClickListener(v -> showListPopupWindow(statusBox, R.array.status_options, tvStatusValue));
        statusBox.setOnClickListener(v -> btnStatusArrow.performClick());

        btnEventColorArrow.setOnClickListener(v -> showListPopupWindow(colorBox, R.array.event_color_options, tvEventColorValue));
        colorBox.setOnClickListener(v -> btnEventColorArrow.performClick());

        // Pre-fill
        editTitle.setText(currentEvent.getTitle());
        editNotesField.setText(currentEvent.getNotes());
        editLocationField.setText(currentEvent.getLocation());
        editDate.setText(currentEvent.getDate());
        editStartTime.setText(currentEvent.getStartTime());
        editEndTime.setText(currentEvent.getEndTime());

        // Set values
        tvPriorityValue.setText(currentEvent.getPriority());
        tvRepeatValue.setText(currentEvent.getRepeat());
        tvReminderValue.setText(currentEvent.getReminder());
        tvCategoryValue.setText(currentEvent.getCategory());
        tvStatusValue.setText(currentEvent.getStatus());
        tvEventColorValue.setText(currentEvent.getEventColor());
        updateStatusDot(viewStatusDot, currentEvent.getStatus());
        updateEventColorDot(viewEventColorDot, currentEvent.getEventColor());

        switchAllDay.setChecked(currentEvent.isAllDay());
        tvAllDayStatus.setText(currentEvent.isAllDay() ? "ON" : "OFF");

        switchAllDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tvAllDayStatus.setText(isChecked ? "ON" : "OFF");
        });
        allDayStatusBox.setOnClickListener(v -> switchAllDay.toggle());

        editDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date d = sdf.parse(editDate.getText().toString());
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) {}

            new DatePickerDialog(this, (d, y, m, day) -> {
                String date = String.format(Locale.getDefault(), "%02d/%02d/%d", day, m + 1, y);
                editDate.setText(date);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        dateBox.setOnClickListener(v -> editDate.performClick());

        editStartTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (t, h, min) -> {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
                editStartTime.setText(sdf.format(cal.getTime()).toUpperCase());
            }, 0, 0, false).show();
        });
        startTimeBox.setOnClickListener(v -> editStartTime.performClick());

        editEndTime.setOnClickListener(v -> {
            new TimePickerDialog(this, (t, h, min) -> {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, h);
                cal.set(Calendar.MINUTE, min);
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
                editEndTime.setText(sdf.format(cal.getTime()).toUpperCase());
            }, 0, 0, false).show();
        });
        endTimeBox.setOnClickListener(v -> editEndTime.performClick());

        btnSave.setOnClickListener(v -> {
            currentEvent.setTitle(editTitle.getText().toString());
            currentEvent.setNotes(editNotesField.getText().toString());
            currentEvent.setLocation(editLocationField.getText().toString());
            currentEvent.setDate(editDate.getText().toString());
            currentEvent.setStartTime(editStartTime.getText().toString());
            currentEvent.setEndTime(editEndTime.getText().toString());
            currentEvent.setPriority(tvPriorityValue.getText().toString());
            currentEvent.setRepeat(tvRepeatValue.getText().toString());
            currentEvent.setReminder(tvReminderValue.getText().toString());
            currentEvent.setCategory(tvCategoryValue.getText().toString());
            currentEvent.setStatus(tvStatusValue.getText().toString());
            currentEvent.setEventColor(tvEventColorValue.getText().toString());
            currentEvent.setAllDay(switchAllDay.isChecked());

            if (currentEvent.getId() == -1) {
                addHistoryLog("Created");
                long id = dbHelper.addNotification(currentEvent);
                currentEvent.setId(id);
                eventId = id;
            } else {
                addHistoryLog("Edited");
                dbHelper.updateNotification(currentEvent);
            }
            NotificationUtils.scheduleNotification(this, currentEvent);
            
            // Re-init main view
            initViews();
            setupClickListeners();
            bindData();
        });

        btnCancel.setOnClickListener(v -> {
            if (currentEvent.getId() == -1) {
                finish();
            } else {
                initViews();
                setupClickListeners();
                bindData();
            }
        });
    }

    private void showListPopupWindow(View anchor, int arrayRes, TextView targetTv) {
        androidx.appcompat.widget.ListPopupWindow popup = new androidx.appcompat.widget.ListPopupWindow(this);
        String[] options = getResources().getStringArray(arrayRes);
        
        android.widget.ArrayAdapter<String> adapter;
        if (targetTv.getId() == R.id.tvStatusValue || targetTv.getId() == R.id.tvEventColorValue) {
            adapter = new android.widget.ArrayAdapter<String>(this, R.layout.item_dropdown_with_dot, R.id.itemLabel, options) {
                @NonNull
                @Override
                public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    View dot = view.findViewById(R.id.itemDot);
                    String item = options[position];
                    
                    if (targetTv.getId() == R.id.tvStatusValue) {
                        updateStatusDot(dot, item);
                    } else {
                        updateEventColorDot(dot, item);
                    }
                    return view;
                }
            };
        } else {
            adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        }

        popup.setAdapter(adapter);
        popup.setAnchorView(anchor);
        popup.setWidth(anchor.getWidth());
        popup.setModal(true);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            String selection = options[position];
            if ("Custom".equalsIgnoreCase(selection)) {
                if (targetTv.getId() == R.id.tvRepeatValue) {
                    showCustomRepeatDialog(targetTv);
                } else if (targetTv.getId() == R.id.tvReminderValue) {
                    showCustomReminderDialog(targetTv);
                }
            } else {
                targetTv.setText(selection);
                if (targetTv.getId() == R.id.tvStatusValue) {
                    updateStatusDot(findViewById(R.id.viewStatusDot), selection);
                } else if (targetTv.getId() == R.id.tvEventColorValue) {
                    updateEventColorDot(findViewById(R.id.viewEventColorDot), selection);
                }
            }
            popup.dismiss();
        });
        popup.show();
    }

    private void updateStatusDot(View dot, String status) {
        if (dot == null) return;
        int color = Color.GRAY;
        if ("Pending".equalsIgnoreCase(status)) color = Color.parseColor("#FBBC05"); // Yellow
        else if ("Confirmed".equalsIgnoreCase(status)) color = Color.parseColor("#4285F4"); // Blue
        else if ("Completed".equalsIgnoreCase(status)) color = Color.parseColor("#34A853"); // Green
        else if ("Cancelled".equalsIgnoreCase(status)) color = Color.parseColor("#EA4335"); // Red
        dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void updateEventColorDot(View dot, String eventColor) {
        if (dot == null) return;
        if (eventColor == null || eventColor.isEmpty() || "Default".equalsIgnoreCase(eventColor) || "None".equalsIgnoreCase(eventColor)) {
            dot.setVisibility(View.GONE);
            return;
        }
        dot.setVisibility(View.VISIBLE);
        int color = Color.parseColor("#34A853"); // Default green
        if ("Red".equalsIgnoreCase(eventColor)) color = Color.parseColor("#EA4335");
        else if ("Blue".equalsIgnoreCase(eventColor)) color = Color.parseColor("#4285F4");
        else if ("Green".equalsIgnoreCase(eventColor)) color = Color.parseColor("#34A853");
        else if ("Yellow".equalsIgnoreCase(eventColor)) color = Color.parseColor("#FBBC05");
        else if ("Purple".equalsIgnoreCase(eventColor)) color = Color.parseColor("#8E24AA");
        dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    }

    private void showCustomRepeatDialog(TextView targetTv) {
        List<String> selectedDates = new ArrayList<>();
        String current = targetTv.getText().toString();
        if (current.startsWith("Custom: ")) {
            String[] existing = current.substring(8).split(", ");
            for (String s : existing) if (!s.isEmpty()) selectedDates.add(s);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle("Custom Repeat Dates");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView datesList = new TextView(this);
        datesList.setTextColor(Color.WHITE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedDates.size(); i++) {
            sb.append(selectedDates.get(i));
            if (i < selectedDates.size() - 1) sb.append(", ");
        }
        datesList.setText(selectedDates.isEmpty() ? "No dates selected" : sb.toString());
        layout.addView(datesList);

        Button btnAdd = new Button(this);
        btnAdd.setText("Add Date");
        btnAdd.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                String date = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year);
                if (!selectedDates.contains(date)) {
                    selectedDates.add(date);
                    java.util.Collections.sort(selectedDates, (d1, d2) -> {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            return sdf.parse(d1).compareTo(sdf.parse(d2));
                        } catch (Exception e) { return 0; }
                    });
                    StringBuilder sb2 = new StringBuilder();
                    for (int i = 0; i < selectedDates.size(); i++) {
                        sb2.append(selectedDates.get(i));
                        if (i < selectedDates.size() - 1) sb2.append(", ");
                    }
                    datesList.setText(sb2.toString());
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(btnAdd);

        builder.setView(layout);
        builder.setPositiveButton("Set", (dialog, which) -> {
            if (!selectedDates.isEmpty()) {
                StringBuilder sbFinal = new StringBuilder("Custom: ");
                for (int i = 0; i < selectedDates.size(); i++) {
                    sbFinal.append(selectedDates.get(i));
                    if (i < selectedDates.size() - 1) sbFinal.append(", ");
                }
                targetTv.setText(sbFinal.toString());
            }
        });
        builder.setNegativeButton("Cancel", null);
        ThemeManager.showDialog(builder, this);
    }

    private void showCustomReminderDialog(TextView targetTv) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            targetTv.setText(String.format(Locale.getDefault(), "Custom: %02d:%02d", hourOfDay, minute));
        }, 12, 0, false).show();
    }


    private void initViews() {
        setContentView(R.layout.activity_notification_details);
        topTitle = findViewById(R.id.topTitle);
        detailTitle = findViewById(R.id.detailTitle);
        detailStatus = findViewById(R.id.detailStatus);
        detailDate = findViewById(R.id.detailDate);
        detailTime = findViewById(R.id.detailTime);
        detailPriority = findViewById(R.id.detailPriority);
        detailRepeat = findViewById(R.id.detailRepeat);
        detailReminder = findViewById(R.id.detailReminder);
        detailLocation = findViewById(R.id.detailLocation);
        detailNotes = findViewById(R.id.detailNotes);
        historyContainer = findViewById(R.id.historyContainer);
        voiceNoteContainer = findViewById(R.id.voiceNoteContainer);
        playVoiceBtn = findViewById(R.id.playVoiceBtn);
        deleteVoiceBtn = findViewById(R.id.deleteVoiceBtn);
        attachmentsRecyclerView = findViewById(R.id.attachmentsRecyclerView);
        attachmentsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        backButton = findViewById(R.id.backButton);
        editTopButton = findViewById(R.id.editTopButton);
        moreOptionsButton = findViewById(R.id.moreOptionsButton);

        completeAction = findViewById(R.id.completeAction);
        snoozeAction = findViewById(R.id.snoozeAction);
        editAction = findViewById(R.id.editAction);
        deleteAction = findViewById(R.id.deleteAction);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        editTopButton.setOnClickListener(v -> showEditDialog());
        editAction.setOnClickListener(v -> showEditDialog());

        completeAction.setOnClickListener(v -> updateStatus("Completed"));
        snoozeAction.setOnClickListener(v -> showSnoozeOptions());
        deleteAction.setOnClickListener(v -> showDeleteConfirmation());

        moreOptionsButton.setOnClickListener(v -> showMoreMenu());
    }

    private void loadEvent() {
        currentEvent = dbHelper.getNotificationById(eventId);
        if (currentEvent != null) {
            bindData();
        } else {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void bindData() {
        topTitle.setText(currentEvent.getTitle());
        detailTitle.setText(currentEvent.getTitle());
        detailStatus.setText(currentEvent.getStatus().toUpperCase());
        detailDate.setText(currentEvent.getDate());
        detailTime.setText(currentEvent.getStartTime() + " - " + currentEvent.getEndTime());
        detailPriority.setText("Priority: " + currentEvent.getPriority());
        detailRepeat.setText(currentEvent.getRepeat());
        detailReminder.setText(currentEvent.getReminder());
        detailLocation.setText(currentEvent.getLocation().isEmpty() ? "No location" : currentEvent.getLocation());
        detailNotes.setText(currentEvent.getNotes());

        if (currentEvent.getVoiceNotePath() != null && !currentEvent.getVoiceNotePath().isEmpty()) {
            voiceNoteContainer.setVisibility(View.VISIBLE);
            playVoiceBtn.setOnClickListener(v -> playVoice(currentEvent.getVoiceNotePath()));
            deleteVoiceBtn.setOnClickListener(v -> deleteVoice());
        } else {
            voiceNoteContainer.setVisibility(View.GONE);
        }

        loadHistory();
        loadAttachments();

        // Update status color
        if ("Pending".equalsIgnoreCase(currentEvent.getStatus())) {
            detailStatus.setTextColor(Color.parseColor("#FBBC05"));
        } else if ("Confirmed".equalsIgnoreCase(currentEvent.getStatus())) {
            detailStatus.setTextColor(Color.parseColor("#4285F4"));
        } else if ("Completed".equalsIgnoreCase(currentEvent.getStatus())) {
            detailStatus.setTextColor(Color.parseColor("#34A853"));
        } else if ("Cancelled".equalsIgnoreCase(currentEvent.getStatus())) {
            detailStatus.setTextColor(Color.parseColor("#EA4335"));
        } else {
            detailStatus.setTextColor(Color.GRAY);
        }
    }

    private void loadAttachments() {
        attachmentList.clear();
        try {
            JSONArray array = new JSONArray(currentEvent.getAttachments());
            for (int i = 0; i < array.length(); i++) {
                attachmentList.add(array.getString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        attachmentAdapter = new AttachmentAdapter(attachmentList);
        attachmentsRecyclerView.setAdapter(attachmentAdapter);
    }

    private void addAttachment(String path) {
        attachmentList.add(path);
        updateAttachmentsInDb();
        loadAttachments();
        addHistoryLog("Attachment added");
    }

    private void updateAttachmentsInDb() {
        try {
            JSONArray array = new JSONArray(attachmentList);
            currentEvent.setAttachments(array.toString());
            dbHelper.updateNotification(currentEvent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        historyContainer.removeAllViews();
        try {
            JSONArray history = new JSONArray(currentEvent.getHistory());
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject log = history.getJSONObject(i);
                TextView logView = new TextView(this);
                logView.setText(log.getString("time") + ": " + log.getString("action"));
                logView.setTextColor(Color.LTGRAY);
                logView.setTextSize(14);
                logView.setPadding(0, 4, 0, 4);
                historyContainer.addView(logView);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.ViewHolder> {
        private final List<String> items;

        AttachmentAdapter(List<String> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(16, 8, 16, 8);
            tv.setBackgroundResource(R.drawable.task_input_border);
            tv.setTextColor(Color.WHITE);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String path = items.get(position);
            String name = path.substring(path.lastIndexOf("/") + 1);
            holder.textView.setText(name);
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(path));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(NotificationDetailsActivity.this, "Cannot open file", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View itemView) {
                super(itemView);
                textView = (TextView) itemView;
            }
        }
    }

    private void updateStatus(String status) {
        if (currentEvent == null) return;
        currentEvent.setStatus(status);
        addHistoryLog("Status changed to " + status);
        dbHelper.updateNotification(currentEvent);
        bindData();
        Toast.makeText(this, "Status updated to " + status, Toast.LENGTH_SHORT).show();
    }

    private void addHistoryLog(String action) {
        try {
            JSONArray history = new JSONArray(currentEvent.getHistory());
            JSONObject log = new JSONObject();
            log.put("action", action);
            log.put("time", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
            history.put(log);
            currentEvent.setHistory(history.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playVoice(String path) {
        if (player != null) {
            player.release();
        }
        player = new MediaPlayer();
        try {
            player.setDataSource(path);
            player.prepare();
            player.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteVoice() {
        currentEvent.setVoiceNotePath("");
        dbHelper.updateNotification(currentEvent);
        bindData();
        addHistoryLog("Voice note deleted");
    }

    private void showEditDialog() {
        setupEditUI();
    }

    private void startRecording() {
        voicePath = getExternalCacheDir().getAbsolutePath() + "/voice_" + System.currentTimeMillis() + ".3gp";
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(voicePath);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
            currentEvent.setVoiceNotePath(voicePath);
        }
    }

    private void showSnoozeOptions() {
        String[] options = {"10 minutes", "30 minutes", "1 hour", "3 hours", "Tomorrow"};
        new AlertDialog.Builder(this)
                .setTitle("Snooze")
                .setItems(options, (dialog, which) -> {
                    Calendar cal = Calendar.getInstance();
                    if (which == 0) cal.add(Calendar.MINUTE, 10);
                    else if (which == 1) cal.add(Calendar.MINUTE, 30);
                    else if (which == 2) cal.add(Calendar.HOUR_OF_DAY, 1);
                    else if (which == 3) cal.add(Calendar.HOUR_OF_DAY, 3);
                    else if (which == 4) cal.add(Calendar.DAY_OF_YEAR, 1);

                    currentEvent.setStatus("Snoozed");
                    addHistoryLog("Snoozed for " + options[which]);
                    dbHelper.updateNotification(currentEvent);
                    
                    // Reschedule for snooze time
                    scheduleSnooze(cal);
                    
                    bindData();
                    Toast.makeText(this, "Snoozed for " + options[which], Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void scheduleSnooze(Calendar time) {
        android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("noteText", currentEvent.getTitle());
        intent.putExtra("eventId", currentEvent.getId());
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(this, (int) currentEvent.getId(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
        }
    }

    private void showDeleteConfirmation() {
        if (!"None".equalsIgnoreCase(currentEvent.getRepeat()) && !"Does not repeat".equalsIgnoreCase(currentEvent.getRepeat())) {
            String[] options = {"Delete this occurrence", "Delete all recurring events"};
            ThemeManager.showDialog(new AlertDialog.Builder(this)
                    .setTitle("Recurring Notification")
                    .setItems(options, (dialog, which) -> {
                        NotificationUtils.cancelNotification(this, eventId);
                        dbHelper.deleteNotification(eventId);
                        finish();
                    }), this);
        } else {
            ThemeManager.showDialog(new AlertDialog.Builder(this)
                    .setTitle("Delete notification?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        NotificationUtils.cancelNotification(this, eventId);
                        dbHelper.deleteNotification(eventId);
                        finish();
                    })
                    .setNegativeButton("Cancel", null), this);
        }
    }

    private void showMoreMenu() {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, moreOptionsButton);
        popup.getMenu().add("Share");
        popup.getMenu().add("Convert to Task");
        popup.setOnMenuItemClickListener(item -> {
            if ("Share".equals(item.getTitle())) {
                shareEvent();
                return true;
            } else if ("Convert to Task".equals(item.getTitle())) {
                convertToTask();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void shareEvent() {
        if (currentEvent == null) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Event: ").append(currentEvent.getTitle()).append("\n");
        sb.append("Date: ").append(currentEvent.getDate()).append("\n");
        sb.append("Time: ").append(currentEvent.getStartTime()).append(" - ").append(currentEvent.getEndTime()).append("\n");
        if (!currentEvent.getLocation().isEmpty()) {
            sb.append("Location: ").append(currentEvent.getLocation()).append("\n");
        }
        if (!currentEvent.getNotes().isEmpty()) {
            sb.append("Notes: ").append(currentEvent.getNotes()).append("\n");
        }
        
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        sendIntent.setType("text/plain");
        
        Intent shareIntent = Intent.createChooser(sendIntent, "Share Event via");
        startActivity(shareIntent);
    }

    private void convertToTask() {
        Intent intent = new Intent(this, TaskActivity.class);
        intent.putExtra("fromNotification", true);
        intent.putExtra("title", currentEvent.getTitle());
        intent.putExtra("notes", currentEvent.getNotes());
        startActivity(intent);
        Toast.makeText(this, "Converted to Task", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
    }

    private void applyColors() {
        if (getWindow() != null) {
            getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        }
        int accent = ThemeManager.getMainAccentColor(this);
        View root = findViewById(android.R.id.content);
        if (root instanceof ViewGroup) {
            applyAccentToLabels((ViewGroup) root, accent);
        }
        View btnAddAttachment = findViewById(R.id.btnAddAttachment);
        if (btnAddAttachment != null) {
            btnAddAttachment.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        View btnRecordVoice = findViewById(R.id.btnRecordVoice);
        if (btnRecordVoice != null) {
            btnRecordVoice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        View playVoiceBtn = findViewById(R.id.playVoiceBtn);
        if (playVoiceBtn instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) playVoiceBtn).setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        androidx.appcompat.widget.SwitchCompat switchAllDay = findViewById(R.id.switchAllDay);
        if (switchAllDay != null) {
            ThemeManager.styleSwitch(switchAllDay, this);
        }
    }

    private void applyAccentToLabels(ViewGroup vg, int accent) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                if (tv.getId() == R.id.editTitleHeader || tv.getId() == R.id.topTitle
                        || tv.getId() == R.id.detailTitle || tv.getId() == R.id.btnSaveEdit) {
                    tv.setTextColor(accent);
                } else if (tv.getCurrentTextColor() == ContextCompat.getColor(this, R.color.light_green)) {
                    tv.setTextColor(accent);
                }
            } else if (child instanceof ViewGroup) {
                applyAccentToLabels((ViewGroup) child, accent);
            }
        }
    }
}
