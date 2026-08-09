package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class IciciUpiMerchantSpendTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-07-25T12:00:00Z")

    @Test
    fun `Apollo Pharmacy UPI debit is Medical spend not Transfer`() {
        val tx = parser.parse(
            RawSms("AX-ICICIB", SampleSms.ICICI_UPI_APOLLO_PHARMACY, t),
        )!!

        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("600.07"))
        assertThat(tx.category).isEqualTo(Categories.MEDICAL)
        assertThat(tx.merchant).isEqualTo("Apollo Pharmacy")
        assertThat(tx.referenceNumber).isEqualTo("657221332249")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LedgerBuckets.isTransfer(tx)).isFalse()
    }

    @Test
    fun `own-account UPI with person name still books as Transfer`() {
        val tx = parser.parse(
            RawSms("AX-ICICIB", SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, t),
        )!!
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `Motilal MF UPI still books as Investment not Transfer`() {
        val tx = parser.parse(
            RawSms("AX-ICICIB", SampleSms.MOTILAL_OSWAL_MF_UPI_DEBIT_40000, t),
        )!!
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }
}
