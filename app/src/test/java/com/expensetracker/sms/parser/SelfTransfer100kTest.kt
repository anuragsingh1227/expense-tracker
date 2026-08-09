package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/** User-reported ICICI↔Axis ₹1,00,000 UPI self-transfer (same UPI id on both legs). */
class SelfTransfer100kTest {

    private val now = Instant.parse("2026-08-01T02:18:42Z")
    private val parser = SmsParser(ownerNames = { listOf("Anurag") })

    @Test
    fun `both legs parse as Transfer with shared UPI ref and are pair-deleted`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, now),
        )!!.copy(id = 1)
        val credit = parser.parse(
            RawSms(
                "AX-AXISBK",
                SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
                now.plusSeconds(60),
            ),
        )!!.copy(id = 2)

        assertThat(debit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(credit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(debit.amount.amount).isEqualTo(BigDecimal("100000.00"))
        assertThat(credit.amount.amount).isEqualTo(BigDecimal("100000.00"))
        assertThat(debit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(debit.referenceNumber).isEqualTo("781919319954")
        assertThat(credit.referenceNumber).isEqualTo("781919319954")
        assertThat(LedgerBuckets.isSpend(debit)).isFalse()
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }
}
