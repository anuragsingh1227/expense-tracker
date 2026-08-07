package com.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.R
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.ui.screen.SpendPeriod
import com.expensetracker.ui.theme.ExpenseColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CardShape = RoundedCornerShape(16.dp)
private val RowShape = RoundedCornerShape(14.dp)

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun HeroBalanceCard(
    label: String,
    amount: Money,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    val positive = amount.amount.signum() >= 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                amount.formatInr(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (positive) {
                    ExpenseColors.Income
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    amount: Money,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 88.dp),
        shape = CardShape,
        color = if (emphasize) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (emphasize) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (emphasize) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                amount.maskableFormatInr(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
fun PeriodFilterRow(
    selected: SpendPeriod,
    onSelect: (SpendPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val periods = listOf(
        SpendPeriod.DAY to stringResource(R.string.period_day),
        SpendPeriod.WEEK to stringResource(R.string.period_week),
        SpendPeriod.MONTH to stringResource(R.string.period_month),
        SpendPeriod.LAST_MONTH to stringResource(R.string.period_last_month),
        SpendPeriod.LAST_3_MONTHS to stringResource(R.string.period_last_3_months),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        periods.forEach { (period, label) ->
            val selectedNow = period == selected
            val a11y = if (selectedNow) {
                stringResource(R.string.period_selected_a11y, label)
            } else {
                stringResource(R.string.period_select_a11y, label)
            }
            Surface(
                onClick = { onSelect(period) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = a11y },
                shape = RoundedCornerShape(999.dp),
                color = if (selectedNow) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (selectedNow) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = if (selectedNow) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedNow) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionListItem(
    tx: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val isCredit = tx.type == TransactionType.CREDIT
    val sign = if (isCredit) "+" else "−"
    val amountColor = if (isCredit) ExpenseColors.Income else MaterialTheme.colorScheme.onSurface
    val date = tx.timestamp.atZone(ZoneId.systemDefault()).format(TX_FMT)
    val title = tx.merchant ?: tx.category
    val a11y = "$title, $sign${tx.amount.maskableFormatInr()}, ${tx.category}, $date"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RowShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .semantics {
                contentDescription = a11y
                role = Role.Button
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectMode) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(4.dp))
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isCredit) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isCredit) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null,
                tint = if (isCredit) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${tx.category} · $date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "$sign${tx.amount.maskableFormatInr()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
            )
            Text(
                stringResource(if (isCredit) R.string.type_credit else R.string.type_debit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetaRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

enum class StatusTone { POSITIVE, NEGATIVE, NEUTRAL }

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor) = when (tone) {
        StatusTone.POSITIVE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        StatusTone.NEGATIVE ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        StatusTone.NEUTRAL ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Binary positive/negative shorthand — e.g. Credit vs Debit. */
@Composable
fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    StatusPill(text, if (positive) StatusTone.POSITIVE else StatusTone.NEGATIVE, modifier)
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun CategoryBreakdown(
    categories: List<com.expensetracker.data.repository.CategorySpend>,
    totalSpend: Money,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    val total = totalSpend.amount.toDouble().coerceAtLeast(0.01)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.forEach { row ->
            val fraction = (row.amount.amount.toDouble() / total).toFloat().coerceIn(0f, 1f)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(row.amount.maskableFormatInr(), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
fun MomRisingList(
    changes: List<com.expensetracker.domain.insights.CategoryMomChange>,
    modifier: Modifier = Modifier,
) {
    if (changes.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        changes.forEach { row ->
            val pct = row.percentChange
            val changeLabel = when {
                row.isNew -> stringResource(R.string.mom_new)
                pct != null -> stringResource(R.string.mom_up_pct, pct)
                else -> row.delta.maskableFormatInr()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.mom_vs_previous,
                            row.previous.maskableFormatInr(),
                            row.current.maskableFormatInr(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "+${row.delta.maskableFormatInr()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColors.Coral,
                    )
                    Text(
                        changeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun StackedMonthBars(
    columns: List<com.expensetracker.domain.insights.StackMonthColumn>,
    modifier: Modifier = Modifier,
) {
    if (columns.isEmpty()) return
    val palette = listOf(
        ExpenseColors.Teal,
        ExpenseColors.TealBright,
        ExpenseColors.TealDeep,
        ExpenseColors.Coral,
        Color(0xFF0EA5E9),
        Color(0xFF94A3B8),
    )
    val legendCats = columns
        .flatMap { it.segments.map { seg -> seg.category } }
        .distinct()
    // Bar height reflects each month's actual total, not just its category mix —
    // the biggest-spend month fills the full track; smaller months are shorter.
    val maxTotal = columns.maxOfOrNull { it.total.amount.toDouble() }?.coerceAtLeast(0.01) ?: 0.01

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            columns.forEach { col ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        col.total.maskableFormatInr(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    val heightFraction = (col.total.amount.toDouble() / maxTotal).toFloat().coerceIn(0f, 1f)
                    val barHeight = (88f * heightFraction).coerceAtLeast(
                        if (col.total.amount.signum() > 0) 3f else 0f,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            col.segments.asReversed().forEach { seg ->
                                val colorIndex = legendCats.indexOf(seg.category).coerceAtLeast(0)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((barHeight * seg.fraction).dp.coerceAtLeast(3.dp))
                                        .background(palette[colorIndex % palette.size]),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        col.monthLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            legendCats.chunked(3).forEach { rowCats ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowCats.forEach { cat ->
                        val colorIndex = legendCats.indexOf(cat).coerceAtLeast(0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(palette[colorIndex % palette.size]),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

val OnboardingIcons = listOf(
    Icons.Outlined.Sms,
    Icons.Outlined.Lock,
    Icons.Outlined.AccountBalanceWallet,
)

private val TX_FMT = DateTimeFormatter.ofPattern("dd MMM · HH:mm")
