package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.InvestmentReturnLinker
import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SpendMath
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Motilal MF debit + ETMONEY "refund initiated" + anonymous IMPS credit:
 * broker ack ignored; bank legs pair-deleted → net zero.
 */
class InvestmentRefundRoundTripTest {

    private val parser = SmsParser()
    private val debitAt = Instant.parse("2026-08-03T10:00:00Z")
    private val creditAt = Instant.parse("2026-08-01T08:00:00Z")

    @Test
    fun `ETMONEY refund initiated is confirmation-only ignored`() {
        assertThat(parser.isTransactional(SampleSms.ETMONEY_REFUND_INITIATED_40000)).isFalse()
        assertThat(
            parser.parse(RawSms("VM-ETMONY", SampleSms.ETMONEY_REFUND_INITIATED_40000, debitAt)),
        ).isNull()
    }

    @Test
    fun `Motilal Oswal MF UPI debit is Investment not Transfer or spend`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.MOTILAL_OSWAL_MF_UPI_DEBIT_40000, debitAt),
        )!!
        assertThat(debit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(debit.category).isEqualTo(Categories.INVESTMENT)
        assertThat(debit.merchant).isEqualTo("Motilal Oswal MF")
        assertThat(debit.referenceNumber).isEqualTo("479986606620")
        assertThat(debit.amount.amount).isEqualTo(BigDecimal("40000.00"))
        assertThat(LedgerBuckets.isInvestment(debit)).isTrue()
        assertThat(LedgerBuckets.isSpend(debit)).isFalse()
        assertThat(LedgerBuckets.isTransfer(debit)).isFalse()
    }

    @Test
    fun `anonymous IMPS credit is Transfer not income and extracts IMPS ref`() {
        val credit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.ICICI_IMPS_MOBILE_CREDIT_40000, creditAt),
        )!!
        assertThat(credit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.referenceNumber).isEqualTo("621321435842")
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()
        assertThat(credit.merchant).isNull()
    }

    @Test
    fun `Motilal debit plus IMPS return pair-delete to net zero`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.MOTILAL_OSWAL_MF_UPI_DEBIT_40000, debitAt),
        )!!.copy(id = 1)
        val credit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.ICICI_IMPS_MOBILE_CREDIT_40000, creditAt),
        )!!.copy(id = 2)

        // Before reconcile: investment shows the debit (credit is Transfer).
        assertThat(LedgerBuckets.investments(listOf(debit, credit)).amount)
            .isEqualTo(BigDecimal("40000.00"))
        assertThat(LedgerBuckets.spend(listOf(debit, credit))).isEqualTo(Money.ZERO)
        assertThat(LedgerBuckets.income(listOf(debit, credit))).isEqualTo(Money.ZERO)

        val pairs = InvestmentReturnLinker.findPairs(listOf(debit, credit))
        assertThat(pairs).hasSize(1)
        assertThat(InvestmentReturnLinker.idsToRemove(pairs)).containsExactly(1L, 2L)

        // After pair-delete: empty ledger → all zeros.
        assertThat(LedgerBuckets.investments(emptyList())).isEqualTo(Money.ZERO)
        assertThat(
            SpendMath.netCashFlow(Money.ZERO, Money.ZERO, Money.ZERO),
        ).isEqualTo(Money.ZERO)
    }

    @Test
    fun `unrelated same-amount IMPS with a named counterparty is not pair-deleted`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.MOTILAL_OSWAL_MF_UPI_DEBIT_40000, debitAt),
        )!!.copy(id = 1)
        val friendCreditBody =
            "ICICI Bank Account XX293 is credited with Rs 40,000.00 on 01-Aug-26 by RAHUL SHARMA. IMPS Ref. no. 999888777666."
        val credit = parser.parse(RawSms("AX-ICICIB", friendCreditBody, creditAt))!!.copy(id = 2)

        assertThat(InvestmentReturnLinker.findPairs(listOf(debit, credit))).isEmpty()
    }
}
