package com.example.budget.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.Transaction
import com.example.budget.domain.model.TransactionType
import com.example.budget.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DateRange(val start: Long?, val end: Long?)

data class TransactionHistoryState(
    val transactions: List<Transaction> = emptyList(),
    val dateRange: DateRange = DateRange(null, null),
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val difference: Double = 0.0
)

@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TransactionHistoryState())
    val state: StateFlow<TransactionHistoryState> = _state.asStateFlow()

    private val dateRangeFlow = MutableStateFlow(DateRange(null, null))

    init {
        viewModelScope.launch {
            combine(
                repository.getAllTransactions(),
                dateRangeFlow
            ) { allTransactions, range ->
                val filtered = allTransactions.filter { tx ->
                    val afterStart = range.start?.let { tx.timestamp >= it } ?: true
                    val beforeEnd = range.end?.let { tx.timestamp <= it } ?: true
                    afterStart && beforeEnd
                }

                val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.totalAmount }
                val expense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.totalAmount }

                TransactionHistoryState(
                    transactions = filtered,
                    dateRange = range,
                    income = income,
                    expense = expense,
                    difference = income - expense
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }
    
    fun setDateRange(start: Long?, end: Long?) {
        dateRangeFlow.value = DateRange(start, end)
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.reverseAndDeleteTransaction(transaction)
        }
    }
}
