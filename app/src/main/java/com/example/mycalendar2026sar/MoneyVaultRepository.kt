package com.example.mycalendar2026sar

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

class MoneyVaultRepository(private val context: Context) {

    private val dbHelper = TransactionDbHelper.getInstance(context)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // ==========================================
    // SAVINGS VAULTS
    // ==========================================

    fun getAllVaults(): List<SavingsVault> {
        val list = mutableListOf<SavingsVault>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_SAVINGS_VAULTS,
            null, null, null, null, null,
            "${TransactionDbHelper.COL_VAULT_CREATED_AT} DESC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_ID)
            val nameIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_NAME)
            val currIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CURRENCY)
            val targetIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_TARGET_AMOUNT)
            val currentIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CURRENT_AMOUNT)
            val createdIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CREATED_AT)

            while (c.moveToNext()) {
                val targetVal = if (c.isNull(targetIdx)) null else c.getDouble(targetIdx)
                list.add(
                    SavingsVault(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx),
                        currency = c.getString(currIdx),
                        targetAmount = targetVal,
                        currentAmount = c.getDouble(currentIdx),
                        createdAt = c.getLong(createdIdx)
                    )
                )
            }
        }
        return list
    }

    fun getVaultById(vaultId: Long): SavingsVault? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_SAVINGS_VAULTS,
            null,
            "${TransactionDbHelper.COL_VAULT_ID}=?",
            arrayOf(vaultId.toString()),
            null, null, null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val targetVal = if (c.isNull(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_TARGET_AMOUNT))) null else c.getDouble(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_TARGET_AMOUNT))
                return SavingsVault(
                    id = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_ID)),
                    name = c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_NAME)),
                    currency = c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CURRENCY)),
                    targetAmount = targetVal,
                    currentAmount = c.getDouble(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CURRENT_AMOUNT)),
                    createdAt = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_VAULT_CREATED_AT))
                )
            }
        }
        return null
    }

    fun createVault(name: String, currency: String, targetAmount: Double?): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(TransactionDbHelper.COL_VAULT_NAME, name)
            put(TransactionDbHelper.COL_VAULT_CURRENCY, currency)
            if (targetAmount != null && targetAmount > 0) {
                put(TransactionDbHelper.COL_VAULT_TARGET_AMOUNT, targetAmount)
            } else {
                putNull(TransactionDbHelper.COL_VAULT_TARGET_AMOUNT)
            }
            put(TransactionDbHelper.COL_VAULT_CURRENT_AMOUNT, 0.0)
            put(TransactionDbHelper.COL_VAULT_CREATED_AT, System.currentTimeMillis())
        }
        return db.insert(TransactionDbHelper.TABLE_SAVINGS_VAULTS, null, values)
    }

    fun deleteVault(vaultId: Long) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TransactionDbHelper.TABLE_VAULT_TRANSACTIONS, "${TransactionDbHelper.COL_VTX_VAULT_ID}=?", arrayOf(vaultId.toString()))
            db.delete(TransactionDbHelper.TABLE_SAVINGS_VAULTS, "${TransactionDbHelper.COL_VAULT_ID}=?", arrayOf(vaultId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addVaultTransaction(vaultId: Long, amount: Double, currency: String, type: VaultTransaction.Type, note: String?): Long {
        if (amount <= 0.0) return -1
        val db = dbHelper.writableDatabase
        var txId: Long = -1
        db.beginTransaction()
        try {
            val vault = getVaultById(vaultId) ?: return -1
            val newCurrentAmount = if (type == VaultTransaction.Type.ADD) {
                vault.currentAmount + amount
            } else {
                vault.currentAmount - amount
            }

            // Insert Vault Transaction
            val values = ContentValues().apply {
                put(TransactionDbHelper.COL_VTX_VAULT_ID, vaultId)
                put(TransactionDbHelper.COL_VTX_AMOUNT, amount)
                put(TransactionDbHelper.COL_VTX_CURRENCY, currency)
                put(TransactionDbHelper.COL_VTX_TYPE, type.name)
                put(TransactionDbHelper.COL_VTX_DATE, System.currentTimeMillis())
                put(TransactionDbHelper.COL_VTX_NOTE, note ?: "")
            }
            txId = db.insert(TransactionDbHelper.TABLE_VAULT_TRANSACTIONS, null, values)

            // Update Savings Vault Current Amount
            val updateVaultValues = ContentValues().apply {
                put(TransactionDbHelper.COL_VAULT_CURRENT_AMOUNT, newCurrentAmount)
            }
            db.update(
                TransactionDbHelper.TABLE_SAVINGS_VAULTS,
                updateVaultValues,
                "${TransactionDbHelper.COL_VAULT_ID}=?",
                arrayOf(vaultId.toString())
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return txId
    }

    fun getVaultTransactions(vaultId: Long): List<VaultTransaction> {
        val list = mutableListOf<VaultTransaction>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_VAULT_TRANSACTIONS,
            null,
            "${TransactionDbHelper.COL_VTX_VAULT_ID}=?",
            arrayOf(vaultId.toString()),
            null, null,
            "${TransactionDbHelper.COL_VTX_DATE} DESC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_ID)
            val amountIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_AMOUNT)
            val currIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_CURRENCY)
            val typeIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_TYPE)
            val dateIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_DATE)
            val noteIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_VTX_NOTE)

            while (c.moveToNext()) {
                val typeStr = c.getString(typeIdx)
                val typeEnum = runCatching { VaultTransaction.Type.valueOf(typeStr) }.getOrDefault(VaultTransaction.Type.ADD)
                list.add(
                    VaultTransaction(
                        id = c.getLong(idIdx),
                        vaultId = vaultId,
                        amount = c.getDouble(amountIdx),
                        currency = c.getString(currIdx),
                        type = typeEnum,
                        date = c.getLong(dateIdx),
                        note = c.getString(noteIdx)
                    )
                )
            }
        }
        return list
    }

    // ==========================================
    // PLANNED PAYMENTS & SCHEDULES
    // ==========================================

    fun getAllPlannedPayments(): List<PlannedPayment> {
        val list = mutableListOf<PlannedPayment>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_PLANNED_PAYMENTS,
            null, null, null, null, null,
            "${TransactionDbHelper.COL_PP_ID} DESC"
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                list.add(readPlannedPayment(c))
            }
        }
        return list
    }

    fun getPlannedPaymentById(paymentId: Long): PlannedPayment? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_PLANNED_PAYMENTS,
            null,
            "${TransactionDbHelper.COL_PP_ID}=?",
            arrayOf(paymentId.toString()),
            null, null, null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                return readPlannedPayment(c)
            }
        }
        return null
    }

    private fun readPlannedPayment(c: Cursor): PlannedPayment {
        val idIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_ID)
        val nameIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_NAME)
        val totalIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_TOTAL_AMOUNT)
        val currIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_CURRENCY)
        val startIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_START_DATE)
        val endIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_END_DATE)
        val freqIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_FREQUENCY)
        val dayIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_PAYMENT_DAY)
        val remIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_REMINDER_DAYS_BEFORE)
        val descIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PP_DESCRIPTION)

        return PlannedPayment(
            id = c.getLong(idIdx),
            name = c.getString(nameIdx),
            totalAmount = c.getDouble(totalIdx),
            currency = c.getString(currIdx),
            startDate = c.getString(startIdx),
            endDate = c.getString(endIdx),
            frequency = c.getString(freqIdx),
            paymentDay = c.getInt(dayIdx),
            reminderDaysBefore = c.getInt(remIdx),
            description = if (c.isNull(descIdx)) null else c.getString(descIdx)
        )
    }

    fun createPlannedPayment(payment: PlannedPayment): Long {
        val db = dbHelper.writableDatabase
        var plannedPaymentId: Long = -1
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(TransactionDbHelper.COL_PP_NAME, payment.name)
                put(TransactionDbHelper.COL_PP_TOTAL_AMOUNT, payment.totalAmount)
                put(TransactionDbHelper.COL_PP_CURRENCY, payment.currency)
                put(TransactionDbHelper.COL_PP_START_DATE, payment.startDate)
                put(TransactionDbHelper.COL_PP_END_DATE, payment.endDate)
                put(TransactionDbHelper.COL_PP_FREQUENCY, payment.frequency)
                put(TransactionDbHelper.COL_PP_PAYMENT_DAY, payment.paymentDay)
                put(TransactionDbHelper.COL_PP_REMINDER_DAYS_BEFORE, payment.reminderDaysBefore)
                put(TransactionDbHelper.COL_PP_DESCRIPTION, payment.description ?: "")
            }
            plannedPaymentId = db.insert(TransactionDbHelper.TABLE_PLANNED_PAYMENTS, null, values)

            if (plannedPaymentId > 0) {
                val fullPayment = payment.copy(id = plannedPaymentId)
                val schedule = generateScheduleItems(fullPayment)
                for (item in schedule) {
                    val itemValues = ContentValues().apply {
                        put(TransactionDbHelper.COL_PSI_PLANNED_PAYMENT_ID, plannedPaymentId)
                        put(TransactionDbHelper.COL_PSI_DUE_DATE, item.dueDate)
                        put(TransactionDbHelper.COL_PSI_AMOUNT, item.amount)
                        put(TransactionDbHelper.COL_PSI_STATUS, item.status.name)
                        put(TransactionDbHelper.COL_PSI_PAID_TIMESTAMP, item.paidTimestamp)
                        put(TransactionDbHelper.COL_PSI_CASH_OUT_TX_ID, item.cashOutTxId)
                    }
                    db.insert(TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS, null, itemValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return plannedPaymentId
    }

    fun deletePlannedPayment(plannedPaymentId: Long) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
                "${TransactionDbHelper.COL_PSI_PLANNED_PAYMENT_ID}=?",
                arrayOf(plannedPaymentId.toString())
            )
            db.delete(
                TransactionDbHelper.TABLE_PLANNED_PAYMENTS,
                "${TransactionDbHelper.COL_PP_ID}=?",
                arrayOf(plannedPaymentId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getScheduleItemsForPayment(plannedPaymentId: Long): List<PaymentScheduleItem> {
        val list = mutableListOf<PaymentScheduleItem>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
            null,
            "${TransactionDbHelper.COL_PSI_PLANNED_PAYMENT_ID}=?",
            arrayOf(plannedPaymentId.toString()),
            null, null,
            "${TransactionDbHelper.COL_PSI_ID} ASC"
        )

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayTime = todayCal.timeInMillis

        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_ID)
            val dueIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_DUE_DATE)
            val amtIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_AMOUNT)
            val statusIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_STATUS)
            val paidIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_PAID_TIMESTAMP)
            val txIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_CASH_OUT_TX_ID)

            while (c.moveToNext()) {
                val rawStatusStr = c.getString(statusIdx)
                val paidTs = c.getLong(paidIdx)
                val dueStr = c.getString(dueIdx)

                var statusEnum = runCatching { PaymentScheduleItem.Status.valueOf(rawStatusStr) }
                    .getOrDefault(PaymentScheduleItem.Status.UPCOMING)

                if (paidTs > 0 || statusEnum == PaymentScheduleItem.Status.PAID) {
                    statusEnum = PaymentScheduleItem.Status.PAID
                } else {
                    // Check if overdue
                    val dueParsed = runCatching { dateFormat.parse(dueStr) }.getOrNull()
                    if (dueParsed != null && dueParsed.time < todayTime) {
                        statusEnum = PaymentScheduleItem.Status.OVERDUE
                    } else {
                        statusEnum = PaymentScheduleItem.Status.UPCOMING
                    }
                }

                list.add(
                    PaymentScheduleItem(
                        id = c.getLong(idIdx),
                        plannedPaymentId = plannedPaymentId,
                        dueDate = dueStr,
                        amount = c.getDouble(amtIdx),
                        status = statusEnum,
                        paidTimestamp = paidTs,
                        cashOutTxId = c.getLong(txIdx)
                    )
                )
            }
        }

        // Cleanup: If a monthly payment schedule has > 12 items (from legacy creation),
        // trim any extra unpaid 13th item from DB and adjust amounts for 12 items.
        val payment = getPlannedPaymentById(plannedPaymentId)
        if (payment != null && (payment.frequency.equals("Monthly", ignoreCase = true) || payment.frequency.isEmpty()) && list.size > 12) {
            val writeDb = dbHelper.writableDatabase
            while (list.size > 12) {
                val lastItem = list.last()
                if (lastItem.status != PaymentScheduleItem.Status.PAID) {
                    writeDb.delete(
                        TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
                        "${TransactionDbHelper.COL_PSI_ID}=?",
                        arrayOf(lastItem.id.toString())
                    )
                    list.removeAt(list.size - 1)
                } else {
                    break
                }
            }
            // Recalculate amount per installment for remaining items
            val n = list.size
            if (n > 0) {
                val rawBase = Math.floor((payment.totalAmount / n) * 100.0) / 100.0
                val baseAmount = Math.round(rawBase * 100.0) / 100.0
                var sumFirstNMinus1 = 0.0

                for (i in 0 until n) {
                    val item = list[i]
                    val correctAmt = if (i == n - 1) {
                        Math.round((payment.totalAmount - sumFirstNMinus1) * 100.0) / 100.0
                    } else {
                        sumFirstNMinus1 += baseAmount
                        baseAmount
                    }
                    if (item.amount != correctAmt) {
                        val values = ContentValues().apply {
                            put(TransactionDbHelper.COL_PSI_AMOUNT, correctAmt)
                        }
                        writeDb.update(
                            TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
                            values,
                            "${TransactionDbHelper.COL_PSI_ID}=?",
                            arrayOf(item.id.toString())
                        )
                        list[i] = item.copy(amount = correctAmt)
                    }
                }
            }
        }

        return list
    }

    /**
     * Marks a schedule item as paid:
     * 1. Updates schedule item status to PAID and sets paid timestamp.
     * 2. Inserts ONE Cash Out transaction into existing Expenses system via TransactionDbHelper.addTransaction.
     * 3. Updates account balance via BalanceManager.updateAccountBalance.
     * 4. Prevents double-counting if already paid.
     */
    fun markScheduleItemAsPaid(scheduleItemId: Long, accountName: String): Boolean {
        val db = dbHelper.writableDatabase
        var success = false
        db.beginTransaction()
        try {
            // Read schedule item
            val cursor = db.query(
                TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
                null,
                "${TransactionDbHelper.COL_PSI_ID}=?",
                arrayOf(scheduleItemId.toString()),
                null, null, null
            )

            var scheduleItem: PaymentScheduleItem? = null
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val statusStr = c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_STATUS))
                    val paidTs = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_PAID_TIMESTAMP))
                    if (paidTs > 0 || statusStr == PaymentScheduleItem.Status.PAID.name) {
                        // Already paid, return false to prevent double-counting
                        db.endTransaction()
                        return false
                    }
                    scheduleItem = PaymentScheduleItem(
                        id = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_ID)),
                        plannedPaymentId = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_PLANNED_PAYMENT_ID)),
                        dueDate = c.getString(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_DUE_DATE)),
                        amount = c.getDouble(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_AMOUNT)),
                        status = PaymentScheduleItem.Status.UPCOMING,
                        paidTimestamp = paidTs,
                        cashOutTxId = c.getLong(c.getColumnIndexOrThrow(TransactionDbHelper.COL_PSI_CASH_OUT_TX_ID))
                    )
                }
            }

            val item = scheduleItem ?: return false
            val plannedPayment = getPlannedPaymentById(item.plannedPaymentId) ?: return false

            val now = System.currentTimeMillis()
            val txTitle = "${plannedPayment.name} (${item.dueDate})"
            val note = "Planned Payment: ${plannedPayment.name}"

            // Create Cash Out transaction
            val cashOutTxId = dbHelper.addTransaction(
                txTitle,
                item.amount,
                plannedPayment.currency,
                Transaction.TYPE_CASH_OUT,
                now,
                accountName,
                note,
                "",
                ""
            )

            // Update schedule item
            val updateValues = ContentValues().apply {
                put(TransactionDbHelper.COL_PSI_STATUS, PaymentScheduleItem.Status.PAID.name)
                put(TransactionDbHelper.COL_PSI_PAID_TIMESTAMP, now)
                put(TransactionDbHelper.COL_PSI_CASH_OUT_TX_ID, cashOutTxId)
            }
            db.update(
                TransactionDbHelper.TABLE_PAYMENT_SCHEDULE_ITEMS,
                updateValues,
                "${TransactionDbHelper.COL_PSI_ID}=?",
                arrayOf(scheduleItemId.toString())
            )

            // Update account balance
            BalanceManager.updateAccountBalance(context, accountName, -item.amount)

            db.setTransactionSuccessful()
            success = true
        } finally {
            db.endTransaction()
        }
        return success
    }

    /**
     * Generates schedule dates and calculates payment amounts per period.
     * Rounding difference is automatically corrected on the final item so sum == totalAmount EXACTLY.
     */
    fun generateScheduleItems(payment: PlannedPayment): List<PaymentScheduleItem> {
        val startParsed = runCatching { dateFormat.parse(payment.startDate) }.getOrNull() ?: Date()
        val endParsed = runCatching { dateFormat.parse(payment.endDate) }.getOrNull() ?: Date()

        val startCal = Calendar.getInstance().apply { time = startParsed }
        val endCal = Calendar.getInstance().apply { time = endParsed }

        if (endCal.before(startCal)) {
            endCal.time = startCal.time
        }

        val dueDates = mutableListOf<String>()
        val currCal = startCal.clone() as Calendar

        val monthStep = when (payment.frequency) {
            "Monthly" -> 1
            "Every 3 months" -> 3
            "Every 6 months" -> 6
            "Yearly" -> 12
            else -> 1 // Custom defaults to monthly
        }

        while (!currCal.after(endCal)) {
            val scheduledCal = currCal.clone() as Calendar
            val maxDays = scheduledCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val dayToSet = payment.paymentDay.coerceIn(1, maxDays)
            scheduledCal.set(Calendar.DAY_OF_MONTH, dayToSet)

            dueDates.add(dateFormat.format(scheduledCal.time))

            if (monthStep == 1 && dueDates.size >= 12) {
                break
            }

            currCal.add(Calendar.MONTH, monthStep)
            if (monthStep >= 12 && currCal.after(endCal) && dueDates.size <= 1) {
                break
            }
        }

        if (dueDates.isEmpty()) {
            dueDates.add(payment.startDate)
        }

        val n = dueDates.size
        val totalAmount = payment.totalAmount

        // Base rounded amount for first N-1 items
        val rawBase = floor((totalAmount / n) * 100.0) / 100.0
        val baseAmount = round(rawBase * 100.0) / 100.0

        val items = mutableListOf<PaymentScheduleItem>()
        var sumFirstNMinus1 = 0.0

        for (i in 0 until n) {
            val dueDate = dueDates[i]
            val amt = if (i == n - 1) {
                // Final item corrects rounding difference
                val finalAmt = totalAmount - sumFirstNMinus1
                round(finalAmt * 100.0) / 100.0
            } else {
                sumFirstNMinus1 += baseAmount
                baseAmount
            }

            items.add(
                PaymentScheduleItem(
                    plannedPaymentId = payment.id,
                    dueDate = dueDate,
                    amount = amt,
                    status = PaymentScheduleItem.Status.UPCOMING
                )
            )
        }

        return items
    }

    // ==========================================
    // FLEXIBLE SAVE TRANSACTIONS
    // ==========================================

    fun getLatestSaveBalance(currency: String): Double {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_SAVE_TRANSACTIONS,
            arrayOf(TransactionDbHelper.COL_SAVE_BALANCE_AFTER),
            "${TransactionDbHelper.COL_SAVE_CURRENCY}=?",
            arrayOf(currency),
            null, null,
            "${TransactionDbHelper.COL_SAVE_ID} DESC",
            "1"
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                return c.getDouble(0)
            }
        }
        return 0.0
    }

    fun addSaveTransaction(amount: Double, type: SaveTransaction.Type, currency: String, note: String?): Long {
        if (amount <= 0.0) return -1
        val db = dbHelper.writableDatabase
        var txId: Long = -1
        db.beginTransaction()
        try {
            val currentBal = getLatestSaveBalance(currency)
            val newBal = if (type == SaveTransaction.Type.SAVE) {
                currentBal + amount
            } else {
                currentBal - amount
            }

            val values = ContentValues().apply {
                put(TransactionDbHelper.COL_SAVE_AMOUNT, amount)
                put(TransactionDbHelper.COL_SAVE_TYPE, type.name)
                put(TransactionDbHelper.COL_SAVE_CURRENCY, currency)
                put(TransactionDbHelper.COL_SAVE_TIMESTAMP, System.currentTimeMillis())
                put(TransactionDbHelper.COL_SAVE_BALANCE_AFTER, newBal)
                put(TransactionDbHelper.COL_SAVE_NOTE, note ?: "")
            }
            txId = db.insert(TransactionDbHelper.TABLE_SAVE_TRANSACTIONS, null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return txId
    }

    fun getAllSaveTransactions(currency: String): List<SaveTransaction> {
        val list = mutableListOf<SaveTransaction>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TransactionDbHelper.TABLE_SAVE_TRANSACTIONS,
            null,
            "${TransactionDbHelper.COL_SAVE_CURRENCY}=?",
            arrayOf(currency),
            null, null,
            "${TransactionDbHelper.COL_SAVE_ID} DESC"
        )
        cursor?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_ID)
            val amtIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_AMOUNT)
            val typeIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_TYPE)
            val currIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_CURRENCY)
            val tsIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_TIMESTAMP)
            val balIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_BALANCE_AFTER)
            val noteIdx = c.getColumnIndexOrThrow(TransactionDbHelper.COL_SAVE_NOTE)

            while (c.moveToNext()) {
                val typeStr = c.getString(typeIdx)
                val typeEnum = runCatching { SaveTransaction.Type.valueOf(typeStr) }.getOrDefault(SaveTransaction.Type.SAVE)
                list.add(
                    SaveTransaction(
                        id = c.getLong(idIdx),
                        amount = c.getDouble(amtIdx),
                        type = typeEnum,
                        currency = c.getString(currIdx),
                        timestamp = c.getLong(tsIdx),
                        balanceAfter = c.getDouble(balIdx),
                        note = if (c.isNull(noteIdx)) null else c.getString(noteIdx)
                    )
                )
            }
        }
        return list
    }
}
