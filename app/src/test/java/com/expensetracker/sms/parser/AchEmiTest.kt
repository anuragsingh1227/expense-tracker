package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class AchEmiTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-08-05T04:10:02Z")

    @Test
    fun `Axis ACH-DR to HDFC Bank is EMI spend not Transfer or Others`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.AXIS_ACH_DR_HDFC_EMI, t))!!

        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("27136.00"))
        assertThat(tx.category).isEqualTo(Categories.EMI)
        assertThat(tx.merchant).isEqualTo("HDFC Bank")
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("47138")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.NET_BANKING)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LedgerBuckets.isInvestment(tx)).isFalse()
        assertThat(LedgerBuckets.isTransfer(tx)).isFalse()
    }

    @Test
    fun `ACH-DR to Bajaj Finance is EMI`() {
        val body = """
Debit INR 8,500.00
Axis Bank A/c XX8291
05-08-26 09:40:02
ACH-DR-BAJAJ FINANCE LTD-99887
""".trimIndent()
        val tx = parser.parse(RawSms("VM-AXISBK", body, t))!!
        assertThat(tx.category).isEqualTo(Categories.EMI)
        assertThat(tx.merchant?.uppercase()).contains("BAJAJ")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `NACH Scripbox stays Investment not EMI`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.AXIS_NACH_SCRIPBOX, t))!!
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
    }
}
