package com.expensetracker.domain.model

import com.expensetracker.domain.insights.SplitLedger
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class HashtagParserTest {

    @Test
    fun `extracts inline hashtags from notes`() {
        val tags = HashtagParser.extract("Dinner with crew #TripToGoa #Reimbursable leftover")
        assertThat(tags).containsExactly("TripToGoa", "Reimbursable").inOrder()
    }

    @Test
    fun `merge chips and notes is case-insensitive distinct`() {
        val tags = HashtagParser.merge("#reimbursable taxi", listOf("Reimbursable", "Business"))
        assertThat(tags.map { it.lowercase() }).containsExactly("reimbursable", "Business".lowercase())
        assertThat(tags).hasSize(2)
    }
}

class SplitLedgerTest {

    @Test
    fun `aggregates amounts owed by person`() {
        val txs = listOf(
            tx(listOf(SplitShare("Rohan", Money.ofRupees("450.00")))),
            tx(listOf(SplitShare("rohan", Money.ofRupees("50.00")), SplitShare("Meera", Money.ofRupees("200.00")))),
        )
        val balances = SplitLedger.balances(txs)
        assertThat(balances).hasSize(2)
        assertThat(balances.first { it.name.equals("Rohan", true) }.owedToMe.amount)
            .isEqualTo(BigDecimal("500.00"))
        assertThat(SplitLedger.totalOwedToMe(balances).amount).isEqualTo(BigDecimal("700.00"))
    }

    private fun tx(shares: List<SplitShare>) = Transaction(
        amount = Money.ofRupees("900.00"),
        type = TransactionType.DEBIT,
        merchant = "Goa Cafe",
        category = "Food",
        bank = null,
        accountLast4 = null,
        cardLast4 = null,
        upiId = null,
        referenceNumber = null,
        balance = null,
        paymentMode = PaymentMode.UPI,
        timestamp = Instant.parse("2026-08-01T10:00:00Z"),
        sender = null,
        rawSms = null,
        narration = null,
        notes = "#TripToGoa",
        dedupeHash = shares.hashCode().toString(),
        isSplit = true,
        splitShares = shares,
        tags = listOf("TripToGoa"),
    )
}
