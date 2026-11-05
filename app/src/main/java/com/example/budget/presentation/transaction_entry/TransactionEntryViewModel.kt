package com.example.budget.presentation.transaction_entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.notifications.PocketAlertChecker

data class TransactionEntryState(
    val type: TransactionType = TransactionType.EXPENSE,
    val totalAmount: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val primaryAssetId: String? = null,
    val secondaryAssetId: String? = null,
    val primaryFundId: String? = null,
    val secondaryFundId: String? = null,
    val expenseCategory: String = "Uncategorized",
    val memo: String = "",
    val isSaved: Boolean = false,
    val saveError: String? = null,
    val assets: List<Asset> = emptyList(),
    val funds: List<FundCategory> = emptyList(),
    val expenseCategories: List<ExpenseCategory> = emptyList()
)

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
    private val pocketAlertChecker: PocketAlertChecker
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionEntryState())
    val state: StateFlow<TransactionEntryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                assetRepository.getAllAssets(),
                fundCategoryRepository.getAllFundCategories(),
                expenseCategoryRepository.getAllExpenseCategories()
            ) { assets, funds, categories ->
                _state.value.copy(
                    assets = assets, 
                    funds = funds, 
                    expenseCategories = categories.ifEmpty { listOf(ExpenseCategory("default", "Uncategorized")) }
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun onEvent(event: TransactionEntryEvent) {
        when (event) {
            is TransactionEntryEvent.ChangedType -> {
                _state.value = _state.value.copy(
                    type = event.type,
                    primaryAssetId = null,
                    secondaryAssetId = null,
                    // Income lands in the income pocket by default; distribute it later
                    primaryFundId = if (event.type == TransactionType.INCOME) settingsRepository.holdingFundId.value else null,
                    secondaryFundId = null,
                    saveError = null
                )
            }
            is TransactionEntryEvent.EnteredTotalAmount -> _state.value = _state.value.copy(totalAmount = event.amount)
            is TransactionEntryEvent.SelectedPrimaryAsset -> _state.value = _state.value.copy(primaryAssetId = event.id)
            is TransactionEntryEvent.SelectedSecondaryAsset -> _state.value = _state.value.copy(secondaryAssetId = event.id)
            is TransactionEntryEvent.SelectedPrimaryFund -> _state.value = _state.value.copy(primaryFundId = event.id)
            is TransactionEntryEvent.SelectedSecondaryFund -> _state.value = _state.value.copy(secondaryFundId = event.id)
            is TransactionEntryEvent.SelectedExpenseCategory -> _state.value = _state.value.copy(expenseCategory = event.category)
            is TransactionEntryEvent.EnteredMemo -> _state.value = _state.value.copy(memo = event.memo)
            is TransactionEntryEvent.ChangedTimestamp -> _state.value = _state.value.copy(timestamp = event.timestamp)
            
            TransactionEntryEvent.SaveTransaction -> saveTransaction()
            TransactionEntryEvent.DismissError -> _state.value = _state.value.copy(saveError = null)
        }
    }

    private fun saveTransaction() {
        val currentState = _state.value
        val totalAmountDouble = currentState.totalAmount.toDoubleOrNull() ?: return
        
        val transactionId = UUID.randomUUID().toString()
        val transaction = Transaction(
            id = transactionId,
            type = currentState.type,
            timestamp = currentState.timestamp,
            totalAmount = totalAmountDouble,
            primaryAssetId = currentState.primaryAssetId,
            secondaryAssetId = currentState.secondaryAssetId,
            primaryFundId = currentState.primaryFundId,
            secondaryFundId = currentState.secondaryFundId,
            expenseCategory = if (currentState.type == TransactionType.EXPENSE) currentState.expenseCategory else "Uncategorized",
            memo = currentState.memo
        )

        viewModelScope.launch {
            try {
                transactionRepository.processTransaction(transaction)
                _state.value = currentState.copy(isSaved = true)
                if (transaction.type == TransactionType.EXPENSE) {
                    transaction.primaryFundId?.let { pocketAlertChecker.onExpenseSaved(it) }
                }
            } catch (e: Exception) {
                _state.value = currentState.copy(saveError = e.message ?: "Unknown error occurred")
                e.printStackTrace()
            }
        }
    }
}
