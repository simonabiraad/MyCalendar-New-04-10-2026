package com.example.mycalendar2026sar

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MoneyVaultActivity : AppCompatActivity() {

    private val viewModel: MoneyVaultViewModel by viewModels()

    private lateinit var tabSave: Button
    private lateinit var tabSavingsVault: Button
    private lateinit var tabPlannedPayments: Button

    private lateinit var saveSection: View
    private lateinit var savingsVaultSection: View
    private lateinit var plannedPaymentsSection: View

    private lateinit var spinnerSaveCurrencySelector: Spinner
    private lateinit var txtSaveRunningTotal: TextView
    private lateinit var btnSaveAdd: Button
    private lateinit var btnSaveWithdraw: Button
    private lateinit var rvSaveHistory: RecyclerView
    private lateinit var txtEmptySaveHistory: TextView

    private lateinit var rvSavingsVaults: RecyclerView
    private lateinit var rvPlannedPayments: RecyclerView
    private lateinit var txtEmptySavings: TextView
    private lateinit var txtEmptyPlanned: TextView

    private lateinit var saveHistoryAdapter: SaveHistoryAdapter
    private lateinit var savingsAdapter: SavingsVaultAdapter
    private lateinit var plannedAdapter: PlannedPaymentAdapter

    private var activeTab = 0 // 0: Save, 1: Savings Vault, 2: Planned Payments
    private val availableCurrencies = arrayOf("USD", "EUR", "LBP", "SAR", "AED", "GBP", "CAD", "AUD", "CHF", "JPY")
    private var selectedSaveCurrency = "USD"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_money_vault)

        initViews()
        setupListeners()
        setupRecyclerViews()
        observeViewModel()

        BottomNavigationHelper.setupBottomNavigation(this, R.id.navExpensesButton)

        val initialTab = intent.getIntExtra("initialTab", 0)
        switchTab(initialTab)

        viewModel.loadData(selectedSaveCurrency)
    }

    private fun initViews() {
        tabSave = findViewById(R.id.tabSave)
        tabSavingsVault = findViewById(R.id.tabSavingsVault)
        tabPlannedPayments = findViewById(R.id.tabPlannedPayments)

        saveSection = findViewById(R.id.saveSection)
        savingsVaultSection = findViewById(R.id.savingsVaultSection)
        plannedPaymentsSection = findViewById(R.id.plannedPaymentsSection)

        spinnerSaveCurrencySelector = findViewById(R.id.spinnerSaveCurrencySelector)
        txtSaveRunningTotal = findViewById(R.id.txtSaveRunningTotal)
        btnSaveAdd = findViewById(R.id.btnSaveAdd)
        btnSaveWithdraw = findViewById(R.id.btnSaveWithdraw)
        rvSaveHistory = findViewById(R.id.rvSaveHistory)
        txtEmptySaveHistory = findViewById(R.id.txtEmptySaveHistory)

        rvSavingsVaults = findViewById(R.id.rvSavingsVaults)
        rvPlannedPayments = findViewById(R.id.rvPlannedPayments)
        txtEmptySavings = findViewById(R.id.txtEmptySavings)
        txtEmptyPlanned = findViewById(R.id.txtEmptyPlanned)

        // Setup Save Currency Spinner
        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableCurrencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSaveCurrencySelector.adapter = currencyAdapter

        spinnerSaveCurrencySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSaveCurrency = availableCurrencies[position]
                viewModel.loadSaveData(selectedSaveCurrency)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAdd).setOnClickListener {
            when (activeTab) {
                0 -> showSaveTransactionDialog(SaveTransaction.Type.SAVE)
                1 -> showCreateVaultDialog()
                2 -> showCreatePlannedPaymentDialog()
            }
        }

        tabSave.setOnClickListener { switchTab(0) }
        tabSavingsVault.setOnClickListener { switchTab(1) }
        tabPlannedPayments.setOnClickListener { switchTab(2) }

        btnSaveAdd.setOnClickListener { showSaveTransactionDialog(SaveTransaction.Type.SAVE) }
        btnSaveWithdraw.setOnClickListener { showSaveTransactionDialog(SaveTransaction.Type.WITHDRAW) }
    }

    private fun switchTab(tabIndex: Int) {
        activeTab = tabIndex
        val accentColor = ThemeManager.getMainAccentColor(this)
        val grayColor = ContextCompat.getColor(this, R.color.gray)

        tabSave.backgroundTintList = ColorStateList.valueOf(if (tabIndex == 0) accentColor else grayColor)
        tabSavingsVault.backgroundTintList = ColorStateList.valueOf(if (tabIndex == 1) accentColor else grayColor)
        tabPlannedPayments.backgroundTintList = ColorStateList.valueOf(if (tabIndex == 2) accentColor else grayColor)

        saveSection.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        savingsVaultSection.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        plannedPaymentsSection.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
    }

    private fun setupRecyclerViews() {
        rvSaveHistory.layoutManager = LinearLayoutManager(this)
        saveHistoryAdapter = SaveHistoryAdapter()
        rvSaveHistory.adapter = saveHistoryAdapter

        rvSavingsVaults.layoutManager = LinearLayoutManager(this)
        savingsAdapter = SavingsVaultAdapter()
        rvSavingsVaults.adapter = savingsAdapter

        rvPlannedPayments.layoutManager = LinearLayoutManager(this)
        plannedAdapter = PlannedPaymentAdapter()
        rvPlannedPayments.adapter = plannedAdapter
    }

    private fun observeViewModel() {
        viewModel.currentSaveBalance.observe(this) { bal ->
            txtSaveRunningTotal.text = "Saved: ${CurrencyFormatter.formatAmount(bal ?: 0.0, selectedSaveCurrency)}"
        }

        viewModel.saveTransactions.observe(this) { list ->
            saveHistoryAdapter.submitList(list, selectedSaveCurrency)
            txtEmptySaveHistory.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.vaults.observe(this) { vaults ->
            savingsAdapter.submitList(vaults)
            txtEmptySavings.visibility = if (vaults.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.plannedPayments.observe(this) { payments ->
            plannedAdapter.submitList(payments, viewModel.paymentSchedules.value ?: emptyMap())
            txtEmptyPlanned.visibility = if (payments.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.paymentSchedules.observe(this) { schedules ->
            plannedAdapter.updateSchedules(schedules)
        }

        viewModel.toastMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // DIALOG: ADD / WITHDRAW TO FLEXIBLE SAVE
    // ==========================================

    private fun showSaveTransactionDialog(type: SaveTransaction.Type) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_save_transaction, null)
        val txtTitle = view.findViewById<TextView>(R.id.txtSaveDialogTitle)
        val etAmount = view.findViewById<EditText>(R.id.etSaveAmount)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinnerSaveCurrency)
        val etNote = view.findViewById<EditText>(R.id.etSaveNote)

        val isSave = type == SaveTransaction.Type.SAVE
        txtTitle.text = if (isSave) "Safe" else "Withdraw Money"

        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableCurrencies)
        currencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrency.adapter = currencyAdapter

        val curIndex = availableCurrencies.indexOf(selectedSaveCurrency)
        if (curIndex >= 0) spinnerCurrency.setSelection(curIndex)

        ThemeManager.showDialog(
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(view)
                .setPositiveButton(if (isSave) "Save" else "Withdraw") { _, _ ->
                    val amountStr = etAmount.text.toString().trim()
                    val amount = amountStr.toDoubleOrNull()
                    val currency = spinnerCurrency.selectedItem.toString()
                    val note = etNote.text.toString().trim()

                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(this, "Amount must be greater than zero", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    viewModel.addSaveTransaction(
                        amount = amount,
                        type = type,
                        currency = currency,
                        note = if (note.isEmpty()) null else note
                    )
                }
                .setNegativeButton("Cancel", null),
            this
        )
    }

    // ==========================================
    // CREATE VAULT DIALOG
    // ==========================================

    private fun showCreateVaultDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_vault, null)
        val etVaultName = view.findViewById<EditText>(R.id.etVaultName)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinnerVaultCurrency)
        val etTarget = view.findViewById<EditText>(R.id.etVaultTarget)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableCurrencies)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrency.adapter = spinnerAdapter

        ThemeManager.showDialog(
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("🔐 Create Savings Vault")
                .setView(view)
                .setPositiveButton("Create") { _, _ ->
                    val name = etVaultName.text.toString().trim()
                    val currency = spinnerCurrency.selectedItem.toString()
                    val targetStr = etTarget.text.toString().trim()
                    val targetAmount = targetStr.toDoubleOrNull()

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Vault name is required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    viewModel.createVault(name, currency, targetAmount)
                }
                .setNegativeButton("Cancel", null),
            this
        )
    }

    // ==========================================
    // ADD / WITHDRAW MONEY DIALOG (FOR VAULTS)
    // ==========================================

    private fun showAddWithdrawDialog(vault: SavingsVault, type: VaultTransaction.Type) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_withdraw_vault, null)
        val txtTitle = view.findViewById<TextView>(R.id.txtDialogTitle)
        val etAmount = view.findViewById<EditText>(R.id.etTxAmount)
        val etNote = view.findViewById<EditText>(R.id.etTxNote)

        val actionName = if (type == VaultTransaction.Type.ADD) "Add Money" else "Withdraw Money"
        txtTitle.text = "$actionName (${vault.name})"

        ThemeManager.showDialog(
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(view)
                .setPositiveButton(if (type == VaultTransaction.Type.ADD) "Deposit" else "Withdraw") { _, _ ->
                    val amountStr = etAmount.text.toString().trim()
                    val amount = amountStr.toDoubleOrNull()
                    val note = etNote.text.toString().trim()

                    if (amount == null || amount <= 0.0) {
                        Toast.makeText(this, "Amount must be greater than zero", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    viewModel.addVaultTransaction(vault.id, amount, vault.currency, type, note)
                }
                .setNegativeButton("Cancel", null),
            this
        )
    }

    // ==========================================
    // VAULT HISTORY DIALOG
    // ==========================================

    private fun showVaultHistoryDialog(vault: SavingsVault) {
        val historyList = MoneyVaultRepository(this).getVaultTransactions(vault.id)
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle("${vault.name} History")

        if (historyList.isEmpty()) {
            builder.setMessage("No transactions recorded for this vault yet.")
        } else {
            val recyclerView = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@MoneyVaultActivity)
                adapter = VaultHistoryAdapter(historyList, vault.currency)
                setPadding(16, 16, 16, 16)
            }
            builder.setView(recyclerView)
        }

        builder.setPositiveButton("Close", null)
        ThemeManager.showDialog(builder, this)
    }

    // ==========================================
    // CREATE PLANNED PAYMENT DIALOG
    // ==========================================

    private fun showCreatePlannedPaymentDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_create_planned_payment, null)
        val etPaymentName = view.findViewById<EditText>(R.id.etPaymentName)
        val etTotalAmount = view.findViewById<EditText>(R.id.etTotalAmount)
        val spinnerCurrency = view.findViewById<Spinner>(R.id.spinnerPaymentCurrency)
        val etStartDate = view.findViewById<EditText>(R.id.etStartDate)
        val etEndDate = view.findViewById<EditText>(R.id.etEndDate)
        val spinnerFrequency = view.findViewById<Spinner>(R.id.spinnerFrequency)
        val etPaymentDay = view.findViewById<EditText>(R.id.etPaymentDay)
        val spinnerReminder = view.findViewById<Spinner>(R.id.spinnerReminder)
        val etDescription = view.findViewById<EditText>(R.id.etDescription)

        // Setup Spinners
        val currAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableCurrencies)
        currAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCurrency.adapter = currAdapter

        val frequencies = arrayOf("Monthly", "Every 3 months", "Every 6 months", "Yearly", "Custom")
        val freqAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, frequencies)
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrequency.adapter = freqAdapter

        val reminders = arrayOf("On the due date", "1 day before", "3 days before", "7 days before")
        val remAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, reminders)
        remAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReminder.adapter = remAdapter

        // Default Date values
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val cal = Calendar.getInstance()
        etStartDate.setText(sdf.format(cal.time))
        cal.add(Calendar.YEAR, 1)
        etEndDate.setText(sdf.format(cal.time))
        etPaymentDay.setText(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString())

        // Date Pickers
        etStartDate.setOnClickListener {
            showDatePicker(etStartDate)
        }
        etEndDate.setOnClickListener {
            showDatePicker(etEndDate)
        }

        ThemeManager.showDialog(
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("📅 Create Planned Payment")
                .setView(view)
                .setPositiveButton("Create") { _, _ ->
                    val name = etPaymentName.text.toString().trim()
                    val totalStr = etTotalAmount.text.toString().trim()
                    val totalAmount = totalStr.toDoubleOrNull() ?: 0.0
                    val currency = spinnerCurrency.selectedItem.toString()
                    val startDate = etStartDate.text.toString().trim()
                    val endDate = etEndDate.text.toString().trim()
                    val frequency = spinnerFrequency.selectedItem.toString()
                    val dayStr = etPaymentDay.text.toString().trim()
                    val day = dayStr.toIntOrNull() ?: 1
                    val reminderSel = spinnerReminder.selectedItem.toString()
                    val desc = etDescription.text.toString().trim()

                    val reminderDays = when (reminderSel) {
                        "7 days before" -> 7
                        "3 days before" -> 3
                        "1 day before" -> 1
                        else -> 0
                    }

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Payment name is required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (totalAmount <= 0.0) {
                        Toast.makeText(this, "Amount must be greater than zero", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val payment = PlannedPayment(
                        name = name,
                        totalAmount = totalAmount,
                        currency = currency,
                        startDate = startDate,
                        endDate = endDate,
                        frequency = frequency,
                        paymentDay = day,
                        reminderDaysBefore = reminderDays,
                        description = if (desc.isEmpty()) null else desc
                    )

                    val createdId = MoneyVaultRepository(this).createPlannedPayment(payment)
                    if (createdId > 0) {
                        val fullPayment = payment.copy(id = createdId)
                        val items = MoneyVaultRepository(this).getScheduleItemsForPayment(createdId)
                        VaultReminderManager.scheduleRemindersForPayment(this, fullPayment, items)
                        Toast.makeText(this, "Planned payment created", Toast.LENGTH_SHORT).show()
                        viewModel.loadPlannedPayments()
                    }
                }
                .setNegativeButton("Cancel", null),
            this
        )
    }

    private fun showDatePicker(editText: EditText) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val cal = Calendar.getInstance()
        val currentStr = editText.text.toString()
        runCatching {
            val parsed = sdf.parse(currentStr)
            if (parsed != null) cal.time = parsed
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                editText.setText(sdf.format(selectedCal.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ==========================================
    // ADAPTER: SAVE HISTORY
    // ==========================================

    private inner class SaveHistoryAdapter : RecyclerView.Adapter<SaveHistoryAdapter.ViewHolder>() {

        private val items = mutableListOf<SaveTransaction>()
        private var currency = "USD"
        private val dateTimeSdf = SimpleDateFormat("MMMM d, yyyy — hh:mm a", Locale.US)

        fun submitList(newItems: List<SaveTransaction>, curr: String) {
            items.clear()
            items.addAll(newItems)
            currency = curr
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_save_transaction, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSave = item.type == SaveTransaction.Type.SAVE

            holder.txtSaveDateTime.text = dateTimeSdf.format(item.timestamp)

            val formattedAmt = CurrencyFormatter.formatAmount(item.amount, currency)
            holder.txtSaveTypeAmount.text = if (isSave) "Save +$formattedAmt" else "Withdraw -$formattedAmt"
            holder.txtSaveTypeAmount.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isSave) R.color.light_green else R.color.chili_red
                )
            )

            holder.txtSaveBalanceAfter.text = "Balance: ${CurrencyFormatter.formatAmount(item.balanceAfter, currency)}"

            if (!item.note.isNullOrBlank()) {
                holder.txtSaveNote.text = item.note
                holder.txtSaveNote.visibility = View.VISIBLE
            } else {
                holder.txtSaveNote.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtSaveDateTime: TextView = v.findViewById(R.id.txtSaveDateTime)
            val txtSaveTypeAmount: TextView = v.findViewById(R.id.txtSaveTypeAmount)
            val txtSaveBalanceAfter: TextView = v.findViewById(R.id.txtSaveBalanceAfter)
            val txtSaveNote: TextView = v.findViewById(R.id.txtSaveNote)
        }
    }

    // ==========================================
    // ADAPTER: SAVINGS VAULTS
    // ==========================================

    private inner class SavingsVaultAdapter : RecyclerView.Adapter<SavingsVaultAdapter.ViewHolder>() {

        private val items = mutableListOf<SavingsVault>()

        fun submitList(newItems: List<SavingsVault>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_savings_vault, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vault = items[position]
            holder.txtVaultName.text = vault.name
            holder.txtVaultCurrency.text = vault.currency
            holder.txtTotalSaved.text = "Saved: ${CurrencyFormatter.formatAmount(vault.currentAmount, vault.currency)}"

            if (vault.targetAmount != null && vault.targetAmount > 0) {
                holder.txtTargetAmount.visibility = View.VISIBLE
                holder.txtTargetAmount.text = "Target: ${CurrencyFormatter.formatAmount(vault.targetAmount, vault.currency)}"

                holder.progressContainer.visibility = View.VISIBLE
                val progress = vault.progressPercentage ?: 0
                holder.txtProgressPercentage.text = "$progress%"
                holder.progressBarVault.progress = progress
            } else {
                holder.txtTargetAmount.visibility = View.GONE
                holder.progressContainer.visibility = View.GONE
            }

            holder.btnAddMoney.setOnClickListener { showAddWithdrawDialog(vault, VaultTransaction.Type.ADD) }
            holder.btnWithdrawMoney.setOnClickListener { showAddWithdrawDialog(vault, VaultTransaction.Type.WITHDRAW) }
            holder.btnHistory.setOnClickListener { showVaultHistoryDialog(vault) }

            holder.btnDeleteVault.setOnClickListener {
                ThemeManager.showDialog(
                    AlertDialog.Builder(this@MoneyVaultActivity, R.style.CustomAlertDialogTheme)
                        .setTitle("Delete Vault")
                        .setMessage("Are you sure you want to delete ${vault.name}?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deleteVault(vault.id) }
                        .setNegativeButton("Cancel", null),
                    this@MoneyVaultActivity
                )
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtVaultName: TextView = v.findViewById(R.id.txtVaultName)
            val txtVaultCurrency: TextView = v.findViewById(R.id.txtVaultCurrency)
            val btnDeleteVault: ImageButton = v.findViewById(R.id.btnDeleteVault)
            val txtTotalSaved: TextView = v.findViewById(R.id.txtTotalSaved)
            val txtTargetAmount: TextView = v.findViewById(R.id.txtTargetAmount)
            val progressContainer: View = v.findViewById(R.id.progressContainer)
            val txtProgressPercentage: TextView = v.findViewById(R.id.txtProgressPercentage)
            val progressBarVault: ProgressBar = v.findViewById(R.id.progressBarVault)
            val btnAddMoney: Button = v.findViewById(R.id.btnAddMoney)
            val btnWithdrawMoney: Button = v.findViewById(R.id.btnWithdrawMoney)
            val btnHistory: Button = v.findViewById(R.id.btnHistory)
        }
    }

    // ==========================================
    // ADAPTER: PLANNED PAYMENTS
    // ==========================================

    private inner class PlannedPaymentAdapter : RecyclerView.Adapter<PlannedPaymentAdapter.ViewHolder>() {

        private val items = mutableListOf<PlannedPayment>()
        private var schedulesMap = mapOf<Long, List<PaymentScheduleItem>>()
        private val expandedStates = mutableMapOf<Long, Boolean>()

        fun submitList(newItems: List<PlannedPayment>, schedules: Map<Long, List<PaymentScheduleItem>>) {
            items.clear()
            items.addAll(newItems)
            schedulesMap = schedules
            notifyDataSetChanged()
        }

        fun updateSchedules(schedules: Map<Long, List<PaymentScheduleItem>>) {
            schedulesMap = schedules
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_planned_payment, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val payment = items[position]
            holder.txtPaymentName.text = payment.name
            holder.txtPaymentCurrency.text = payment.currency
            holder.txtTotalAmount.text = "Total: ${CurrencyFormatter.formatAmount(payment.totalAmount, payment.currency)}"
            holder.txtFrequency.text = "${payment.frequency} (Day ${payment.paymentDay})"
            holder.txtDateRange.text = "Period: ${payment.startDate} — ${payment.endDate}"

            val scheduleList = schedulesMap[payment.id] ?: emptyList()
            holder.txtScheduleTitle.text = "Payment Schedule (${scheduleList.size} installments)"

            val isExpanded = expandedStates[payment.id] ?: false
            holder.rvPaymentSchedule.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.imgScheduleArrow.rotation = if (isExpanded) 180f else 0f

            holder.rvPaymentSchedule.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.rvPaymentSchedule.adapter = ScheduleItemAdapter(payment, scheduleList)

            holder.scheduleHeaderRow.setOnClickListener {
                val nextState = !isExpanded
                expandedStates[payment.id] = nextState
                notifyItemChanged(position)
            }

            holder.btnDeletePayment.setOnClickListener {
                ThemeManager.showDialog(
                    AlertDialog.Builder(this@MoneyVaultActivity, R.style.CustomAlertDialogTheme)
                        .setTitle("Delete Planned Payment")
                        .setMessage("Delete ${payment.name} and its schedule?")
                        .setPositiveButton("Delete") { _, _ -> viewModel.deletePlannedPayment(payment.id) }
                        .setNegativeButton("Cancel", null),
                    this@MoneyVaultActivity
                )
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtPaymentName: TextView = v.findViewById(R.id.txtPaymentName)
            val txtPaymentCurrency: TextView = v.findViewById(R.id.txtPaymentCurrency)
            val btnDeletePayment: ImageButton = v.findViewById(R.id.btnDeletePayment)
            val txtTotalAmount: TextView = v.findViewById(R.id.txtTotalAmount)
            val txtFrequency: TextView = v.findViewById(R.id.txtFrequency)
            val txtDateRange: TextView = v.findViewById(R.id.txtDateRange)
            val scheduleHeaderRow: View = v.findViewById(R.id.scheduleHeaderRow)
            val txtScheduleTitle: TextView = v.findViewById(R.id.txtScheduleTitle)
            val imgScheduleArrow: ImageView = v.findViewById(R.id.imgScheduleArrow)
            val rvPaymentSchedule: RecyclerView = v.findViewById(R.id.rvPaymentSchedule)
        }
    }

    // ==========================================
    // ADAPTER: PAYMENT SCHEDULE ITEMS
    // ==========================================

    private inner class ScheduleItemAdapter(
        private val payment: PlannedPayment,
        private val items: List<PaymentScheduleItem>
    ) : RecyclerView.Adapter<ScheduleItemAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_payment_schedule, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtDueDate.text = "Due: ${item.dueDate}"
            holder.txtScheduleAmount.text = CurrencyFormatter.formatAmount(item.amount, payment.currency)

            when (item.status) {
                PaymentScheduleItem.Status.PAID -> {
                    holder.txtStatusBadge.text = "Paid ✓"
                    holder.txtStatusBadge.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.light_green))
                    holder.btnMarkAsPaid.visibility = View.GONE
                    holder.txtPaidCheck.visibility = View.VISIBLE
                }
                PaymentScheduleItem.Status.OVERDUE -> {
                    holder.txtStatusBadge.text = "Overdue"
                    holder.txtStatusBadge.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.chili_red))
                    holder.btnMarkAsPaid.visibility = View.VISIBLE
                    holder.txtPaidCheck.visibility = View.GONE
                }
                PaymentScheduleItem.Status.UPCOMING -> {
                    holder.txtStatusBadge.text = "Upcoming"
                    holder.txtStatusBadge.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                    holder.btnMarkAsPaid.visibility = View.VISIBLE
                    holder.txtPaidCheck.visibility = View.GONE
                }
            }

            holder.btnMarkAsPaid.setOnClickListener {
                showMarkAsPaidDialog(item)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtDueDate: TextView = v.findViewById(R.id.txtDueDate)
            val txtStatusBadge: TextView = v.findViewById(R.id.txtStatusBadge)
            val txtScheduleAmount: TextView = v.findViewById(R.id.txtScheduleAmount)
            val btnMarkAsPaid: Button = v.findViewById(R.id.btnMarkAsPaid)
            val txtPaidCheck: TextView = v.findViewById(R.id.txtPaidCheck)
        }
    }

    private fun showMarkAsPaidDialog(item: PaymentScheduleItem) {
        val accounts = BalanceManager.loadAccounts(this)
        val accountNames = if (accounts.isNotEmpty()) accounts.map { it.name }.toTypedArray() else arrayOf("Main")

        ThemeManager.showDialog(
            AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setTitle("Mark as Paid")
                .setMessage("Deduct ${CurrencyFormatter.formatAmount(item.amount, "USD")} and record Cash Out?")
                .setItems(accountNames) { _, which ->
                    val selectedAccount = accountNames[which]
                    val success = viewModel.markScheduleItemAsPaid(item.id, selectedAccount)
                    if (success) {
                        VaultReminderManager.cancelReminder(this, item.id)
                    }
                }
                .setNegativeButton("Cancel", null),
            this
        )
    }

    // ==========================================
    // ADAPTER: VAULT HISTORY
    // ==========================================

    private inner class VaultHistoryAdapter(
        private val list: List<VaultTransaction>,
        private val currency: String
    ) : RecyclerView.Adapter<VaultHistoryAdapter.ViewHolder>() {

        private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vault_transaction, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val tx = list[position]
            val isAdd = tx.type == VaultTransaction.Type.ADD

            holder.txtTxTypeBadge.text = if (isAdd) "+" else "-"
            holder.txtTxTypeBadge.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isAdd) R.color.light_green else R.color.chili_red
                )
            )

            holder.txtTxNote.text = if (tx.note.isNullOrBlank()) (if (isAdd) "Deposit" else "Withdrawal") else tx.note
            holder.txtTxDate.text = sdf.format(tx.date)

            val prefix = if (isAdd) "+" else "-"
            holder.txtTxAmount.text = "$prefix${CurrencyFormatter.formatAmount(tx.amount, currency)}"
            holder.txtTxAmount.setTextColor(
                ContextCompat.getColor(
                    holder.itemView.context,
                    if (isAdd) R.color.light_green else R.color.chili_red
                )
            )
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val txtTxTypeBadge: TextView = v.findViewById(R.id.txtTxTypeBadge)
            val txtTxNote: TextView = v.findViewById(R.id.txtTxNote)
            val txtTxDate: TextView = v.findViewById(R.id.txtTxDate)
            val txtTxAmount: TextView = v.findViewById(R.id.txtTxAmount)
        }
    }
}
