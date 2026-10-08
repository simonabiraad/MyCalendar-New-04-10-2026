package com.example.mycalendar2026sar

/**
 * Represents an entry in the flexible Save system (Save + / Withdraw -).
 */
data class SaveTransaction(
    val id: Long = 0,
    val amount: Double,
    val type: Type, // SAVE or WITHDRAW
    val currency: String = "USD",
    val timestamp: Long = System.currentTimeMillis(),
    val balanceAfter: Double,
    val note: String? = null
) {
    enum class Type {
        SAVE,
        WITHDRAW
    }
}
