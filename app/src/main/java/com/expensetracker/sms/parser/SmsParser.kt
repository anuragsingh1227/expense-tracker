package com.expensetracker.sms.parser

import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

data class RawSms(val sender: String?, val body: String, val timestamp: Instant)

class SmsParser(
    private val merchants: MerchantMatcher = DefaultMerchantMatcher,
    private val labelRules: LabelRuleMatcher = NoLabelRules,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    fun parse(sms: RawSms): Transaction? {
        val body = sms.body.trim()
        if (body.isEmpty()) return null
        if (!isTransactional(body)) return null

        val amount = SmsAmountExtractor.extract(body) ?: return null
        val type = detectType(body)
        val bank = BankSenders.identify(sms.sender)
        val merchantMatch = merchants.match(body)
        val merchant = merchantMatch?.displayName ?: extractMerchant(body)
        val labeled = labelRules.match(sms.sender, body, merchant)
        // Ledger-correctness categories (Transfer/Refund) must win over merchant/dictionary
        // matches — e.g. "Refund from AMAZON" must not be booked as Shopping income.
        val category = labeled ?: resolveAutoCategory(body, type, merchantMatch?.category)
        val paymentMode = detectPaymentMode(body)
        // Prefer date written in the SMS (paste/share import has no telephony timestamp).
        val timestamp = SmsDateExtractor.extract(body, zone, sms.timestamp)

        val accountLast4 = ACCOUNT_LAST4.find(body)?.groupValues?.get(1)
        val cardLast4 = CARD_LAST4.find(body)?.groupValues?.get(1)
        val upiId = UPI_ID.find(body)?.value
        val referenceNumber = extractReference(body)
        val balance = extractBalance(body)
        val narration = extractNarration(body)

        return Transaction(
            amount = amount,
            type = type,
            merchant = merchant,
            category = category,
            bank = bank,
            accountLast4 = accountLast4,
            cardLast4 = cardLast4,
            upiId = upiId,
            referenceNumber = referenceNumber,
            balance = balance,
            paymentMode = paymentMode,
            timestamp = timestamp,
            sender = sms.sender,
            rawSms = sms.body,
            narration = narration,
            notes = null,
            dedupeHash = computeHash(sms.sender, amount.amount, timestamp, referenceNumber, sms.body),
        )
    }

    fun isTransactional(body: String): Boolean = TransactionGate.isTransactional(body)

    private fun detectType(body: String): TransactionType {
        val upper = body.uppercase()
            .replace("CREDIT CARD", "CC_NOUN")
            .replace("DEBIT CARD", "DC_NOUN")
            // "Available credit limit" must not count as a CREDIT txn verb.
            .replace("AVAILABLE CREDIT LIMIT", "AVL_LIMIT")
            .replace("CREDIT LIMIT", "AVL_LIMIT")
            .replace("AVL LIMIT", "AVL_LIMIT")
            .replace("AVBL LIMIT", "AVL_LIMIT")
        if (upper.contains("REVERSED") ||
            upper.contains("REVERSAL") ||
            upper.contains("REFUND") ||
            upper.contains("WAS CREDITED") ||
            upper.contains("HAS BEEN CREDITED") ||
            upper.contains("CREDITED TO YOUR") ||
            upper.contains("CREDITED WITH")
        ) {
            return TransactionType.CREDIT
        }
        val creditHit = CREDIT_WORDS.any { upper.contains(it) }
        val debitHit = DEBIT_WORDS.any { upper.contains(it) }
        return when {
            creditHit && !debitHit -> TransactionType.CREDIT
            debitHit && !creditHit -> TransactionType.DEBIT
            debitHit && creditHit -> if (upper.indexOfAny(DEBIT_WORDS) < upper.indexOfAny(CREDIT_WORDS)) {
                TransactionType.DEBIT
            } else {
                TransactionType.CREDIT
            }
            else -> TransactionType.DEBIT
        }
    }

    private fun detectPaymentMode(body: String): PaymentMode {
        val upper = body.uppercase()
        return when {
            upper.contains("FASTAG") -> PaymentMode.FASTAG
            upper.contains("UPI") || UPI_ID.containsMatchIn(body) -> PaymentMode.UPI
            upper.contains("CREDIT CARD") || upper.contains("CC ") -> PaymentMode.CARD_CREDIT
            upper.contains("DEBIT CARD") || upper.contains("DC ") -> PaymentMode.CARD_DEBIT
            upper.contains("NEFT") || upper.contains("IMPS") || upper.contains("RTGS") -> PaymentMode.NET_BANKING
            upper.contains("WALLET") -> PaymentMode.WALLET
            upper.contains("ATM") -> PaymentMode.CARD_DEBIT
            else -> PaymentMode.UNKNOWN
        }
    }

    private fun extractMerchant(body: String): String? {
        MERCHANT_AT.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        MERCHANT_TO.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        return null
    }

    /** Drop trailing dispute/help text that banks append after the merchant. */
    private fun sanitizeMerchantCandidate(raw: String): String? {
        var name = raw.trim().trimEnd('.', ',', ';', ':')
        // "ZOMATO. To dispute call …" / "AMAZON To dispute…"
        name = name.replace(Regex("""(?i)\s*(?:\.?\s*)?to\s+dispute\b.*$"""), "").trim()
        name = name.replace(Regex("""(?i)\s+dispute\s+call\b.*$"""), "").trim()
        name = name.trimEnd('.', ',', ';', ':').trim()
        return name.takeIf { it.length >= 2 }
    }

    /**
     * Reject phone numbers, SMS keywords, and dispute helplines that `\bto\s+`
     * greedily captures from credit-limit / card-alert footers.
     */
    private fun isPlausibleMerchant(name: String): Boolean {
        val compact = name.replace(Regex("""[\s./-]"""), "")
        if (compact.isEmpty()) return false
        // Pure phone / shortcode (e.g. "9215676766", "5676766").
        if (compact.all { it.isDigit() } && compact.length in 6..13) return false
        val upper = name.uppercase()
        if (upper.startsWith("DISPUTE")) return false
        if (upper.startsWith("SMS")) return false
        if (upper.startsWith("RS") || upper.startsWith("INR") || name.startsWith("₹")) return false
        if (DISPUTE_OR_HELPLINE.containsMatchIn(name)) return false
        if (PHONE_HEAVY_MERCHANT.containsMatchIn(name)) return false
        return true
    }

    /**
     * Resolves category for rows the merchant dictionary/label rules didn't claim,
     * but first forces Transfer/Refund so those ledger-correctness rules cannot be
     * bypassed by a merchant match (e.g. a refund from a known merchant).
     */
    private fun resolveAutoCategory(body: String, type: TransactionType, merchantCategory: String?): String {
        val upper = body.uppercase()
        return when {
            type == TransactionType.CREDIT && isRefundOrReversal(upper) -> Categories.REFUND
            isSelfOrCardTransfer(upper) -> Categories.TRANSFER
            else -> merchantCategory ?: inferCategory(body, type)
        }
    }

    private fun isRefundOrReversal(upper: String): Boolean {
        return upper.contains("REFUND") ||
            upper.contains("REVERSED") ||
            upper.contains("REVERSAL") ||
            upper.contains("CHARGEBACK")
    }

    private fun inferCategory(body: String, type: TransactionType): String {
        val upper = body.uppercase()
        return when {
            type == TransactionType.CREDIT && (upper.contains("SALARY") || upper.contains("SAL CR")) -> Categories.SALARY
            isSelfOrCardTransfer(upper) -> Categories.TRANSFER
            upper.contains("ATM") || upper.contains("CASH WDL") -> Categories.CASH_WITHDRAWAL
            upper.contains("EMI") -> Categories.EMI
            upper.contains("RENT") -> Categories.RENT
            upper.contains("INSURANCE") || upper.contains("INS PREMIUM") || upper.contains("PREMIUM PAID") ->
                Categories.INSURANCE
            upper.contains("MUTUAL FUND") || upper.contains("SIP") || upper.contains("ZERODHA") ||
                upper.contains("GROWW") -> Categories.INVESTMENT
            upper.contains("RECHARGE") -> Categories.RECHARGE
            upper.contains("ELECTRICITY") || upper.contains("WATER BILL") || upper.contains("GAS BILL") ->
                Categories.UTILITIES
            type == TransactionType.CREDIT -> Categories.TRANSFER
            else -> Categories.OTHERS
        }
    }

    /**
     * Card bill pays and account-to-account moves must not inflate “spend”
     * (card spends already hit the ledger when the purchase SMS arrived).
     */
    private fun isSelfOrCardTransfer(upper: String): Boolean {
        if (upper.contains("BILLPAY") || upper.contains("BILL PAY")) return true
        if (upper.contains("CREDIT CARD PAYMENT") || upper.contains("CC PAYMENT")) return true
        if (upper.contains("CREDIT CARD BILL") || upper.contains("CC BILL")) return true
        if (upper.contains("TOWARDS") && upper.contains("CARD")) return true
        if (upper.contains("PAYMENT TO") && (upper.contains("CREDIT CARD") || upper.contains(" CC "))) return true
        if (upper.contains("CREDITED TO YOUR CARD") || upper.contains("CREDITED TO YOUR CC")) return true
        // IMPS/NEFT self-move templates: "Acct A debited ... & Acct B credited"
        if (upper.contains("DEBITED") && upper.contains("CREDITED") &&
            (upper.contains("IMPS") || upper.contains("NEFT") || upper.contains("RTGS"))
        ) {
            return true
        }
        if (Regex("""ACCT\s+XX\d+\s+DEBITED.*ACCT\s+XX\d+\s+CREDITED""").containsMatchIn(upper)) {
            return true
        }
        return false
    }

    private fun extractReference(body: String): String? {
        REFERENCE_PATTERNS.forEach { pattern ->
            pattern.find(body)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun extractBalance(body: String): Money? {
        val match = BALANCE_PATTERN.find(body) ?: return null
        val raw = match.groupValues[1].replace(",", "").replace(" ", "")
        return runCatching { Money(BigDecimal(raw).setScale(2, RoundingMode.HALF_UP)) }.getOrNull()
    }

    private fun extractNarration(body: String): String? {
        INFO_PATTERN.find(body)?.let { return it.groupValues[1].trim() }
        return null
    }

    private fun computeHash(
        sender: String?,
        amount: BigDecimal,
        timestamp: Instant,
        reference: String?,
        body: String,
    ): String {
        val minute = timestamp.epochSecond / 60
        val key = buildString {
            append(sender.orEmpty().uppercase()).append('|')
            append(amount.toPlainString()).append('|')
            append(minute).append('|')
            append(reference.orEmpty()).append('|')
            if (reference.isNullOrBlank()) append(body.replace(Regex("\\s+"), " ").take(32))
        }
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun String.indexOfAny(words: List<String>): Int {
        var best = Int.MAX_VALUE
        for (w in words) {
            val i = indexOf(w)
            if (i in 0 until best) best = i
        }
        return if (best == Int.MAX_VALUE) -1 else best
    }

    companion object {
        private val DEBIT_WORDS = listOf(
            "DEBITED", "DEBIT", "SPENT", "PAID", "PURCHASE", "WITHDRAWN", "WITHDRAWAL",
            "CHARGED", "TXN OF", "TRANSFERRED", "SENT", "PAYMENT OF",
        )
        private val CREDIT_WORDS = listOf(
            "CREDITED", "CREDIT", "RECEIVED", "REFUND", "DEPOSITED", "SALARY",
        )
        private val ACCOUNT_LAST4 = Regex("""(?i)a/c(?:\s*(?:no)?\.?)?\s*[Xx*]{2,}(\d{4})""")
        private val CARD_LAST4 =
            Regex("""(?i)card(?:\s*(?:no)?\.?)?\s*(?:ending(?:\s*with)?)?\s*(?:[Xx*]{2,})?\s*(\d{4})\b""")
        private val UPI_ID = Regex("""\b[a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,}\b""")
        // Stop before common trailing clauses (via/on/UPI/ref/dispute).
        private val MERCHANT_AT =
            Regex("""(?i)\bat\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""")
        private val MERCHANT_TO =
            Regex("""(?i)\bto\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""")
        private val DISPUTE_OR_HELPLINE =
            Regex("""(?i)\b(?:dispute|helpline|customer\s+care|toll\s*free)\b""")
        private val PHONE_HEAVY_MERCHANT =
            Regex("""(?i)(?:\d[\d\s/-]{6,}\d)|(?:\b\d{4,}[-/]\d{4,}\b)""")
        private val REFERENCE_PATTERNS = listOf(
            Regex("""(?i)(?:ref(?:erence)?(?:\s*no)?\.?|txn(?:\s*id)?\.?|utr)[:\s#]*([A-Z0-9]{6,})"""),
            Regex("""(?i)UPI(?:\s*ref)?[:\s]*([0-9]{9,})"""),
        )
        private val BALANCE_PATTERN = Regex(
            """(?i)(?:avl\.?\s*bal|available\s*balance|bal(?:ance)?)[:\s]*(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        )
        private val INFO_PATTERN = Regex("""(?i)info[:\-\s]+([A-Z0-9 ./*-]{3,60})""")
    }
}
