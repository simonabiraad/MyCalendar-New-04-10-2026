package com.example.mycalendar2026sar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object VaultReminderManager {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    fun scheduleRemindersForPayment(context: Context, payment: PlannedPayment, scheduleItems: List<PaymentScheduleItem>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        for (item in scheduleItems) {
            if (item.status == PaymentScheduleItem.Status.PAID) continue

            val dueParsed = runCatching { dateFormat.parse(item.dueDate) }.getOrNull() ?: continue
            val dueCal = Calendar.getInstance().apply {
                time = dueParsed
                set(Calendar.HOUR_OF_DAY, 9) // 09:00 AM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Calculate trigger time by subtracting reminderDaysBefore
            val reminderCal = (dueCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -payment.reminderDaysBefore)
            }

            if (reminderCal.before(Calendar.getInstance())) continue

            val intent = Intent(context, VaultReminderReceiver::class.java).apply {
                putExtra("paymentName", payment.name)
                putExtra("amount", CurrencyFormatter.formatAmount(item.amount, payment.currency))
                putExtra("dueDate", item.dueDate)
                putExtra("isToday", payment.reminderDaysBefore == 0)
                putExtra("scheduleItemId", item.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderCal.timeInMillis, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminderCal.timeInMillis, pendingIntent)
            }
        }
    }

    fun cancelReminder(context: Context, scheduleItemId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, VaultReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleItemId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
