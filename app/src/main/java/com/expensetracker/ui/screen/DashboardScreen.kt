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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.R
import com.expensetracker.ui.components.CategoryBreakdown
import com.expensetracker.ui.components.EmptyState
import com.expensetracker.ui.components.MetricTile
import com.expensetracker.ui.components.SectionLabel
import com.expensetracker.ui.components.SurfaceCard
import com.expensetracker.ui.components.TransactionListItem

@Composable
fun DashboardScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(4.dp))
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

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        stringResource(R.string.month_spent_hero),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.monthSpend.formatInr(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            R.string.month_spend_supporting,
                            state.monthIncome.formatInr(),
                            state.monthNet.formatInr(),
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
                    label = stringResource(R.string.today_spent),
                    amount = state.todaySpend,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = stringResource(R.string.month_income),
                    amount = state.monthIncome,
                    emphasize = true,
                    modifier = Modifier.weight(1f),
                )
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
                        totalSpend = state.monthSpend,
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
