package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.SampleSms
import com.expensetracker.sms.parser.SmsParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class SelfTransferLinkerTest {

    private val parser = SmsParser()
    private val t0 = Instant.parse("2026-06-15T15:13:26Z")
    /** Matches [SampleSms.OWN_ACCOUNT_UPI_*_100000] body dates (01-Aug-26). */
    private val t100k = Instant.parse("2026-08-01T02:18:42Z")

    private fun tx(
        id: Long,
        body: String,
        sender: String,
        at: Instant,
    ): Transaction {
        val parsed = parser.parse(
            com.expensetracker.sms.parser.RawSms(sender, body, at),
        )!!
        return parsed.copy(id = id)
    }

    @Test
    fun `pairs own-account NEFT debit and credit that mention ANURAG`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
        val credit = tx(
            2,
            SampleSms.OWN_ACCOUNT_NEFT_CREDIT_ANURAG,
            "AD-ICICIB",
            t0.plus(2, ChronoUnit.MINUTES),
        )

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = listOf("ANURAG"))
        assertThat(pairs).hasSize(1)
        assertThat(pairs[0].debit.id).isEqualTo(1)
        assertThat(pairs[0].credit.id).isEqualTo(2)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `does not pair when owner name is absent and reference differs`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
        // Different reference than the debit's AXOMB16602145999, and no owner name —
        // same amount + window alone must not be enough to pair (would delete an
        // unrelated same-amount transaction otherwise).
        val creditBody =
            "Credit INR 17383.00\nICICI Bank A/c XX293\n15-06-26 20:45:10\nNEFT/MB/HDFCB99999999999/V"
        val credit = tx(2, creditBody, "AD-ICICIB", t0.plus(2, ChronoUnit.MINUTES))

        assertThat(SelfTransferLinker.findPairs(listOf(debit, credit))).isEmpty()
    }

    @Test
    fun `pairs on shared reference alone even without an owner name`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
        // Same reference as the debit (AXOMB16602145999), no owner name mentioned —
        // a shared bank-assigned reference is authoritative on its own.
        val creditBody =
            "Credit INR 17383.00\nICICI Bank A/c XX293\n15-06-26 20:45:10\nNEFT/MB/AXOMB16602145999/V"
        val credit = tx(2, creditBody, "AD-ICICIB", t0.plus(2, ChronoUnit.MINUTES))

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `never pairs unrelated spend without a shared bank reference`() {
        // A real Food spend (Zomato) that happens to share amount/time/name with an
        // unrelated credit must never be swept up — no shared UPI/NEFT reference.
        val foodSpend = tx(1, SampleSms.ICICI_CARD_SPEND_WITH_DISPUTE, "AD-ICICIB", t0)
        assertThat(foodSpend.category).isNotEqualTo(Categories.TRANSFER)

        val creditBody =
            "Credit INR ${foodSpend.amount.amount}\nICICI Bank A/c XX293\n15-06-26 20:45:10\nNEFT ANURAG"
        val credit = tx(2, creditBody, "AD-ICICIB", foodSpend.timestamp.plus(1, ChronoUnit.HOURS))

        assertThat(
            SelfTransferLinker.findPairs(
                listOf(foodSpend, credit),
                ownerNames = listOf("ANURAG"),
            ),
        ).isEmpty()
    }

    @Test
    fun `100k ICICI-Axis UPI self-transfer both legs are Transfer and pair-deleted`() {
        val debit = tx(1, SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, "AX-ICICIB", t100k)
        val credit = tx(
            2,
            SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
            "AX-AXISBK",
            t100k.plus(1, ChronoUnit.MINUTES),
        )

        assertThat(debit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(debit.referenceNumber).isEqualTo("781919319954")
        assertThat(credit.referenceNumber).isEqualTo("781919319954")
        assertThat(LedgerBuckets.isSpend(debit)).isFalse()
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `100k pair still links when telephony Instant is months off body dates`() {
        // Regression: Axis compact credit stamps ("01-08-26, 07:48:42 IST") must be
        // parsed from the body; otherwise debit uses on-Aug-26 while credit keeps a
        // stale Instant and the 72h window misses the pair.
        val stale = Instant.parse("2026-01-01T00:00:00Z")
        val debit = tx(1, SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, "AX-ICICIB", stale)
        val credit = tx(2, SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000, "AX-AXISBK", stale)
        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `shared UPI ref pairs even when an older parse miscategorized the debit as spend`() {
        val debit = tx(1, SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, "AX-ICICIB", t100k)
            .copy(category = Categories.OTHERS)
        val credit = tx(
            2,
            SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
            "AX-AXISBK",
            t100k.plus(1, ChronoUnit.MINUTES),
        )

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `legacy blank referenceNumber still pairs using ref extracted from raw SMS`() {
        val debit = tx(1, SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, "AX-ICICIB", t100k)
            .copy(referenceNumber = null)
        val credit = tx(
            2,
            SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
            "AX-AXISBK",
            t100k.plus(1, ChronoUnit.MINUTES),
        ).copy(referenceNumber = null)

        assertThat(SelfTransferLinker.extractReferenceFromRaw(debit.rawSms))
            .isEqualTo("781919319954")
        assertThat(SelfTransferLinker.extractReferenceFromRaw(credit.rawSms))
            .isEqualTo("781919319954")

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `does not pair different amounts`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
        val creditBody =
            "Credit INR 100.00\nICICI Bank A/c XX293\n15-06-26 20:45:10\nNEFT ANURAG"
        val credit = tx(2, creditBody, "AD-ICICIB", t0.plus(2, ChronoUnit.MINUTES))

        assertThat(SelfTransferLinker.findPairs(listOf(debit, credit))).isEmpty()
    }

    @Test
    fun `skips manually edited rows`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
            .copy(manuallyEdited = true)
        val credit = tx(
            2,
            SampleSms.OWN_ACCOUNT_NEFT_CREDIT_ANURAG,
            "AD-ICICIB",
            t0.plus(2, ChronoUnit.MINUTES),
        )

        assertThat(SelfTransferLinker.findPairs(listOf(debit, credit))).isEmpty()
    }

    @Test
    fun `CRD-PMNT Axis debit is Transfer so it never counts as spend`() {
        val payment = tx(3, SampleSms.AXIS_DEBIT_INR_CRD_PMNT, "VM-AXISBK", t0)
        assertThat(payment.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(payment)).isFalse()
        assertThat(LedgerBuckets.spend(listOf(payment))).isEqualTo(Money.ZERO)
    }

    @Test
    fun `unpaired Axis NEFT debit is Transfer not spend but still visible`() {
        val debit = tx(1, SampleSms.AXIS_DEBIT_INR_NEFT, "VM-AXISBK", t0)
        assertThat(debit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(debit.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(debit)).isFalse()
        assertThat(debit.paymentMode).isEqualTo(PaymentMode.NET_BANKING)
    }
}
