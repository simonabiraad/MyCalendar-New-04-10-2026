package com.example.mycalendar2026sar

/**
 * Represents an individual payment installment in a Planned Payment schedule.
 */
data class PaymentScheduleItem(
    val id: Long = 0,
    val plannedPaymentId: Long,
    val dueDate: String, // Format: "dd/MM/yyyy"
    val amount: Double,
    val status: Status = Status.UPCOMING,
    val paidTimestamp: Long = 0,
    val cashOutTxId: Long = -1
) {
    enum class Status {
        UPCOMING,
        PAID,
        OVERDUE
    }
}
