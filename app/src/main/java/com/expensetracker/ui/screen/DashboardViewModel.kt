package com.expensetracker.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DashboardState(
    val todaySpend: Money = Money.ZERO,
    val monthSpend: Money = Money.ZERO,
    val monthIncome: Money = Money.ZERO,
    val categories: List<CategorySpend> = emptyList(),
    val recent: List<Transaction> = emptyList(),
) {
    val monthNet: Money get() = monthIncome - monthSpend
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: TransactionRepository,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<DashboardState> = dateBoundaryFlow(clock)
        .flatMapLatest {
            val window = DashboardRanges.at(clock)
            combine(
                repository.observeSpendTotal(window.startOfDay, window.endExclusive),
                repository.observeSpendTotal(window.startOfMonth, window.endExclusive),
                repository.observeTotal(TransactionType.CREDIT, window.startOfMonth, window.endExclusive),
                repository.observeCategorySpend(window.startOfMonth, window.endExclusive, 5),
                repository.observeRecent(12),
            ) { today, month, income, categories, recent ->
                DashboardState(today, month, income, categories, recent)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

internal fun dateBoundaryFlow(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(clock))
        delay(DashboardRanges.millisUntilNextDay(clock))
    }
}
