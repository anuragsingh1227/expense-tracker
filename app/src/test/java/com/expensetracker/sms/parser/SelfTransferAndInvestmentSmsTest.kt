package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * Regression coverage for the Aug 2026 SMS dump that inflated Month spend to
 * ₹5,05,602.10 (self-transfers, Scripbox/Motilal as spend, Airtel receipts as income).
 */
class SelfTransferAndInvestmentSmsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val fallback = Instant.parse("2026-08-14T03:00:00Z")
    private val parser = SmsParser(zone = zone, ownerNames = { listOf("Anurag") })

    private fun parse(body: String, sender: String = "VM-BANK") =
        parser.parse(RawSms(sender, body, fallback))

    @Test
    fun `ICICI UPI debit to owner name is Transfer not spend`() {
        val tx = parse(SampleSms.ICICI_UPI_SELF_TRANSFER_ANURAG, "AD-ICICIB")!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("60000.00"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.referenceNumber).isEqualTo("024744670304")
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `Axis UPI P2A credit extracts shared reference for pairing`() {
        val credit = parse(SampleSms.AXIS_UPI_P2A_CREDIT_ANURAG, "VM-AXISBK")!!
        assertThat(credit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.referenceNumber).isEqualTo("024744670304")
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()
    }

    @Test
    fun `UPI self-transfer debit and credit pair on shared reference`() {
        val debit = parse(SampleSms.ICICI_UPI_SELF_TRANSFER_ANURAG, "AD-ICICIB")!!.copy(id = 1)
        val credit = parse(SampleSms.AXIS_UPI_P2A_CREDIT_ANURAG, "VM-AXISBK")!!.copy(id = 2)
        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = listOf("Anurag"))
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `without owner name UPI self-transfer debit is not forced Transfer`() {
        val noOwner = SmsParser(zone = zone, ownerNames = { emptyList() })
        val tx = noOwner.parse(
            RawSms("AD-ICICIB", SampleSms.ICICI_UPI_SELF_TRANSFER_ANURAG, fallback),
        )!!
        // Still a debit; category stays Others unless a merchant/heuristic matches.
        assertThat(tx.category).isNotEqualTo(Categories.TRANSFER)
    }

    @Test
    fun `Motilal Oswal MF UPI is Investment not spend`() {
        val tx = parse(SampleSms.ICICI_UPI_MOTILAL_MF, "AD-ICICIB")!!
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.merchant).isEqualTo("Motilal Oswal")
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `Scripbox ACH debit is Investment not spend`() {
        val tx = parse(SampleSms.AXIS_ACH_SCRIPBOX, "VM-AXISBK")!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("43200.00"))
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.merchant).isEqualTo("Scripbox")
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `card spend with EMI conversion footer is not EMI category`() {
        val tx = parse(SampleSms.ICICI_CARD_SPEND_EMI_FOOTER, "AD-ICICIT-S")!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("10924.20"))
        assertThat(tx.category).isNotEqualTo(Categories.EMI)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `Airtel payment receipt is ignored`() {
        assertThat(parser.isTransactional(SampleSms.AIRTEL_PAYMENT_RECEIPT)).isFalse()
        assertThat(parse(SampleSms.AIRTEL_PAYMENT_RECEIPT)).isNull()
    }

    @Test
    fun `Scripbox withdrawal instruction is ignored`() {
        assertThat(parse(SampleSms.SCRIPBOX_WITHDRAWAL_INSTRUCTION)).isNull()
    }

    @Test
    fun `IPRUMF AMC purchase confirmation is ignored to avoid double-counting investment`() {
        assertThat(parse(SampleSms.IPRUMF_PURCHASE_CONFIRMATION)).isNull()
    }

    @Test
    fun `Swiggy refund initiated notice is ignored until bank credit arrives`() {
        assertThat(parse(SampleSms.SWIGGY_REFUND_INITIATED)).isNull()
    }

    @Test
    fun `owner-name UPI transfer works with transferred wording`() {
        // Keep the existing NEFT-style owner-name path covered alongside UPI.
        val body =
            "Rs 2500.00 transferred to ANURAG SINGH on 15-06-26. UPI Ref 401234567890"
        val tx = parse(body, "VM-HDFCBK")!!
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
    }
}
