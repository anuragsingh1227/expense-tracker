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
        Regex("""\bREQUESTED\s+OTP\b""", RegexOption.IGNORE_CASE),
        Regex("""\bIS\s+SCHEDULED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSCHEDULED\s+FOR\s+(?:ECS|CLEARANCE)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPLEASE\s+KEEP\s+SUFFICIENT\s+BALANCE\b""", RegexOption.IGNORE_CASE),
        Regex("""\bWILL\s+GET\s+MATURED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bACCOUNT\s+.*\bOPENED\s+SUCCESSFULLY\b""", RegexOption.IGNORE_CASE),
        Regex("""\bHAS\s+BEEN\s+DELIVERED\b""", RegexOption.IGNORE_CASE),
        Regex("""\bOVERDRAFT\s+FACILITY\s+HAS\s+BEEN\s+SANCTIONED\b""", RegexOption.IGNORE_CASE),
    )

    /** Strong ledger verbs — loose "UPI" / "purchase" alone is not enough. */
    private val STRONG_DEBIT = listOf(
        "HAS BEEN DEBITED", "BEEN DEBITED", "DEBITED WITH", "DEBITED FOR", "DEBITED FROM",
        "DEBITED", "SPENT ON", "SPENT AT", "YOU'VE SPENT", "YOU HAVE SPENT", "SPENT INR", "SPENT RS",
        "WITHDRAWN", "WITHDRAWAL",
        "PURCHASE OF", "PURCHASE ON", "TXN OF", "TRANSACTION OF", "SENT RS", "SENT INR", "SENT ₹",
        "PAID TO", "PAID RS", "PAID INR", "CHARGED", "DR AMT", "DR/",
        "THANK YOU FOR USING", "FOR USING YOUR", "CARD ENDING", "CARD XX",
    )
    private val STRONG_CREDIT = listOf(
        "HAS BEEN CREDITED", "BEEN CREDITED", "CREDITED WITH", "CREDITED TO", "CREDITED",
        "RECEIVED FROM", "RECEIVED RS", "DEPOSITED", "CR AMT", "CR/",
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
