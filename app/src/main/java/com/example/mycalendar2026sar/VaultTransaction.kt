package com.example.mycalendar2026sar

/**
 * Represents a deposit (ADD) or withdrawal (WITHDRAW) transaction for a Savings Vault.
 */
data class VaultTransaction(
    val id: Long = 0,
    val vaultId: Long,
    val amount: Double,
    val currency: String,
    val type: Type,
    val date: Long = System.currentTimeMillis(),
    val note: String? = null
) {
    enum class Type {
        ADD,
        WITHDRAW
    }
}
