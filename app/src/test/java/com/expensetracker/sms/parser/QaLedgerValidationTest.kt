package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * QA rules: spend vs transfer vs confirmation-only (investment/card payment acks).
 */
class QaLedgerValidationTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-08-12T10:00:00Z")

    @Test
    fun `card spend at Zomato is online Food expense`() {
        val tx = parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_ZOMATO_CARD_SPEND, t))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
        assertThat(tx.category).isEqualTo(Categories.FOOD)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("549.00"))
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LedgerBuckets.isTransfer(tx)).isFalse()
    }

    @Test
    fun `PPF contribution received thank-you is confirmation-only ignored`() {
        assertThat(parser.isTransactional(SampleSms.PPF_CONTRIBUTION_RECEIVED_ACK)).isFalse()
        assertThat(parser.parse(RawSms("VM-ICICIB", SampleSms.PPF_CONTRIBUTION_RECEIVED_ACK, t))).isNull()
    }

    @Test
    fun `NPS contribution received ack is confirmation-only ignored`() {
        assertThat(parser.isTransactional(SampleSms.NPS_CONTRIBUTION_RECEIVED_ACK)).isFalse()
        assertThat(parser.parse(RawSms("VM-NSDLNPS", SampleSms.NPS_CONTRIBUTION_RECEIVED_ACK, t))).isNull()
    }

    @Test
    fun `PPF SI credited-in-PPF posts as Investment debit not spend`() {
        val tx = parser.parse(RawSms("VM-IDBIBK", SampleSms.IDBI_PPF_SI_CREDIT, t))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.merchant).isEqualTo("PPF")
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("12000.00"))
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
        assertThat(LedgerBuckets.isIncome(tx)).isFalse()
    }

    @Test
    fun `explicit SELF TRANSFER credit is Transfer not expense or income`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.AXIS_SELF_TRANSFER_CREDIT, t))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(LedgerBuckets.isTransfer(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
        assertThat(LedgerBuckets.isIncome(tx)).isFalse()
    }

    @Test
    fun `card payment received against credit card is confirmation-only ignored`() {
        assertThat(parser.isTransactional(SampleSms.SBI_CARD_PAYMENT_RECEIVED_AGAINST)).isFalse()
        assertThat(parser.parse(RawSms("VM-SBICRD", SampleSms.SBI_CARD_PAYMENT_RECEIVED_AGAINST, t))).isNull()
    }
}
