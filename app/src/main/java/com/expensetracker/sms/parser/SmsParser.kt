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
    /** Account-holder names for parse-time own-account Transfer detection. */
    private val ownerNames: () -> List<String> = { emptyList() },
) {

    fun parse(sms: RawSms): Transaction? {
        val body = sms.body.trim()
        if (body.isEmpty()) return null
        if (!isTransactional(body)) return null

        val amount = SmsAmountExtractor.extract(body) ?: return null
        val type = detectType(body)
        val bank = BankSenders.identify(sms.sender)
        val merchantMatch = merchants.match(body)
        val labeled = labelRules.match(sms.sender, body, merchantMatch?.displayName ?: extractMerchant(body))
        // Ledger-correctness categories (Transfer/Refund) must win over merchant/dictionary
        // matches — e.g. "Refund from AMAZON" must not be booked as Shopping income.
        val category = labeled ?: resolveAutoCategory(body, type, merchantMatch?.category)
        val paymentMode = detectPaymentMode(body)
        // Prefer date written in the SMS (paste/share import has no telephony timestamp).
        val timestamp = SmsDateExtractor.extract(body, zone, sms.timestamp)

        val accountLast4 = ACCOUNT_LAST4.find(body)?.groupValues?.get(1)
        val cardLast4 = CARD_LAST4.find(body)?.groupValues?.get(1)
        val upiId = UPI_ID.find(body)?.value
        val merchant = resolveMerchant(merchantMatch?.displayName ?: extractMerchant(body), upiId)
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
            upper.contains("CREDIT CARD") || upper.contains("CC ") ||
                Regex("""(?i)\bcard(?:\s*no\.?)?\s*xx?\d{2,4}\b""").containsMatchIn(body) ||
                (upper.contains("CARD XX") && (upper.contains("AVL LMT") || upper.contains("AVL LIMIT") || upper.contains("SPENT"))) ->
                PaymentMode.CARD_CREDIT
            upper.contains("DEBIT CARD") || upper.contains("DC ") -> PaymentMode.CARD_DEBIT
            // ICICI often omits Credit/Debit: "spent using ICICI Bank Card XX1014 … Avl Limit"
            upper.contains("BANK CARD") -> PaymentMode.CARD_CREDIT
            upper.contains("UPI") || UPI_ID.containsMatchIn(body) -> PaymentMode.UPI
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
        MERCHANT_TOWARDS.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        MERCHANT_TO.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        MERCHANT_BY.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // ICICI savings UPI: "debited for Rs X; WHISKEY JUNCTIO credited. UPI:…"
        MERCHANT_CREDITED.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // POS / branch location: "debited … on +SECTOR 18  03-07-2026"
        MERCHANT_ON_LOCATION.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1].trimStart('+')) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // "on 01-Sep-26 on GOODCHOICE PREM" — skip the date, keep the merchant.
        MERCHANT_ON.findAll(body).forEach { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@forEach
            if (looksLikeDateToken(name)) return@forEach
            if (isPlausibleMerchant(name)) return name
        }
        // "for UPI/123456-SWIGGY", "for UPI-123456-CASA DON", or "for UPI/SWIGGY".
        UPI_MERCHANT.find(body)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd('.', ',')
            if (name.length >= 2 && isPlausibleMerchant(name)) return name
        }
        // Axis compact: "UPI/P2A/824994710955/ASMITA SINGH DO SH"
        UPI_P2_PAYEE.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // Axis compact card template: merchant sits on its own line after the timestamp.
        AXIS_LINE_MERCHANT.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // Last resort for Axis multi-line: line after IST that isn't boilerplate.
        AXIS_FALLBACK_MERCHANT.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        extractCompactPayeeLine(body)?.let { return it }
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

    /** Prefer a full VPA (`user@okicici`) over the local-part captured by `to …`. */
    private fun resolveMerchant(extracted: String?, upiId: String?): String? {
        if (upiId != null && extracted != null &&
            upiId.startsWith(extracted, ignoreCase = true)
        ) {
            return upiId
        }
        return extracted ?: upiId
    }

    /**
     * Reject phone numbers, SMS keywords, and dispute helplines that `\bto\s+`
     * greedily captures from credit-limit / card-alert footers.
     */
    private fun isPlausibleMerchant(name: String): Boolean {
        val compact = name.replace(Regex("""[\s./-]"""), "")
        if (compact.isEmpty()) return false
        // Pure phone / shortcode / UPI numeric suffix (e.g. "9215676766", "11389").
        if (compact.all { it.isDigit() } && compact.length in 3..13) return false
        // Bare UPI reference masquerading as a merchant: "UPI-43487520125".
        if (Regex("""(?i)^UPI[-/]?\d{6,}$""").matches(name.trim())) return false
        val upper = name.uppercase()
        if (upper.startsWith("DISPUTE")) return false
        if (upper.startsWith("SMS")) return false
        // Currency tokens only — do not reject merchants like "RSP*DISTRIC".
        if (Regex("""(?i)^(?:RS\.?|INR|₹)(?:\s|$)""").containsMatchIn(name.trim())) return false
        if (upper.startsWith("IF NOT")) return false
        if (looksLikeDateToken(name)) return false
        if (DISPUTE_OR_HELPLINE.containsMatchIn(name)) return false
        if (PHONE_HEAVY_MERCHANT.containsMatchIn(name)) return false
        return true
    }

    /** "01-Sep-26" / "12-01-24" / "15-APR" captured by `\bon` before the real merchant. */
    private fun looksLikeDateToken(name: String): Boolean = DATE_LIKE_MERCHANT.containsMatchIn(name.trim())

    /**
     * Axis compact card-spend puts the merchant on its own line:
     * `Spent INR 1548` / card / `28-08-26 18:34:34 IST` / `SWIGGY FOOD` / Avl Limit.
     */
    private fun extractCompactPayeeLine(body: String): String? {
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.length < 3) continue
            val upper = trimmed.uppercase()
            if (COMPACT_PAYEE_SKIP.containsMatchIn(upper)) continue
            if (looksLikeDateToken(trimmed) || DATE_TIME_LINE.containsMatchIn(trimmed)) continue
            val name = sanitizeMerchantCandidate(trimmed) ?: continue
            if (isPlausibleMerchant(name)) return name
        }
        return null
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
            // Card banks append "convert this txn to EMI" footers — that is not an EMI debit.
            isEmiDebit(upper) -> Categories.EMI
            upper.contains("RENT") -> Categories.RENT
            upper.contains("INSURANCE") || upper.contains("INS PREMIUM") || upper.contains("PREMIUM PAID") ->
                Categories.INSURANCE
            isInvestmentNarration(upper) -> Categories.INVESTMENT
            isTaxPayment(upper) -> Categories.TAXES
            upper.contains("RECHARGE") -> Categories.RECHARGE
            upper.contains("FUEL") || upper.contains("PETROL") || upper.contains("DIESEL") ->
                Categories.FUEL
            upper.contains("ELECTRICITY") || upper.contains("WATER BILL") || upper.contains("GAS BILL") ||
                upper.contains("IGL") || upper.contains("IOAGPL") || upper.contains("BILLDESK") ->
                Categories.UTILITIES
            // Home-loan / RACPC ACH installments.
            upper.contains("RACPC") -> Categories.EMI
            // PPF/EPF SI credits are savings inflows to another own account — not income.
            upper.contains("PPF") || upper.contains("EPF") || upper.contains("PROVIDENT FUND") ->
                Categories.TRANSFER
            // One-sided NEFT/IMPS/RTGS account move with no known merchant —
            // account-to-account transfer: shown in Activity, excluded from spend.
            (upper.contains("NEFT") || upper.contains("IMPS") || upper.contains("RTGS")) &&
                (upper.contains("A/C") || upper.contains("ACCT") || upper.contains("ACCOUNT")) ->
                Categories.TRANSFER
            type == TransactionType.CREDIT -> Categories.TRANSFER
            else -> Categories.OTHERS
        }
    }

    private fun isTaxPayment(upper: String): Boolean {
        return upper.contains("CHALLAN") ||
            upper.contains("INTERNET TAX") ||
            upper.contains("INCOME TAX") ||
            upper.contains("E-FILING") ||
            upper.contains("EFILING") ||
            (upper.contains("ASSESSMENT YEAR") && upper.contains("PAN")) ||
            upper.contains("ITD") && upper.contains("PAID")
    }

    /** True EMI installment — not card-spend footers offering EMI conversion. */
    private fun isEmiDebit(upper: String): Boolean {
        if (!upper.contains("EMI")) return false
        if (upper.contains("EMI CONVERSION") ||
            upper.contains("CONVERT THIS TXN TO EMI") ||
            upper.contains("CONVERT TO EMI") ||
            upper.contains("TO EMI GIVE") ||
            upper.contains("KNOW MORE ABOUT EMI")
        ) {
            return false
        }
        return true
    }

    /** Mutual-fund / broker / AMC purchase SMS (bank ACH or AMC confirmation). */
    private fun isInvestmentNarration(upper: String): Boolean {
        // PPF / EPF credits are savings movements, not spend/income — handled as Transfer below.
        if (upper.contains("MUTUAL FUND") || upper.contains("SIP") ||
            upper.contains("ZERODHA") || upper.contains("GROWW") ||
            upper.contains("SCRIPBOX") || upper.contains("MOTILAL") ||
            upper.contains("ETMONEY") || upper.contains("ET MONEY") ||
            upper.contains("IPRUMF") || upper.contains("ICICI PRUDENTIAL")
        ) {
            return true
        }
        // AMC confirmations: "Dear Investor, Your Purchase of Rs… Folio … NAV…"
        if (upper.contains("DEAR INVESTOR") &&
            (upper.contains("FOLIO") || upper.contains("NAV") || upper.contains("UNITS"))
        ) {
            return true
        }
        if (upper.contains("FOLIO") && (upper.contains("PURCHASE") || upper.contains("NAV"))) {
            return true
        }
        return false
    }

    /**
     * Card bill pays and account-to-account moves must not inflate “spend”
     * (card spends already hit the ledger when the purchase SMS arrived).
     */
    private fun isSelfOrCardTransfer(upper: String): Boolean {
        if (upper.contains("BILLPAY") || upper.contains("BILL PAY")) return true
        if (upper.contains("CREDIT CARD PAYMENT") || upper.contains("CC PAYMENT")) return true
        // Own-account move: "Transferred Rs.5000 from A/C x1234 to A/C x5678"
        val accountLast4s = ACCOUNT_LAST4.findAll(upper).map { it.groupValues[1] }.distinct().toList()
        if (accountLast4s.size >= 2 &&
            (upper.contains("TRANSFERRED") || upper.contains("TRANSFER FROM") ||
                (upper.contains("FROM") && upper.contains("TO") &&
                    (upper.contains("A/C") || upper.contains("ACCT") || upper.contains("ACCOUNT"))))
        ) {
            return true
        }
        if (upper.contains("CREDIT CARD BILL") || upper.contains("CC BILL")) return true
        // Axis compact card-bill template: "CRD-PMNT-530562****0887"
        if (upper.contains("CRD-PMNT") || upper.contains("CRD PMNT") || upper.contains("CRDPMNT")) return true
        // ICICI narration code for automatic bill-payment debits: "InfoATD*Auto Debi"
        if (ATD_AUTO_DEBIT.containsMatchIn(upper)) return true
        // ICICI bill-pay via NEFT: "InfoBIL*NEFT*IN12" — card/utility bill, not spend.
        if (BIL_NEFT.containsMatchIn(upper)) return true
        // Recurring Axis ACH to another bank — typically a card/loan EMI mandate.
        if (Regex("""ACH[- ]?DR[- ]?HDFC""").containsMatchIn(upper)) return true
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
        // Owner-name hint on a bank/UPI transfer SMS → own-account move.
        // ICICI: "Acct XX293 debited for Rs X; ANURAG SINGH credited. UPI:…"
        // Axis:  "UPI/P2A/…/ANURAG SI/ICIC/Paym"
        val names = ownerNames().map { it.trim() }.filter { it.length >= 2 }
        if (names.isNotEmpty() &&
            names.any { name -> upper.contains(name.uppercase()) } &&
            (upper.contains("NEFT") || upper.contains("IMPS") || upper.contains("RTGS") ||
                upper.contains("TRANSFERRED") || upper.contains("TRANSFER") ||
                upper.contains("UPI") || upper.contains("P2A"))
        ) {
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
            // Use the full normalized body so same-amount same-minute UPI rows
            // without a reference number don't collide and silently drop.
            if (reference.isNullOrBlank()) append(body.replace(Regex("\\s+"), " ").take(256))
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
            "CHARGED", "TXN OF", "TRANSFERRED", "SENT", "PAYMENT OF", "DEBITED BY",
        )
        private val CREDIT_WORDS = listOf(
            "CREDITED", "CREDIT", "RECEIVED", "REFUND", "DEPOSITED", "SALARY",
        )
        private val ACCOUNT_LAST4 = Regex("""(?i)a/c(?:\s*(?:no)?\.?)?\s*[Xx*]{0,6}(\d{4})""")
        private val CARD_LAST4 =
            Regex("""(?i)card(?:\s*(?:no)?\.?)?\s*(?:ending(?:\s*with)?)?\s*(?:[Xx*]{2,})?\s*(\d{4})\b""")
        private val UPI_ID = Regex("""\b[a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,}\b""")
        // ICICI uses dash: "for UPI-811309422116-CASA DON"; also "for UPI/123456-SWIGGY".
        private val UPI_MERCHANT =
            Regex(
                """(?i)\bfor\s+UPI[-/](?:[0-9]{6,}[-/])?([A-Za-z][A-Za-z0-9 .&'*]{1,38}?)(?=\s+(?:via|on|through|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""",
            )
        // Axis: "UPI/P2A/824994710955/ASMITA SINGH DO SH" or "UPI/P2M/148918812356/ROSHAN KUMAR PRASAD"
        private val UPI_P2_PAYEE =
            Regex("""(?i)\bUPI/P2[AM]/[A-Z0-9]{6,}/([^\n]+)""")
        private val COMPACT_PAYEE_SKIP =
            Regex(
                """(?i)(?:debited|credited|spent|\bavl\b|not you|sms\s+block|a/c|acct|\baccount\b|\bcard\b|\bbank\b|upi/|\binr\b|\brs\.?\b|₹)""",
            )
        private val DATE_TIME_LINE =
            Regex("""^\d{1,2}[./\-]\d{1,2}[./\-]\d{2,4}""")
        // Stop before common trailing clauses (via/on/UPI/ref/dispute).
        private val MERCHANT_AT =
            Regex("""(?i)\bat\s+([A-Z0-9+][A-Z0-9 +.&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute|\d{1,2}[./-]\d{1,2})|\s*[.,;]|$)""")
        private val MERCHANT_TO =
            Regex("""(?i)\bto\s+([A-Z0-9][A-Z0-9 .&'*/@-]{2,40}?)(?=\s+(?:via|on|for|through|upi|ref|avl|bal|info|if\s+not|to\s+dispute)|\s*[.,;]|$)""")
        // ICICI: "spent using … Card XX1014 on 01-Sep-26 on GOODCHOICE PREM. Avl Limit"
        private val MERCHANT_ON =
            Regex("""(?i)\bon\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|for|through|upi|ref|avl|bal|info|if\s+not|to\s+dispute)|\s*[.,;]|$)""")
        private val DATE_LIKE_MERCHANT =
            Regex("""(?i)^\d{1,2}[./\-]\s*(?:\d{1,2}|[A-Za-z]{3,9})(?:[./\-]\s*\d{2,4})?\b""")
        private val MERCHANT_BY =
            Regex("""(?i)\bby\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""")
        // "towards OpenAI LLC for INR" / "towards Google Play for GOOGLE AutoPay"
        private val MERCHANT_TOWARDS =
            Regex("""(?i)\btowards\s+([A-Z0-9][A-Z0-9 ,.&'*]{1,50}?)(?=\s+for\b|\s*[.,;]|$)""")
        // "; WHISKEY JUNCTIO credited" / "; U P Power Corpo credited"
        private val MERCHANT_CREDITED =
            Regex("""(?i);\s*([A-Z0-9][A-Z0-9 .&'*]{1,40}?)\s+credited\b""")
        // "on +SECTOR 18  03-07-2026" / "on CONNAUGHT PLACE 03-07-2026"
        private val MERCHANT_ON_LOCATION =
            Regex("""(?i)\bon\s+(\+?[A-Z][A-Z0-9 +.&'/-]{1,40}?)(?=\s+\d{1,2}[./-]\d{1,2}|\s{2,}\d{1,2}[./-])""")
        // Axis multi-line: timestamp line, then merchant, then Avl Limit / Not you
        private val AXIS_LINE_MERCHANT =
            Regex(
                """(?im)(?:IST|\d{2}:\d{2}(?::\d{2})?)\s*\r?\n\s*([A-Z0-9*][A-Z0-9* .&'/_-]{1,40})\s*\r?\n\s*(?:Avl|Available|Not\s+you|WhatsApp)""",
            )
        /** Broader Axis fallback when Avl line wording varies. */
        private val AXIS_FALLBACK_MERCHANT =
            Regex("""(?im)\bIST\s*\r?\n\s*([A-Z0-9*][A-Z0-9* .&'/_-]{1,40})\s*(?:\r?\n|$)""")
        private val DISPUTE_OR_HELPLINE =
            Regex("""(?i)\b(?:dispute|helpline|customer\s+care|toll\s*free)\b""")
        private val PHONE_HEAVY_MERCHANT =
            Regex("""(?i)(?:\d[\d\s/-]{6,}\d)|(?:\b\d{4,}[-/]\d{4,}\b)""")
        /**
         * ICICI's narration code for automatic bill-payment debits: "InfoATD*Auto Debi"
         * (no word boundary before ATD — it's glued directly onto "Info" in the SMS).
         */
        private val ATD_AUTO_DEBIT = Regex("""ATD\s*\*?\s*AUTO\s*DEBI""", RegexOption.IGNORE_CASE)
        /** ICICI bill-pay via NEFT: "InfoBIL*NEFT*IN12". */
        private val BIL_NEFT = Regex("""BIL\s*\*?\s*NEFT""", RegexOption.IGNORE_CASE)
        private val REFERENCE_PATTERNS = listOf(
            Regex("""(?i)(?:ref(?:erence)?(?:\s*no)?\.?|txn(?:\s*id)?\.?|utr)[:\s#]*([A-Z0-9]{6,})"""),
            Regex("""(?i)UPI(?:\s*ref)?[:\s]*([0-9]{9,})"""),
            // Axis compact UPI: "UPI/P2A/824994710955/ASMITA SINGH"
            Regex("""(?i)UPI/P2[AM]/([0-9]{9,})"""),
            // Axis inbound UPI self-credit: "UPI/P2A/024744670304/ANURAG SI/ICIC/Paym"
            Regex("""(?i)UPI/[A-Z0-9]{1,6}/([0-9]{9,})"""),
            // ICICI card UPI: "UPI-002104929024-Airtel"
            Regex("""(?i)UPI-([0-9]{9,})"""),
            // Axis NEFT/IMPS compact template: "NEFT/MB/AXOMB16602145999/V"
            Regex("""(?i)(?:NEFT|IMPS|RTGS)/[A-Z]{1,3}/([A-Z0-9]{8,})"""),
            // Axis "Info - NEFT/IN12624453192288/ANUR" (no 1–3 letter bank-code slot)
            Regex("""(?i)(?:NEFT|IMPS|RTGS)/([A-Z0-9]{8,})"""),
            // Axis card-payment compact template: "CRD-PMNT-530562****0887"
            Regex("""(?i)CRD[- ]?PMNT[- ]?([A-Z0-9*]{6,})"""),
        )
        private val BALANCE_PATTERN = Regex(
            """(?i)(?:avl\.?\s*bal|available\s*balance|bal(?:ance)?)[:\s]*(?:rs\.?|inr|₹)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        )
        private val INFO_PATTERN = Regex("""(?i)info[:\-\s]+([A-Z0-9 ./*-]{3,60})""")
    }
}
