package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.SpendMath
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LedgerCalculationTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val parser = SmsParser(zone = zone)
    private val fallback = Instant.parse("2026-08-06T04:00:00Z")

    @Test
    fun `web sample INR debit parses amount not balance`() {
        val tx = parser.parse(RawSms("VM-HDFCBK", SampleSms.INR_DEBITED_ECS, fallback))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2000.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.timestamp).isEqualTo(
            LocalDate.of(2019, 2, 5).atTime(7, 27, 11).atZone(zone).toInstant(),
        )
    }

    @Test
    fun `BillPay credit-card repayment is Transfer not spend`() {
        val tx = parser.parse(RawSms("VM-HDFCBK", SampleSms.BILLPAY_DEBIT, fallback))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2487.00"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isTransfer(tx)).isTrue()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `IMPS self-move is Transfer on both verb sides`() {
        val tx = parser.parse(RawSms("VM-HDFCBK", SampleSms.IMPS_SELF_TRANSFER, fallback))!!
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("4600.00"))
    }

    @Test
    fun `card bill credited SMS is Transfer credit not income`() {
        val tx = parser.parse(RawSms("VM-AXISBK", SampleSms.CARD_PAYMENT_CREDITED, fallback))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isIncome(tx)).isFalse()
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2487.00"))
    }

    @Test
    fun `FX reversal must not book available credit limit as income`() {
        assertThat(parser.parse(RawSms("VM-HDFCBK", SampleSms.FX_REVERSAL_LIMIT_TRAP, fallback))).isNull()
        assertThat(SmsAmountExtractor.extract(SampleSms.FX_REVERSAL_LIMIT_TRAP)).isNull()
    }

    @Test
    fun `scheduled EMI reminder is rejected`() {
        assertThat(parser.parse(RawSms("VM-HDFCBK", SampleSms.EMI_SCHEDULED_SPAM, fallback))).isNull()
    }

    @Test
    fun `year-less APR date uses fallback year`() {
        val tx = parser.parse(RawSms("VM-HDFCBK", SampleSms.HDFC_UPI_NO_YEAR, fallback))!!
        assertThat(LocalDate.ofInstant(tx.timestamp, zone)).isEqualTo(LocalDate.of(2026, 4, 15))
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("450.00"))
    }

    @Test
    fun `paste batch uses SMS dates so month spend is correct`() {
        val txs = SampleSms.LEDGER_BATCH
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { body -> parser.parse(RawSms("VM-HDFCBK", body, fallback)) }

        // spam rejected
        assertThat(txs).hasSize(10)

        val augStart = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant()
        val sepStart = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant()
        val julStart = LocalDate.of(2026, 7, 1).atStartOfDay(zone).toInstant()

        val aug = txs.filter { !it.timestamp.isBefore(augStart) && it.timestamp.isBefore(sepStart) }
        val jul = txs.filter { !it.timestamp.isBefore(julStart) && it.timestamp.isBefore(augStart) }

        val augSpend = LedgerBuckets.spend(aug)
        val augInvest = LedgerBuckets.investments(aug)
        val augIncome = LedgerBuckets.income(aug)

        // Swiggy 420 + Instamart 612 + Flipkart 1299 = 2331 (Groww 5000 excluded from spend)
        assertThat(augSpend.amount).isEqualTo(BigDecimal("2331.00"))
        assertThat(augInvest.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(augIncome.amount).isEqualTo(BigDecimal("52000.00"))
        assertThat(SpendMath.netCashFlow(augIncome, augSpend, augInvest).amount)
            .isEqualTo(BigDecimal("44669.00"))

        val julSpend = LedgerBuckets.spend(jul)
        // 1200 + 890 + 2400 = 4490 (Groww 5000 excluded)
        assertThat(julSpend.amount).isEqualTo(BigDecimal("4490.00"))
    }

    @Test
    fun `import file math excludes BillPay and transfer credits from spend and income`() {
        val txs = docsImportFallback()
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parser.parse(RawSms("VM-HDFCBK", it, fallback)) }

        val augStart = LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant()
        val sepStart = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant()
        val aug = txs.filter { !it.timestamp.isBefore(augStart) && it.timestamp.isBefore(sepStart) }

        val spend = LedgerBuckets.spend(aug)
        val invest = LedgerBuckets.investments(aug)
        val income = LedgerBuckets.income(aug)
        val transfers = aug.filter(LedgerBuckets::isTransfer)

        // 420 + 612 + 1299 + 555 + 450 = 3336 (BillPay 2487 + IMPS 4600 excluded)
        assertThat(spend.amount).isEqualTo(BigDecimal("3336.00"))
        assertThat(invest.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(income.amount).isEqualTo(BigDecimal("52000.00"))
        assertThat(transfers).hasSize(3) // BillPay debit, IMPS debit, card payment credit
        assertThat(SpendMath.netCashFlow(income, spend, invest).amount)
            .isEqualTo(BigDecimal("43664.00"))

        // Category bars must reconcile to spend (skill: withOtherBucket)
        val byCat = aug.filter(LedgerBuckets::isSpend)
            .groupBy { it.category }
            .map { (cat, rows) ->
                com.expensetracker.data.repository.CategorySpend(
                    cat,
                    rows.fold(Money.ZERO) { a, t -> a + t.amount },
                )
            }
            .sortedByDescending { it.amount.amount }
            .take(2)
        val bars = SpendMath.withOtherBucket(byCat, spend)
        val barSum = bars.fold(BigDecimal.ZERO) { a, c -> a + c.amount.amount }
        assertThat(barSum).isEqualTo(spend.amount)
    }

    @Test
    fun `category bars append Other so fractions match total spend`() {
        val cats = listOf(
            com.expensetracker.data.repository.CategorySpend("Food", Money.ofRupees(1000)),
            com.expensetracker.data.repository.CategorySpend("Travel", Money.ofRupees(400)),
        )
        val reconciled = SpendMath.withOtherBucket(cats, Money.ofRupees(1800))
        assertThat(reconciled.last().category).isEqualTo("Other")
        assertThat(reconciled.last().amount.amount).isEqualTo(BigDecimal("400.00"))
        val sum = reconciled.fold(BigDecimal.ZERO) { a, c -> a + c.amount.amount }
        assertThat(sum).isEqualTo(BigDecimal("1800.00"))
    }

    @Test
    fun `refund nets against spend instead of inflating income`() {
        val spend = parser.parse(RawSms("AD-ICICIB", "Rs 1,499.00 debited from ICICI Bank A/c XX789 at AMAZON on 08-08-26. Ref 900123455.", fallback))!!
        val refund = parser.parse(RawSms("AD-ICICIB", SampleSms.AMAZON_REFUND_CREDITED, fallback))!!

        assertThat(LedgerBuckets.isRefund(refund)).isTrue()
        assertThat(LedgerBuckets.isIncome(refund)).isFalse()

        val net = LedgerBuckets.spend(listOf(spend, refund))
        assertThat(net.amount).isEqualTo(BigDecimal.ZERO.setScale(2))
        assertThat(LedgerBuckets.income(listOf(spend, refund)).amount).isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `partial refund nets correctly against remaining spend`() {
        val purchase = parser.parse(RawSms("AD-ICICIB", "Rs 2,000.00 debited from ICICI Bank A/c XX789 at AMAZON on 08-08-26. Ref 900123455.", fallback))!!
        val partialRefund = parser.parse(RawSms("AD-ICICIB", SampleSms.AMAZON_REFUND_CREDITED, fallback))!! // 1499 back

        val net = LedgerBuckets.spend(listOf(purchase, partialRefund))
        // 2000 − 1499 = 501 stays spend
        assertThat(net.amount).isEqualTo(BigDecimal("501.00"))
        assertThat(LedgerBuckets.income(listOf(purchase, partialRefund)).amount)
            .isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `refund-only month yields negative spend so the reversal is visible`() {
        val refund = parser.parse(RawSms("AD-ICICIB", SampleSms.AMAZON_REFUND_CREDITED, fallback))!!
        val net = LedgerBuckets.spend(listOf(refund))
        // Not floored at zero: a refund-only window reports negative net spend.
        assertThat(net.amount).isEqualTo(BigDecimal("-1499.00"))
        // And it's not income either.
        assertThat(LedgerBuckets.income(listOf(refund)).amount).isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `credit card purchase is spend once and its auto-pay pair is Transfer not double-counted`() {
        val purchase = parser.parse(RawSms("AD-ICICIB", SampleSms.ICICI_CARD_SPEND_WITH_DISPUTE, fallback))!!
        val billDebit = parser.parse(RawSms("VM-HDFCBK", SampleSms.PAYMENT_TO_CREDIT_CARD_TRANSFER, fallback))!!
        val billCredit = parser.parse(RawSms("VM-ICICIB", SampleSms.CARD_PAYMENT_CREDITED, fallback))!!

        assertThat(LedgerBuckets.isSpend(purchase)).isTrue()
        assertThat(LedgerBuckets.isTransfer(billDebit)).isTrue()
        assertThat(LedgerBuckets.isTransfer(billCredit)).isTrue()

        val all = listOf(purchase, billDebit, billCredit)
        // Only the card purchase counts toward spend — the 3200 debit + 2487 credit cancel
        // out as Transfers and never inflate spend or income.
        assertThat(LedgerBuckets.spend(all).amount).isEqualTo(purchase.amount.amount)
        assertThat(LedgerBuckets.income(all).amount).isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `date extractor reads Jul mon-name format`() {
        val instant = SmsDateExtractor.extractOrNull(
            "Acct XX126 debited with INR 4,600.00 on 23-Jul-2026",
            zone,
        )!!
        assertThat(LocalDate.ofInstant(instant, zone)).isEqualTo(LocalDate.of(2026, 7, 23))
    }

    @Test
    fun `date extractor reads month-first English format`() {
        val instant = SmsDateExtractor.extractOrNull(
            "You have made a payment of Rs. 46000 on August 05, 2026 at 14:15.",
            zone,
        )!!
        assertThat(LocalDate.ofInstant(instant, zone)).isEqualTo(LocalDate.of(2026, 8, 5))
    }

    private fun docsImportFallback(): String = """
INR 52000.00 credited to A/c XXXX9876 on 01-Jul-26 by NEFT Salary. Avl Bal Rs 80,000.00

Rs 1,200.00 debited from a/c XX1234 on 03-Jul-26 at SWIGGY via UPI. UPI Ref 7001001

Rs 5,000.00 debited from a/c XX4411 for GROWW purchase on 05-Jul-26. UPI Ref 7001002

Rs 890.00 debited via UPI to ZEPTO on 12-Jul-26. Ref 7001003

Rs 2,400.00 debited from a/c XX1234 on 18-Jul-26 at AMAZON via UPI. UPI Ref 7001004

INR 52000.00 credited to A/c XXXX9876 on 01-Aug-26 by NEFT Salary. Avl Bal Rs 90,000.00

Rs 420.00 debited from a/c XX1234 on 02-Aug-26 at SWIGGY via UPI. UPI Ref 8001001

Rs 612.00 debited via UPI to SWIGGY INSTAMART on 03-Aug-26. Ref 8001002

Rs 5,000.00 debited from a/c XX4411 for GROWW purchase on 04-Aug-26. UPI Ref 8001003

Rs 1,299.00 spent on AXIS Bank Credit Card ending 4321 at FLIPKART on 05-Aug-26

Alert: You've spent INR 555.00 on your bank card **9123 at BD JIO MONEY on 06/08/2026 at 11:07 IST.

Rs.450.00 debited from a/c XX1234 on 07-Aug-26 for UPI/412839-BIGBASKET. Avl Bal Rs 8,200.00

Dear Customer, Rs.2,487.00 is debited from A/c XXXX6791 for BillPay/Credit Card payment via NetBanking on 08-08-26.

Acct XX126 debited with INR 4,600.00 on 08-Aug-2026 & Acct XX791 credited. IMPS: XXX410XX.

Dear bank cardmember, Payment of Rs 2487 was credited to your card ending 1234 on 08/Aug/2026.

Get up to Rs 500 cashback on UPI spends this weekend. Shop now.

INR 2000 debited from A/c no. XX3423 on 05-02-19 07:27:11 IST at ECS PAY. Avl Bal- INR 2343.23.

Dear customer, your EMI of Rs. 99650.00 is scheduled for ECS clearance on 10-08-2026. Please keep sufficient balance.

Your transaction of SGD 1.38 on HDFC Bank Credit Card ending 4321 is reversed. Available Credit Limit is Rs.2,26,151.86
""".trimIndent()
}
