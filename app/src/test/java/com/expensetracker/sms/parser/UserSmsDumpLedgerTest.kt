package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.Transaction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Full Aug-2026 user SMS dump: before the fix, Month spend matched the dashboard
 * ₹5,05,602.10 with self-transfers and investments booked as spend. After the fix,
 * those rows leave spend and land in Transfer / Investment (or are ignored).
 */
class UserSmsDumpLedgerTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val fallback = Instant.parse("2026-08-14T03:00:00Z")
    private val parser = SmsParser(zone = zone, ownerNames = { listOf("Anurag") })

    private val bodies = listOf(
        "ICICI Bank Acct XX293 debited for Rs 5000.00 on 12-Aug-26; ANURAG SINGH credited. UPI:512612969523. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "Rs 10,924.20 spent on ICICI Bank Card XX7002 on 12-Aug-26 at UPI-42064733520. Avl Lmt: Rs 2,05,414.17. To dispute, call 18002662/SMS BLOCK 7002 to 9215676766. To convert this txn to EMI give a missed call on 9924667667. Know more about EMI conversion at https://icici.co/ICICIT/j/9e2c0243",
        "Spent INR 311\nAxis Bank Card no. XX0887\n11-08-26 20:13:46 IST\nSWIGGY FOOD\nAvl Limit: INR 196149.46\nNot you? SMS BLOCK 0887 to 919951860002",
        "Dear Investor, Your Purchase of Rs.43,197.84 in Folio 44796240 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 392.137 units has been processed for NAV of 110.16 on 11-Aug-2026. Account statement will be sent to your registered email address within 2 working days- IPRUMF",
        "Hi, a payment of Rs. 17.08 is updated against your Airtel Postpaid 9716073383 . To download your payment receipt or know more about your connection, visit Airtel Thanks App https://www.airtel.in/5/trnxs",
        "Debit INR 7900.00\nAxis Bank A/c XX8291\n10-08-26 08:41:35\nACH-DR-SCRIPBOXWEALTHMANAG\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Debit INR 43200.00\nAxis Bank A/c XX8291\n10-08-26 08:41:31\nACH-DR-SCRIPBOXWEALTHMANAG\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "ICICI Bank Credit Card XX7002 debited for INR 153.81 on 09-Aug-26 for UPI-816827629215-Airtel. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "Your A/c has been debited towards JioHotstar for INR 699.00 on 09-08-26. 78b6bf5462eb43b8b3251d87b31a243e@pingpay - Axis Bank",
        "ICICI Bank Credit Card XX7002 debited for INR 1,763.00 on 09-Aug-26 for UPI-503547865861-ZOMATO. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "Hi Anurag Singh, we have received payment of Rs. 228.18 for your Airtel mobile 919716073383. To download the payment receipt, click https://digi-api.airtel.in/raas/fetch?key=abc .\nThis receipt will be available to download for 7 days.",
        "ICICI Bank Credit Card XX7002 debited for INR 375.00 on 08-Aug-26 for UPI-116377982206-Mukhiya. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "Your NEFT txn with Ref. No. AXOMB22002006227 for INR 10000.00 is credited to beneficiary Manju Singh, A/c no. XX5116 on 08-08-26 at 11:52:35 IST - Axis Bank",
        "ICICI Bank Credit Card XX7002 debited for INR 200.00 on 08-Aug-26 for UPI-974210232206-ARVIND. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "Debit INR 10000.00\nAxis Bank A/c XX8291\n08-08-26 06:27:28\nNEFT/MB/AXOMB22002006227/M\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Refund of Rs 478.0 has been initiated for Swiggy order 245081078136287. Updated balance should reflect in 4-7 days. https://r.swiggy.com/refunds",
        "Spent INR 478\nAxis Bank Card no. XX0887\n07-08-26 19:34:53 IST\nSWIGGY FOOD\nAvl Limit: INR 195982.46\nNot you? SMS BLOCK 0887 to 919951860002",
        "ICICI Bank Credit Card XX7002 debited for INR 574.35 on 07-Aug-26 for UPI-847888862196-Dominos. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 221.00 on 07-Aug-26 for UPI-938563051193-Blinkit. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "SI Transaction of Rs 12000 successfully credited in PPF Ac No ****************0079 on 07/08/2026 -IDBI Bank",
        "Your A/c has been debited towards NETFLIX for INR 199.00 on 07-08-26. 2e6ba27316c24809a972e8b6eda26ff2@ybl - Axis Bank",
        "ICICI Bank Credit Card XX7002 debited for INR 240.00 on 05-Aug-26 for UPI-356887062176-Amigos. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Acc XX293 debited Rs. 24,911.00 on 05-Aug-26 InfoACH*RACPC CUM.Avl Bal Rs. 6,52,083.87.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        "INR 52036.03 credited to A/c no. XX8291 on 05-08-26 at 10:01:40 IST. Info - IFT/CB0144408165/202608054. Chk Bal https://ccm.axis.bank.in/AXISBK/ltt3Dvko - Axis Bank",
        "Debit INR 27136.00\nAxis Bank A/c XX8291\n05-08-26 09:40:02\nACH-DR-HDFC BANK LTD-47138\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "IDBI Bank A/c NN59024 credited for INR 15000.00 through Net Banking. Bal INR 44996.19 (incl. of chq in clg)  as of 05 AUG 06:44 hrs.",
        "Debit INR 15000.00\nAxis Bank A/c XX8291\n05-08-26 06:44:55\nIMPS/P2A/621706928597/Anur\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "ICICI Bank Acct XX293 debited for Rs 60000.00 on 04-Aug-26; ANURAG SINGH credited. UPI:024744670304. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "Your withdrawal instruction of Rs 52036.55 has been successfully processed.-Team Scripbox",
        "INR 60000.00 credited\nA/c no. XX8291\n04-08-26, 07:32:27 IST\nUPI/P2A/024744670304/ANURAG SI/ICIC/Paym - Axis Bank",
        "Hi Anurag Singh, we have received payment of Rs. 45.64 for your Airtel mobile 919716073383. To download the payment receipt, click https://example.com/a .\nThis receipt will be available to download for 7 days.",
        "Dear Investor, Your SIP Purchase of Rs.2,799.86 in Folio 44796240 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 25.154 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
        "Dear Investor, Your SIP Purchase of Rs.6,599.67 in Folio 44796233 in Large Cap Fund (erstwhile Bluechip Fund) - Growth for 59.291 units has been processed for NAV of Rs.111.31 on 03-Aug-2026 - IPRUMF",
        "ICICI Bank Acct XX293 debited for Rs 40000.00 on 03-Aug-26; MotilalOswalMF credited. UPI:479986606620. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "You have placed a withdrawal request for Rs 51179.97 on Aug 3, 2026.-Team Scripbox",
        "Dear Customer, we have received online payment of Rs. 1000 .Thank you. Doodhvale.com",
        "Hi Anurag Singh, we have received payment of Rs. 45.64 for your Airtel mobile 919716073383. To download the payment receipt, click https://example.com/b .\nThis receipt will be available to download for 7 days.",
        "Dear Customer, we have received online payment of Rs. 500 .Thank you. Doodhvale.com",
        "ICICI Bank Credit Card XX7002 debited for INR 100.00 on 02-Aug-26 for UPI-618779192146-STYLE. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 267.00 on 02-Aug-26 for UPI-495273822147-BLINKIT. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 250.00 on 02-Aug-26 for UPI-539522962146-TITU. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Acc XX293 debited Rs. 34,757.45 on 02-Aug-26 InfoATD*Auto Debi.Avl Bal Rs. 7,76,994.87.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        "Dear ANURAG SINGH, refund of Rs. 40,000 against your Investment in Balanced+ Portfolio (Order Number 101-0240413-0013485) has been initiated. It may take 5-7 working days for same to get processed. - ETMONEY",
        "Dear Customer, Acct XX293 is credited with Rs 0.01 on 01-Aug-26 from INDSTOCKS PRIVA. UPI:103757250964-ICICI Bank.",
        "ICICI Bank Account XX293 is credited with Rs 40,000.00 on 01-Aug-26 by Account linked to mobile number XXXXX00000. IMPS Ref. no. 621321435842.",
        "ICICI Bank Acct XX293 debited for Rs 40000.00 on 01-Aug-26; ET Money credited. UPI:110340720940. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "ICICI Bank Credit Card XX7002 debited for INR 1,734.30 on 01-Aug-26 for UPI-365480902136-AIRTEL. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 1,122.00 on 01-Aug-26 for UPI-384585377782-Zomato. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 360.00 on 01-Aug-26 for UPI-317055874499-Airtel. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Credit Card XX7002 debited for INR 1,030.00 on 01-Aug-26 for UPI-326471572136-1 CINEMA. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        "ICICI Bank Acct XX293 debited for Rs 100000.00 on 01-Aug-26; ANURAG SINGH credited. UPI:781919319954. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        "Debit INR 49200.00\nAxis Bank A/c XX8291\n01-08-26 08:19:14\nACH-DR-SCRIPBOXWEALTHMANAG\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Debit INR 20000.00\nAxis Bank A/c XX8291\n01-08-26 08:19:16\nACH-DR-SCRIPBOXWEALTHMANAG\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Debit INR 1300.00\nAxis Bank A/c XX8291\n01-08-26 08:19:18\nACH-DR-SCRIPBOXWEALTHMANAG\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Debit INR 2000.00\nAxis Bank A/c XX8291\n01-08-26 07:28:10\nACH-DR-Groww Pay Services \nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "INR 100000.00 credited\nA/c no. XX8291\n01-08-26, 07:48:42 IST\nUPI/P2A/781919319954/ANURAG SI/ICIC/Paym - Axis Bank",
        "Debit INR 5000.00\nAxis Bank A/c XX8291\n01-08-26 07:28:06\nACH-DR-Groww Pay Services \nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        "Debit INR 10000.00\nAxis Bank A/c XX8291\n01-08-26 07:27:08\nACH-DR-Groww Pay Services \nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
    )

    @Test
    fun `aug dump no longer books self-transfers or investments as spend`() {
        var id = 1L
        val txs = bodies.mapNotNull { body ->
            parser.parse(RawSms("VM-BANK", body, fallback))?.copy(id = id++)
        }

        val augStart = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant()
        val sepStart = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant()
        val aug = txs.filter { !it.timestamp.isBefore(augStart) && it.timestamp.isBefore(sepStart) }

        val spend = LedgerBuckets.spend(aug)
        val invest = LedgerBuckets.investments(aug)
        val income = LedgerBuckets.income(aug)

        // Pre-fix dashboard: spend 505602.10 / invest 66399.53 / income 319.46
        // Post-fix: self-transfers + Scripbox/Motilal leave spend; receipts ignored.
        assertThat(spend.amount).isEqualTo(BigDecimal("73048.66"))
        assertThat(income.amount).isEqualTo(BigDecimal.ZERO.setScale(2))

        // Bank-side investments: Scripbox 121600 + Motilal 40000 + Groww 17000 + ET Money 40000
        assertThat(invest.amount).isEqualTo(BigDecimal("218600.00"))

        // Self-transfer UPI legs must be Transfer and pair on shared refs.
        val selfDebits = aug.filter {
            it.type == com.expensetracker.domain.model.TransactionType.DEBIT &&
                it.category == Categories.TRANSFER &&
                it.amount.amount in setOf(
                    BigDecimal("5000.00"),
                    BigDecimal("60000.00"),
                    BigDecimal("100000.00"),
                )
        }
        assertThat(selfDebits).hasSize(3)

        val pairs = SelfTransferLinker.findPairs(aug, ownerNames = listOf("Anurag"))
        assertThat(pairs.map { it.debit.amount.amount }).containsAtLeast(
            BigDecimal("60000.00"),
            BigDecimal("100000.00"),
        )

        // No Airtel merchant receipts, AMC confirmations, or withdrawal notices.
        assertThat(txs.none { it.rawSms.orEmpty().contains("Dear Investor", ignoreCase = true) }).isTrue()
        assertThat(txs.none { it.rawSms.orEmpty().contains("payment receipt", ignoreCase = true) }).isTrue()
        assertThat(txs.none { it.rawSms.orEmpty().contains("withdrawal instruction", ignoreCase = true) }).isTrue()
    }
}
