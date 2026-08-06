package com.expensetracker.sms.parser

import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.TransactionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

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
    fun `amount extractor skips balance and picks debit amount`() {
        val body =
            "Avl Bal Rs 12,340.55. Rs 245.00 debited from a/c XXXX1234 at AMAZON via UPI. UPI Ref 401234567890"
        val tx = parser.parse(raw("VM-HDFCBK", body))!!
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("245.00"))
    }
}
