package com.example.budget.presentation.transaction_entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.budget.Bootstrap
import com.example.budget.domain.exception.InvariantViolationException
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.domain.repository.TransactionRepository
import com.example.budget.notifications.PocketAlertChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TransactionEntryState(
    val type: TransactionType = TransactionType.EXPENSE,
    /** Whole rupiah as typed on the keypad, digits only */
    val totalAmount: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val primaryAssetId: String? = null,
    val secondaryAssetId: String? = null,
    val primaryFundId: String? = null,
    val secondaryFundId: String? = null,
    val expenseCategory: String = Bootstrap.UNCATEGORIZED,
    val memo: String = "",
    val isSaved: Boolean = false,
    val saveError: String? = null,
    val assets: List<Asset> = emptyList(),
    val funds: List<FundCategory> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList(),
    val holdingFundId: String? = null
) {
    val amount: Double get() = totalAmount.toDoubleOrNull() ?: 0.0

    /** Pockets in a useful order for picking: spending first, then bills and goals, income pocket last */
    val pocketsForPicking: List<FundCategory>
        get() = funds.sortedWith(
            compareBy<FundCategory> { it.id == holdingFundId }
                .thenBy { if (it.type == EnvelopeType.MONTHLY_SPENDING) 0 else 1 }
                .thenByDescending { it.isPinned }
        )
}

sealed class TransactionEntryEvent {
    data class ChangedType(val type: TransactionType) : TransactionEntryEvent()
    data class EnteredTotalAmount(val amount: String) : TransactionEntryEvent()
    data class SelectedPrimaryAsset(val id: String?) : TransactionEntryEvent()
    data class SelectedSecondaryAsset(val id: String?) : TransactionEntryEvent()
    data class SelectedPrimaryFund(val id: String?) : TransactionEntryEvent()
    data class SelectedSecondaryFund(val id: String?) : TransactionEntryEvent()
    data class SelectedExpenseCategory(val category: String) : TransactionEntryEvent()
    data class EnteredMemo(val memo: String) : TransactionEntryEvent()
    data class ChangedTimestamp(val timestamp: Long) : TransactionEntryEvent()
    object SaveTransaction : TransactionEntryEvent()
    object DismissError : TransactionEntryEvent()
}

@HiltViewModel
class TransactionEntryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val assetRepository: AssetRepository,
    private val fundCategoryRepository: FundCategoryRepository,
    private val expenseCategoryRepository: ExpenseCategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val pocketAlertChecker: PocketAlertChecker,
    bootstrap: Bootstrap
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionEntryState())
    val state: StateFlow<TransactionEntryState> = _state.asStateFlow()

    /** Learned from history: category → pocket last used with it; type → last account used */
    private var pocketForCategory: Map<String, String> = emptyMap()
    private var lastAssetForType: Map<TransactionType, String> = emptyMap()
    private var defaultsApplied = false

    init {
        viewModelScope.launch { bootstrap.ensure() }
        viewModelScope.launch {
            val history = transactionRepository.getAllTransactions().first()
            pocketForCategory = history.asSequence()
                .filter { it.type == TransactionType.EXPENSE && it.primaryFundId != null }
                .distinctBy { it.expenseCategory }
                .associate { it.expenseCategory to it.primaryFundId!! }
            lastAssetForType = history.asSequence()
                .filter { it.primaryAssetId != null }
                .distinctBy { it.type }
                .associate { it.type to it.primaryAssetId!! }

            combine(
                assetRepository.getAllAssets(),
                fundCategoryRepository.getAllFundCategories(),
                expenseCategoryRepository.getAllExpenseCategories()
            ) { assets, funds, categories -> Triple(assets, funds, categories) }
                .collect { (assets, funds, categories) ->
                    _state.update {
                        it.copy(
                            assets = assets,
                            funds = funds,
                            expenseCategories = categories.ifEmpty { listOf(ExpenseCategory("default", Bootstrap.UNCATEGORIZED)) },
                            holdingFundId = settingsRepository.holdingFundId.value
                        )
                    }
                    if (!defaultsApplied && assets.isNotEmpty()) {
                        defaultsApplied = true
                        applyDefaults(_state.value.type)
                    }
                }
        }
    }

    private fun defaultAsset(type: TransactionType): String? {
        val assets = _state.value.assets
        val liquid = assets.filter { it.type == AssetType.LIQUID }
        return lastAssetForType[type]?.takeIf { id -> assets.any { it.id == id } }
            ?: liquid.firstOrNull { it.isPinned }?.id
            ?: liquid.firstOrNull()?.id
            ?: assets.firstOrNull()?.id
    }

    private fun applyDefaults(type: TransactionType) {
        _state.update { s ->
            s.copy(
                type = type,
                primaryAssetId = if (type == TransactionType.BUDGET_ASSIGNMENT) null else defaultAsset(type),
                secondaryAssetId = null,
                primaryFundId = when (type) {
                    TransactionType.INCOME -> settingsRepository.holdingFundId.value
                    TransactionType.EXPENSE -> pocketForCategory[s.expenseCategory]
                    TransactionType.BUDGET_ASSIGNMENT -> settingsRepository.holdingFundId.value
                    TransactionType.ASSET_TRANSFER -> null
                },
                secondaryFundId = null,
                saveError = null
            )
        }
    }

    fun onEvent(event: TransactionEntryEvent) {
        when (event) {
            is TransactionEntryEvent.ChangedType -> if (event.type != _state.value.type) applyDefaults(event.type)
            is TransactionEntryEvent.EnteredTotalAmount -> _state.update { it.copy(totalAmount = event.amount) }
            is TransactionEntryEvent.SelectedPrimaryAsset -> _state.update { it.copy(primaryAssetId = event.id) }
            is TransactionEntryEvent.SelectedSecondaryAsset -> _state.update { it.copy(secondaryAssetId = event.id) }
            is TransactionEntryEvent.SelectedPrimaryFund -> _state.update { it.copy(primaryFundId = event.id) }
            is TransactionEntryEvent.SelectedSecondaryFund -> _state.update { it.copy(secondaryFundId = event.id) }
            is TransactionEntryEvent.SelectedExpenseCategory -> _state.update {
                // Smart default: the pocket you used last time with this category
                it.copy(
                    expenseCategory = event.category,
                    primaryFundId = pocketForCategory[event.category] ?: it.primaryFundId
                )
            }
            is TransactionEntryEvent.EnteredMemo -> _state.update { it.copy(memo = event.memo) }
            is TransactionEntryEvent.ChangedTimestamp -> _state.update { it.copy(timestamp = event.timestamp) }
            TransactionEntryEvent.SaveTransaction -> save()
            TransactionEntryEvent.DismissError -> _state.update { it.copy(saveError = null) }
        }
    }

    /** Plain-language reason the entry can't be saved yet, or null when it can. */
    fun problem(s: TransactionEntryState = _state.value): String? {
        if (s.amount <= 0) return "Enter an amount"
        return when (s.type) {
            TransactionType.EXPENSE -> when {
                s.primaryAssetId == null -> "Choose the account you paid from"
                s.primaryFundId == null && s.assets.firstOrNull { it.id == s.primaryAssetId }?.type == AssetType.LIQUID ->
                    "Choose which pocket this comes out of"
                else -> null
            }
            TransactionType.INCOME -> if (s.primaryAssetId == null) "Choose the account it went into" else null
            TransactionType.ASSET_TRANSFER -> when {
                s.primaryAssetId == null || s.secondaryAssetId == null -> "Choose both accounts"
                s.primaryAssetId == s.secondaryAssetId -> "Choose two different accounts"
                else -> null
            }
            TransactionType.BUDGET_ASSIGNMENT -> when {
                s.primaryFundId == null || s.secondaryFundId == null -> "Choose both pockets"
                s.primaryFundId == s.secondaryFundId -> "Choose two different pockets"
                else -> null
            }
        }
    }

    private fun save() {
        val s = _state.value
        problem(s)?.let { msg -> _state.update { it.copy(saveError = msg) }; return }

        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            type = s.type,
            timestamp = s.timestamp,
            totalAmount = s.amount,
            primaryAssetId = if (s.type == TransactionType.BUDGET_ASSIGNMENT) null else s.primaryAssetId,
            secondaryAssetId = if (s.type == TransactionType.ASSET_TRANSFER) s.secondaryAssetId else null,
            primaryFundId = if (s.type == TransactionType.ASSET_TRANSFER) null else s.primaryFundId,
            secondaryFundId = if (s.type == TransactionType.BUDGET_ASSIGNMENT) s.secondaryFundId else null,
            expenseCategory = if (s.type == TransactionType.EXPENSE) s.expenseCategory else Bootstrap.UNCATEGORIZED,
            memo = s.memo.trim()
        )

        viewModelScope.launch {
            try {
                transactionRepository.processTransaction(transaction)
                _state.update { it.copy(isSaved = true) }
                if (transaction.type == TransactionType.EXPENSE) {
                    transaction.primaryFundId?.let { pocketAlertChecker.onExpenseSaved(it) }
                }
            } catch (e: InvariantViolationException) {
                _state.update {
                    it.copy(saveError = "Your accounts and pockets would stop adding up. Pick a pocket for this money and try again.")
                }
            } catch (e: Exception) {
                _state.update { it.copy(saveError = "Couldn't save: ${e.message ?: "unknown error"}") }
            }
        }
    }
}
