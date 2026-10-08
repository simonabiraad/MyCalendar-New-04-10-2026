package com.example.mycalendar2026sar

/**
 * Represents a future financial obligation and payment frequency settings.
 */
data class PlannedPayment(
    val id: Long = 0,
    val name: String,
    val totalAmount: Double,
    val currency: String,
    val startDate: String, // Format: "dd/MM/yyyy"
    val endDate: String,   // Format: "dd/MM/yyyy"
    val frequency: String, // "Monthly", "Every 3 months", "Every 6 months", "Yearly", "Custom"
    val paymentDay: Int = 1,
    val reminderDaysBefore: Int = 0, // 7, 3, 1, or 0 (On due date)
    val description: String? = null
)
