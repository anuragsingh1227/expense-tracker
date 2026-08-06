package com.expensetracker.domain.model

import com.expensetracker.domain.insights.SpendMath
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `ofRupees strips commas`() {
        assertThat(Money.ofRupees("1,20,450.00").amount).isEqualTo(BigDecimal("120450.00"))
    }

    @Test
    fun `formatInr uses Indian grouping and no decimals when whole`() {
        val m = Money.ofRupees("120450")
        assertThat(m.formatInr()).isEqualTo("₹1,20,450")
    }

    @Test
    fun `formatInr keeps two decimals when fractional`() {
        val m = Money.ofRupees("1234.50")
        assertThat(m.formatInr()).isEqualTo("₹1,234.50")
    }

    @Test
    fun `minus keeps two decimal scale for dashboard net`() {
        val income = Money.ofRupees("50000.00")
        val expense = Money.ofRupees("245.00")
        assertThat((income - expense).amount).isEqualTo(BigDecimal("49755.00"))
    }

    @Test
    fun `dashboard net uses SpendMath not ad-hoc income minus spend`() {
        val income = Money.ofRupees("52000.00")
        val spend = Money.ofRupees("3336.00")
        val invest = Money.ofRupees("5000.00")
        assertThat(SpendMath.netCashFlow(income, spend, invest).amount)
            .isEqualTo(BigDecimal("43664.00"))
    }

    @Test
    fun `large credit-limit style amount keeps exact scale`() {
        assertThat(Money.ofRupees("2,26,151.86").amount).isEqualTo(BigDecimal("226151.86"))
        assertThat(Money.ofRupees("2,26,151.86").formatInr()).isEqualTo("₹2,26,151.86")
    }
}
