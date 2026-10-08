package com.example.mycalendar2026sar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class MoneyVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MoneyVaultRepository(application)

    private val _vaults = MutableLiveData<List<SavingsVault>>()
    val vaults: LiveData<List<SavingsVault>> = _vaults

    private val _plannedPayments = MutableLiveData<List<PlannedPayment>>()
    val plannedPayments: LiveData<List<PlannedPayment>> = _plannedPayments

    private val _selectedVaultTransactions = MutableLiveData<Pair<Long, List<VaultTransaction>>>()
    val selectedVaultTransactions: LiveData<Pair<Long, List<VaultTransaction>>> = _selectedVaultTransactions

    private val _paymentSchedules = MutableLiveData<Map<Long, List<PaymentScheduleItem>>>()
    val paymentSchedules: LiveData<Map<Long, List<PaymentScheduleItem>>> = _paymentSchedules

    private val _saveTransactions = MutableLiveData<List<SaveTransaction>>()
    val saveTransactions: LiveData<List<SaveTransaction>> = _saveTransactions

    private val _currentSaveBalance = MutableLiveData<Double>()
    val currentSaveBalance: LiveData<Double> = _currentSaveBalance

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    fun loadData(currency: String = "USD") {
        loadSaveData(currency)
        loadVaults()
        loadPlannedPayments()
    }

    fun loadSaveData(currency: String = "USD") {
        _currentSaveBalance.value = repository.getLatestSaveBalance(currency)
        _saveTransactions.value = repository.getAllSaveTransactions(currency)
    }

    fun addSaveTransaction(amount: Double, type: SaveTransaction.Type, currency: String = "USD", note: String? = null): Boolean {
        if (amount <= 0.0) {
            _toastMessage.value = "Amount must be greater than zero"
            return false
        }
        val txId = repository.addSaveTransaction(amount, type, currency, note)
        if (txId > 0) {
            _toastMessage.value = if (type == SaveTransaction.Type.SAVE) "Money saved" else "Money withdrawn"
            loadSaveData(currency)
            return true
        } else {
            _toastMessage.value = "Failed to record transaction"
            return false
        }
    }

    fun loadVaults() {
        _vaults.value = repository.getAllVaults()
    }

    fun loadPlannedPayments() {
        val payments = repository.getAllPlannedPayments()
        _plannedPayments.value = payments
        val map = mutableMapOf<Long, List<PaymentScheduleItem>>()
        for (p in payments) {
            map[p.id] = repository.getScheduleItemsForPayment(p.id)
        }
        _paymentSchedules.value = map
    }

    fun createVault(name: String, currency: String, targetAmount: Double?): Boolean {
        if (name.isBlank()) {
            _toastMessage.value = "Vault name is required"
            return false
        }
        if (currency.isBlank()) {
            _toastMessage.value = "Currency is required"
            return false
        }
        val id = repository.createVault(name.trim(), currency.trim().uppercase(), targetAmount)
        if (id > 0) {
            _toastMessage.value = "Vault created successfully"
            loadVaults()
            return true
        } else {
            _toastMessage.value = "Failed to create vault"
            return false
        }
    }

    fun deleteVault(vaultId: Long) {
        repository.deleteVault(vaultId)
        _toastMessage.value = "Vault deleted"
        loadVaults()
    }

    fun addVaultTransaction(vaultId: Long, amount: Double, currency: String, type: VaultTransaction.Type, note: String?): Boolean {
        if (amount <= 0.0) {
            _toastMessage.value = "Amount must be greater than zero"
            return false
        }
        val txId = repository.addVaultTransaction(vaultId, amount, currency, type, note)
        if (txId > 0) {
            _toastMessage.value = if (type == VaultTransaction.Type.ADD) "Money added to vault" else "Money withdrawn from vault"
            loadVaults()
            loadVaultTransactions(vaultId)
            return true
        } else {
            _toastMessage.value = "Failed to add transaction"
            return false
        }
    }

    fun loadVaultTransactions(vaultId: Long) {
        val list = repository.getVaultTransactions(vaultId)
        _selectedVaultTransactions.value = Pair(vaultId, list)
    }

    fun createPlannedPayment(payment: PlannedPayment): Boolean {
        if (payment.name.isBlank()) {
            _toastMessage.value = "Payment name is required"
            return false
        }
        if (payment.totalAmount <= 0.0) {
            _toastMessage.value = "Total amount must be greater than zero"
            return false
        }
        if (payment.currency.isBlank()) {
            _toastMessage.value = "Currency is required"
            return false
        }

        val id = repository.createPlannedPayment(payment)
        if (id > 0) {
            _toastMessage.value = "Planned payment created"
            loadPlannedPayments()
            return true
        } else {
            _toastMessage.value = "Failed to create planned payment"
            return false
        }
    }

    fun deletePlannedPayment(paymentId: Long) {
        repository.deletePlannedPayment(paymentId)
        _toastMessage.value = "Planned payment deleted"
        loadPlannedPayments()
    }

    fun markScheduleItemAsPaid(scheduleItemId: Long, accountName: String): Boolean {
        val success = repository.markScheduleItemAsPaid(scheduleItemId, accountName)
        if (success) {
            _toastMessage.value = "Payment marked as paid and added to Cash Out"
            loadPlannedPayments()
        } else {
            _toastMessage.value = "Payment already paid or failed to process"
        }
        return success
    }
}
