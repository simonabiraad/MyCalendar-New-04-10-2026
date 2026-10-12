package com.example.mycalendar2026sar;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;

public class BackupManager {

    public static String createBackupJson(Context context) {
        try {
            JSONObject backup = new JSONObject();

            String[] prefsToBackup = {
                "CalendarNotes",
                "ArchivedNotes",
                "DeletedNotes",
                "SecureBoxNotes",
                "SecureBoxCategories",
                "AppColors",
                "AppFonts",
                "SecuritySettings",
                "ReminderStatus",
                "ExpensesPrefs",
                "AppSettings"
            };

            JSONObject allPrefs = new JSONObject();
            for (String prefsName : prefsToBackup) {
                SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                JSONObject prefsJson = new JSONObject();
                Map<String, ?> allEntries = prefs.getAll();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    prefsJson.put(entry.getKey(), entry.getValue());
                }
                allPrefs.put(prefsName, prefsJson);
            }

            backup.put("preferences", allPrefs);

            // Backup Expenses Database
            JSONArray transactionsJson = new JSONArray();
            List<Transaction> transactions = TransactionDbHelper.getInstance(context).getAllTransactionsAscending();
            for (Transaction t : transactions) {
                JSONObject tObj = new JSONObject();
                tObj.put("title", t.getTitle());
                tObj.put("amount", t.getAmount());
                tObj.put("currency", t.getCurrency());
                tObj.put("type", t.getType());
                tObj.put("timestamp", t.getTimestamp());
                tObj.put("account", t.getAccount());
                tObj.put("notes", t.getNotes());
                tObj.put("voice_path", t.getVoiceNotePath());
                tObj.put("bills", t.getBillAttachments());
                transactionsJson.put(tObj);
            }
            backup.put("transactions", transactionsJson);

            // Backup Notifications Database
            JSONArray notificationsJson = new JSONArray();
            List<NotificationEvent> notifications = TransactionDbHelper.getInstance(context).getAllNotificationsForBackup();
            for (NotificationEvent e : notifications) {
                JSONObject eObj = new JSONObject();
                eObj.put("title", e.getTitle());
                eObj.put("notes", e.getNotes());
                eObj.put("date", e.getDate());
                eObj.put("start_time", e.getStartTime());
                eObj.put("end_time", e.getEndTime());
                eObj.put("priority", e.getPriority());
                eObj.put("status", e.getStatus());
                eObj.put("repeat", e.getRepeat());
                eObj.put("reminder", e.getReminder());
                eObj.put("location", e.getLocation());
                eObj.put("attachments", e.getAttachments());
                eObj.put("voice_path", e.getVoiceNotePath());
                eObj.put("history", e.getHistory());
                eObj.put("category", e.getCategory());
                eObj.put("color", e.getEventColor());
                eObj.put("is_all_day", e.isAllDay());
                notificationsJson.put(eObj);
            }
            backup.put("notifications", notificationsJson);

            backup.put("backup_time", System.currentTimeMillis());
            backup.put("app_version", "1.0");

            return backup.toString(4);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean restoreBackupJson(Context context, String jsonContent) {
        try {
            JSONObject backup = new JSONObject(jsonContent);
            
            // Restore Preferences
            if (backup.has("preferences")) {
                JSONObject allPrefs = backup.getJSONObject("preferences");
                java.util.Iterator<String> keys = allPrefs.keys();

                while (keys.hasNext()) {
                    String prefsName = keys.next();
                    JSONObject prefsJson = allPrefs.getJSONObject(prefsName);
                    SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.clear();

                    java.util.Iterator<String> entryKeys = prefsJson.keys();
                    while (entryKeys.hasNext()) {
                        String key = entryKeys.next();
                        Object value = prefsJson.get(key);

                        if (value instanceof Boolean) {
                            editor.putBoolean(key, (Boolean) value);
                        } else if (value instanceof Integer) {
                            editor.putInt(key, (Integer) value);
                        } else if (value instanceof Long) {
                            editor.putLong(key, (Long) value);
                        } else if (value instanceof Float) {
                            editor.putFloat(key, (Float) value);
                        } else if (value instanceof Double) {
                            editor.putFloat(key, ((Double) value).floatValue());
                        } else if (value instanceof String) {
                            editor.putString(key, (String) value);
                        }
                    }
                    editor.apply();
                }
            }

            // Restore Transactions Database
            if (backup.has("transactions")) {
                JSONArray transactionsJson = backup.getJSONArray("transactions");
                TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(context);
                dbHelper.clearAllTransactions();
                
                for (int i = 0; i < transactionsJson.length(); i++) {
                    JSONObject tObj = transactionsJson.getJSONObject(i);
                    dbHelper.addTransaction(
                        tObj.getString("title"),
                        tObj.getDouble("amount"),
                        tObj.optString("currency", "USD"),
                        tObj.getString("type"),
                        tObj.getLong("timestamp"),
                        tObj.optString("account", ""),
                        tObj.optString("notes", ""),
                        tObj.optString("voice_path", ""),
                        tObj.optString("bills", "")
                    );
                }
            }

            // Restore Notifications Database
            if (backup.has("notifications")) {
                JSONArray notificationsJson = backup.getJSONArray("notifications");
                TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(context);
                dbHelper.clearAllNotificationsPermanently();

                for (int i = 0; i < notificationsJson.length(); i++) {
                    JSONObject eObj = notificationsJson.getJSONObject(i);
                    NotificationEvent event = new NotificationEvent(
                        -1,
                        eObj.getString("title"),
                        eObj.optString("notes", ""),
                        eObj.getString("date"),
                        eObj.optString("start_time", ""),
                        eObj.optString("end_time", ""),
                        eObj.optString("priority", "Medium"),
                        eObj.optString("status", "Pending"),
                        eObj.optString("repeat", "None"),
                        eObj.optString("reminder", "None"),
                        eObj.optString("location", ""),
                        eObj.optString("category", "Other"),
                        eObj.optString("color", "Default"),
                        eObj.optBoolean("is_all_day", false),
                        eObj.optString("attachments", "[]"),
                        eObj.optString("voice_path", ""),
                        eObj.optString("history", "[]")
                    );
                    dbHelper.addNotification(event);
                }
            }

            NotificationUtils.rescheduleAllNotifications(context);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
