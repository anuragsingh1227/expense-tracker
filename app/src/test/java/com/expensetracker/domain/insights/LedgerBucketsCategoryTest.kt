package com.expensetracker.domain.insights

import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class LedgerBucketsCategoryTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `amazon refund nets against shopping not a separate refund bar`() {
        val purchase = tx(
            amount = "1499.00",
            type = TransactionType.DEBIT,
            merchant = "AMAZON",
            category = Categories.SHOPPING,
            at = day(2026, 8, 8),
        )
        val refund = tx(
            amount = "1499.00",
            type = TransactionType.CREDIT,
            merchant = "AMAZON",
            category = Categories.REFUND,
            at = day(2026, 8, 9),
        )
        val byCat = LedgerBuckets.spendByCategory(listOf(purchase, refund))
        assertThat(byCat).isEmpty()
        assertThat(LedgerBuckets.spend(listOf(purchase, refund))).isEqualTo(Money.ZERO)
        val bars = SpendMath.withOtherBucket(
            byCat.entries.map { CategorySpend(it.key, it.value) },
            LedgerBuckets.spend(listOf(purchase, refund)),
        )
        assertThat(bars.sumOfAmounts()).isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `partial refund reduces the original merchant category`() {
        val purchase = tx(
            amount = "2000.00",
            type = TransactionType.DEBIT,
            merchant = "AMAZON",
            category = Categories.SHOPPING,
            at = day(2026, 8, 8),
        )
        val refund = tx(
            amount = "1499.00",
            type = TransactionType.CREDIT,
            merchant = "AMAZON",
            category = Categories.REFUND,
            at = day(2026, 8, 9),
        )
        val byCat = LedgerBuckets.spendByCategory(listOf(purchase, refund))
        assertThat(byCat[Categories.SHOPPING]?.amount).isEqualTo(BigDecimal("501.00"))
        assertThat(byCat).doesNotContainKey(Categories.REFUND)
        val spend = LedgerBuckets.spend(listOf(purchase, refund))
        assertThat(byCat.values.fold(Money.ZERO) { acc, m -> acc + m }).isEqualTo(spend)
    }

    @Test
    fun `unmatched refund stays as negative refund so bars still match hero`() {
        val food = tx(
            amount = "800.00",
            type = TransactionType.DEBIT,
            merchant = "SWIGGY",
            category = Categories.FOOD,
            at = day(2026, 8, 8),
        )
        val refund = tx(
            amount = "200.00",
            type = TransactionType.CREDIT,
            merchant = "UNKNOWN-M",
            category = Categories.REFUND,
            at = day(2026, 8, 9),
        )
        val byCat = LedgerBuckets.spendByCategory(listOf(food, refund))
        assertThat(byCat[Categories.FOOD]?.amount).isEqualTo(BigDecimal("800.00"))
        assertThat(byCat[Categories.REFUND]?.amount).isEqualTo(BigDecimal("-200.00"))
        val spend = LedgerBuckets.spend(listOf(food, refund))
        assertThat(spend.amount).isEqualTo(BigDecimal("600.00"))
        assertThat(byCat.values.fold(Money.ZERO) { acc, m -> acc + m }).isEqualTo(spend)
    }

    @Test
    fun `february refund does not reduce january stacked column`() {
        val jan = tx(
            amount = "1000.00",
            type = TransactionType.DEBIT,
            merchant = "AMAZON",
            category = Categories.SHOPPING,
            at = day(2026, 1, 15),
        )
        val febRefund = tx(
            amount = "400.00",
            type = TransactionType.CREDIT,
            merchant = "AMAZON",
            category = Categories.REFUND,
            at = day(2026, 2, 2),
        )
        val rows = LedgerBuckets.spendByCategoryAndMonth(listOf(jan, febRefund), zone)
        val janRow = rows.single { it.monthKey == "2026-01" }
        assertThat(janRow.category).isEqualTo(Categories.SHOPPING)
        assertThat(janRow.amount.amount).isEqualTo(BigDecimal("1000.00"))
        val febRow = rows.single { it.monthKey == "2026-02" }
        assertThat(febRow.category).isEqualTo(Categories.REFUND)
        assertThat(febRow.amount.amount).isEqualTo(BigDecimal("-400.00"))
    }

    @Test
    fun `withOtherBucket adds negative Other when truncated list overstates hero`() {
        val cats = listOf(
            CategorySpend(Categories.FOOD, Money.ofRupees("1000.00")),
            CategorySpend(Categories.TRAVEL, Money.ofRupees("400.00")),
        )
        val reconciled = SpendMath.withOtherBucket(cats, Money.ofRupees("1200.00"))
        assertThat(reconciled.last().category).isEqualTo("Other")
        assertThat(reconciled.last().amount.amount).isEqualTo(BigDecimal("-200.00"))
        assertThat(reconciled.sumOfAmounts()).isEqualTo(BigDecimal("1200.00"))
    }

    private fun List<CategorySpend>.sumOfAmounts(): BigDecimal =
        fold(BigDecimal.ZERO) { acc, row -> acc + row.amount.amount }

    private fun day(year: Int, month: Int, day: Int): Instant =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant()

    private fun tx(
        amount: String,
        type: TransactionType,
        merchant: String?,
        category: String,
        at: Instant,
    ) = Transaction(
        amount = Money.ofRupees(amount),
        type = type,
        merchant = merchant,
        category = category,
        bank = "ICICI",
        accountLast4 = "0789",
        cardLast4 = null,
        upiId = null,
        referenceNumber = null,
        balance = null,
        paymentMode = PaymentMode.UPI,
        timestamp = at,
        sender = "AD-ICICIB",
        rawSms = "test",
        narration = null,
        notes = null,
        dedupeHash = "h-$amount-$merchant-$at",
    )
}
