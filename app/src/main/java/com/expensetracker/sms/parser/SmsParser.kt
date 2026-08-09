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
        val ppfDeposit = isPpfDepositPosting(body)
        // PPF SI / "credited in PPF" is money parked in PPF — book as DEBIT Investment
        // even when the bank wording uses "credited". Contribution-received thank-you
        // SMS are rejected earlier by TransactionGate.
        val type = if (ppfDeposit) TransactionType.DEBIT else detectType(body)
        val bank = BankSenders.identify(sms.sender)
        val merchantMatch = merchants.match(body)
        val merchant = when {
            ppfDeposit -> "PPF"
            else -> merchantMatch?.displayName ?: extractMerchant(body)
        }
        val labeled = labelRules.match(sms.sender, body, merchant)
        // Ledger-correctness categories (Transfer/Refund/Investment-PPF) must win over
        // merchant/dictionary matches — e.g. "Refund from AMAZON" must not be Shopping.
        val category = when {
            ppfDeposit -> Categories.INVESTMENT
            else -> labeled ?: resolveAutoCategory(body, type, merchantMatch?.category)
        }
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
            upper.contains("CREDIT CARD") || Regex("""(?i)\bCC\b""").containsMatchIn(body) ->
                PaymentMode.CARD_CREDIT
            upper.contains("DEBIT CARD") || Regex("""(?i)\bDC\b""").containsMatchIn(body) ->
                PaymentMode.CARD_DEBIT
            upper.contains("NACH") || upper.contains("ECS") || upper.contains("UMRN") ||
                upper.contains("ACH DEBIT") || upper.contains("ACH-DR") ||
                upper.contains("ACH/DR") || upper.contains("ACH DR") ||
                upper.contains("ACH*") || ACH_DR_LINE.containsMatchIn(body) ->
                PaymentMode.NET_BANKING
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
        // "NACH debit towards SCRIPBOXWEALTHMANAGE for INR …"
        MERCHANT_TOWARDS.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        MERCHANT_TO.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // ICICI UPI payee: "Acct XX293 debited …; MotilalOswalMF credited. UPI:…"
        ICICI_PAYEE_CREDITED.find(body)?.let { match ->
            val name = sanitizeMerchantCandidate(match.groupValues[1]) ?: return@let
            if (isPlausibleMerchant(name)) return name
        }
        // "for UPI/123456-SWIGGY" or "for UPI/SWIGGY" — the name after the slash/dash.
        UPI_MERCHANT.find(body)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd('.', ',')
            if (name.length >= 2 && isPlausibleMerchant(name)) return name
        }
        // Axis compact: "UPI/P2A/<ref>/<PAYEE>" or "UPI/P2M/<ref>/<MERCHANT>"
        UPI_PATH_PAYEE.find(body)?.let { match ->
            val name = match.groupValues[1].trim().trimEnd('.', ',', '/')
            if (name.length >= 2 && isPlausibleMerchant(name)) return name
        }
        // Axis compact ACH: "ACH-DR-HDFC BANK LTD-47138"
        ACH_DR_LINE.find(body)?.let { match ->
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
        // IMPS "by Account linked to mobile number XXXXX00000" — not a merchant.
        if (upper.contains("MOBILE NUMBER") || upper.contains("LINKED TO MOBILE")) return false
        if (Regex("""(?i)\bX{3,}\d{2,}\b""").containsMatchIn(name)) return false
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

    /**
     * Authoritative PPF deposit posting (SI / credited-in-PPF / deduction).
     * Contribution-received thank-you SMS are rejected by [TransactionGate] instead.
     */
    private fun isPpfDepositPosting(body: String): Boolean {
        val upper = body.uppercase()
        if (!upper.contains("PPF") && !upper.contains("PUBLIC PROVIDENT")) return false
        return upper.contains("SI TRANSACTION") ||
            upper.contains("STANDING INSTRUCTION") ||
            upper.contains("DEBITED") ||
            upper.contains("DEDUCTION") ||
            upper.contains("CREDITED IN PPF") ||
            upper.contains("CREDITED TO PPF") ||
            upper.contains("CREDITED IN YOUR PPF") ||
            upper.contains("DEPOSIT") && (upper.contains("PPF") || upper.contains("PUBLIC PROVIDENT"))
    }

    private fun inferCategory(body: String, type: TransactionType): String {
        val upper = body.uppercase()
        return when {
            type == TransactionType.CREDIT && (upper.contains("SALARY") || upper.contains("SAL CR")) -> Categories.SALARY
            isSelfOrCardTransfer(upper) -> Categories.TRANSFER
            upper.contains("ATM") || upper.contains("CASH WDL") -> Categories.CASH_WITHDRAWAL
            upper.contains("EMI") || isAchLoanEmi(upper) -> Categories.EMI
            upper.contains("RENT") -> Categories.RENT
            upper.contains("INSURANCE") || upper.contains("INS PREMIUM") || upper.contains("PREMIUM PAID") ->
                Categories.INSURANCE
            upper.contains("MUTUAL FUND") || upper.contains("SIP") || upper.contains("ZERODHA") ||
                upper.contains("GROWW") || upper.contains("PPF") || upper.contains("PUBLIC PROVIDENT") ||
                upper.contains("NPS") || upper.contains("NATIONAL PENSION") ||
                upper.contains("SCRIPBOX") || upper.contains("WEALTHMANAGE") ||
                upper.contains("WEALTH MANAGE") || upper.contains("FISDOM") ||
                upper.contains("INDMONEY") || upper.contains("FUNDSINDIA") ||
                // Mandate collect to an investment platform (NACH/ECS/ACH-DR + wealth/MF).
                (isMandateCollectRail(upper) &&
                    (upper.contains("WEALTH") || upper.contains("MUTUAL") ||
                        upper.contains("INVEST") || upper.contains("GROWW") ||
                        upper.contains("SCRIPBOX"))) ->
                Categories.INVESTMENT
            upper.contains("RECHARGE") -> Categories.RECHARGE
            upper.contains("ELECTRICITY") || upper.contains("WATER BILL") || upper.contains("GAS BILL") ->
                Categories.UTILITIES
            // One-sided NEFT/IMPS/RTGS account move with no known merchant —
            // account-to-account transfer: shown in Activity, excluded from spend.
            (upper.contains("NEFT") || upper.contains("IMPS") || upper.contains("RTGS")) &&
                (upper.contains("A/C") || upper.contains("ACCT") || upper.contains("ACCOUNT")) ->
                Categories.TRANSFER
            type == TransactionType.CREDIT -> Categories.TRANSFER
            else -> Categories.OTHERS
        }
    }

    private fun isMandateCollectRail(upper: String): Boolean =
        upper.contains("ACH-DR") || upper.contains("ACH/DR") ||
            upper.contains("ACH DR") || upper.contains("ACH DEBIT") ||
            upper.contains("NACH") || upper.contains("ECS") || upper.contains("UMRN") ||
            upper.contains("ACH*") || ACH_DR_LINE.containsMatchIn(upper)

    /**
     * Axis/HDFC compact ACH loan collect: `ACH-DR-HDFC BANK LTD-47138`,
     * ICICI `InfoACH*RACPC CUM`. Bank/NBFC collectors on ACH/NACH/ECS rails are
     * EMI — unless the payee is clearly an investment platform.
     */
    private fun isAchLoanEmi(upper: String): Boolean {
        if (!isMandateCollectRail(upper)) return false
        if (INVESTMENT_PAYEE_HINTS.any { upper.contains(it) }) return false
        if (upper.contains("WEALTH") || upper.contains("MUTUAL") || upper.contains("INVEST")) return false
        if (upper.contains("RACPC")) return true
        return LOAN_EMI_COLLECTOR_HINTS.any { upper.contains(it) }
    }

    /**
     * Card bill pays and account-to-account moves must not inflate “spend”
     * (card spends already hit the ledger when the purchase SMS arrived).
     */
    private fun isSelfOrCardTransfer(upper: String): Boolean {
        if (upper.contains("BILLPAY") || upper.contains("BILL PAY")) return true
        if (upper.contains("CREDIT CARD PAYMENT") || upper.contains("CC PAYMENT")) return true
        if (upper.contains("CREDIT CARD BILL") || upper.contains("CC BILL")) return true
        // Axis compact card-bill template: "CRD-PMNT-530562****0887"
        if (upper.contains("CRD-PMNT") || upper.contains("CRD PMNT") || upper.contains("CRDPMNT")) return true
        // ICICI narration code for automatic bill-payment debits: "InfoATD*Auto Debi"
        if (ATD_AUTO_DEBIT.containsMatchIn(upper)) return true
        if (upper.contains("TOWARDS") && upper.contains("CARD")) return true
        if (upper.contains("PAYMENT TO") && (upper.contains("CREDIT CARD") || upper.contains(" CC "))) return true
        if (upper.contains("CREDITED TO YOUR CARD") || upper.contains("CREDITED TO YOUR CC")) return true
        // Explicit self-transfer wording from banks / UPI apps.
        if (upper.contains("SELF TRANSFER") || upper.contains("SELF-TRANSFER") ||
            upper.contains("TO SELF") || upper.contains("FROM SELF")
        ) {
            return true
        }
        // IMPS/NEFT/UPI self-move templates: "Acct A debited ... NAME credited".
        // Skip when the payee looks like an MF/broker — those are Investment, not Transfer.
        if (upper.contains("DEBITED") && upper.contains("CREDITED") &&
            (upper.contains("IMPS") || upper.contains("NEFT") || upper.contains("RTGS") ||
                upper.contains("UPI"))
        ) {
            if (!looksLikeInvestmentPayee(upper)) return true
        }
        if (Regex("""ACCT\s+XX\d+\s+DEBITED.*ACCT\s+XX\d+\s+CREDITED""").containsMatchIn(upper)) {
            return true
        }
        // Owner-name hint on a bank transfer / UPI self-move SMS → own-account move.
        val names = ownerNames().map { it.trim() }.filter { it.length >= 2 }
        if (names.isNotEmpty() &&
            names.any { name -> upper.contains(name.uppercase()) } &&
            (upper.contains("NEFT") || upper.contains("IMPS") || upper.contains("RTGS") ||
                upper.contains("UPI") ||
                upper.contains("TRANSFERRED") || upper.contains("TRANSFER"))
        ) {
            return true
        }
        return false
    }

    /** MF/broker payee on a "X credited" UPI/IMPS debit — book as Investment, not Transfer. */
    private fun looksLikeInvestmentPayee(upper: String): Boolean {
        if (INVESTMENT_PAYEE_HINTS.any { upper.contains(it) }) return true
        // "; MotilalOswalMF credited" / "GROWW credited" — dictionary keyword before credited.
        val payee = ICICI_PAYEE_CREDITED.find(upper)?.groupValues?.getOrNull(1)
        if (!payee.isNullOrBlank() &&
            MerchantDictionary.match(payee)?.category == Categories.INVESTMENT
        ) {
            return true
        }
        return MerchantDictionary.match(upper)?.category == Categories.INVESTMENT
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
        // ICICI glued narration: "InfoACH*RACPC CUM" (no colon/space after Info).
        INFO_ACH_GLUED.find(body)?.let { return it.groupValues[1].trim() }
        ACH_DR_LINE.find(body)?.let { match ->
            val payee = match.groupValues[1].trim()
            val ref = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            return if (ref != null) "ACH-DR-$payee-$ref" else "ACH-DR-$payee"
        }
        return null
    }

    private fun computeHash(
        sender: String?,
        amount: BigDecimal,
        timestamp: Instant,
        reference: String?,
        body: String,
    ): String {
        val umrn = UMRN_PATTERN.find(body)?.groupValues?.getOrNull(1)
        // Mandate UMRN uniquely identifies one collect — drop minute so SMS retries
        // ("today", redelivered a few minutes later) do not create duplicate rows.
        val key = if (!umrn.isNullOrBlank()) {
            buildString {
                append(sender.orEmpty().uppercase()).append('|')
                append(amount.toPlainString()).append('|')
                append("UMRN:").append(umrn.uppercase())
            }
        } else {
            val minute = timestamp.epochSecond / 60
            buildString {
                append(sender.orEmpty().uppercase()).append('|')
                append(amount.toPlainString()).append('|')
                append(minute).append('|')
                append(reference.orEmpty()).append('|')
                // Use the full normalized body so same-amount same-minute UPI rows
                // without a reference number don't collide and silently drop.
                if (reference.isNullOrBlank()) append(body.replace(Regex("\\s+"), " ").take(256))
            }
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
        // "A/c XX8291" / "Acc XX293" / "Acct XX293" / "Account XX8291" (3–4 trailing digits).
        private val ACCOUNT_LAST4 = Regex(
            """(?i)(?:a/c(?:\s*(?:no)?\.?)?|acct|acc(?:ount)?)\s*[Xx*]{2,}(\d{3,4})""",
        )
        private val UMRN_PATTERN = Regex("""(?i)UMRN[:\s]*([A-Z0-9]{6,})""")
        private val INFO_ACH_GLUED = Regex("""(?i)info\s*(ACH\*[A-Z0-9 ./*-]{2,40})""")
        private val CARD_LAST4 =
            Regex("""(?i)card(?:\s*(?:no)?\.?)?\s*(?:ending(?:\s*with)?)?\s*(?:[Xx*]{2,})?\s*(\d{4})\b""")
        private val UPI_ID = Regex("""\b[a-zA-Z0-9._-]{2,}@[a-zA-Z]{2,}\b""")
        // "for UPI/123456-SWIGGY" or "for UPI/SWIGGY" — capture the name part.
        private val UPI_MERCHANT =
            Regex("""(?i)\bfor\s+UPI/[A-Z0-9]*[-/]?([A-Z][A-Z0-9 .&'*]{1,38}?)(?=\s+(?:via|on|through|ref|avl|bal|info)|\s*[.,;]|$)""")
        // Stop before common trailing clauses (via/on/UPI/ref/dispute).
        private val MERCHANT_AT =
            Regex("""(?i)\bat\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""")
        private val MERCHANT_TO =
            Regex("""(?i)\bto\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,40}?)(?=\s+(?:via|on|through|upi|ref|avl|bal|info|to\s+dispute)|\s*[.,;]|$)""")
        private val MERCHANT_TOWARDS =
            Regex("""(?i)\btowards\s+([A-Z0-9][A-Z0-9 .&'*/-]{2,48}?)(?=\s+(?:for|with|on|via|umrn|in\s+a|ref)|\s*[.,;]|$)""")
        private val DISPUTE_OR_HELPLINE =
            Regex("""(?i)\b(?:dispute|helpline|customer\s+care|toll\s*free)\b""")
        private val PHONE_HEAVY_MERCHANT =
            Regex("""(?i)(?:\d[\d\s/-]{6,}\d)|(?:\b\d{4,}[-/]\d{4,}\b)""")
        /**
         * ICICI's narration code for automatic bill-payment debits: "InfoATD*Auto Debi"
         * (no word boundary before ATD — it's glued directly onto "Info" in the SMS).
         */
        private val ATD_AUTO_DEBIT = Regex("""ATD\s*\*?\s*AUTO\s*DEBI""", RegexOption.IGNORE_CASE)
        // Axis compact UPI: "UPI/P2A/111991242206/LALAWMPUII"
        private val UPI_PATH_PAYEE =
            Regex("""(?i)\bUPI/(?:P2A|P2M|P2P)/[0-9]{6,}/([A-Z][A-Z0-9 .&'*_-]{1,40})""")
        // ICICI: "…; MotilalOswalMF credited. UPI:…" / "… & Acct XX791 credited"
        private val ICICI_PAYEE_CREDITED =
            Regex(
                """(?i)(?:;|&)\s*([A-Z][A-Z0-9 .&'*_-]{1,40}?)\s+credited\b""",
            )
        private val INVESTMENT_PAYEE_HINTS = listOf(
            "MUTUAL FUND", " MOTILAL", "MOTILALOSWAL", "GROWW", "ZERODHA", "UPSTOX",
            "ETMONEY", "ET MONEY", "SCRIPBOX", "FISDOM", "INDMONEY", "KUVERA",
            "SMALLCASE", "PAYTM MONEY", "ANGELONE", "ANGEL ONE", "MOAMC",
            "WEALTHMANAGE", "FUNDSINDIA",
            // Compact UPI payee tokens often end with MF (MotilalOswalMF, AxisMF, …).
            "MF CREDITED",
        )
        // Axis compact ACH debit line: "ACH-DR-HDFC BANK LTD-47138"
        private val ACH_DR_LINE = Regex(
            """(?im)^\s*ACH[-/ ]?DR[-/ ]([A-Z][A-Z0-9 .&']+?)(?:[-/ ](\d{3,}))?\s*$""",
        )
        private val LOAN_EMI_COLLECTOR_HINTS = listOf(
            "HDFC BANK", "ICICI BANK", "AXIS BANK", "KOTAK", "STATE BANK", " SBI",
            "BANK LTD", "BANK LIMITED", "BAJAJ FINANCE", "BAJAJ FINSERV",
            "TATA CAPITAL", "FULLERTON", "HOME CREDIT", "IDFC", "INDUSIND",
            "YES BANK", "RBL BANK", "FEDERAL BANK", "BANK OF BARODA", "PNB ",
            "PUNJAB NATIONAL", "CANARA BANK", "UNION BANK", "LOAN", "FINANCE LTD",
            "FINSERV", "HOME LOAN", "PERSONAL LOAN",
        )
        private val REFERENCE_PATTERNS = listOf(
            // "IMPS Ref. no. 621321435842" / "Ref no. ABC" / "UPI Ref 123"
            Regex("""(?i)(?:ref(?:erence)?\.?(?:\s*no\.?)?|txn(?:\s*id)?\.?|utr)[:\s#]*([A-Z0-9]{6,})"""),
            Regex("""(?i)UMRN[:\s]*([A-Z0-9]{6,})"""),
            Regex("""(?i)UPI(?:\s*ref)?[:\s]*([0-9]{9,})"""),
            // Axis/ICICI compact UPI path: "UPI/P2A/111991242206/LALAWMPUII"
            Regex("""(?i)UPI/[A-Z0-9]+/([0-9]{9,})"""),
            // Axis NEFT/IMPS compact template: "NEFT/MB/AXOMB16602145999/V" or "IMPS/P2A/…"
            Regex("""(?i)(?:NEFT|IMPS|RTGS)/[A-Z0-9]{1,3}/([A-Z0-9]{8,})"""),
            // Axis card-payment compact template: "CRD-PMNT-530562****0887"
            Regex("""(?i)CRD[- ]?PMNT[- ]?([A-Z0-9*]{6,})"""),
            // Axis ACH compact: "ACH-DR-HDFC BANK LTD-47138" → mandate/loan id
            Regex("""(?i)ACH[-/ ]?DR[-/ ][A-Z0-9 .&']+?[-/ ](\d{3,})\b"""),
        )
        // Prefer full ungrouped amounts (44996.19) before 1–3 digit + comma groups,
        // otherwise "Bal INR 44996.19" incorrectly captures 449.
        private val BALANCE_PATTERN = Regex(
            """(?i)(?:avl\.?\s*bal|available\s*balance|bal(?:ance)?)[:\s]*(?:rs\.?|inr|₹)?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        )
        private val INFO_PATTERN = Regex("""(?i)info[:\-\s]+([A-Z0-9 ./*-]{3,60})""")
    }
}
