package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class CategoryMonthSpend(
    val category: String,
    /** ISO `yyyy-MM`. */
    val monthKey: String,
    val amount: Money,
)

data class CategoryMomChange(
    val category: String,
    val current: Money,
    val previous: Money,
    val delta: Money,
    /** null when previous is zero (new or undefined %). */
    val percentChange: Double?,
    val isNew: Boolean,
)

data class StackMonthColumn(
    val monthKey: String,
    val monthLabel: String,
    val segments: List<StackSegment>,
    val total: Money,
)

data class StackSegment(
    val category: String,
    val amount: Money,
    val fraction: Float,
)

/**
 * Pure aggregates for MoM + stacked months — no I/O, safe to unit test.
 * Keeps UI free of chart libraries (size + attack surface).
 */
object SpendInsights {

    private const val OTHER = "Other"

    fun monthOverMonth(
        current: Map<String, Money>,
        previous: Map<String, Money>,
        limit: Int = 5,
    ): List<CategoryMomChange> {
        val keys = current.keys + previous.keys
        return keys.map { category ->
            val cur = current[category] ?: Money.ZERO
            val prev = previous[category] ?: Money.ZERO
            val delta = cur - prev
            val pct = when {
                prev.amount.compareTo(BigDecimal.ZERO) == 0 -> null
                else -> delta.amount
                    .multiply(BigDecimal(100))
                    .divide(prev.amount, 1, RoundingMode.HALF_UP)
                    .toDouble()
            }
            CategoryMomChange(
                category = category,
                current = cur,
                previous = prev,
                delta = delta,
                percentChange = pct,
                isNew = prev.amount.compareTo(BigDecimal.ZERO) == 0 &&
                    cur.amount.compareTo(BigDecimal.ZERO) > 0,
            )
        }
            .filter { it.delta.amount.compareTo(BigDecimal.ZERO) > 0 }
            .sortedWith(
                compareByDescending<CategoryMomChange> { it.delta.amount }
                    .thenByDescending { it.current.amount },
            )
            .take(limit)
    }

    fun stackedMonths(
        rows: List<CategoryMonthSpend>,
        monthKeysOldestFirst: List<String>,
        topCategories: Int = 5,
    ): List<StackMonthColumn> {
        if (monthKeysOldestFirst.isEmpty()) return emptyList()
        val totalsByCategory = mutableMapOf<String, BigDecimal>()
        rows.forEach { row ->
            totalsByCategory[row.category] =
                (totalsByCategory[row.category] ?: BigDecimal.ZERO) + row.amount.amount
        }
        val top = totalsByCategory.entries
            .filter { it.value.signum() > 0 }
            .sortedByDescending { it.value }
            .take(topCategories)
            .map { it.key }
            .toSet()

        return monthKeysOldestFirst.map { key ->
            val monthRows = rows.filter { it.monthKey == key }
            val monthTotal = monthRows.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount.amount }
            val byCat = linkedMapOf<String, BigDecimal>()
            var other = BigDecimal.ZERO
            monthRows.forEach { row ->
                if (row.amount.amount.signum() <= 0) return@forEach
                if (row.category in top) {
                    byCat[row.category] =
                        (byCat[row.category] ?: BigDecimal.ZERO) + row.amount.amount
                } else {
                    other += row.amount.amount
                }
            }
            if (other.compareTo(BigDecimal.ZERO) > 0) {
                byCat[OTHER] = other
            }
            val barTotal = byCat.values.fold(BigDecimal.ZERO, BigDecimal::add)
            val totalD = monthTotal.toDouble().let { net ->
                if (net > 0.0) net else barTotal.toDouble().coerceAtLeast(0.01)
            }
            val ordered = top.toList() + listOfNotNull(OTHER.takeIf { OTHER in byCat })
            val segments = ordered.mapNotNull { cat ->
                val amt = byCat[cat] ?: return@mapNotNull null
                if (amt.compareTo(BigDecimal.ZERO) <= 0) return@mapNotNull null
                StackSegment(
                    category = cat,
                    amount = Money(amt.setScale(2, RoundingMode.HALF_UP)),
                    fraction = (amt.toDouble() / totalD).toFloat().coerceIn(0f, 1f),
                )
            }
            StackMonthColumn(
                monthKey = key,
                monthLabel = shortMonthLabel(key),
                segments = segments,
                total = Money(monthTotal.setScale(2, RoundingMode.HALF_UP)),
            )
        }
    }

    fun toMoneyMap(rows: List<Pair<String, Money>>): Map<String, Money> =
        rows.associate { it.first to it.second }

    private fun shortMonthLabel(monthKey: String): String {
        val parts = monthKey.split('-')
        if (parts.size != 2) return monthKey
        val month = parts[1].toIntOrNull() ?: return monthKey
        val names = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        return names.getOrElse(month - 1) { monthKey }
    }
}
