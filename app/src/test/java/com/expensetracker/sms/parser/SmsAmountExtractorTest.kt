package com.expensetracker.sms.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SmsAmountExtractorTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2026-08-06T04:00:00Z")

    @Test
    fun `skips available credit limit and never books it as amount`() {
        val body =
            "Rs 1,299.00 spent on AXIS Bank Credit Card ending 4321 at FLIPKART. Available Credit Limit is Rs.2,26,151.86"
        assertThat(SmsAmountExtractor.extract(body)!!.amount).isEqualTo(BigDecimal("1299.00"))
    }

    @Test
    fun `FX reversal with only limit INR is rejected not booked as 2 lakh credit`() {
        assertThat(SmsAmountExtractor.extract(SampleSms.FX_REVERSAL_LIMIT_TRAP)).isNull()
        assertThat(parser.parse(RawSms("VM-HDFCBK", SampleSms.FX_REVERSAL_LIMIT_TRAP, now))).isNull()
    }

    @Test
    fun `FX spend uses INR equivalent not available limit`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.FX_SPEND_WITH_INR_EQUIV, now))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1045.00"))
        assertThat(tx.type.name).isEqualTo("DEBIT")
    }

    @Test
    fun `balance-only INR figures yield null`() {
        assertThat(SmsAmountExtractor.extract(SampleSms.BALANCE_ONLY)).isNull()
    }

    @Test
    fun `Avl limit after real debit is skipped`() {
        assertThat(SmsAmountExtractor.extract(SampleSms.AXIS_CARD)!!.amount)
            .isEqualTo(BigDecimal("1299.00"))
    }

    @Test
    fun `ICICI spent-using amount is 2500 not Avl Limit`() {
        assertThat(SmsAmountExtractor.extract(SampleSms.ICICI_CARD_SPENT_USING)!!.amount)
            .isEqualTo(BigDecimal("2500.00"))
    }
}
