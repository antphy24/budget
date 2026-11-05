package com.example.budget.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.budget.domain.model.CategorySum
import com.example.budget.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject

data class StatisticsState(
    val month: Int,
    val year: Int
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _timeState = MutableStateFlow(
        StatisticsState(
            month = Calendar.getInstance().get(Calendar.MONTH),
            year = Calendar.getInstance().get(Calendar.YEAR)
        )
    )
    val timeState: StateFlow<StatisticsState> = _timeState.asStateFlow()

    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val categorySums: StateFlow<List<CategorySum>> = _timeState
        .flatMapLatest { state ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, state.year)
                set(Calendar.MONTH, state.month)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfMonth = cal.timeInMillis
            
            cal.add(Calendar.MONTH, 1)
            cal.add(Calendar.MILLISECOND, -1)
            val endOfMonth = cal.timeInMillis

            transactionRepository.getExpenseSumByCategoryForMonth(startOfMonth, endOfMonth)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextMonth() {
        _timeState.update { current ->
            var newMonth = current.month + 1
            var newYear = current.year
            if (newMonth > Calendar.DECEMBER) {
                newMonth = Calendar.JANUARY
                newYear++
            }
            current.copy(month = newMonth, year = newYear)
        }
    }

    fun previousMonth() {
        _timeState.update { current ->
            var newMonth = current.month - 1
            var newYear = current.year
            if (newMonth < Calendar.JANUARY) {
                newMonth = Calendar.DECEMBER
                newYear--
            }
            current.copy(month = newMonth, year = newYear)
        }
    }
}
