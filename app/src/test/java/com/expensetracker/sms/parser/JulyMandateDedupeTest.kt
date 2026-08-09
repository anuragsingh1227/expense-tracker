package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.MandateDuplicateLinker
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/** User-reported Jul-26 batch: self-transfer + NACH retries + returned mandate. */
class JulyMandateDedupeTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val parser = SmsParser(zone = zone, ownerNames = { listOf("Anurag", "Anur") })
    private val t = Instant.parse("2026-07-01T12:00:00Z")

    private fun parseAll(bodies: List<Pair<String, String>>): List<Transaction> {
        var id = 1L
        val byHash = linkedMapOf<String, Transaction>()
        for ((sender, body) in bodies) {
            val tx = parser.parse(RawSms(sender, body, t)) ?: continue
            if (tx.dedupeHash in byHash) continue
            byHash[tx.dedupeHash] = tx.copy(id = id++)
        }
        return byHash.values.toList()
    }

    @Test
    fun `NACH returned is ignored — not an investment debit`() {
        val body =
            "NACH debit towards Groww Pay Services P for INR 2,000.00 with UMRN UTIB7010111210003840 has been returned today - Axis Bank"
        assertThat(parser.isTransactional(body)).isFalse()
        assertThat(parser.parse(RawSms("VM-AXISBK", body, t))).isNull()
    }

    @Test
    fun `identical Scripbox NACH UMRN retries collapse to one Investment`() {
        val body =
            "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 49,200.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank"
        val rows = parseAll(listOf(body, body, body).map { "VM-AXISBK" to it })
        assertThat(rows).hasSize(1)
        assertThat(rows.single().category).isEqualTo(Categories.INVESTMENT)
        assertThat(rows.single().amount.amount).isEqualTo(BigDecimal("49200.00"))
    }

    @Test
    fun `Groww 10k NACH twins collapse and returned 2k never books`() {
        val bodies = listOf(
            "VM-AXISBK" to
                "NACH debit towards Groww Pay Services P for INR 2,000.00 with UMRN UTIB7010111210003840 has been returned today - Axis Bank",
            "VM-AXISBK" to
                "NACH debit towards Groww Pay Services P for INR 2,000.00 with UMRN UTIB7010111210003840 has been returned today - Axis Bank",
            "VM-AXISBK" to
                "NACH debit towards Groww Pay Services P for INR 10,000.00 with UMRN UTIB7010111210003840 has been successfully processed in A/c no. XX8291 today - Axis Bank",
            "VM-AXISBK" to
                "NACH debit towards Groww Pay Services P for INR 10,000.00 with UMRN UTIB7010111210003840 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        )
        val rows = parseAll(bodies)
        assertThat(rows).hasSize(1)
        assertThat(rows.single().amount.amount).isEqualTo(BigDecimal("10000.00"))
        assertThat(LedgerBuckets.investments(rows).amount).isEqualTo(BigDecimal("10000.00"))
    }

    @Test
    fun `ICICI 90k NEFT debit and Axis ANUR credit pair-delete as self transfer`() {
        val debit =
            "ICICI Bank Acc XX293 debited Rs. 90,000.00 on 01-Jul-26 InfoBIL*NEFT*IN12.Avl Bal Rs. 7,31,586.64.To dispute call 18002662 or SMS BLOCK 293 to 9215676766"
        val credit =
            "INR 90000.00 credited to A/c no. XX8291 on 01-07-26 at 10:01:21 IST. Info - NEFT/IN12618244087080/ANUR. Chk Bal https://ccm.axis.bank.in/AXISBK/ltt3Dvko - Axis Bank"
        val rows = parseAll(
            listOf(
                "AX-ICICIB" to debit,
                "VM-AXISBK" to credit,
            ),
        )
        assertThat(rows).hasSize(2)
        assertThat(rows.all { it.category == Categories.TRANSFER }).isTrue()
        assertThat(LedgerBuckets.spend(rows)).isEqualTo(Money.ZERO)
        assertThat(LedgerBuckets.income(rows)).isEqualTo(Money.ZERO)

        // Truncated ICICI UTR prefix matches full Axis NEFT UTR.
        assertThat(SelfTransferLinker.referencesMatch("IN12", "IN12618244087080")).isTrue()

        val pairs = SelfTransferLinker.findPairs(rows, ownerNames = listOf("Anur", "Anurag"))
        assertThat(pairs).hasSize(1)
        assertThat(SelfTransferLinker.idsToRemove(pairs)).containsExactly(1L, 2L)
    }

    @Test
    fun `Scripbox ACH-DR amounts are distinct investments not duplicates of NACH 49200`() {
        val bodies = listOf(
            "VM-AXISBK" to
                "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 49,200.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
            "VM-AXISBK" to
                "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 49,200.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
            "VM-AXISBK" to
                """
Debit INR 20000.00
Axis Bank A/c XX8291
01-07-26 08:31:06
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
            "VM-AXISBK" to
                """
Debit INR 8800.00
Axis Bank A/c XX8291
01-07-26 08:31:06
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        )
        val rows = parseAll(bodies)
        val afterMandate = rows.filter {
            it.id !in MandateDuplicateLinker.idsToRemove(rows, zone).toSet()
        }
        // 49200 (once) + 20000 + 8800 — different amounts, not cross-rail twins.
        assertThat(afterMandate.map { it.amount.amount }.sorted()).containsExactly(
            BigDecimal("8800.00"),
            BigDecimal("20000.00"),
            BigDecimal("49200.00"),
        )
        assertThat(LedgerBuckets.investments(afterMandate).amount)
            .isEqualTo(BigDecimal("78000.00"))
    }
}
