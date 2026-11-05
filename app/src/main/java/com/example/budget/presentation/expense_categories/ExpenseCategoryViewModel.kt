package com.example.budget.presentation.expense_categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.repository.ExpenseCategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExpenseCategoryViewModel @Inject constructor(
    private val repository: ExpenseCategoryRepository
) : ViewModel() {

    val categories: StateFlow<List<ExpenseCategory>> = repository.getAllExpenseCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertExpenseCategory(ExpenseCategory(UUID.randomUUID().toString(), name.trim()))
        }
    }

    fun deleteCategory(category: ExpenseCategory) {
        viewModelScope.launch {
            repository.deleteExpenseCategory(category)
        }
    }

    fun updateCategory(category: ExpenseCategory, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateExpenseCategory(category.copy(name = newName.trim()))
        }
    }
}
