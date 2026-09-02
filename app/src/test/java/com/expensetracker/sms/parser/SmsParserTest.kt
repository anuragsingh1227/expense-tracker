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

class SmsParserTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2024-01-12T10:15:00Z")

    private fun raw(sender: String, body: String) = RawSms(sender, body, now)

    @Test
    fun `HDFC debit is parsed with amount, merchant, category, mode`() {
        val tx = parser.parse(raw("VM-HDFCBK", SampleSms.HDFC_DEBIT))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("245.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("Amazon")
        assertThat(tx.category).isEqualTo(Categories.SHOPPING)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.bank).isEqualTo("HDFC")
        assertThat(tx.accountLast4).isEqualTo("1234")
        assertThat(tx.referenceNumber).isEqualTo("401234567890")
    }

    @Test
    fun `SBI credit with comma-separated balance is parsed`() {
        val tx = parser.parse(raw("JD-SBIINB", SampleSms.SBI_CREDIT))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("50000.00"))
        assertThat(tx.bank).isEqualTo("SBI")
        assertThat(tx.category).isEqualTo(Categories.SALARY)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.NET_BANKING)
    }

    @Test
    fun `ICICI UPI debit picks merchant from Info field`() {
        val tx = parser.parse(raw("AD-ICICIB", SampleSms.ICICI_UPI_DEBIT))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("499.00"))
        assertThat(tx.merchant).isEqualTo("Swiggy")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
    }

    @Test
    fun `Axis credit card purchase extracts card last 4 and merchant`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_CARD))!!
        assertThat(tx.cardLast4).isEqualTo("4321")
        assertThat(tx.merchant).isEqualTo("Flipkart")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    fun `FASTag recharge is categorized as Transport`() {
        val tx = parser.parse(raw("VM-KOTAKB", SampleSms.KOTAK_FASTAG))!!
        assertThat(tx.category).isEqualTo(Categories.TRANSPORT)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.FASTAG)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("500.00"))
    }

    @Test
    fun `UPI to Zomato via GPay picks correct merchant and category`() {
        val tx = parser.parse(raw("VM-GPAY", SampleSms.UPI_TO_MERCHANT))!!
        assertThat(tx.merchant).isEqualTo("Zomato")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
    }

    @Test
    fun `OTP SMS is rejected`() {
        assertThat(parser.isTransactional(SampleSms.OTP_MESSAGE)).isFalse()
    }

    @Test
    fun `Promotional SMS without amount is rejected`() {
        assertThat(parser.isTransactional(SampleSms.PROMOTIONAL)).isFalse()
    }

    @Test
    fun `Duplicate SMS (same sender, amount, minute, reference) has identical hash`() {
        val a = parser.parse(raw("VM-HDFCBK", SampleSms.HDFC_DEBIT))!!
        val b = parser.parse(raw("VM-HDFCBK", SampleSms.HDFC_DEBIT_DUP))!!
        assertThat(a.dedupeHash).isEqualTo(b.dedupeHash)
    }

    @Test
    fun `Different amounts produce different hashes`() {
        val a = parser.parse(raw("VM-HDFCBK", SampleSms.HDFC_DEBIT))!!
        val altered = SampleSms.HDFC_DEBIT.replace("245.00", "246.00")
        val b = parser.parse(raw("VM-HDFCBK", altered))!!
        assertThat(a.dedupeHash).isNotEqualTo(b.dedupeHash)
    }

    @Test
    fun `Groww purchase is categorized as Investment`() {
        val tx = parser.parse(raw("VM-HDFCBK", SampleSms.GROWW_INVESTMENT))!!
        assertThat(tx.merchant).isEqualTo("Groww")
        assertThat(tx.category).isEqualTo(Categories.INVESTMENT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("5000.00"))
    }

    @Test
    fun `Swiggy Instamart is Groceries not Food`() {
        val tx = parser.parse(raw("VM-ICICIB", SampleSms.INSTAMART_GROCERY))!!
        assertThat(tx.merchant).isEqualTo("Swiggy Instamart")
        assertThat(tx.category).isEqualTo(Categories.GROCERIES)
    }

    @Test
    fun `Zepto quick commerce is Groceries`() {
        val tx = parser.parse(raw("VM-GPAY", SampleSms.ZEPTO_GROCERY))!!
        assertThat(tx.merchant).isEqualTo("Zepto")
        assertThat(tx.category).isEqualTo(Categories.GROCERIES)
    }

    @Test
    fun `custom merchant matcher override wins over dictionary`() {
        val matcher = MerchantMatcher { text ->
            if (text.uppercase().contains("SWIGGY")) {
                MerchantDictionary.Entry("SWIGGY", "Swiggy", Categories.GROCERIES)
            } else {
                MerchantDictionary.match(text)
            }
        }
        val tx = SmsParser(matcher).parse(raw("AD-ICICIB", SampleSms.ICICI_UPI_DEBIT))!!
        assertThat(tx.category).isEqualTo(Categories.GROCERIES)
    }

    @Test
    fun `cashback marketing SMS is rejected`() {
        assertThat(parser.isTransactional(SampleSms.CASHBACK_SPAM)).isFalse()
        assertThat(parser.parse(raw("AX-PROMO", SampleSms.CASHBACK_SPAM))).isNull()
    }

    @Test
    fun `loan offer SMS is rejected`() {
        assertThat(parser.isTransactional(SampleSms.LOAN_OFFER_SPAM)).isFalse()
    }

    @Test
    fun `due date reminder is rejected`() {
        assertThat(parser.isTransactional(SampleSms.DUE_REMINDER_SPAM)).isFalse()
    }

    @Test
    fun `ICICI amount-due auto-debit notice is rejected`() {
        assertThat(parser.isTransactional(SampleSms.ICICI_AMOUNT_DUE_NOTICE)).isFalse()
        assertThat(parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_AMOUNT_DUE_NOTICE))).isNull()
    }

    @Test
    fun `ICICI credit-limit raise promo is rejected`() {
        assertThat(parser.isTransactional(SampleSms.ICICI_CREDIT_LIMIT_RAISE)).isFalse()
        assertThat(parser.parse(raw("CP-ICICIT-S", SampleSms.ICICI_CREDIT_LIMIT_RAISE))).isNull()
    }

    @Test
    fun `card spend keeps merchant and ignores dispute helpline footer`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_SPEND_WITH_DISPUTE))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1122.00"))
        assertThat(tx.merchant?.uppercase()).contains("ZOMATO")
        assertThat(tx.merchant?.lowercase()).doesNotContain("dispute")
        assertThat(tx.merchant).doesNotContain("18001080")
        assertThat(tx.merchant).doesNotContain("9215676766")
    }

    @Test
    fun `balance-only alert is rejected`() {
        assertThat(parser.isTransactional(SampleSms.BALANCE_ONLY)).isFalse()
    }

    @Test
    fun `refund from a known merchant is categorized as Refund not merchant income`() {
        val tx = parser.parse(raw("AD-ICICIB", SampleSms.AMAZON_REFUND_CREDITED))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.category).isEqualTo(Categories.REFUND)
    }

    @Test
    fun `card txn reversal without the word credited still parses as a refund`() {
        val tx = parser.parse(raw("AD-ICICIB", SampleSms.CARD_TXN_REVERSED_NO_CREDIT_WORD))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.category).isEqualTo(Categories.REFUND)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("799.00"))
    }

    @Test
    fun `payment to credit card via netbanking is Transfer not spend`() {
        val tx = parser.parse(raw("VM-HDFCBK", SampleSms.PAYMENT_TO_CREDIT_CARD_TRANSFER))!!
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
    }

    @Test
    fun `ICICI card spend with is-used-for template parses as debit`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_IS_USED))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2450.00"))
        assertThat(tx.merchant?.uppercase()).contains("AMAZON")
    }

    @Test
    fun `ICICI card spend with has-been-used template parses as debit`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_HAS_BEEN_USED))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1122.00"))
        assertThat(tx.merchant?.uppercase()).contains("ZOMATO")
    }

    @Test
    fun `ICICI INR spent-using Bank Card is a payment not rejected`() {
        assertThat(parser.isTransactional(SampleSms.ICICI_CARD_SPENT_USING)).isTrue()
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_SPENT_USING))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("2500.00"))
        assertThat(tx.merchant?.uppercase()).contains("GOODCHOICE")
        assertThat(tx.merchant?.uppercase()).doesNotContain("SEP")
        assertThat(tx.merchant).doesNotContain("9215676766")
        assertThat(tx.merchant).doesNotContain("1800")
        assertThat(tx.cardLast4).isEqualTo("1014")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
        assertThat(tx.bank).isEqualTo("ICICI")
        assertThat(tx.category).isNotEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LocalDate.ofInstant(tx.timestamp, ZoneId.systemDefault()))
            .isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `Axis inbound NEFT credit is kept as Transfer not income`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_NEFT_CREDIT_INBOUND))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("90000.00"))
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("IN12624453192288")
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isIncome(tx)).isFalse()
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
        assertThat(LocalDate.ofInstant(tx.timestamp, ZoneId.systemDefault()))
            .isEqualTo(LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `ICICI NEFT beneficiary confirmation is ignored as duplicate of inbound credit`() {
        assertThat(parser.isTransactional(SampleSms.ICICI_NEFT_BENEFICIARY_CONFIRMATION)).isFalse()
        assertThat(parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_NEFT_BENEFICIARY_CONFIRMATION))).isNull()
    }

    @Test
    fun `Axis compact Spent INR card alert is spend not ignored`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_COMPACT_CARD_SPENT_SWIGGY))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1548.00"))
        assertThat(tx.merchant).isEqualTo("Swiggy")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
        assertThat(tx.cardLast4).isEqualTo("0887")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
        assertThat(LocalDate.ofInstant(tx.timestamp, ZoneId.systemDefault()))
            .isEqualTo(LocalDate.of(2026, 8, 28))
    }

    @Test
    fun `Axis compact INR debited UPI P2A is spend not ignored`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_COMPACT_UPI_P2A_ASMITA))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(tx.merchant?.uppercase()).contains("ASMITA")
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("824994710955")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.category).isNotEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `Axis compact UPI P2A Sushmita and hardware and P2M are spend`() {
        val sushmita = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_COMPACT_UPI_P2A_SUSHMITA))!!
        assertThat(sushmita.amount.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(sushmita.merchant?.uppercase()).contains("SUSHMITA")
        assertThat(LedgerBuckets.isSpend(sushmita)).isTrue()

        val hardware = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_COMPACT_UPI_P2A_HARDWARE))!!
        assertThat(hardware.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(hardware.merchant?.uppercase()).contains("ANEJA")
        assertThat(LedgerBuckets.isSpend(hardware)).isTrue()

        val p2m = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_COMPACT_UPI_P2M_ROSHAN))!!
        assertThat(p2m.amount.amount).isEqualTo(BigDecimal("210.00"))
        assertThat(p2m.merchant?.uppercase()).contains("ROSHAN")
        assertThat(p2m.referenceNumber).isEqualTo("148918812356")
        assertThat(LedgerBuckets.isSpend(p2m)).isTrue()
    }

    @Test
    fun `ICICI spent-using PARAS FUELS is fuel spend`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_SPENT_USING_FUEL))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("4096.00"))
        assertThat(tx.merchant?.uppercase()).contains("PARAS")
        assertThat(tx.category).isEqualTo(Categories.FUEL)
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `ICICI credit-card UPI hyphen Airtel is recharge spend`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_CARD_UPI_HYPHEN_AIRTEL))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("360.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("Airtel")
        assertThat(tx.category).isEqualTo(Categories.RECHARGE)
        assertThat(tx.cardLast4).isEqualTo("7002")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
        assertThat(tx.referenceNumber).isEqualTo("002104929024")
        assertThat(LedgerBuckets.isSpend(tx)).isTrue()
    }

    @Test
    fun `UPI debit with named merchant after reference extracts merchant`() {
        val tx = parser.parse(raw("VM-HDFCBK", SampleSms.UPI_WITH_NAMED_MERCHANT))!!
        assertThat(tx.merchant?.uppercase()).contains("SWIGGY")
    }

    @Test
    fun `UPI debit with only a reference number has no merchant`() {
        val tx = parser.parse(raw("VM-HDFCBK", SampleSms.UPI_REF_ONLY))!!
        assertThat(tx.merchant).isNull()
    }

    @Test
    fun `same-amount same-minute UPI rows with different bodies are not deduped`() {
        val body1 = "Rs 1,000.00 debited from A/c XX1234 on 05-Aug-26 for UPI/111111111111. Avl Bal Rs 8,200.00"
        val body2 = "Rs 1,000.00 debited from A/c XX1234 on 05-Aug-26 for UPI/222222222222. Avl Bal Rs 7,200.00"
        val a = parser.parse(raw("VM-HDFCBK", body1))!!
        val b = parser.parse(raw("VM-HDFCBK", body2))!!
        assertThat(a.dedupeHash).isNotEqualTo(b.dedupeHash)
    }

    @Test
    fun `received UPI payment is CREDIT Transfer not spend and not income`() {
        val tx = parser.parse(raw("JM-IDFCBK", SampleSms.IDFC_RECEIVED_UPI))!!
        assertThat(tx.type).isEqualTo(TransactionType.CREDIT)
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `auto-pay will-be-debited activation notice is ignored entirely`() {
        assertThat(parser.isTransactional(SampleSms.AUTOPAY_WILL_BE_DEBITED_NOTICE)).isFalse()
        assertThat(parser.parse(raw("AD-ICICIT-S", SampleSms.AUTOPAY_WILL_BE_DEBITED_NOTICE))).isNull()
    }

    @Test
    fun `Axis Debit INR NEFT compact template parses as Transfer debit`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_DEBIT_INR_NEFT))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("17383.00"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.accountLast4).isEqualTo("8291")
        assertThat(tx.referenceNumber).isEqualTo("AXOMB16602145999")
    }

    @Test
    fun `Axis Debit INR CRD-PMNT is Transfer not spend`() {
        val tx = parser.parse(raw("VM-AXISBK", SampleSms.AXIS_DEBIT_INR_CRD_PMNT))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("3868.40"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
    }

    @Test
    fun `card-side thank-you-for-payment auto-debit confirmation is ignored (duplicate)`() {
        assertThat(parser.isTransactional(SampleSms.CARD_AUTODEBIT_THANK_YOU_DUPLICATE)).isFalse()
        assertThat(parser.parse(raw("AD-ICICIT-S", SampleSms.CARD_AUTODEBIT_THANK_YOU_DUPLICATE))).isNull()
    }

    @Test
    fun `ICICI bank-side ATD auto-debit is the kept record, parsed as Transfer`() {
        val tx = parser.parse(raw("AD-ICICIT-S", SampleSms.ICICI_ACC_DEBITED_ATD_AUTO_DEBIT))!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("29004.46"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
    }

    @Test
    fun `amount extractor skips balance and picks debit amount`() {
        val body =
            "Avl Bal Rs 12,340.55. Rs 245.00 debited from a/c XXXX1234 at AMAZON via UPI. UPI Ref 401234567890"
        val tx = parser.parse(raw("VM-HDFCBK", body))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("245.00"))
    }

    @Test
    fun `UPI VPA paid Rs to handle extracts amount debit merchant and ref`() {
        val tx = parser.parse(
            raw("VM-HDFCBK", "Paid Rs.250.00 to user@okicici via UPI Ref 1234567890"),
        )!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("250.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("user@okicici")
        assertThat(tx.upiId).isEqualTo("user@okicici")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.referenceNumber).isEqualTo("1234567890")
        assertThat(tx.bank).isEqualTo("HDFC")
    }

    @Test
    fun `GPay debit to numeric VPA extracts amount and last-4-free merchant`() {
        val tx = parser.parse(
            raw("VM-GPAY", "Debited INR 1,500.00 via GPay to 9876543210@upi"),
        )!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("1500.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("9876543210@upi")
        assertThat(tx.upiId).isEqualTo("9876543210@upi")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.bank).isEqualTo("Google Pay")
    }

    @Test
    fun `reversed wording with Indian grouping extracts debit Swiggy and last4`() {
        val tx = parser.parse(
            raw(
                "VM-HDFCBK",
                "Your A/C x1234 debited by INR 1,50,000.50 on 12-Aug-26 by Swiggy",
            ),
        )!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("150000.50"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("Swiggy")
        assertThat(tx.accountLast4).isEqualTo("1234")
        assertThat(tx.bank).isEqualTo("HDFC")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
    }

    @Test
    fun `INR-first debit at Zomato extracts amount last4 and UPI mode`() {
        val tx = parser.parse(
            raw("VM-HDFCBK", "INR 450.00 debited from A/C x5678 at ZOMATO UPI"),
        )!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("450.00"))
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.merchant).isEqualTo("Zomato")
        assertThat(tx.accountLast4).isEqualTo("5678")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.UPI)
        assertThat(tx.category).isEqualTo(Categories.FOOD)
    }

    @Test
    fun `refund of Rs is Credit Refund and nets against spend`() {
        val spend = parser.parse(
            raw("VM-HDFCBK", "Rs 350.00 debited from A/c XX1234 at AMAZON via UPI. UPI Ref 111"),
        )!!
        val refund = parser.parse(
            raw(
                "VM-HDFCBK",
                "Refund of Rs.350.00 processed for your transaction at Amazon",
            ),
        )!!
        assertThat(refund.type).isEqualTo(TransactionType.CREDIT)
        assertThat(refund.category).isEqualTo(Categories.REFUND)
        assertThat(refund.merchant?.uppercase()).contains("AMAZON")
        assertThat(refund.amount.amount).isEqualTo(BigDecimal("350.00"))
        assertThat(LedgerBuckets.spend(listOf(spend, refund)).amount)
            .isEqualTo(BigDecimal.ZERO.setScale(2))
        assertThat(LedgerBuckets.isIncome(refund)).isFalse()
    }

    @Test
    fun `own-account transfer between two last4s is Self-Transfer excluded from spend`() {
        val tx = parser.parse(
            raw(
                "VM-HDFCBK",
                "Transferred Rs.5000.00 from A/C x1234 to A/C x5678",
            ),
        )!!
        assertThat(tx.type).isEqualTo(TransactionType.DEBIT)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("5000.00"))
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
        assertThat(tx.accountLast4).isEqualTo("1234")
        assertThat(LedgerBuckets.isSpend(tx)).isFalse()
        assertThat(LedgerBuckets.spend(listOf(tx)).amount).isEqualTo(BigDecimal.ZERO.setScale(2))
    }

    @Test
    fun `OTP login SMS is ignored`() {
        val body = "123456 is your OTP for login. Do not share with anyone."
        assertThat(parser.isTransactional(body)).isFalse()
        assertThat(parser.parse(raw("VM-HDFCBK", body))).isNull()
    }

    @Test
    fun `credit card limit increase promo is ignored`() {
        val body = "Your credit card limit has been increased to Rs. 2,00,000. Apply now."
        assertThat(parser.isTransactional(body)).isFalse()
        assertThat(parser.parse(raw("VM-HDFCBK", body))).isNull()
    }

    @Test
    fun `electricity bill due reminder is ignored`() {
        val body = "Reminder: Your electricity bill of Rs. 1200 is due on 20th Aug."
        assertThat(parser.isTransactional(body)).isFalse()
        assertThat(parser.parse(raw("VM-HDFCBK", body))).isNull()
        assertThat(CreditCardStatementParser.parse(raw("VM-HDFCBK", body))).isNull()
    }
}
