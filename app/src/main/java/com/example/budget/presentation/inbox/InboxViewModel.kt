package com.example.budget.presentation.inbox

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.exception.InvariantViolationException
import com.example.budget.domain.model.Asset
import com.example.budget.domain.model.ExpenseCategory
import com.example.budget.domain.model.FundCategory
import com.example.budget.domain.repository.AssetRepository
import com.example.budget.domain.repository.ExpenseCategoryRepository
import com.example.budget.domain.repository.FundCategoryRepository
import com.example.budget.domain.repository.SettingsRepository
import com.example.budget.scan.Draft
import com.example.budget.scan.ScanRepository
import com.example.budget.scan.ScanStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxState(
    val drafts: List<Draft> = emptyList(),
    val problems: Map<String, String> = emptyMap(),
    val assets: List<Asset> = emptyList(),
    val funds: List<FundCategory> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val holdingFundId: String? = null,
    val status: ScanStatus = ScanStatus(),
    val hasKey: Boolean = true,
    val hidden: Boolean = false,
    val editing: Draft? = null,
    val message: String? = null
) {
    val readyCount: Int get() = drafts.count { it.id !in problems }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val scan: ScanRepository,
    assets: AssetRepository,
    funds: FundCategoryRepository,
    categories: ExpenseCategoryRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val lookups = combine(assets.getAllAssets(), funds.getAllFundCategories(), categories.getAllExpenseCategories()) { a, f, c -> Triple(a, f, c) }
            val prefs = combine(settings.geminiApiKey, settings.isBalanceHidden, settings.holdingFundId) { k, h, hold -> Triple(k, h, hold) }
            combine(scan.drafts, lookups, scan.status, prefs) { drafts, (a, f, c), status, (key, hidden, hold) ->
                _state.value.copy(
                    drafts = drafts,
                    problems = drafts.mapNotNull { d -> scan.problem(d, a)?.let { d.id to it } }.toMap(),
                    assets = a, funds = f, categories = c, holdingFundId = hold,
                    status = status, hasKey = key.isNotBlank(), hidden = hidden
                )
            }.collect { _state.value = it }
        }
    }

    fun scanImages(uris: List<Uri>) = scan.scanImages(uris, "SCAN")

    fun edit(d: Draft) = _state.update { it.copy(editing = d) }
    fun updateEditing(t: (Draft) -> Draft) = _state.update { s -> s.copy(editing = s.editing?.let(t)) }

    /** Closing the sheet keeps the changes as a draft */
    fun closeEditor() {
        val d = _state.value.editing ?: return
        _state.update { it.copy(editing = null) }
        viewModelScope.launch { scan.save(d) }
    }

    fun confirm(d: Draft) {
        scan.problem(d, _state.value.assets)?.let { p -> _state.update { it.copy(message = p) }; return }
        _state.update { it.copy(editing = null) }
        viewModelScope.launch {
            try {
                scan.confirm(d)
            } catch (e: InvariantViolationException) {
                _state.update { it.copy(message = "Couldn't add ${d.merchant}: pick a pocket for it") }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't add ${d.merchant}: ${e.message}") }
            }
        }
    }

    fun confirmAll() {
        val st = _state.value
        val ready = st.drafts.filter { it.id !in st.problems }
        viewModelScope.launch {
            var ok = 0
            ready.forEach { d -> runCatching { scan.confirm(d) }.onSuccess { ok++ } }
            val left = st.drafts.size - ok
            _state.update {
                it.copy(message = if (left > 0) "Added $ok. $left still need a look." else "Added $ok to Budget")
            }
        }
    }

    fun discard(d: Draft) {
        _state.update { it.copy(editing = null) }
        viewModelScope.launch { scan.discard(d.id) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
        scan.clearMessage()
    }
}
