package com.example.budget.presentation.asset_management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.AssetType
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AssetManagementState(
    val assets: List<Asset> = emptyList(),
    val showDialog: Boolean = false,
    val editingAssetId: String? = null,
    val nameInput: String = "",
    val typeInput: AssetType = AssetType.LIQUID,
    val balanceInput: String = "",
    val isBalanceHidden: Boolean = false
)

sealed class AssetManagementEvent {
    object AddNewClick : AssetManagementEvent()
    data class EditClick(val asset: Asset) : AssetManagementEvent()
    object DismissDialog : AssetManagementEvent()
    data class NameChanged(val name: String) : AssetManagementEvent()
    data class TypeChanged(val type: AssetType) : AssetManagementEvent()
    data class BalanceChanged(val balance: String) : AssetManagementEvent()
    object Save : AssetManagementEvent()
    data class Delete(val asset: Asset) : AssetManagementEvent()
}

@HiltViewModel
class AssetManagementViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AssetManagementState())
    val state: StateFlow<AssetManagementState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllAssets().collect { assets ->
                _state.value = _state.value.copy(assets = assets)
            }
        }
        viewModelScope.launch {
            settingsRepository.isBalanceHidden.collect { isHidden ->
                _state.value = _state.value.copy(isBalanceHidden = isHidden)
            }
        }
    }

    fun onEvent(event: AssetManagementEvent) {
        when (event) {
            AssetManagementEvent.AddNewClick -> {
                _state.value = _state.value.copy(
                    showDialog = true,
                    editingAssetId = null,
                    nameInput = "",
                    typeInput = AssetType.LIQUID,
                    balanceInput = ""
                )
            }
            is AssetManagementEvent.EditClick -> {
                _state.value = _state.value.copy(
                    showDialog = true,
                    editingAssetId = event.asset.id,
                    nameInput = event.asset.name,
                    typeInput = event.asset.type,
                    balanceInput = event.asset.balance.toString()
                )
            }
            AssetManagementEvent.DismissDialog -> {
                _state.value = _state.value.copy(showDialog = false)
            }
            is AssetManagementEvent.NameChanged -> _state.value = _state.value.copy(nameInput = event.name)
            is AssetManagementEvent.TypeChanged -> _state.value = _state.value.copy(typeInput = event.type)
            is AssetManagementEvent.BalanceChanged -> _state.value = _state.value.copy(balanceInput = event.balance)
            is AssetManagementEvent.Delete -> {
                viewModelScope.launch { repository.deleteAsset(event.asset) }
            }
            AssetManagementEvent.Save -> saveAsset()
        }
    }

    private fun saveAsset() {
        val currentState = _state.value
        if (currentState.nameInput.isBlank()) return
        val balanceDouble = currentState.balanceInput.toDoubleOrNull() ?: 0.0

        val asset = Asset(
            id = currentState.editingAssetId ?: UUID.randomUUID().toString(),
            name = currentState.nameInput,
            type = currentState.typeInput,
            balance = balanceDouble
        )

        viewModelScope.launch {
            if (currentState.editingAssetId == null) {
                repository.insertAsset(asset)
            } else {
                repository.updateAsset(asset)
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
            repository.togglePinAsset(id, isPinned)
        }
    }
}
