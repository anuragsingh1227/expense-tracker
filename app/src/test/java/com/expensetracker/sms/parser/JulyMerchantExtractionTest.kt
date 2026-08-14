package com.expensetracker.sms.parser

import com.expensetracker.domain.model.PaymentMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class JulyMerchantExtractionTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val fallback = Instant.parse("2026-08-01T00:00:00Z")
    private val parser = SmsParser(zone = zone)

    private fun parse(body: String) = parser.parse(RawSms("VM-BANK", body, fallback))!!

    @Test
    fun `ICICI UPI dash merchant is extracted`() {
        val tx = parse(
            "ICICI Bank Credit Card XX7002 debited for INR 150.00 on 30-Jul-26 for UPI-811309422116-CASA DON. To dispute call 18001080/SMS BLOCK 7002 to 9215676766",
        )
        assertThat(tx.merchant).isEqualTo("Casa Don")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("150.00"))
    }

    @Test
    fun `towards OpenAI is Subscription`() {
        val tx = parse(
            "Your A/c has been debited towards OpenAI LLC for INR 1999.00 on 30-07-26. c87c9ff04c4a49799896a3cee09b8484@ybl - Axis Bank",
        )
        assertThat(tx.merchant).isEqualTo("OpenAI")
        assertThat(tx.category).isEqualTo(Categories.SUBSCRIPTION)
    }

    @Test
    fun `ICICI credited-name merchant from savings UPI`() {
        val tx = parse(
            "ICICI Bank Acct XX293 debited for Rs 960.00 on 25-Jul-26; WHISKEY JUNCTIO credited. UPI:543490502066. Call 18002662 for dispute. SMS BLOCK 293 to 9215676766.",
        )
        assertThat(tx.merchant).isEqualTo("Whiskey Junction")
        assertThat(tx.category).isEqualTo(Categories.FOOD)
    }

    @Test
    fun `Axis compact line merchant after IST`() {
        val tx = parse(
            "Spent INR 1300.54\nAxis Bank Card no. XX0887\n31-07-26 23:45:30 IST\nRSP*DISTRIC\nAvl Limit: INR 196460.46\nNot you? SMS BLOCK 0887 to 919951860002",
        )
        assertThat(tx.merchant).isEqualTo("RSP*DISTRIC")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
    }

    @Test
    fun `Axis sector location merchant`() {
        val tx = parse(
            "INR 5000.00 debited from A/c no. XX848291 on +SECTOR 18  03-07-2026 14:38:26 IST. Avl bal: INR 117985.53. Not you? SMS BLOCKCARD XX5483 to +919951860002 - Axis Bank",
        )
        assertThat(tx.merchant).isEqualTo("SECTOR 18")
    }

    @Test
    fun `InfoBIL NEFT is Transfer not spend`() {
        val tx = parse(
            "ICICI Bank Acc XX293 debited Rs. 90,000.00 on 01-Jul-26 InfoBIL*NEFT*IN12.Avl Bal Rs. 7,31,586.64.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        )
        assertThat(tx.category).isEqualTo(Categories.TRANSFER)
    }

    @Test
    fun `RACPC ACH is EMI`() {
        val tx = parse(
            "ICICI Bank Acc XX293 debited Rs. 24,911.00 on 05-Jul-26 InfoACH*RACPC CUM.Avl Bal Rs. 5,85,911.24.To dispute call 18002662 or SMS BLOCK 293 to 9215676766",
        )
        assertThat(tx.category).isEqualTo(Categories.EMI)
    }

    @Test
    fun `internet tax debit is Taxes`() {
        val tx = parse(
            "Debit INR 230040.00\nAxis Bank A/c XX8309\n08-07-26 14:47:50\nINB/144563665/INTERNET TAX\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002",
        )
        assertThat(tx.category).isEqualTo(Categories.TAXES)
        assertThat(tx.amount.amount).isEqualTo(BigDecimal("230040.00"))
    }

    @Test
    fun `INSTAPAY billdesk receipt is ignored`() {
        assertThat(
            parser.parse(
                RawSms(
                    "VM-IOAGPL",
                    "Dear Customer, Payment of Rs. 976.00 with Transaction Id BBPSPP016186BX5KD17UE424 is received for Customer ID 2000124429 by INSTAPAY from BILLDESK - IOAGPL Gas",
                    fallback,
                ),
            ),
        ).isNull()
    }

    @Test
    fun `Avenue Supermarts card spend is Groceries not EMI`() {
        val tx = parse(
            "Rs 8,646.47 spent on ICICI Bank Card XX1014 on 26-Jul-26 at Avenue Supermar. Avl Lmt: Rs 2,06,758.37. To dispute, call 18002662/SMS BLOCK 1014 to 9215676766. To convert this txn to EMI give a missed call on 9924667667. Know more about EMI conversion at https://icici.co/ICICIT/iIPGGt",
        )
        assertThat(tx.category).isEqualTo(Categories.GROCERIES)
        assertThat(tx.merchant).isEqualTo("Avenue Supermarts")
        assertThat(tx.paymentMode).isEqualTo(PaymentMode.CARD_CREDIT)
    }
}
