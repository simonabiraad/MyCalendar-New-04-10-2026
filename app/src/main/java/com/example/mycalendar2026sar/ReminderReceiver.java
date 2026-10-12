package com.example.mycalendar2026sar;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SARCalendar:ReminderWakeLock");
            try {
                wakeLock.acquire(5000);
            } catch (Exception ignored) {}
        }

        try {
            long eventId = intent.getLongExtra("eventId", -1);
            String noteTextExtra = intent.getStringExtra("noteText");

            TransactionDbHelper dbHelper = TransactionDbHelper.getInstance(context);
            NotificationEvent event = null;
            if (eventId != -1) {
                event = dbHelper.getNotificationById(eventId);
            }

            if (event != null && ("Completed".equalsIgnoreCase(event.getStatus()) || "Cancelled".equalsIgnoreCase(event.getStatus()))) {
                return;
            }

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "calendar_reminder_channel";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        "Calendar Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Reminders for calendar events and notes");
                channel.enableVibration(true);
                channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build();
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);

                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            }

            Intent notifIntent = new Intent(context, NotificationDetailsActivity.class);
            if (eventId != -1) {
                notifIntent.putExtra("eventId", eventId);
                notifIntent.putExtra("mode", "view");
            }
            notifIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    (int) (eventId != -1 ? eventId : System.currentTimeMillis()),
                    notifIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            String title = (event != null && event.getTitle() != null && !event.getTitle().trim().isEmpty())
                    ? event.getTitle()
                    : (noteTextExtra != null && !noteTextExtra.trim().isEmpty() ? noteTextExtra : "SAR Calendar Reminder");

            StringBuilder details = new StringBuilder();
            if (event != null) {
                if (event.getDate() != null && !event.getDate().trim().isEmpty()) {
                    details.append("Date: ").append(event.getDate());
                }
                if (event.getStartTime() != null && !event.getStartTime().trim().isEmpty()) {
                    if (details.length() > 0) details.append(" | ");
                    details.append("Time: ").append(event.getStartTime());
                    if (event.getEndTime() != null && !event.getEndTime().trim().isEmpty()) {
                        details.append(" - ").append(event.getEndTime());
                    }
                }
                if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
                    if (details.length() > 0) details.append("\n");
                    details.append("Location: ").append(event.getLocation());
                }
                if (event.getNotes() != null && !event.getNotes().trim().isEmpty()) {
                    if (details.length() > 0) details.append("\n");
                    details.append(event.getNotes());
                }
            } else if (noteTextExtra != null && !noteTextExtra.trim().isEmpty()) {
                details.append(noteTextExtra);
            }

            if (details.length() == 0) {
                details.append("You have an upcoming event scheduled.");
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setColor(Color.GREEN)
                    .setContentTitle(title)
                    .setContentText(details.toString())
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(details.toString()))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            if (notificationManager != null) {
                int notifId = eventId != -1 ? (int) eventId : (int) System.currentTimeMillis();
                notificationManager.notify(notifId, builder.build());
            }

            if (event != null) {
                NotificationUtils.scheduleNextOccurrence(context, event);
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) {}
            }
        }
    }
}
