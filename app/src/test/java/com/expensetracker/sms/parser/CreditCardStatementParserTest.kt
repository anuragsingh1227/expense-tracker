package com.expensetracker.sms.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CreditCardStatementParserTest {

    private val now = Instant.parse("2026-08-14T04:30:00Z")
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun raw(body: String, sender: String = "VM-HDFCBK") = RawSms(sender, body, now)

    @Test
    fun `HDFC statement with total due min due and due date is parsed`() {
        val stmt = CreditCardStatementParser.parse(
            raw(
                "HDFC Bank: Your Credit Card ending 4321 statement. Total Due Rs. 12,500.00. Min Due Rs. 625.00. Due Date 20-Aug-26.",
            ),
            zone,
        )!!
        assertThat(stmt.totalDue?.amount).isEqualTo(BigDecimal("12500.00"))
        assertThat(stmt.minDue?.amount).isEqualTo(BigDecimal("625.00"))
        assertThat(stmt.dueDate).isEqualTo(LocalDate.of(2026, 8, 20))
        assertThat(stmt.cardLast4).isEqualTo("4321")
        assertThat(stmt.bank).isEqualTo("HDFC")
    }

    @Test
    fun `sample due-date reminder SMS is a statement not a transaction`() {
        val stmt = CreditCardStatementParser.parse(raw(SampleSms.DUE_REMINDER_SPAM), zone)!!
        assertThat(stmt.minDue?.amount).isEqualTo(BigDecimal("1250.00"))
        assertThat(stmt.dueDate).isEqualTo(LocalDate.of(2024, 8, 12))
        assertThat(SmsParser().parse(raw(SampleSms.DUE_REMINDER_SPAM))).isNull()
    }

    @Test
    fun `ICICI total-amount-is-due notice is a statement`() {
        val stmt = CreditCardStatementParser.parse(
            raw(SampleSms.ICICI_AMOUNT_DUE_NOTICE, "AD-ICICIT-S"),
            zone,
        )!!
        assertThat(stmt.totalDue?.amount).isEqualTo(BigDecimal("34757.45"))
        assertThat(stmt.cardLast4).isEqualTo("1014")
        assertThat(stmt.dueDate).isEqualTo(LocalDate.of(2026, 8, 2))
    }

    @Test
    fun `electricity bill is not a card statement`() {
        assertThat(
            CreditCardStatementParser.parse(
                raw("Reminder: Your electricity bill of Rs. 1200 is due on 20th Aug."),
            ),
        ).isNull()
    }

    @Test
    fun `limit-increase promo is not a card statement`() {
        assertThat(
            CreditCardStatementParser.parse(
                raw("Your credit card limit has been increased to Rs. 2,00,000. Apply now."),
            ),
        ).isNull()
    }
}
