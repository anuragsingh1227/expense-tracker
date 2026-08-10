package com.expensetracker.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.insights.CategoryMomChange
import com.expensetracker.domain.insights.CategoryMonthSpend
import com.expensetracker.domain.insights.SpendInsights
import com.expensetracker.domain.insights.SpendMath
import com.expensetracker.domain.insights.StackMonthColumn
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DashboardState(
    val period: SpendPeriod = SpendPeriod.MONTH,
    val rangeLabel: String = "",
    val spend: Money = Money.ZERO,
    val income: Money = Money.ZERO,
    val investments: Money = Money.ZERO,
    val categories: List<CategorySpend> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val momChanges: List<CategoryMomChange> = emptyList(),
    val momCurrentLabel: String = "",
    val momPreviousLabel: String = "",
    val momPartial: Boolean = false,
    /** Always three columns: current−2, current−1, current (zeros when no spend yet). */
    val stack: List<StackMonthColumn> = emptyList(),
) {
    /** Income − spend − investments (transfers ignored). */
    val net: Money get() = SpendMath.netCashFlow(income, spend, investments)
}

private data class PeriodCore(
    val spend: Money,
    val income: Money,
    val investments: Money,
    val categories: List<CategorySpend>,
    val recent: List<Transaction>,
)

private data class MomInsights(
    val momChanges: List<CategoryMomChange> = emptyList(),
    val momCurrentLabel: String = "",
    val momPreviousLabel: String = "",
    val momPartial: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val periodFlow = MutableStateFlow(SpendPeriod.MONTH)

    val state: StateFlow<DashboardState> = combine(
        periodFlow,
        dateBoundaryFlow(clock),
    ) { period, _ -> period }
        .flatMapLatest { period ->
            val window = DashboardRanges.forPeriod(period, clock)

            // Hero / category / recent totals follow the selected chip only.
            val core = combine(
                repository.observeSpendTotal(window.fromInclusive, window.toExclusive),
                repository.observeIncomeTotal(window.fromInclusive, window.toExclusive),
                repository.observeInvestmentTotal(window.fromInclusive, window.toExclusive),
                repository.observeCategorySpend(window.fromInclusive, window.toExclusive, 5),
                repository.observeBetween(window.fromInclusive, window.toExclusive, 12),
            ) { spend, income, investments, categories, recent ->
                PeriodCore(
                    spend = spend,
                    income = income,
                    investments = investments,
                    categories = SpendMath.withOtherBucket(categories, spend),
                    recent = recent,
                )
            }

            // Chart X-axis is permanently the rolling 3 calendar months. Missing months
            // are padded to Money.ZERO so first-install (current month only) still draws
            // three equal slots and fills progressively as local data accumulates.
            val stackWindow = DashboardRanges.lastThreeMonthsWindow(clock)
            val monthKeys = DashboardRanges.monthKeysForLastThree(clock)
            val stackFlow = repository
                .observeCategoryMonthSpend(stackWindow.fromInclusive, stackWindow.toExclusive)
                .map { monthRows: List<CategoryMonthSpend> ->
                    SpendInsights.stackedMonths(monthRows, monthKeys, topCategories = 5)
                }

            // MoM compare stays opt-in (needs a prior month of category totals).
            val momFlow = if (period == SpendPeriod.LAST_3_MONTHS) {
                val compare = DashboardRanges.monthCompareWindows(clock)
                combine(
                    repository.observeCategorySpend(
                        compare.currentFrom,
                        compare.currentToExclusive,
                        ALL_CATEGORIES_LIMIT,
                    ),
                    repository.observeCategorySpend(
                        compare.previousFrom,
                        compare.previousToExclusive,
                        ALL_CATEGORIES_LIMIT,
                    ),
                ) { currentCats, previousCats ->
                    MomInsights(
                        momChanges = SpendInsights.monthOverMonth(
                            current = currentCats.associate { it.category to it.amount },
                            previous = previousCats.associate { it.category to it.amount },
                            limit = 5,
                        ),
                        momCurrentLabel = compare.currentLabel,
                        momPreviousLabel = compare.previousLabel,
                        momPartial = compare.currentIsPartial,
                    )
                }
            } else {
                flowOf(MomInsights())
            }

            combine(core, stackFlow, momFlow) { c, stack, mom ->
                DashboardState(
                    period = period,
                    rangeLabel = window.labelRange,
                    spend = c.spend,
                    income = c.income,
                    investments = c.investments,
                    categories = c.categories,
                    recent = c.recent,
                    momChanges = mom.momChanges,
                    momCurrentLabel = mom.momCurrentLabel,
                    momPreviousLabel = mom.momPreviousLabel,
                    momPartial = mom.momPartial,
                    stack = stack,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun setPeriod(period: SpendPeriod) {
        periodFlow.value = period
    }

    private companion object {
        const val ALL_CATEGORIES_LIMIT = 500
    }
}

internal fun dateBoundaryFlow(clock: Clock): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now(clock))
        delay(DashboardRanges.millisUntilNextDay(clock))
    }
}
