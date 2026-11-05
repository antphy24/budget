package com.example.budget.presentation.fund_management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.model.EnvelopeType
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class FundManagementState(
    val funds: List<FundCategory> = emptyList(),
    val showDialog: Boolean = false,
    val isResetting: Boolean = false, // If true, we are just manually resetting balance
    val editingFundId: String? = null,
    val nameInput: String = "",
    val typeInput: EnvelopeType = EnvelopeType.MONTHLY_SPENDING,
    val balanceInput: String = "",
    val isBalanceHidden: Boolean = false
)

sealed class FundManagementEvent {
    object AddNewClick : FundManagementEvent()
    data class EditClick(val fund: FundCategory) : FundManagementEvent()
    data class ResetClick(val fund: FundCategory) : FundManagementEvent()
    object DismissDialog : FundManagementEvent()
    data class NameChanged(val name: String) : FundManagementEvent()
    data class TypeChanged(val type: EnvelopeType) : FundManagementEvent()
    data class BalanceChanged(val balance: String) : FundManagementEvent()
    object Save : FundManagementEvent()
    data class Delete(val fund: FundCategory) : FundManagementEvent()
}

@HiltViewModel
class FundManagementViewModel @Inject constructor(
    private val repository: FundCategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FundManagementState())
    val state: StateFlow<FundManagementState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllFundCategories().collect { funds ->
                _state.value = _state.value.copy(funds = funds)
            }
        }
        viewModelScope.launch {
            settingsRepository.isBalanceHidden.collect { isHidden ->
                _state.value = _state.value.copy(isBalanceHidden = isHidden)
            }
        }
    }

    fun onEvent(event: FundManagementEvent) {
        when (event) {
            FundManagementEvent.AddNewClick -> {
                _state.value = _state.value.copy(
                    showDialog = true,
                    isResetting = false,
                    editingFundId = null,
                    nameInput = "",
                    typeInput = EnvelopeType.MONTHLY_SPENDING,
                    balanceInput = ""
                )
            }
            is FundManagementEvent.EditClick -> {
                _state.value = _state.value.copy(
                    showDialog = true,
                    isResetting = false,
                    editingFundId = event.fund.id,
                    nameInput = event.fund.name,
                    typeInput = event.fund.type,
                    balanceInput = event.fund.balance.toString()
                )
            }
            is FundManagementEvent.ResetClick -> {
                _state.value = _state.value.copy(
                    showDialog = true,
                    isResetting = true,
                    editingFundId = event.fund.id,
                    nameInput = event.fund.name,
                    typeInput = event.fund.type,
                    balanceInput = event.fund.balance.toString()
                )
            }
            FundManagementEvent.DismissDialog -> {
                _state.value = _state.value.copy(showDialog = false)
            }
            is FundManagementEvent.NameChanged -> _state.value = _state.value.copy(nameInput = event.name)
            is FundManagementEvent.TypeChanged -> _state.value = _state.value.copy(typeInput = event.type)
            is FundManagementEvent.BalanceChanged -> _state.value = _state.value.copy(balanceInput = event.balance)
            is FundManagementEvent.Delete -> {
                viewModelScope.launch { repository.deleteFundCategory(event.fund) }
            }
            FundManagementEvent.Save -> saveFund()
        }
    }

    private fun saveFund() {
        val currentState = _state.value
        val balanceDouble = currentState.balanceInput.toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            if (currentState.isResetting && currentState.editingFundId != null) {
                // Just reset the balance, don't change other fields
                repository.resetBalance(currentState.editingFundId, balanceDouble)
            } else {
                if (currentState.nameInput.isBlank()) return@launch
                val fund = FundCategory(
                    id = currentState.editingFundId ?: UUID.randomUUID().toString(),
                    name = currentState.nameInput,
                    type = currentState.typeInput,
                    balance = balanceDouble
                )
                if (currentState.editingFundId == null) {
                    repository.insertFundCategory(fund)
                } else {
                    repository.updateFundCategory(fund)
                }
            }
            _state.value = currentState.copy(showDialog = false)
        }
    }

    fun toggleHideBalance() {
        viewModelScope.launch {
            settingsRepository.setBalanceHidden(!_state.value.isBalanceHidden)
        }
    }

    fun togglePin(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinFundCategory(id, isPinned)
        }
    }
}
