package com.example.mycalendar2026sar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

class VaultReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val paymentName = intent.getStringExtra("paymentName") ?: "Planned Payment"
        val amountStr = intent.getStringExtra("amount") ?: ""
        val dueDateStr = intent.getStringExtra("dueDate") ?: ""
        val isToday = intent.getBooleanExtra("isToday", false)
        val scheduleItemId = intent.getLongExtra("scheduleItemId", -1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "money_vault_reminders_channel"

        val channel = NotificationChannel(
            channelId,
            "Money Vault Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for Money Vault planned payments"
            enableVibration(true)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
        }
        notificationManager.createNotificationChannel(channel)

        val notifIntent = Intent(context, MoneyVaultActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("scheduleItemId", scheduleItemId)
            putExtra("initialTab", 1) // Open Planned Payments section
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (scheduleItemId.toInt() + System.currentTimeMillis().toInt()),
            notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isToday) "Payment Due Today" else "Upcoming Payment"
        val contentText = "$paymentName\n$amountStr\nDue $dueDateStr"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setColor(Color.parseColor("#4CAF50"))
            .setContentTitle(title)
            .setContentText("$paymentName - $amountStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify((scheduleItemId.toInt() xor System.currentTimeMillis().toInt()), builder.build())
    }
}
