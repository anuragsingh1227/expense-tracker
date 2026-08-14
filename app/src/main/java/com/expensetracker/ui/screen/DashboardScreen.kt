package com.expensetracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.AppFeatures
import com.expensetracker.R
import com.expensetracker.ui.components.AmountVisibilityToggle
import com.expensetracker.ui.components.CategoryBreakdown
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.MetricTile
import com.expensetracker.ui.components.MomRisingList
import com.expensetracker.ui.components.PeriodFilterRow
import com.expensetracker.ui.components.SectionLabel
import com.expensetracker.ui.components.StackedMonthBars
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.TransactionListItem
import com.expensetracker.ui.components.maskableFormatInr
import com.expensetracker.ui.widget.MonthSpendWidgetProvider
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenAdd: () -> Unit = {},
    amountsHidden: Boolean = false,
    onToggleAmountsHidden: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.spend, state.period, amountsHidden) {
        if (state.period == SpendPeriod.MONTH) {
            MonthSpendWidgetProvider.cacheAmount(context, state.spend.amount, amountsHidden)
        }
    }
    val spendLabel = when {
        state.spend.amount.signum() < 0 -> stringResource(R.string.period_net_refunds)
        else -> when (state.period) {
            SpendPeriod.DAY -> stringResource(R.string.period_spent_day)
            SpendPeriod.WEEK -> stringResource(R.string.period_spent_week)
            SpendPeriod.MONTH -> stringResource(R.string.period_spent_month)
            SpendPeriod.LAST_MONTH -> stringResource(R.string.period_spent_last_month)
            SpendPeriod.LAST_3_MONTHS -> stringResource(R.string.period_spent_last_3_months)
            SpendPeriod.FINANCIAL_YEAR -> stringResource(R.string.period_spent_financial_year)
            SpendPeriod.BILLING_CYCLE -> stringResource(R.string.period_spent_billing_cycle)
        }
    }
    LaunchedEffect(state.period, state.billingCycleStartDay) {
        if (state.period == SpendPeriod.BILLING_CYCLE && state.billingCycleStartDay == 1) {
            viewModel.setPeriod(SpendPeriod.MONTH)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(R.string.home_greeting),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                AmountVisibilityToggle(
                    amountsHidden = amountsHidden,
                    onToggle = onToggleAmountsHidden,
                )
            }
        }

        item {
            PeriodFilterRow(
                selected = state.period,
                onSelect = viewModel::setPeriod,
                showBillingCycle = state.billingCycleStartDay != 1,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        spendLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                    if (state.rangeLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.rangeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.spend.maskableFormatInr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MetricTile(
                    label = stringResource(R.string.period_income),
                    amount = state.income,
                    emphasize = true,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.period_investments),
                    amount = state.investments,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            MetricTile(
                label = stringResource(R.string.period_net),
                amount = state.net,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.upcomingDues.isNotEmpty()) {
            item {
                SurfaceCard(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                ) {
                    Text(
                        stringResource(R.string.dashboard_card_due_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    val dueFmt = DateTimeFormatter.ofPattern("d MMM")
                    state.upcomingDues.forEach { stmt ->
                        val card = stmt.cardLast4?.let { "••••$it" } ?: "—"
                        val bank = stmt.bank ?: stringResource(R.string.card_due_unknown_bank)
                        Text(
                            stringResource(
                                R.string.dashboard_card_due_row,
                                bank,
                                card,
                                stmt.dueDate.format(dueFmt),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        stmt.totalDue?.let { total ->
                            Text(
                                stringResource(R.string.dashboard_card_due_amount, total.maskableFormatInr()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(R.string.dashboard_card_due_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        val showMonthInsights = state.period != SpendPeriod.DAY && state.period != SpendPeriod.WEEK
        if (showMonthInsights && state.stack.any { it.total.amount.signum() > 0 }) {
            item {
                SurfaceCard {
                    Text(
                        stringResource(R.string.stack_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.stack_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    StackedMonthBars(columns = state.stack)
                }
            }
        }

        if (showMonthInsights && state.momChanges.isNotEmpty()) {
            item {
                SurfaceCard {
                    Text(
                        stringResource(R.string.mom_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.momPartial) {
                            stringResource(
                                R.string.mom_subtitle_partial,
                                state.momCurrentLabel,
                                state.momPreviousLabel,
                            )
                        } else {
                            stringResource(
                                R.string.mom_subtitle,
                                state.momCurrentLabel,
                                state.momPreviousLabel,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    MomRisingList(changes = state.momChanges)
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item {
                SurfaceCard {
                    Text(
                        stringResource(R.string.where_money_went),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    CategoryBreakdown(
                        categories = state.categories,
                        totalSpend = state.spend,
                    )
                }
            }
        }

        item {
            SectionLabel(stringResource(R.string.recent_activity))
        }

        if (state.recent.isEmpty()) {
            item {
                val autoSms = AppFeatures.autoSms
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = stringResource(R.string.empty_dashboard_title),
                    body = stringResource(R.string.empty_dashboard_body),
                    actionLabel = stringResource(
                        if (autoSms) R.string.empty_dashboard_action else R.string.empty_dashboard_add,
                    ),
                    onAction = if (autoSms) onOpenSettings else onOpenAdd,
                    secondaryActionLabel = stringResource(
                        if (autoSms) R.string.empty_dashboard_add else R.string.empty_dashboard_paste,
                    ),
                    onSecondaryAction = if (autoSms) onOpenAdd else onOpenSettings,
                )
            }
        } else {
            items(state.recent, key = { it.id }) { tx ->
                TransactionListItem(tx, onClick = { onOpenTransaction(tx.id) })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
