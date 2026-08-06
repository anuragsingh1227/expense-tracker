package com.expensetracker.domain.insights

import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure spend math so hero totals, category bars, and MoM stay consistent.
 */
object SpendMath {

    /**
     * Ensures category bars sum to [totalSpend] by appending an "Other" row
     * when the truncated list is short of the period total.
     */
    fun withOtherBucket(
        categories: List<CategorySpend>,
        totalSpend: Money,
        otherLabel: String = "Other",
    ): List<CategorySpend> {
        if (totalSpend.amount.signum() <= 0) return categories
        val shown = categories.fold(BigDecimal.ZERO) { acc, row -> acc + row.amount.amount }
        val remainder = totalSpend.amount.subtract(shown).setScale(2, RoundingMode.HALF_UP)
        if (remainder.compareTo(BigDecimal("0.01")) < 0) return categories
        return categories + CategorySpend(otherLabel, Money(remainder))
    }

    /** Income − spend − investments (transfers ignored — usually self-to-self). */
    fun netCashFlow(income: Money, spend: Money, investments: Money): Money =
        income - spend - investments
}
