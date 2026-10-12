package com.example.mycalendar2026sar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reschedules all active event notifications when the device reboots, time/timezone changes, or the app package is updated.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            Intent.ACTION_TIME_CHANGED.equals(action) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(action) ||
            Intent.ACTION_DATE_CHANGED.equals(action)) {
            
            NotificationUtils.rescheduleAllNotifications(context);
        }
    }
}
