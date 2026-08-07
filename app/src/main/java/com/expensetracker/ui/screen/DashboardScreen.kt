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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.R
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

@Composable
fun DashboardScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    amountsHidden: Boolean = false,
    onToggleAmountsHidden: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val spendLabel = when (state.period) {
        SpendPeriod.DAY -> stringResource(R.string.period_spent_day)
        SpendPeriod.WEEK -> stringResource(R.string.period_spent_week)
        SpendPeriod.MONTH -> stringResource(R.string.period_spent_month)
        SpendPeriod.LAST_MONTH -> stringResource(R.string.period_spent_last_month)
        SpendPeriod.LAST_3_MONTHS -> stringResource(R.string.period_spent_last_3_months)
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.period_spend_supporting,
                            state.income.maskableFormatInr(),
                            state.investments.maskableFormatInr(),
                            state.net.maskableFormatInr(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
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

        if (state.stack.any { it.total.amount.signum() > 0 }) {
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

        if (state.momChanges.isNotEmpty()) {
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
                    title = stringResource(R.string.empty_dashboard_title),
                    body = stringResource(R.string.empty_dashboard_body),
                    actionLabel = stringResource(R.string.empty_dashboard_action),
                    onAction = onOpenSettings,
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
