package com.expensetracker.ui.screen

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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.expensetracker.R
import com.expensetracker.ui.components.CategoryBreakdown
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.MomRisingList
import com.expensetracker.ui.components.PeriodFilterRow
import com.expensetracker.ui.components.SectionLabel
import com.expensetracker.ui.components.StackedMonthBars
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.TransactionListItem
import com.expensetracker.ui.components.maskableFormatInr
import com.expensetracker.ui.widget.MonthSpendWidgetProvider

@Composable
fun DashboardScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    amountsHidden: Boolean = false,
    onToggleAmountsHidden: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.spend, state.period) {
        if (state.period == SpendPeriod.MONTH) {
            MonthSpendWidgetProvider.cacheAmount(context, state.spend.amount)
        }
    }
    val spendLabel = when (state.period) {
        SpendPeriod.DAY -> stringResource(R.string.period_spent_day)
        SpendPeriod.WEEK -> stringResource(R.string.period_spent_week)
        SpendPeriod.MONTH -> stringResource(R.string.period_spent_month)
        SpendPeriod.LAST_MONTH -> stringResource(R.string.period_spent_last_month)
        SpendPeriod.LAST_3_MONTHS -> stringResource(R.string.period_spent_last_3_months)
    }
    val hasLedgerActivity = state.recent.isNotEmpty() ||
        state.spend.amount.signum() != 0 ||
        state.income.amount.signum() != 0 ||
        state.investments.amount.signum() != 0
    // Stack / MoM only when the user opts into the 3-month filter.
    val showMonthInsights = state.period == SpendPeriod.LAST_3_MONTHS

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
                Column(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = onToggleAmountsHidden) {
                    Icon(
                        if (amountsHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (amountsHidden) {
                                R.string.dashboard_show_amounts_a11y
                            } else {
                                R.string.dashboard_hide_amounts_a11y
                            },
                        ),
                    )
                }
            }
        }

        item {
            PeriodFilterRow(
                selected = state.period,
                onSelect = viewModel::setPeriod,
                periods = listOf(
                    SpendPeriod.MONTH,
                    SpendPeriod.LAST_MONTH,
                    SpendPeriod.LAST_3_MONTHS,
                ),
            )
        }

        if (!hasLedgerActivity) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = stringResource(R.string.empty_dashboard_title),
                    body = stringResource(R.string.empty_dashboard_body),
                    actionLabel = stringResource(R.string.empty_dashboard_action),
                    onAction = onOpenSettings,
                )
            }
        } else {
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
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.period_investments),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.investments.maskableFormatInr(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        HeroMetricStrip(
                            income = state.income.maskableFormatInr(),
                            net = state.net.maskableFormatInr(),
                        )
                    }
                }
            }

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
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.dashboard_empty_period_title),
                        body = stringResource(R.string.dashboard_empty_period_body),
                    )
                }
            } else {
                items(state.recent, key = { it.id }) { tx ->
                    TransactionListItem(tx, onClick = { onOpenTransaction(tx.id) })
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HeroMetricStrip(
    income: String,
    net: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeroMetricCell(
            label = stringResource(R.string.period_income),
            value = income,
            modifier = Modifier.weight(1f),
        )
        HeroMetricCell(
            label = stringResource(R.string.period_net),
            value = net,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeroMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
        )
    }
}
