package com.expensetracker.domain.insights

import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.domain.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class SpendMathTest {

    @Test
    fun `netCashFlow is income minus spend minus investments`() {
        val income = Money.ofRupees("52000.00")
        val spend = Money.ofRupees("2331.00")
        val invest = Money.ofRupees("5000.00")
        assertThat(SpendMath.netCashFlow(income, spend, invest).amount)
            .isEqualTo(BigDecimal("44669.00"))
    }

    @Test
    fun `withOtherBucket reconciles truncated categories to total spend`() {
        val cats = listOf(
            CategorySpend("Food", Money.ofRupees("1000.00")),
            CategorySpend("Travel", Money.ofRupees("400.00")),
        )
        val reconciled = SpendMath.withOtherBucket(cats, Money.ofRupees("1800.00"))
        assertThat(reconciled.map { it.category }).containsExactly("Food", "Travel", "Other").inOrder()
        assertThat(reconciled.last().amount.amount).isEqualTo(BigDecimal("400.00"))
        val sum = reconciled.fold(BigDecimal.ZERO) { a, c -> a + c.amount.amount }
        assertThat(sum).isEqualTo(BigDecimal("1800.00"))
    }

    @Test
    fun `withOtherBucket skips Other when bars already match total`() {
        val cats = listOf(CategorySpend("Food", Money.ofRupees("100.00")))
        assertThat(SpendMath.withOtherBucket(cats, Money.ofRupees("100.00"))).hasSize(1)
    }
}
