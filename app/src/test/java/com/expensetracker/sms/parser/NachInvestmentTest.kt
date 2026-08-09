package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class NachInvestmentTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-08-09T10:00:00Z")

    @Test
    fun `Axis NACH debit to Scripbox is Investment not spend`() {
        val body = SampleSms.AXIS_NACH_SCRIPBOX

        assertThat(TransactionGate.isTransactional(body)).isTrue()
        val tx = parser.parse(RawSms("VM-AXISBK", body, t))!!

        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1300.00"))
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.merchant?.uppercase()).contains("SCRIPBOX")
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("UTIB7010806200000399")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.NET_BANKING)
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `NACH debit towards Groww is Investment`() {
        val body =
            "NACH debit towards GROWW for INR 5,000.00 with UMRN HDFC123456789012 has been successfully processed in A/c no. XX4411 today"
        val tx = parser.parse(RawSms("VM-HDFCBK", body, t))!!
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }
}
