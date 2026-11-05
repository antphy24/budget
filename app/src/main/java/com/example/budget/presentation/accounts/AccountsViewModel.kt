package com.example.budget.presentation.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsState(
    val totalLiquidAssets: Double = 0.0,
    val totalFundsBalance: Double = 0.0,
    val funds: List<FundCategory> = emptyList(),
    val isBalanceHidden: Boolean = false
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val assetRepository: AssetRepository,
    private val fundCategoryRepository: FundCategoryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsState())
    val state: StateFlow<AccountsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                assetRepository.getSumOfLiquidBalances(),
                fundCategoryRepository.getSumOfAllBalances(),
                fundCategoryRepository.getAllFundCategories(),
                settingsRepository.isBalanceHidden
            ) { liquidTotal, fundsTotal, fundsList, isHidden ->
                AccountsState(
                    totalLiquidAssets = liquidTotal,
                    totalFundsBalance = fundsTotal,
                    funds = fundsList.filter { it.type != com.example.budget.domain.model.EnvelopeType.MONTHLY_SPENDING },
                    isBalanceHidden = isHidden
                )
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun toggleHideBalance() {
        viewModelScope.launch {
            settingsRepository.setBalanceHidden(!_state.value.isBalanceHidden)
        }
    }
}
