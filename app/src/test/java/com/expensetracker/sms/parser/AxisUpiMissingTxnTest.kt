package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real Axis compact UPI debit that was missing from the ledger because the gate
 * only recognized "DEBITED INR" / "DEBITED FROM", not "INR 2500.00 debited".
 */
class AxisUpiMissingTxnTest {

    private val parser = SmsParser(zone = ZoneId.of("Asia/Kolkata"))
    private val fallback = Instant.parse("2026-08-08T09:16:26Z")

    private val body = """
INR 2500.00 debited
A/c no. XX8291
08-08-26, 14:46:26
UPI/P2A/111991242206/LALAWMPUII
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent()

    @Test
    fun `Axis INR-amount-then-debited UPI P2A is kept in the ledger`() {
        assertThat(TransactionGate.isTransactional(body)).isTrue()

        val tx = parser.parse(RawSms("VM-AXISBK", body, fallback))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2500.00"))
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("111991242206")
        assertThat(tx.merchant?.uppercase()).contains("LALAWMPUII")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LedgerBuckets.isTransfer(tx)).isFalse()
        assertThat(LocalDate.ofInstant(tx.timestamp, ZoneId.of("Asia/Kolkata")))
            .isEqualTo(LocalDate.of(2026, 8, 8))
    }

    @Test
    fun `Rs-amount-then-debited without from-connector is also accepted`() {
        val compact = "Rs 999.00 debited\nA/c XX1234\n01-08-26\nUPI/P2M/999888777666/SWIGGY"
        assertThat(TransactionGate.isTransactional(compact)).isTrue()
        val tx = parser.parse(RawSms("VM-AXISBK", compact, fallback))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("999.00"))
        assertThat(tx.merchant?.uppercase()).contains("SWIGGY")
    }
}
