package com.expensetracker.data.repository

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.Money
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.RawSms
import com.expensetracker.sms.parser.SampleSms
import com.expensetracker.sms.parser.SmsParser
import com.expensetracker.sms.parser.TransactionGate
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * QA scenarios: Clean-spam safety, confirmation purge, aggregate invariants,
 * malformed gate rejects, and idempotent parse hashes.
 */
class PurgeAndIngestInvariantsTest {

    private val parser = SmsParser()
    private val t = Instant.parse("2026-08-12T10:00:00Z")

    @Test
    fun `purge never deletes manual rows with blank rawSms`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = null,
                manuallyEdited = true,
                parser = parser,
            ),
        ).isFalse()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = "",
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `purge never deletes manually edited SMS rows`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = SampleSms.PPF_CONTRIBUTION_RECEIVED_ACK,
                manuallyEdited = true,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `purge removes confirmation-only investment and card payment acks`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.PPF_CONTRIBUTION_RECEIVED_ACK,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.NPS_CONTRIBUTION_RECEIVED_ACK,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.SBI_CARD_PAYMENT_RECEIVED_AGAINST,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.CARD_AUTODEBIT_THANK_YOU_DUPLICATE,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
    }

    @Test
    fun `purge keeps real spend investment SI and self-transfer credits`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.HDFC_ZOMATO_CARD_SPEND,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.IDBI_PPF_SI_CREDIT,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.AXIS_SELF_TRANSFER_CREDIT,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                SampleSms.HDFC_DEBIT,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `malformed and non-movement payloads are gate-rejected`() {
        val rejects = listOf(
            SampleSms.OTP_MESSAGE,
            SampleSms.PROMOTIONAL,
            SampleSms.DUE_REMINDER_SPAM,
            SampleSms.ICICI_CREDIT_LIMIT_RAISE,
            SampleSms.BALANCE_ONLY,
            "Hi",
            "Rs 100",
        )
        rejects.forEach { body ->
            assertThat(TransactionGate.isTransactional(body)).isFalse()
            assertThat(parser.parse(RawSms("VM-BANK", body, t))).isNull()
        }
    }

    @Test
    fun `mixed batch ledger totals exclude transfers refunds confirmations and investments from spend`() {
        val bodies = listOf(
            SampleSms.HDFC_ZOMATO_CARD_SPEND, // 549 spend
            SampleSms.PPF_CONTRIBUTION_RECEIVED_ACK, // ignore
            SampleSms.SBI_CARD_PAYMENT_RECEIVED_AGAINST, // ignore
            SampleSms.AXIS_SELF_TRANSFER_CREDIT, // transfer credit
            SampleSms.IDBI_PPF_SI_CREDIT, // 12000 investment
            SampleSms.AMAZON_REFUND_CREDITED, // 1499 refund
            SampleSms.HDFC_DEBIT, // 245 spend
        )
        val txs = bodies.mapNotNull { parser.parse(RawSms("VM-BANK", it, t)) }

        // Confirmations dropped; 5 kept: zomato, self-transfer, ppf si, refund, hdfc debit
        assertThat(txs).hasSize(5)

        val spend = LedgerBuckets.spend(txs)
        // 549 + 245 - 1499 = -705
        assertThat(spend.amount).isEqualTo(BigDecimal("-705.00"))
        assertThat(LedgerBuckets.investments(txs).amount).isEqualTo(BigDecimal("12000.00"))
        assertThat(LedgerBuckets.income(txs).amount).isEqualTo(BigDecimal.ZERO.setScale(2))
        assertThat(txs.count(LedgerBuckets::isTransfer)).isEqualTo(1)
        assertThat(txs.count(LedgerBuckets::isRefund)).isEqualTo(1)
    }

    @Test
    fun `identical SMS parse produces identical dedupe hash for idempotent insert`() {
        val a = parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_DEBIT, t))!!
        val b = parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_DEBIT, t))!!
        assertThat(a.dedupeHash).isEqualTo(b.dedupeHash)
        assertThat(a.dedupeHash).isNotEmpty()
    }

    @Test
    fun `spend amount is Money scale-2 never float drift in batch sum`() {
        val txs = listOf(
            parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_ZOMATO_CARD_SPEND, t))!!,
            parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_DEBIT, t))!!,
        )
        val spend = LedgerBuckets.spend(txs)
        assertThat(spend).isEqualTo(Money.ofRupees("794.00"))
        assertThat(spend.amount.scale()).isEqualTo(2)
    }
}
