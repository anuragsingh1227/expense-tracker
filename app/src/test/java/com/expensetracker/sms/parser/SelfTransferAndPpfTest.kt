package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class SelfTransferAndPpfTest {

    private val now = Instant.parse("2026-08-01T02:00:00Z")
    private val parser = SmsParser(ownerNames = { listOf("Anurag") })

    @Test
    fun `100000 UPI debit and credit are Transfer with shared ref and pair-deleted`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, now),
        )!!.copy(id = 1)
        val credit = parser.parse(
            RawSms(
                "AX-AXISBK-S",
                SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
                now.plusSeconds(60),
            ),
        )!!.copy(id = 2)

        assertThat(debit.type).isEqualTo(TransactionType.DEBIT)
        assertThat(debit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(debit.referenceNumber).isEqualTo("781919319954")
        assertThat(credit.referenceNumber).isEqualTo("781919319954")
        assertThat(LedgerBuckets.isSpend(debit)).isFalse()
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()

        val pairs = SelfTransferLinker.findPairs(
            listOf(debit, credit),
            ownerNames = listOf("Anurag"),
        )
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `60000 Axis UPI credit with owner in path is Transfer and extracts ref`() {
        val credit = parser.parse(
            RawSms("AX-AXISBK-S", SampleSms.OWN_ACCOUNT_UPI_CREDIT_60000, now),
        )!!
        assertThat(credit.type).isEqualTo(TransactionType.CREDIT)
        assertThat(credit.category).isEqualTo(Categories.TRANSFER)
        assertThat(credit.referenceNumber).isEqualTo("024744670304")
        assertThat(LedgerBuckets.isIncome(credit)).isFalse()
    }

    @Test
    fun `60000 self-transfer pairs from raw SMS even when stored refs are blank`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.OWN_ACCOUNT_UPI_DEBIT_60000, now),
        )!!.copy(id = 21, referenceNumber = null, category = Categories.OTHERS)
        val credit = parser.parse(
            RawSms("AX-AXISBK-S", SampleSms.OWN_ACCOUNT_UPI_CREDIT_60000, now.plusSeconds(90)),
        )!!.copy(id = 22, referenceNumber = null)

        assertThat(SelfTransferLinker.extractReferenceFromRaw(debit.rawSms))
            .isEqualTo("024744670304")
        assertThat(SelfTransferLinker.extractReferenceFromRaw(credit.rawSms))
            .isEqualTo("024744670304")

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(21L, 22L)
    }

    @Test
    fun `shared UPI ref pairs even when an older parse miscategorized the debit`() {
        val debit = parser.parse(
            RawSms("AX-ICICIB", SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000, now),
        )!!.copy(id = 11, category = Categories.OTHERS)
        val credit = parser.parse(
            RawSms("AX-AXISBK-S", SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000, now.plusSeconds(60)),
        )!!.copy(id = 12)

        val pairs = SelfTransferLinker.findPairs(listOf(debit, credit), ownerNames = emptyList())
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(11L, 12L)
    }

    @Test
    fun `PPF SI deposit is Investment debit not Transfer or spend`() {
        val tx = parser.parse(
            RawSms("AX-IDBIBK-S", SampleSms.IDBI_PPF_SI_CREDIT, Instant.parse("2026-08-07T04:00:00Z")),
        )!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.merchant).isEqualTo("PPF")
        assertThat(tx.bank).isEqualTo("IDBI")
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
        assertThat(LedgerBuckets.isInvestment(tx)).isTrue()
        assertThat(tx.amount.amount.toPlainString()).isEqualTo("12000.00")
    }
}
