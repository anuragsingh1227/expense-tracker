package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class EmiConversionOfferTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-07-26T12:00:00Z")

    @Test
    fun `Avenue Supermar card spend with EMI conversion footer is Groceries not EMI`() {
        val tx = parser.parse(
            RawSms("AX-ICICIB", SampleSms.ICICI_CARD_AVENUE_SUPERMAR_EMI_OFFER, t),
        )!!

        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("8646.47"))
        assertThat(tx.category).isEqualTo(Categories.GROCERIES)
        assertThat(tx.merchant).isEqualTo("DMart")
        assertThat(tx.cardLast4).isEqualTo("1014")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(tx.category).isNotEqualTo(Categories.EMI)
    }

    @Test
    fun `real HDFC ACH EMI still books as EMI`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.AXIS_ACH_DR_HDFC_EMI, t))!!
        assertThat(tx.category).isEqualTo(Categories.EMI)
    }
}
