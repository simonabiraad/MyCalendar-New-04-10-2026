package com.example.mycalendar2026sar;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

public class EventsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private EventAdapter adapter;
    private List<DateGroup> groupedEvents = new ArrayList<>();
    private TransactionDbHelper dbHelper;

    public static class DateGroup {
        String date;
        List<NotificationEvent> events;

        DateGroup(String date, List<NotificationEvent> events) {
            this.date = date;
            this.events = events;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events);

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navEventButton);
        
        dbHelper = TransactionDbHelper.getInstance(this);
        recyclerView = findViewById(R.id.allEventsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadAllEvents();

        findViewById(R.id.addEventHeaderButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationDetailsActivity.class);
            intent.putExtra("mode", "add");
            String selectedDate = getIntent().getStringExtra("selected_date");
            if (selectedDate == null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                selectedDate = sdf.format(new Date());
            }
            intent.putExtra("date", selectedDate); 
            startActivity(intent);
        });

        findViewById(R.id.eventBackButton).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showLeaveConfirmation();
            }
        });
    }

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Leave Page")
                .setMessage("Are you sure you want to leave this page?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    Intent intent = new Intent(EventsActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyColors();
        loadAllEvents();
    }

    private void applyColors() {
        if (getWindow() != null) {
            getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        }
        int accent = ThemeManager.getMainAccentColor(this);
        TextView titleTv = findViewById(R.id.eventsTitleText);
        if (titleTv != null) {
            titleTv.setTextColor(accent);
        }
        android.widget.ImageView titleIcon = findViewById(R.id.eventsTitleIcon);
        if (titleIcon != null) {
            titleIcon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        android.widget.ImageButton addBtn = findViewById(R.id.addEventHeaderButton);
        if (addBtn != null) {
            addBtn.setImageTintList(android.content.res.ColorStateList.valueOf(accent));
        }
        BottomNavigationHelper.setupBottomNavigation(this, R.id.navEventButton);
    }

    private void loadAllEvents() {
        groupedEvents.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        String sortOrder = getSharedPreferences("AppSettings", MODE_PRIVATE).getString("sort_order", "DESC");
        
        List<NotificationEvent> allEvents = new ArrayList<>();
        // Correct chronological sort for dd/MM/yyyy format in SQLite
        String orderBy = "substr(" + TransactionDbHelper.COL_NOTIF_DATE + ", 7, 4) || substr(" + TransactionDbHelper.COL_NOTIF_DATE + ", 4, 2) || substr(" + TransactionDbHelper.COL_NOTIF_DATE + ", 1, 2) " + sortOrder 
                + ", " + TransactionDbHelper.COL_NOTIF_START_TIME + " ASC";
        
        Cursor c = db.query(TransactionDbHelper.TABLE_NOTIFICATIONS, null, TransactionDbHelper.COL_NOTIF_DELETED + "=0", null, null, null, orderBy);
        if (c != null) {
            while (c.moveToNext()) {
                allEvents.add(readNotification(c));
            }
            c.close();
        }

        // Grouping logic
        Map<String, List<NotificationEvent>> map = new LinkedHashMap<>();
        for (NotificationEvent event : allEvents) {
            String date = event.getDate();
            map.computeIfAbsent(date, k -> new ArrayList<>()).add(event);
        }

        for (Map.Entry<String, List<NotificationEvent>> entry : map.entrySet()) {
            groupedEvents.add(new DateGroup(entry.getKey(), entry.getValue()));
        }

        adapter = new EventAdapter(groupedEvents);
        recyclerView.setAdapter(adapter);
    }

    private NotificationEvent readNotification(Cursor c) {
        return new NotificationEvent(
                c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_ID)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_TITLE)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_NOTES)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_DATE)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_START_TIME)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_END_TIME)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_PRIORITY)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_STATUS)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_REPEAT)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_REMINDER)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_LOCATION)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_CATEGORY)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_COLOR)),
                c.getInt(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_ALL_DAY)) == 1,
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_ATTACHMENTS)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_VOICE_PATH)),
                c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_NOTIF_HISTORY))
        );
    }

    private class EventAdapter extends RecyclerView.Adapter<EventAdapter.ViewHolder> {
        private List<DateGroup> items;

        EventAdapter(List<DateGroup> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DateGroup group = items.get(position);
            
            // Format Date
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = sdf.parse(group.date);
                if (date != null) {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);
                    
                    SimpleDateFormat dayPillSdf = new SimpleDateFormat("EEE", Locale.getDefault());
                    holder.dayOfWeekPill.setText(dayPillSdf.format(date));
                    
                    // Set pill color cyclically
                    int accent = ThemeManager.getMainAccentColor(holder.itemView.getContext());
                    int[] pillColors = {accent, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63};
                    if (holder.dayOfWeekPill.getBackground() != null) {
                        holder.dayOfWeekPill.getBackground().setTint(pillColors[position % pillColors.length]);
                    }

                    holder.dayNumber.setText(String.format(Locale.getDefault(), "%02d", cal.get(Calendar.DAY_OF_MONTH)));
                    
                    SimpleDateFormat monthYearSdf = new SimpleDateFormat("MMMM, yyyy", Locale.getDefault());
                    holder.monthYear.setText(monthYearSdf.format(date));
                }
            } catch (Exception e) {
                holder.dayNumber.setText("?");
                holder.monthYear.setText(group.date);
            }

            // Bind Events
            holder.eventsContainer.removeAllViews();
            
            for (int i = 0; i < group.events.size(); i++) {
                NotificationEvent event = group.events.get(i);
                View detailView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.item_event_details, holder.eventsContainer, false);
                
                TextView title = detailView.findViewById(R.id.detailTitle);
                TextView location = detailView.findViewById(R.id.detailLocation);
                TextView time = detailView.findViewById(R.id.detailTime);
                TextView statusBadge = detailView.findViewById(R.id.detailStatus);
                View eventColorIndicator = detailView.findViewById(R.id.eventColorIndicator);
                View locationLayout = detailView.findViewById(R.id.locationLayout);
                View priorityLine = detailView.findViewById(R.id.priorityLine);

                String titleText = event.getTitle();
                if (event.isAllDay()) {
                    titleText += " (All Day)";
                }
                title.setText(titleText);
                if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                    location.setText(event.getLocation());
                    locationLayout.setVisibility(View.VISIBLE);
                } else {
                    locationLayout.setVisibility(View.GONE);
                }
                time.setText(event.getStartTime());
                
                // Status Badge
                updateStatusBadge(statusBadge, event.getStatus());
                statusBadge.setOnClickListener(v -> {
                    String newStatus = "Completed".equalsIgnoreCase(event.getStatus()) ? "Pending" : "Completed";
                    event.setStatus(newStatus);
                    addHistoryLog(event, "Status changed to " + newStatus);
                    dbHelper.updateNotification(event);
                    updateStatusBadge(statusBadge, newStatus);
                });

                // Event Color indicator logic
                String eventColorStr = event.getEventColor();
                if (eventColorIndicator != null) {
                    if (eventColorStr != null && !eventColorStr.isEmpty() && !"Default".equalsIgnoreCase(eventColorStr)) {
                        int indicatorColor = Color.parseColor("#34A853"); // Default green
                        if ("Red".equalsIgnoreCase(eventColorStr)) indicatorColor = Color.parseColor("#EA4335");
                        else if ("Blue".equalsIgnoreCase(eventColorStr)) indicatorColor = Color.parseColor("#4285F4");
                        else if ("Green".equalsIgnoreCase(eventColorStr)) indicatorColor = Color.parseColor("#34A853");
                        else if ("Yellow".equalsIgnoreCase(eventColorStr)) indicatorColor = Color.parseColor("#FBBC05");
                        else if ("Purple".equalsIgnoreCase(eventColorStr)) indicatorColor = Color.parseColor("#8E24AA");
                        
                        eventColorIndicator.setBackgroundTintList(android.content.res.ColorStateList.valueOf(indicatorColor));
                        eventColorIndicator.setVisibility(View.VISIBLE);
                    } else {
                        eventColorIndicator.setVisibility(View.GONE);
                    }
                }

                // Priority Logic for line color
                int priorityColor = ThemeManager.getMainAccentColor(holder.itemView.getContext());
                if ("High".equalsIgnoreCase(event.getPriority())) priorityColor = Color.RED;
                else if ("Medium".equalsIgnoreCase(event.getPriority())) priorityColor = Color.YELLOW;

                if (priorityLine != null) {
                    priorityLine.setBackgroundColor(priorityColor);
                }

                detailView.setOnClickListener(v -> {
                    Intent intent = new Intent(EventsActivity.this, NotificationDetailsActivity.class);
                    intent.putExtra("mode", "view");
                    intent.putExtra("eventId", event.getId());
                    startActivity(intent);
                });

                holder.eventsContainer.addView(detailView);
            }
        }

        private void updateStatusBadge(TextView badge, String status) {
            badge.setText(status.toUpperCase());
            if ("Completed".equalsIgnoreCase(status)) {
                badge.setTextColor(Color.GRAY);
            } else {
                badge.setTextColor(Color.parseColor("#8BC34A"));
            }
        }

        private void addHistoryLog(NotificationEvent event, String action) {
            try {
                JSONArray history = new JSONArray(event.getHistory());
                JSONObject log = new JSONObject();
                log.put("action", action);
                log.put("time", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
                history.put(log);
                event.setHistory(history.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dayOfWeekPill, dayNumber, monthYear;
            LinearLayout eventsContainer;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                dayOfWeekPill = itemView.findViewById(R.id.dayOfWeekPill);
                dayNumber = itemView.findViewById(R.id.dayNumber);
                monthYear = itemView.findViewById(R.id.monthYear);
                eventsContainer = itemView.findViewById(R.id.eventsContainer);
            }
        }
    }
}

