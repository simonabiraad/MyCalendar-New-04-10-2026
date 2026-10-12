package com.example.mycalendar2026sar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationUtils {

    /**
     * Checks and prompts for notification permission (POST_NOTIFICATIONS) and exact alarm permission where required.
     */
    public static void checkAndPromptAlarmPermissions(Activity activity) {
        if (activity == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Please enable Exact Alarms for SAR Calendar so reminders trigger on time when closed", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Schedules a notification for the given NotificationEvent based on its date, start time, reminder, and repeat settings.
     */
    public static void scheduleNotification(Context context, NotificationEvent event) {
        if (context == null || event == null || event.getId() <= 0) return;

        // Do not schedule if reminder is disabled/none or if event is soft-deleted/completed/cancelled
        if (isReminderDisabled(event.getReminder())) {
            cancelNotification(context, event.getId());
            return;
        }

        if ("Completed".equalsIgnoreCase(event.getStatus()) || "Cancelled".equalsIgnoreCase(event.getStatus())) {
            cancelNotification(context, event.getId());
            return;
        }

        Calendar eventCal = getEventCalendar(event);
        if (eventCal == null) return;

        Calendar triggerCal = (Calendar) eventCal.clone();
        applyReminderOffset(triggerCal, event.getReminder());

        long now = System.currentTimeMillis();
        if (triggerCal.getTimeInMillis() < now) {
            if (isRepeating(event)) {
                // Advance repeating event to the next future occurrence
                boolean advanced = advanceToNextFutureOccurrence(context, event);
                if (!advanced) {
                    cancelNotification(context, event.getId());
                    return;
                }
                eventCal = getEventCalendar(event);
                if (eventCal == null) return;

                triggerCal = (Calendar) eventCal.clone();
                applyReminderOffset(triggerCal, event.getReminder());

                if (triggerCal.getTimeInMillis() < now) {
                    cancelNotification(context, event.getId());
                    return;
                }
            } else {
                // Past event without repeat -> cancel any stale alarm
                cancelNotification(context, event.getId());
                return;
            }
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("eventId", event.getId());
        intent.putExtra("noteText", event.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) event.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerMillis = triggerCal.getTimeInMillis();

        boolean canExact = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            canExact = alarmManager.canScheduleExactAlarms();
        }

        if (canExact) {
            Intent showIntent = new Intent(context, NotificationDetailsActivity.class);
            showIntent.putExtra("eventId", event.getId());
            showIntent.putExtra("mode", "view");
            PendingIntent showPendingIntent = PendingIntent.getActivity(
                    context,
                    (int) event.getId(),
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent);
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent);
            }
        }
    }

    /**
     * Cancels any pending notification alarm for the given event ID.
     */
    public static void cancelNotification(Context context, long eventId) {
        if (context == null || eventId <= 0) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) eventId,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    /**
     * Advances a repeating event to its next occurrence after triggering and schedules it.
     */
    public static void scheduleNextOccurrence(Context context, NotificationEvent event) {
        if (context == null || event == null || !isRepeating(event)) return;

        Calendar eventCal = getEventCalendar(event);
        if (eventCal == null) return;

        String repeat = event.getRepeat();
        if (repeat != null && repeat.startsWith("Custom: ")) {
            String datesStr = repeat.substring(8);
            String[] dates = datesStr.split(",");
            SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            long currentEventMillis = eventCal.getTimeInMillis();
            long nextMillis = Long.MAX_VALUE;
            String nextDate = null;

            for (String d : dates) {
                String trimmed = d.trim();
                try {
                    Date parsed = sdfDate.parse(trimmed);
                    if (parsed != null) {
                        Calendar candidateCal = Calendar.getInstance();
                        candidateCal.setTime(parsed);
                        candidateCal.set(Calendar.HOUR_OF_DAY, eventCal.get(Calendar.HOUR_OF_DAY));
                        candidateCal.set(Calendar.MINUTE, eventCal.get(Calendar.MINUTE));
                        candidateCal.set(Calendar.SECOND, 0);

                        if (candidateCal.getTimeInMillis() > currentEventMillis && candidateCal.getTimeInMillis() < nextMillis) {
                            nextMillis = candidateCal.getTimeInMillis();
                            nextDate = trimmed;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (nextDate != null) {
                event.setDate(nextDate);
                TransactionDbHelper.getInstance(context).updateNotification(event);
                scheduleNotification(context, event);
            }
            return;
        }

        String repLower = repeat != null ? repeat.trim().toLowerCase(Locale.ROOT) : "";
        if (repLower.contains("day") || repLower.contains("daily")) {
            eventCal.add(Calendar.DAY_OF_YEAR, 1);
        } else if (repLower.contains("week") || repLower.contains("weekly")) {
            eventCal.add(Calendar.WEEK_OF_YEAR, 1);
        } else if (repLower.contains("month") || repLower.contains("monthly")) {
            eventCal.add(Calendar.MONTH, 1);
        } else if (repLower.contains("year") || repLower.contains("yearly")) {
            eventCal.add(Calendar.YEAR, 1);
        } else {
            return;
        }

        SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        event.setDate(sdfDate.format(eventCal.getTime()));

        TransactionDbHelper.getInstance(context).updateNotification(event);
        scheduleNotification(context, event);
    }

    /**
     * Reschedules all active, non-deleted, pending notifications in the app.
     */
    public static void rescheduleAllNotifications(Context context) {
        if (context == null) return;
        List<NotificationEvent> events = TransactionDbHelper.getInstance(context).getAllActiveNotifications();
        if (events != null) {
            for (NotificationEvent event : events) {
                scheduleNotification(context, event);
            }
        }
    }

    /**
     * Advances repeating event occurrence by occurrence until its reminder trigger time is in the future.
     */
    public static boolean advanceToNextFutureOccurrence(Context context, NotificationEvent event) {
        if (event == null || !isRepeating(event)) return false;

        int maxIterations = 500;
        long now = System.currentTimeMillis();

        for (int i = 0; i < maxIterations; i++) {
            Calendar eventCal = getEventCalendar(event);
            if (eventCal == null) return false;

            Calendar triggerCal = (Calendar) eventCal.clone();
            applyReminderOffset(triggerCal, event.getReminder());

            if (triggerCal.getTimeInMillis() >= now) {
                return true;
            }

            String repeat = event.getRepeat();
            if (repeat != null && repeat.startsWith("Custom: ")) {
                String datesStr = repeat.substring(8);
                String[] dates = datesStr.split(",");
                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                long nextMillis = Long.MAX_VALUE;
                String nextDate = null;

                for (String d : dates) {
                    String trimmed = d.trim();
                    try {
                        Date parsed = sdfDate.parse(trimmed);
                        if (parsed != null) {
                            Calendar candCal = Calendar.getInstance();
                            candCal.setTime(parsed);
                            candCal.set(Calendar.HOUR_OF_DAY, eventCal.get(Calendar.HOUR_OF_DAY));
                            candCal.set(Calendar.MINUTE, eventCal.get(Calendar.MINUTE));
                            candCal.set(Calendar.SECOND, 0);

                            Calendar candTrigger = (Calendar) candCal.clone();
                            applyReminderOffset(candTrigger, event.getReminder());

                            if (candTrigger.getTimeInMillis() >= now && candCal.getTimeInMillis() < nextMillis) {
                                nextMillis = candCal.getTimeInMillis();
                                nextDate = trimmed;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (nextDate != null) {
                    event.setDate(nextDate);
                    TransactionDbHelper.getInstance(context).updateNotification(event);
                } else {
                    return false;
                }
            } else {
                String repLower = repeat != null ? repeat.trim().toLowerCase(Locale.ROOT) : "";
                if (repLower.contains("day") || repLower.contains("daily")) {
                    eventCal.add(Calendar.DAY_OF_YEAR, 1);
                } else if (repLower.contains("week") || repLower.contains("weekly")) {
                    eventCal.add(Calendar.WEEK_OF_YEAR, 1);
                } else if (repLower.contains("month") || repLower.contains("monthly")) {
                    eventCal.add(Calendar.MONTH, 1);
                } else if (repLower.contains("year") || repLower.contains("yearly")) {
                    eventCal.add(Calendar.YEAR, 1);
                } else {
                    return false;
                }

                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                event.setDate(sdfDate.format(eventCal.getTime()));
                TransactionDbHelper.getInstance(context).updateNotification(event);
            }
        }
        return false;
    }

    /**
     * Parses the event date and start time into a Calendar object.
     */
    public static Calendar getEventCalendar(NotificationEvent event) {
        if (event == null || event.getDate() == null) return null;
        String dateStr = event.getDate().trim();
        String timeStr = event.getStartTime();

        if (timeStr == null || timeStr.trim().isEmpty() || event.isAllDay()) {
            timeStr = "09:00 AM";
        } else {
            timeStr = timeStr.trim();
        }

        String dateTimeStr = dateStr + " " + timeStr;
        Calendar calendar = Calendar.getInstance();
        Date parsedDate = null;

        String[] formats = new String[]{
                "dd/MM/yyyy hh:mm a",
                "dd/MM/yyyy h:mm a",
                "dd/MM/yyyy HH:mm",
                "dd/MM/yyyy H:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd hh:mm a"
        };

        for (String fmt : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.US);
                sdf.setLenient(false);
                parsedDate = sdf.parse(dateTimeStr);
                if (parsedDate != null) break;
            } catch (Exception ignored) {}
        }

        if (parsedDate == null) {
            try {
                SimpleDateFormat sdfDate = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                parsedDate = sdfDate.parse(dateStr);
                if (parsedDate != null) {
                    calendar.setTime(parsedDate);
                    calendar.set(Calendar.HOUR_OF_DAY, 9);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    return calendar;
                }
            } catch (Exception ignored) {}
            return null;
        }

        calendar.setTime(parsedDate);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    /**
     * Applies reminder offset to the event start time calendar.
     */
    private static void applyReminderOffset(Calendar calendar, String reminder) {
        if (reminder == null || calendar == null) return;
        String rem = reminder.trim();

        if (rem.startsWith("Custom: ")) {
            try {
                String timePart = rem.substring(8).trim();
                String[] parts = timePart.split(":");
                if (parts.length >= 2) {
                    int hour = Integer.parseInt(parts[0].trim());
                    int min = Integer.parseInt(parts[1].trim().split(" ")[0]);
                    if (timePart.toLowerCase(Locale.ROOT).contains("pm") && hour < 12) hour += 12;
                    if (timePart.toLowerCase(Locale.ROOT).contains("am") && hour == 12) hour = 0;
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, min);
                    calendar.set(Calendar.SECOND, 0);
                }
            } catch (Exception ignored) {}
            return;
        }

        String lower = rem.toLowerCase(Locale.ROOT);
        if (lower.contains("5 min") || lower.contains("5m")) {
            calendar.add(Calendar.MINUTE, -5);
        } else if (lower.contains("10 min") || lower.contains("10m")) {
            calendar.add(Calendar.MINUTE, -10);
        } else if (lower.contains("15 min") || lower.contains("15m")) {
            calendar.add(Calendar.MINUTE, -15);
        } else if (lower.contains("30 min") || lower.contains("30m")) {
            calendar.add(Calendar.MINUTE, -30);
        } else if (lower.contains("1 hour") || lower.contains("1h")) {
            calendar.add(Calendar.HOUR_OF_DAY, -1);
        } else if (lower.contains("1 day") || lower.contains("1d")) {
            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }
    }

    /**
     * Checks whether reminder option means disabled.
     */
    public static boolean isReminderDisabled(String reminder) {
        if (reminder == null) return true;
        String rem = reminder.trim().toLowerCase(Locale.ROOT);
        return rem.isEmpty() || "none".equals(rem) || "disabled".equals(rem) || "off".equals(rem) || "no reminder".equals(rem);
    }

    /**
     * Checks whether the event repeats.
     */
    public static boolean isRepeating(NotificationEvent event) {
        if (event == null || event.getRepeat() == null) return false;
        String r = event.getRepeat().trim().toLowerCase(Locale.ROOT);
        return !r.isEmpty() && !"none".equals(r) && !"does not repeat".equals(r) && !"off".equals(r);
    }
}
