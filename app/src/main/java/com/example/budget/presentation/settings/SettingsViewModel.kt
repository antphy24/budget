package com.example.budget.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.Bootstrap
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.update.AppVersion
import com.example.budget.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

data class AccountEditor(
    val id: String? = null,
    val name: String = "",
    val type: AssetType = AssetType.LIQUID,
    val balanceText: String = "",
    val currentBalance: Double = 0.0
)

data class CategoryEditor(val category: ExpenseCategory?, val name: String = category?.name ?: "")

data class SettingsState(
    val hidden: Boolean = false,
    val assets: List<Asset> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val liquidTotal: Double = 0.0,
    val otherTotal: Double = 0.0,
    val accountEditor: AccountEditor? = null,
    val categoryEditor: CategoryEditor? = null,
    val message: String? = null,
    val version: AppVersion = AppVersion("", 0),
    val watchedApps: Set<String> = emptySet(),
    val paymentApps: Map<String, String> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val assetRepo: AssetRepository,
    private val categoryRepo: ExpenseCategoryRepository,
    private val settings: SettingsRepository,
    private val txRepo: TransactionRepository,
    private val bootstrap: Bootstrap,
    val updates: UpdateManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState(version = updates.currentVersion))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.watchedApps, settings.seenPaymentApps) { w, seen -> w to seen }
                .collect { (w, seen) -> _state.update { it.copy(watchedApps = w, paymentApps = seen) } }
        }
        viewModelScope.launch { bootstrap.ensure() }
        viewModelScope.launch {
            combine(assetRepo.getAllAssets(), categoryRepo.getAllExpenseCategories(), settings.isBalanceHidden) { a, c, h ->
                Triple(a, c, h)
            }.collect { (a, c, h) ->
                _state.update {
                    it.copy(
                        hidden = h,
                        assets = a,
                        categories = c,
                        liquidTotal = a.filter { x -> x.type == AssetType.LIQUID }.sumOf { x -> x.balance },
                        otherTotal = a.filter { x -> x.type == AssetType.NON_LIQUID }.sumOf { x -> x.balance }
                    )
                }
            }
        }
    }

    // --- Accounts ---

    fun newAccount() = _state.update { it.copy(accountEditor = AccountEditor()) }
    fun editAccount(a: Asset) = _state.update {
        it.copy(accountEditor = AccountEditor(a.id, a.name, a.type, a.balance.toLong().toString(), a.balance))
    }
    fun updateAccount(t: (AccountEditor) -> AccountEditor) = _state.update { s -> s.copy(accountEditor = s.accountEditor?.let(t)) }
    fun closeAccount() = _state.update { it.copy(accountEditor = null) }

    fun saveAccount() {
        val e = _state.value.accountEditor ?: return
        val name = e.name.trim()
        if (name.isEmpty()) { toast("Give the account a name"); return }
        val balance = e.balanceText.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            try {
                bootstrap.ensure()
                val holding = settings.holdingFundId.value
                if (e.id == null) {
                    val id = UUID.randomUUID().toString()
                    if (e.type == AssetType.LIQUID) {
                        assetRepo.insertAsset(Asset(id, name, e.type, 0.0))
                        // Record the starting balance as income into the income pocket so the
                        // books stay balanced; it then shows up as money to distribute.
                        if (balance > 0 && holding != null) txRepo.processTransaction(balanceTx(TransactionType.INCOME, id, holding, balance, "Starting balance"))
                    } else {
                        assetRepo.insertAsset(Asset(id, name, e.type, balance))
                    }
                } else {
                    val current = assetRepo.getAssetById(e.id) ?: return@launch
                    assetRepo.updateAsset(current.copy(name = name))
                    val delta = balance - current.balance
                    if (abs(delta) >= 1.0) {
                        if (current.type == AssetType.LIQUID && holding != null) {
                            val type = if (delta > 0) TransactionType.INCOME else TransactionType.EXPENSE
                            txRepo.processTransaction(balanceTx(type, e.id, holding, abs(delta), "Balance correction"))
                        } else if (current.type == AssetType.NON_LIQUID) {
                            assetRepo.updateAsset(current.copy(name = name, balance = balance))
                        }
                    }
                }
                _state.update { it.copy(accountEditor = null) }
            } catch (ex: Exception) {
                toast("Couldn't save the account: ${ex.message}")
            }
        }
    }

    private fun balanceTx(type: TransactionType, assetId: String, fundId: String, amount: Double, memo: String) = Transaction(
        id = UUID.randomUUID().toString(),
        type = type,
        timestamp = System.currentTimeMillis(),
        totalAmount = amount,
        primaryAssetId = assetId,
        secondaryAssetId = null,
        primaryFundId = fundId,
        secondaryFundId = null,
        expenseCategory = Bootstrap.UNCATEGORIZED,
        memo = memo
    )

    fun deleteAccount() {
        val e = _state.value.accountEditor ?: return
        val id = e.id ?: return
        if (abs(e.currentBalance) >= 1.0) { toast("Set the balance to 0 or transfer the money out before deleting"); return }
        viewModelScope.launch {
            assetRepo.getAssetById(id)?.let { assetRepo.deleteAsset(it) }
            _state.update { it.copy(accountEditor = null, message = "Account deleted") }
        }
    }

    // --- Categories ---

    fun newCategory() = _state.update { it.copy(categoryEditor = CategoryEditor(null)) }
    fun editCategory(c: ExpenseCategory) = _state.update { it.copy(categoryEditor = CategoryEditor(c)) }
    fun updateCategoryName(n: String) = _state.update { s -> s.copy(categoryEditor = s.categoryEditor?.copy(name = n)) }
    fun closeCategory() = _state.update { it.copy(categoryEditor = null) }

    fun saveCategory() {
        val e = _state.value.categoryEditor ?: return
        val name = e.name.trim()
        if (name.isEmpty()) { toast("Give the category a name"); return }
        viewModelScope.launch {
            if (e.category == null) categoryRepo.insertExpenseCategory(ExpenseCategory(UUID.randomUUID().toString(), name))
            else categoryRepo.updateExpenseCategory(e.category.copy(name = name))
            _state.update { it.copy(categoryEditor = null) }
        }
    }

    fun deleteCategory() {
        val c = _state.value.categoryEditor?.category ?: return
        viewModelScope.launch {
            categoryRepo.deleteExpenseCategory(c)
            _state.update { it.copy(categoryEditor = null, message = "Deleted ${c.name}. Past spending keeps its label.") }
        }
    }

    fun setAppWatched(pkg: String, watched: Boolean) {
        viewModelScope.launch { settings.setAppWatched(pkg, watched) }
    }

    fun toggleHidden() { viewModelScope.launch { settings.setBalanceHidden(!_state.value.hidden) } }
    fun clearMessage() = _state.update { it.copy(message = null) }
    private fun toast(m: String) = _state.update { it.copy(message = m) }
}
