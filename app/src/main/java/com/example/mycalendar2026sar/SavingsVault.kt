package com.example.mycalendar2026sar

/**
 * Represents a Savings Vault created by the user (e.g., Emergency, Vacation, Car, House, Personal, Custom).
 */
data class SavingsVault(
    val id: Long = 0,
    val name: String,
    val currency: String,
    val targetAmount: Double? = null, // Optional fixed target
    val currentAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Calculates the progress percentage if a target amount is provided.
     * Returns null if targetAmount is null or <= 0.
     */
    val progressPercentage: Int?
        get() {
            val target = targetAmount ?: return null
            if (target <= 0.0) return null
            val progress = (currentAmount / target) * 100.0
            return progress.coerceIn(0.0, 100.0).toInt()
        }
}
