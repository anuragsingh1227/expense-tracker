package com.expensetracker.sms.parser

/**
 * Rejects OTPs, marketing, loan offers, and other non-ledger SMS before parsing.
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
        // Card-side "thank you for your payment ... towards ... Credit Card ... through
        // Auto Debit" confirmation duplicates the source account's own debit SMS —
        // keep only the bank-side debit as the ledger record.
        Regex(
            """(?=.*\bTHANK\s+YOU\s+FOR\s+YOUR\s+PAYMENT\b)(?=.*\bCREDIT\s+CARD\b)(?=.*\bAUTO\s+DEBIT\b)""",
            RegexOption.IGNORE_CASE,
        ),
        // Merchant / app payment receipts — the bank debit SMS is the ledger row.
        Regex("""\bpayment\s+receipt\b""", RegexOption.IGNORE_CASE),
        Regex("""\breceipt\s+will\s+be\s+available\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwe\s+have\s+received\s+(?:online\s+)?payment\b""", RegexOption.IGNORE_CASE),
        Regex("""\ba\s+payment\s+of\b.{0,40}\bis\s+updated\s+against\b""", RegexOption.IGNORE_CASE),
        Regex("""\breceived\s+online\s+payment\s+of\b""", RegexOption.IGNORE_CASE),
        // Broker / AMC request or initiation notices — money has not moved yet.
        Regex("""\bwithdrawal\s+instruction\b""", RegexOption.IGNORE_CASE),
        Regex("""\bwithdrawal\s+request\b""", RegexOption.IGNORE_CASE),
        Regex("""\bplaced\s+a\s+withdrawal\b""", RegexOption.IGNORE_CASE),
        Regex("""\brefund\b.{0,80}\bhas\s+been\s+initiated\b""", RegexOption.IGNORE_CASE),
        Regex("""\bhas\s+been\s+initiated\b.{0,40}\b(?:processed|working\s+days)\b""", RegexOption.IGNORE_CASE),
        // AMC unit allotment confirmations — bank ACH/UPI debit is the investment row.
        Regex("""\bdear\s+investor\b""", RegexOption.IGNORE_CASE),
        Regex(
            """(?=.*\bfolio\b)(?=.*\b(?:nav|units)\b)(?=.*\b(?:purchase|sip|processed)\b)""",
            RegexOption.IGNORE_CASE,
        ),
        // BillDesk / INSTAPAY / biller "payment received" receipts — bank debit is the ledger row.
        Regex("""\binstapay\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbilldesk\b.{0,40}\b(?:payment|received)\b""", RegexOption.IGNORE_CASE),
        Regex(
            """(?=.*\bpayment\s+of\b)(?=.*\bis\s+received\b)(?=.*\b(?:customer\s+id|billdesk|instapay)\b)""",
            RegexOption.IGNORE_CASE,
        ),
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
        "TRANSFERRED RS", "TRANSFERRED INR", "TRANSFERRED ₹",
        // ICICI "Acc XX293 debited Rs. X on DATE Info..." — bare "debited" + amount,
        // no with/for/from/via connector.
        "DEBITED RS", "DEBITED INR", "DEBITED ₹",
        "DEBITED BY",
    )
    private val STRONG_CREDIT = listOf(
        "HAS BEEN CREDITED", "BEEN CREDITED", "CREDITED WITH", "CREDITED TO", "CREDITED",
        "RECEIVED FROM", "RECEIVED RS", "DEPOSITED", "CR AMT", "CR/",
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
        if (!SmsAmountExtractor.AMOUNT_PATTERN.containsMatchIn(trimmed)) return false
        val debit = STRONG_DEBIT.any { upper.contains(it) }
        val credit = STRONG_CREDIT.any { upper.contains(it) }
        return debit || credit
    }

    fun looksLikeSpam(body: String): Boolean = !isTransactional(body)
}
