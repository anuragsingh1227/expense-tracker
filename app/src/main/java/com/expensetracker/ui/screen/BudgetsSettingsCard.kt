package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.R
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.entity.BudgetEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Money
import com.expensetracker.sms.parser.Categories
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.maskableFormatInr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class BudgetRowUi(
    val budget: BudgetEntity,
    val spent: Money,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetDao: BudgetDao,
    repository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    val budgets: StateFlow<List<BudgetRowUi>> = combine(
        budgetDao.observeAll(),
        dateBoundaryFlow(clock),
    ) { budgets, _ -> budgets }
        .flatMapLatest { budgets ->
            val window = DashboardRanges.forPeriod(SpendPeriod.MONTH, clock)
            repository.observeCategorySpend(window.fromInclusive, window.toExclusive, 500)
                .map { spends ->
                    val byCat = spends.associate { it.category to it.amount }
                    budgets.map { b ->
                        BudgetRowUi(
                            budget = b,
                            spent = byCat[b.category] ?: Money.ZERO,
                        )
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addBudget(category: String, limitText: String) {
        val limit = runCatching {
            BigDecimal(limitText.trim().replace(",", "")).setScale(2, RoundingMode.HALF_UP)
        }.getOrNull() ?: return
        if (limit.signum() <= 0) return
        val cat = category.trim().ifEmpty { return }
        viewModelScope.launch {
            budgetDao.upsert(
                BudgetEntity(
                    category = cat,
                    monthlyLimit = limit,
                    startsAt = Instant.now(clock),
                ),
            )
        }
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch { budgetDao.delete(id) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetsSettingsCard(
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val rows by viewModel.budgets.collectAsState()
    var category by remember { mutableStateOf(Categories.FOOD) }
    var limitText by remember { mutableStateOf("") }

    val canAdd = remember(limitText, category) {
        category.isNotBlank() &&
            runCatching {
                BigDecimal(limitText.trim().replace(",", "")).signum() > 0
            }.getOrDefault(false)
    }

    SurfaceCard {
        Text(stringResource(R.string.settings_budgets_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_budgets_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.label_category), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Categories.defaults.take(12).forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { category = option },
                    label = { Text(option) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = limitText,
            onValueChange = { limitText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text(stringResource(R.string.budget_monthly_limit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            prefix = { Text("₹") },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.addBudget(category, limitText)
                limitText = ""
            },
            enabled = canAdd,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.budget_add))
        }

        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            rows.forEach { row ->
                BudgetProgressRow(
                    row = row,
                    onDelete = { viewModel.deleteBudget(row.budget.id) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BudgetProgressRow(
    row: BudgetRowUi,
    onDelete: () -> Unit,
) {
    val limit = Money(row.budget.monthlyLimit.setScale(2, RoundingMode.HALF_UP))
    val progress = if (limit.amount.signum() > 0) {
        (row.spent.amount.toDouble() / limit.amount.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val over = row.spent.amount > limit.amount

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                row.budget.category,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.action_delete))
            }
        }
        Text(
            stringResource(
                R.string.budget_progress,
                row.spent.maskableFormatInr(),
                limit.maskableFormatInr(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
