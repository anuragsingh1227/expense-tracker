package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.InvestmentReturnLinker
import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.MandateDuplicateLinker
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.insights.SpendMath
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * CA-style audit of the user-reported August 2026 SMS set against dashboard bugs:
 * double EMI, inflated Investment, ₹0 income quirks, merchant/AMC acks.
 */
class AugustLedgerAuditTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val parser = SmsParser(zone = zone, ownerNames = { listOf("Anurag", "Anur") })
    private val t = Instant.parse("2026-08-09T12:00:00Z")

    private fun parseAll(bodies: List<String>): List<Transaction> {
        var id = 1L
        val byHash = linkedMapOf<String, Transaction>()
        for (body in bodies) {
            val tx = parser.parse(RawSms("VM-AXISBK", body, t)) ?: continue
            // Simulate Room IGNORE on dedupeHash.
            if (tx.dedupeHash in byHash) continue
            byHash[tx.dedupeHash] = tx.copy(id = id++)
        }
        return byHash.values.toList()
    }

    private fun reconcile(rows: List<Transaction>): List<Transaction> {
        val self = SelfTransferLinker.idsToRemove(
            SelfTransferLinker.findPairs(rows, listOf("Anurag", "Anur")),
        ).toSet()
        var left = rows.filter { it.id !in self }
        val invest = InvestmentReturnLinker.idsToRemove(InvestmentReturnLinker.findPairs(left)).toSet()
        left = left.filter { it.id !in invest }
        val mandate = MandateDuplicateLinker.idsToRemove(left, zone).toSet()
        return left.filter { it.id !in mandate }
    }

    @Test
    fun `ignore broker AMC and merchant acknowledgement SMS`() {
        val ignore = listOf(
            "Your withdrawal instruction of Rs 52036.55 has been successfully processed.-Team Scripbox",
            "You have placed a withdrawal request for Rs 51179.97 on Aug 3, 2026.-Team Scripbox",
            "Dear Investor, Your SIP Purchase of Rs.2,799.86 in Folio 44796240 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 25.154 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
            "Dear Investor, Your SIP Purchase of Rs.6,599.67 in Folio 44796233 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 59.291 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
            "Dear Customer, we have received online payment of Rs. 1000 .Thank you. Doodhvale.com",
            "Dear Customer, we have received online payment of Rs. 500 .Thank you. Doodhvale.com",
        )
        for (body in ignore) {
            assertThat(parser.isTransactional(body)).isFalse()
            assertThat(parser.parse(RawSms("VM-ALERT", body, t))).isNull()
        }
    }

    @Test
    fun `HDFC EMI is not double counted from NACH plus ACH-DR`() {
        val bodies = listOf(
            "NACH debit towards HDFC BANK LTD for INR 27,136.00 with UMRN UTIB7022807210000967 has been successfully processed in A/c no. XX8291 today - Axis Bank",
            """
Debit INR 27136.00
Axis Bank A/c XX8291
05-08-26 09:40:02
ACH-DR-HDFC BANK LTD-47138
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        )
        val kept = reconcile(parseAll(bodies))
        val emi = kept.filter { it.category == Categories.EMI }
        assertThat(emi).hasSize(1)
        assertThat(emi.single().amount.amount).isEqualTo(BigDecimal("27136.00"))
        assertThat(LedgerBuckets.spend(kept).amount).isEqualTo(BigDecimal("27136.00"))
    }

    @Test
    fun `identical NACH UMRN retries collapse via dedupe hash`() {
        val body =
            "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank"
        val bodies = listOf(body, body, body, body)
        val parsed = parseAll(bodies)
        assertThat(parsed).hasSize(1)
        assertThat(parsed.single().category).isEqualTo(Categories.INVESTMENT)
    }

    @Test
    fun `Scripbox NACH plus ACH-DR same amount collapses to one Investment`() {
        val bodies = listOf(
            "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
            """
Debit INR 1300.00
Axis Bank A/c XX8291
01-08-26 08:19:18
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        )
        val kept = reconcile(parseAll(bodies))
        assertThat(kept).hasSize(1)
        assertThat(kept.single().category).isEqualTo(Categories.INVESTMENT)
        assertThat(LedgerBuckets.investments(kept).amount).isEqualTo(BigDecimal("1300.00"))
    }

    @Test
    fun `RACPC ACH collect is EMI spend with account last4`() {
        val body =
            "ICICI Bank Acc XX293 debited Rs. 24,911.00 on 05-Aug-26 InfoACH*RACPC CUM.Avl Bal Rs. 6,52,083.87.To dispute call 18002662 or SMS BLOCK 293 to 9215676766"
        val tx = parser.parse(RawSms("AX-ICICIB", body, t))!!
        assertThat(tx.category).isEqualTo(Categories.EMI)
        assertThat(tx.accountLast4).isEqualTo("293")
        assertThat(tx.paymentMode.name).isEqualTo("NET_BANKING")
        assertThat(tx.narration?.uppercase()).contains("RACPC")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `Axis IMPS to Anur plus IDBI credit pair-delete as self transfer`() {
        val bodies = listOf(
            """
Debit INR 15000.00
Axis Bank A/c XX8291
05-08-26 06:44:55
IMPS/P2A/621706928597/Anur
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
            "IDBI Bank A/c NN59024 credited for INR 15000.00 through Net Banking. Bal INR 44996.19 (incl. of chq in clg)  as of 05 AUG 06:44 hrs.",
        )
        val parsed = parseAll(bodies)
        assertThat(parsed).hasSize(2)
        assertThat(parsed.all { it.category == Categories.TRANSFER }).isTrue()
        val kept = reconcile(parsed)
        assertThat(kept).isEmpty()
    }

    @Test
    fun `August dashboard buckets are not inflated by known duplicate and ack bugs`() {
        val bodies = AugustSmsFixture.messages
        val kept = reconcile(parseAll(bodies))

        val spend = LedgerBuckets.spend(kept)
        val income = LedgerBuckets.income(kept)
        val invested = LedgerBuckets.investments(kept)
        val emi = kept.filter { it.category == Categories.EMI }
            .fold(Money.ZERO) { acc, tx -> acc + tx.amount }

        // EMI = RACPC 24911 + HDFC 27136 (once) — not 54272.
        assertThat(emi.amount).isEqualTo(BigDecimal("52047.00"))

        // No broker/AMC/merchant acks as investment; Scripbox/Groww not multiplied.
        // Unique invest: PPF 12000 + Motilal 40000 + Scripbox (1300+20000+49200) + Groww (2000+5000+10000)
        // = 139500.00 (NACH twins collapsed into ACH-DR amounts).
        assertThat(invested.amount).isEqualTo(BigDecimal("139500.00"))

        // IFT credit is Transfer (not salary wording) → income stays 0 unless categorized otherwise.
        assertThat(income).isEqualTo(Money.ZERO)

        // Spend must not include the phantom second EMI (27136).
        assertThat(spend.amount).isLessThan(BigDecimal("112752.27"))

        val net = SpendMath.netCashFlow(income, spend, invested)
        assertThat(net.amount).isEqualTo(income.amount - spend.amount - invested.amount)

        // Sanity: every kept debit is a bank movement, not an ignored ack.
        assertThat(
            kept.none {
                it.rawSms.orEmpty().contains("Doodhvale", ignoreCase = true) ||
                    it.rawSms.orEmpty().contains("IPRUMF", ignoreCase = true) ||
                    it.rawSms.orEmpty().contains("withdrawal instruction", ignoreCase = true)
            },
        ).isTrue()
    }
}

/** Subset of the user-reported August SMS corpus used for ledger audit. */
object AugustSmsFixture {
    val messages: List<String> = listOf(
        "ICICI Bank Credit Card XX7002 debited for INR 153.81 on 09-Aug-26 for UPI-543001850491-Airtel. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 153.81 on 09-Aug-26 for UPI-816827629215-Airtel. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "Your A/c has been debited towards JioHotstar for INR 699.00 on 09-08-26. 78b6bf5462eb43b8b3251d87b31a243e@pingpay - Axis Bank",
        "ICICI Bank Credit Card XX7002 debited for INR 1,763.00 on 09-Aug-26 for UPI-503547865861-ZOMATO. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        """
INR 60.00 debited
A/c no. XX8291
09-08-26, 07:11:03
UPI/P2M/273322632216/ANIL KUMAR RAM
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        """
INR 2500.00 debited
A/c no. XX8291
08-08-26, 14:46:26
UPI/P2A/111991242206/LALAWMPUII
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        "ICICI Bank Credit Card XX7002 debited for INR 375.00 on 08-Aug-26 for UPI-116377982206-Mukhiya. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 200.00 on 08-Aug-26 for UPI-974210232206-ARVIND. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        """
Debit INR 10000.00
Axis Bank A/c XX8291
08-08-26 06:27:28
NEFT/MB/AXOMB22002006227/M
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
INR 600.00 debited
A/c no. XX8291
07-08-26, 20:52:58
UPI/P2A/658501968150/SANJAY
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        """
Spent INR 478
Axis Bank Card no. XX0887
07-08-26 19:34:53 IST
SWIGGY FOOD
Avl Limit: INR 195982.46
Not you? SMS BLOCK 0887 to 919951860002
""".trimIndent(),
        "ICICI Bank Credit Card XX7002 debited for INR 574.35 on 07-Aug-26 for UPI-847888862196-Dominos. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 221.00 on 07-Aug-26 for UPI-938563051193-Blinkit. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "SI Transaction of Rs 12000 successfully credited in PPF Ac No ****************0079 on 07/08/2026 -IDBI Bank",
        "Your A/c has been debited towards NETFLIX for INR 199.00 on 07-08-26. 2e6ba27316c24809a972e8b6eda26ff2@ybl - Axis Bank",
        "NACH debit towards HDFC BANK LTD for INR 27,136.00 with UMRN UTIB7022807210000967 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "ICICI Bank Credit Card XX7002 debited for INR 240.00 on 05-Aug-26 for UPI-356887062176-Amigos. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Acc XX293 debited Rs. 24,911.00 on 05-Aug-26 InfoACH*RACPC CUM.Avl Bal Rs. 6,52,083.87.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        "INR 52036.03 credited to A/c no. XX8291 on 05-08-26 at 10:01:40 IST. Info - IFT/CB0144408165/202608054. Chk Bal https://ccm.axis.bank.in/AXISBK/ltt3Dvko - Axis Bank",
        """
INR 19399.00 debited
A/c no. XX8291
05-08-26, 10:21:47
UPI/P2M/103800009359/FOOTPRINTS
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        """
Debit INR 27136.00
Axis Bank A/c XX8291
05-08-26 09:40:02
ACH-DR-HDFC BANK LTD-47138
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        "IDBI Bank A/c NN59024 credited for INR 15000.00 through Net Banking. Bal INR 44996.19 (incl. of chq in clg)  as of 05 AUG 06:44 hrs.",
        """
Debit INR 15000.00
Axis Bank A/c XX8291
05-08-26 06:44:55
IMPS/P2A/621706928597/Anur
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
INR 90.00 debited
A/c no. XX8291
04-08-26, 13:54:39
UPI/P2M/999811792166/NITIN KUMAR
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        "Your withdrawal instruction of Rs 52036.55 has been successfully processed.-Team Scripbox",
        "Dear Investor, Your SIP Purchase of Rs.2,799.86 in Folio 44796240 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 25.154 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
        "Dear Investor, Your SIP Purchase of Rs.6,599.67 in Folio 44796233 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 59.291 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
        "ICICI Bank Acct XX293 debited for Rs 40000.00 on 03-Aug-26; MotilalOswalMF credited. UPI:479986606620. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "You have placed a withdrawal request for Rs 51179.97 on Aug 3, 2026.-Team Scripbox",
        "Dear Customer, we have received online payment of Rs. 1000 .Thank you. Doodhvale.com",
        """
INR 1000.00 debited
A/c no. XX8291
03-08-26, 07:53:39
UPI/P2M/359597423615/SANJEEVANI DAIRY FA
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent(),
        "Dear Customer, we have received online payment of Rs. 500 .Thank you. Doodhvale.com",
        "ICICI Bank Credit Card XX7002 debited for INR 100.00 on 02-Aug-26 for UPI-618779192146-STYLE. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 267.00 on 02-Aug-26 for UPI-495273822147-BLINKIT. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 250.00 on 02-Aug-26 for UPI-539522962146-TITU. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Acc XX293 debited Rs. 34,757.45 on 02-Aug-26 InfoATD*Auto Debi.Avl Bal Rs. 7,76,994.87.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        "Dear Customer, Acct XX293 is credited with Rs 0.01 on 01-Aug-26 from INDSTOCKS PRIVA. UPI:103757250964-ICICI Bank.",
        "ICICI Bank Credit Card XX7002 debited for INR 1,734.30 on 01-Aug-26 for UPI-365480902136-AIRTEL. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 1,122.00 on 01-Aug-26 for UPI-384585377782-Zomato. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 360.00 on 01-Aug-26 for UPI-317055874499-Airtel. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 1,030.00 on 01-Aug-26 for UPI-326471572136-1 CINEMA. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "NACH debit towards SCRIPBOXWEALTHMANAGE for INR 1,300.00 with UMRN UTIB7010806200000399 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "NACH debit towards Groww Pay Services P for INR 5,000.00 with UMRN UTIB7010111210003840 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        "NACH debit towards Groww Pay Services P for INR 5,000.00 with UMRN UTIB7010111210003840 has been successfully processed in A/c no. XX8291 today - Axis Bank",
        """
Debit INR 1300.00
Axis Bank A/c XX8291
01-08-26 08:19:18
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
Debit INR 20000.00
Axis Bank A/c XX8291
01-08-26 08:19:16
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
Debit INR 49200.00
Axis Bank A/c XX8291
01-08-26 08:19:14
ACH-DR-SCRIPBOXWEALTHMANAG
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
Debit INR 2000.00
Axis Bank A/c XX8291
01-08-26 07:28:10
ACH-DR-Groww Pay Services 
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
Debit INR 5000.00
Axis Bank A/c XX8291
01-08-26 07:28:06
ACH-DR-Groww Pay Services 
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
        """
Debit INR 10000.00
Axis Bank A/c XX8291
01-08-26 07:27:08
ACH-DR-Groww Pay Services 
WhatsApp BAL to 917036165000
Not You? SMS BLOCKALL CustID to 919951860002
""".trimIndent(),
    )
}
