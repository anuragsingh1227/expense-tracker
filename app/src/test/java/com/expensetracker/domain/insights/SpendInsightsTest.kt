package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpendInsightsTest {

    @Test
    fun `monthOverMonth ranks largest spend increases`() {
        val current = mapOf(
            "Food" to Money.ofRupees(3000),
            "Travel" to Money.ofRupees(500),
            "Gadgets" to Money.ofRupees(2000),
        )
        val previous = mapOf(
            "Food" to Money.ofRupees(1000),
            "Travel" to Money.ofRupees(800),
            "Gadgets" to Money.ofRupees(0),
        )
        val rises = SpendInsights.monthOverMonth(current, previous, limit = 5)
        assertThat(rises.map { it.category }).containsExactly("Food", "Gadgets").inOrder()
        assertThat(rises[0].delta).isEqualTo(Money.ofRupees(2000))
        assertThat(rises[1].isNew).isTrue()
        assertThat(rises.none { it.category == "Travel" }).isTrue()
    }

    @Test
    fun `stackedMonths keeps top categories and folds rest into Other`() {
        val rows = listOf(
            CategoryMonthSpend("Food", "2024-01", Money.ofRupees(1000)),
            CategoryMonthSpend("Travel", "2024-01", Money.ofRupees(400)),
            CategoryMonthSpend("MiscA", "2024-01", Money.ofRupees(50)),
            CategoryMonthSpend("MiscB", "2024-01", Money.ofRupees(40)),
            CategoryMonthSpend("Food", "2024-02", Money.ofRupees(800)),
            CategoryMonthSpend("Travel", "2024-02", Money.ofRupees(200)),
        )
        val stack = SpendInsights.stackedMonths(
            rows = rows,
            monthKeysOldestFirst = listOf("2024-01", "2024-02"),
            topCategories = 2,
        )
        assertThat(stack).hasSize(2)
        assertThat(stack[0].segments.map { it.category }).contains("Other")
        assertThat(stack[0].total).isEqualTo(Money.ofRupees(1490))
        assertThat(stack[1].monthLabel).isEqualTo("Feb")
    }
}
