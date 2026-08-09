package com.expensetracker.sms.parser

/**
 * Rejects OTPs, marketing, loan offers, and other non-ledger SMS before parsing.
 *
 * Ledger rule (see [com.expensetracker.domain.insights.LedgerPolicy]): only bank/card
 * account *movements* become rows. Acknowledgements from card issuers, merchants, or
 * other third parties ("we received your payment…") are not ledger facts — they
 * duplicate the source account's own debit/credit SMS.
 */
object TransactionGate {

    private val OTP = Regex("""\b(OTP|ONE\s?TIME\s?PASSWORD|VERIFICATION\s?CODE|AUTH\s?CODE)\b""", RegexOption.IGNORE_CASE)

    private val SPAM = listOf(
        Regex("""\bPRE[-\s]?APPROVED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bAPPLY\s+NOW\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCLICK\s+HERE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCASHBACK\b""", RegexOption.IGNORE_CASE),
        Regex("""\bGET\s+UP\s+TO\b""", RegexOption.IGNORE_CASE),
        Regex("""\bWIN\s+(?:RS|INR|₹)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCONGRATULATIONS?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bLIMITED\s+PERIOD\b""", RegexOption.IGNORE_CASE),
        Regex("""\bEXCLUSIVE\s+OFFER\b""", RegexOption.IGNORE_CASE),
        Regex("""\bFLAT\s+\d+%\s*OFF\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d+%\s*OFF\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSHOP\s+NOW\b""", RegexOption.IGNORE_CASE),
        Regex("""\bDOWNLOAD\s+(?:THE\s+)?APP\b""", RegexOption.IGNORE_CASE),
        Regex("""\bLOAN\s+OFFER\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCREDIT\s+CARD\s+OFFER\b""", RegexOption.IGNORE_CASE),
        Regex("""\bREFER\s+(?:AND|&)\s+EARN\b""", RegexOption.IGNORE_CASE),
        Regex("""\bINVITE\s+CODE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bT&C\s+APPLY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bUNSUBSCRIBE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bFREE\s+(?:RS|INR|₹)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bVOUCHER\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCOUPON\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSAVE\s+BIG\b""", RegexOption.IGNORE_CASE),
        Regex("""\bHURRY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bGRAB\s+NOW\b""", RegexOption.IGNORE_CASE),
        Regex("""\bEMI\s+STARTING\b""", RegexOption.IGNORE_CASE),
        Regex("""\bNO\s+COST\s+EMI\b""", RegexOption.IGNORE_CASE),
        Regex("""\bINSTANT\s+APPROVAL\b""", RegexOption.IGNORE_CASE),
        Regex("""\bKYC\s+(?:PENDING|UPDATE|REQUIRED)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bCOMPLETE\s+YOUR\s+KYC\b""", RegexOption.IGNORE_CASE),
        Regex("""\bWANTED\s+TO\s+INFORM\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSPECIAL\s+OFFER\b""", RegexOption.IGNORE_CASE),
        Regex("""\bDISCOUNT\b""", RegexOption.IGNORE_CASE),
        Regex("""\bREWARD\s+POINTS?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bBITS?\s+WILL\s+EXPIRE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPOINTS?\s+EXPIR""", RegexOption.IGNORE_CASE),
        Regex("""\bSTATEMENT\s+(?:IS\s+)?READY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bBILL\s+(?:IS\s+)?GENERATED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPAYMENT\s+DUE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bDUE\s+DATE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bMIN(?:IMUM)?\s+(?:AMT|AMOUNT)\s+DUE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bOUTSTANDING\s+(?:ON|OF)\b""", RegexOption.IGNORE_CASE),
        // Auto-pay / due notices (not completed ledger movements).
        Regex("""\bis\s+due\b""", RegexOption.IGNORE_CASE),
        Regex("""\bamount\s+(?:is\s+)?due\b""", RegexOption.IGNORE_CASE),
        Regex("""\btotal\s+amount\b.{0,48}\bis\s+due\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwill\s+be\s+debited\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwill\s+be\s+auto[-\s]?debited\b""", RegexOption.IGNORE_CASE),
        // Auto-pay / mandate setup notices — the real debit arrives as its own SMS.
        Regex("""\bauto[-\s]?pay\s+(?:is\s+)?(?:activated|enabled|registered|set\s*up)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bauto[-\s]?debit\s+(?:is\s+)?(?:activated|enabled|registered|mandate)\b""", RegexOption.IGNORE_CASE),
        Regex("""\be[-\s]?mandate\b.{0,60}\bregistered\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmandate\s+(?:is\s+)?(?:registered|created|activated)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstanding\s+instruction\b.{0,60}\b(?:registered|activated|set)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bplease\s+ignore\s+if\s+(?:already\s+)?paid\b""", RegexOption.IGNORE_CASE),
        Regex("""\bignore\s+if\s+(?:already\s+)?paid\b""", RegexOption.IGNORE_CASE),
        // Credit-limit marketing / raise-limit instructions (not spends).
        Regex("""\bCRLIM\b""", RegexOption.IGNORE_CASE),
        Regex("""\bincreas(?:e|ing)\s+(?:the\s+)?(?:credit\s+)?limit\b""", RegexOption.IGNORE_CASE),
        Regex("""\braise\s+(?:the\s+)?(?:credit\s+)?limit\b""", RegexOption.IGNORE_CASE),
        Regex("""\bmanage\s+spends?\s+effectively\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:credit\s+)?limit\s+on\b.{0,80}\bfrom\s+(?:rs\.?|inr|₹)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\bREQUESTED\s+OTP\b""", RegexOption.IGNORE_CASE),
        Regex("""\bIS\s+SCHEDULED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSCHEDULED\s+FOR\s+(?:ECS|CLEARANCE)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPLEASE\s+KEEP\s+SUFFICIENT\s+BALANCE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bWILL\s+GET\s+MATURED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bACCOUNT\s+.*\bOPENED\s+SUCCESSFULLY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bHAS\s+BEEN\s+DELIVERED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bOVERDRAFT\s+FACILITY\s+HAS\s+BEEN\s+SANCTIONED\b""", RegexOption.IGNORE_CASE),
        // --- Third-party / card-issuer payment acknowledgements (not ledger movements) ---
        // "Thank you for your payment …" (any card/issuer/merchant ack). Distinct from
        // spend alerts "Thank you for using … Credit Card … at MERCHANT".
        Regex("""\bTHANK\s+YOU\s+FOR\s+(?:YOUR\s+)?PAYMENT\b""", RegexOption.IGNORE_CASE),
        // "We have received your payment…" / "We've received payment of…" from
        // card issuers, billers, or merchants — duplicates the bank debit SMS.
        Regex("""\bWE(?:['’]VE|\s+HAVE)\s+RECEIVED\s+(?:YOUR\s+)?PAYMENT\b""", RegexOption.IGNORE_CASE),
        Regex("""\bRECEIVED\s+(?:YOUR\s+)?PAYMENT\s+(?:OF|FROM|TOWARDS|FOR|AGAINST)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPAYMENT\s+(?:HAS\s+BEEN\s+|WAS\s+)?RECEIVED\s+(?:OF|FROM|TOWARDS|FOR|AGAINST|SUCCESSFULLY)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPAYMENT\s+RECEIVED\s+SUCCESSFULLY\b""", RegexOption.IGNORE_CASE),
        // "Payment of Rs X received successfully" (words between amount and verb).
        Regex("""\bPAYMENT\b.{0,48}\bRECEIVED\s+SUCCESSFULLY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSUCCESSFULLY\s+RECEIVED\s+(?:YOUR\s+)?PAYMENT\b""", RegexOption.IGNORE_CASE),
        // Card-side bill-payment posting: "Payment of Rs X was credited to your card…"
        // — the savings/current debit SMS is the single ledger row to keep.
        Regex(
            """\bPAYMENT\b.{0,80}\bCREDITED\s+TO\s+YOUR\s+(?:CREDIT\s+)?CARD\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\bCREDITED\s+TO\s+YOUR\s+(?:CREDIT\s+)?CARD\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\bPAYMENT\b.{0,48}\bRECEIVED\s+AGAINST\b""", RegexOption.IGNORE_CASE),
        // Investment contribution acknowledgements (PPF/NPS/SIP) — not expenses.
        // Real SI/"credited in PPF"/savings debits are kept and booked as Investment.
        Regex(
            """\bWE\s+(?:HAVE\s+)?RECEIVED\s+(?:YOUR\s+)?(?:CONTRIBUTION|TRANSACTION)\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\bCONTRIBUTION\s+(?:HAS\s+BEEN\s+|WAS\s+)?RECEIVED\b""", RegexOption.IGNORE_CASE),
        Regex(
            """\b(?:SIP|NPS|PPF|PROVIDENT)\b.{0,48}\bCONTRIBUTION\b.{0,48}\bRECEIVED\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\bRECEIVED\b.{0,48}\b(?:SIP|NPS|PPF|PROVIDENT)\b.{0,48}\bCONTRIBUTION\b""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\bRECEIVED\s+IN\s+YOUR\s+(?:PPF|NPS|PUBLIC\s+PROVIDENT)\b""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val PAYMENT_TOWARDS_CARD = Regex(
        """\bPAYMENT\s+(?:OF|TOWARDS|FOR|AGAINST|TO)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CREDIT_OR_YOUR_CARD = Regex(
        """\b(?:CREDIT\s+CARD|YOUR\s+CARD)\b""",
        RegexOption.IGNORE_CASE,
    )
    /** Verbs that prove a real account/card money movement (keep as ledger). */
    private val MOVEMENT_VERBS = listOf(
        "DEBITED", "SPENT", "WITHDRAWN", "WITHDRAWAL", "PURCHASE",
        "USED FOR", "HAS BEEN USED", "IS USED FOR", "WAS USED FOR",
        "DEBIT INR", "DEBIT RS", "DEBIT ₹",
    )

    /**
     * Strong ledger verbs — loose "UPI" / "purchase" / bare "CARD XX" alone is not enough.
     * Card-limit and due notices often mention the card mask without a real spend verb.
     */
    private val STRONG_DEBIT = listOf(
        "HAS BEEN DEBITED", "BEEN DEBITED", "IS DEBITED", "WAS DEBITED",
        "DEBITED WITH", "DEBITED FOR", "DEBITED FROM", "DEBITED VIA",
        "SPENT ON", "SPENT AT", "YOU'VE SPENT", "YOU HAVE SPENT", "SPENT INR", "SPENT RS",
        "WITHDRAWN", "WITHDRAWAL",
        "PURCHASE OF", "PURCHASE ON", "TXN OF", "TRANSACTION OF", "SENT RS", "SENT INR", "SENT ₹",
        "PAID TO", "PAID RS", "PAID INR", "CHARGED", "DR AMT", "DR/",
        "PAYMENT OF", "MADE A PAYMENT",
        "THANK YOU FOR USING",
        // ICICI card-spend templates that skip "debited" entirely.
        "IS USED FOR", "HAS BEEN USED", "BEEN USED FOR", "USED FOR RS", "USED FOR INR",
        "WAS USED FOR", "CARD USED",
        // Axis compact multi-line template: "Debit INR 17383.00\nAxis Bank A/c XX…"
        "DEBIT INR", "DEBIT RS", "DEBIT ₹",
        "TRANSFERRED TO",
        // ICICI "Acc XX293 debited Rs. X on DATE Info..." — bare "debited" + amount,
        // no with/for/from/via connector.
        "DEBITED RS", "DEBITED INR", "DEBITED ₹",
        // Mandate / auto-collect: "NACH debit towards SCRIPBOX… for INR … processed"
        "NACH DEBIT", "ECS DEBIT", "ACH DEBIT", "DEBIT TOWARDS",
    )

    /**
     * Axis (and some other banks) put the currency *before* the verb:
     * "INR 2500.00 debited\nA/c no. XX8291\n…\nUPI/P2A/…".
     * That fails the "DEBITED INR" / "DEBITED FROM" substring checks above.
     */
    private val AMOUNT_THEN_DEBITED = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*[0-9,]+\.?\d*\s+debited\b""",
    )
    private val AMOUNT_THEN_CREDITED = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*[0-9,]+\.?\d*\s+credited\b""",
    )
    private val STRONG_CREDIT = listOf(
        "HAS BEEN CREDITED", "BEEN CREDITED", "CREDITED WITH", "CREDITED TO", "CREDITED",
        // "RECEIVED FROM" is OK here: third-party "received *payment* from …" acks are
        // rejected earlier by the SPAM patterns above.
        "RECEIVED FROM", "RECEIVED RS", "RECEIVED INR", "RECEIVED ₹", "DEPOSITED", "CR AMT", "CR/",
        // Refund / reversal SMS often skip "credited" entirely.
        "REFUNDED", "REFUND OF", "HAS BEEN REVERSED", "BEEN REVERSED", "REVERSED TO", "REVERSAL OF",
        // IDFC-style "Rs X received in your Account … from <vpa>" templates.
        "RECEIVED IN YOUR", "RECEIVED IN A/C", "RECEIVED IN ACCOUNT", "YOU HAVE RECEIVED",
        // Axis compact multi-line template: "Credit INR 5000.00\nAxis Bank A/c XX…"
        "CREDIT INR", "CREDIT RS", "CREDIT ₹",
    )

    fun isTransactional(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.length < 20) return false
        val upper = trimmed.uppercase()
        if (OTP.containsMatchIn(upper)) return false
        if (SPAM.any { it.containsMatchIn(trimmed) }) return false
        // Issuer/biller "payment … towards your Credit Card" with no debit/spend verb —
        // checked in code (not a search-position lookahead) so real BillPay debits stay.
        if (isCardPaymentAckWithoutMovement(trimmed, upper)) return false
        // PPF/NPS/SIP "contribution received / thank you" acks without an SI/debit posting.
        if (isInvestmentContributionAck(upper)) return false
        if (!SmsAmountExtractor.AMOUNT_PATTERN.containsMatchIn(trimmed)) return false
        val debit = STRONG_DEBIT.any { upper.contains(it) } ||
            AMOUNT_THEN_DEBITED.containsMatchIn(trimmed)
        val credit = STRONG_CREDIT.any { upper.contains(it) } ||
            AMOUNT_THEN_CREDITED.containsMatchIn(trimmed)
        return debit || credit
    }

    /**
     * Investment vehicle thank-you / contribution-received SMS. Standing-instruction
     * and "credited in PPF" postings are real deposits and must stay for Investment booking.
     */
    private fun isInvestmentContributionAck(upper: String): Boolean {
        val vehicle = upper.contains("PPF") ||
            upper.contains("PUBLIC PROVIDENT") ||
            upper.contains("NPS") ||
            upper.contains("NATIONAL PENSION") ||
            upper.contains("SIP")
        if (!vehicle) return false
        // Authoritative deposit postings — keep for the parser to book as Investment.
        if (upper.contains("SI TRANSACTION") ||
            upper.contains("STANDING INSTRUCTION") ||
            upper.contains("DEBITED") ||
            upper.contains("DEDUCTION") ||
            upper.contains("CREDITED IN PPF") ||
            upper.contains("CREDITED TO PPF") ||
            upper.contains("CREDITED IN YOUR PPF") ||
            upper.contains("CREDITED IN NPS") ||
            upper.contains("CREDITED TO NPS")
        ) {
            return false
        }
        val contributionOrReceived =
            upper.contains("CONTRIBUTION") ||
                upper.contains("RECEIVED IN YOUR PPF") ||
                upper.contains("RECEIVED IN PPF") ||
                upper.contains("RECEIVED IN YOUR NPS") ||
                (upper.contains("RECEIVED") && upper.contains("THANK YOU"))
        return contributionOrReceived &&
            (upper.contains("RECEIVED") || upper.contains("THANK YOU"))
    }

    /**
     * Card-bill acknowledgements that mention payment + card but never move money
     * on an account ("payment of X towards your Credit Card has been posted").
     * Bank BillPay SMS include "debited" and must remain Transfer rows.
     */
    private fun isCardPaymentAckWithoutMovement(body: String, upper: String): Boolean {
        if (!PAYMENT_TOWARDS_CARD.containsMatchIn(body)) return false
        if (!CREDIT_OR_YOUR_CARD.containsMatchIn(body)) return false
        return MOVEMENT_VERBS.none { upper.contains(it) }
    }

    fun looksLikeSpam(body: String): Boolean = !isTransactional(body)
}
