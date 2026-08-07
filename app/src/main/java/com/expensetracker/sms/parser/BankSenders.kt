package com.expensetracker.sms.parser

/**
 * Maps DLT-header suffixes (the part after "AX-", "VM-", etc.) to bank names.
 * Indian SMS headers look like "VM-HDFCBK", "AD-ICICIB", "JD-SBIINB".
 */
object BankSenders {

    private val banks: List<Pair<Regex, String>> = listOf(
        Regex("SBI(INB|UPI|BNK|CRD)?", RegexOption.IGNORE_CASE) to "SBI",
        Regex("HDFC(BK|BN|CC)?", RegexOption.IGNORE_CASE) to "HDFC",
        Regex("ICICI(B|T|BK)?", RegexOption.IGNORE_CASE) to "ICICI",
        Regex("AXIS(BK|B|SMS)?", RegexOption.IGNORE_CASE) to "Axis",
        Regex("KOTAK(B|BK)?", RegexOption.IGNORE_CASE) to "Kotak",
        Regex("IDFC(FB|B|BK)?", RegexOption.IGNORE_CASE) to "IDFC First",
        Regex("IDBI(BK|B)?", RegexOption.IGNORE_CASE) to "IDBI",
        Regex("PNB(SMS|B)?", RegexOption.IGNORE_CASE) to "PNB",
        Regex("BOB(SMS|BNK|IB)?", RegexOption.IGNORE_CASE) to "Bank of Baroda",
        Regex("CANBNK|CANARA", RegexOption.IGNORE_CASE) to "Canara",
        Regex("UNIONB|UBIN", RegexOption.IGNORE_CASE) to "Union Bank",
        Regex("YES(BNK|B)?", RegexOption.IGNORE_CASE) to "Yes Bank",
        Regex("INDUS(IND|B)?", RegexOption.IGNORE_CASE) to "IndusInd",
        Regex("AUBANK|AUFIN", RegexOption.IGNORE_CASE) to "AU Bank",
        Regex("HSBC(IN|BK)?", RegexOption.IGNORE_CASE) to "HSBC",
        Regex("CITIBK|CITI", RegexOption.IGNORE_CASE) to "Citi",
        Regex("AMEX", RegexOption.IGNORE_CASE) to "American Express",
        Regex("FEDERA|FEDBNK", RegexOption.IGNORE_CASE) to "Federal Bank",
        Regex("RBL(BNK|BANK)?", RegexOption.IGNORE_CASE) to "RBL",
        Regex("BANDHAN|BNDNBN", RegexOption.IGNORE_CASE) to "Bandhan",
        Regex("DBS(IN|BK)?", RegexOption.IGNORE_CASE) to "DBS",
        Regex("SCB(IN|ANK)?|STANCHART", RegexOption.IGNORE_CASE) to "Standard Chartered",
        Regex("UCO(BNK|BANK)?", RegexOption.IGNORE_CASE) to "UCO",
        Regex("IOB(ANK|BNK)?", RegexOption.IGNORE_CASE) to "Indian Overseas",
        Regex("FIMONEY|FIBANK|JUSPAYFI", RegexOption.IGNORE_CASE) to "Fi",
        Regex("JUPITER|JUPITR", RegexOption.IGNORE_CASE) to "Jupiter",
        Regex("NIYO(GLB)?", RegexOption.IGNORE_CASE) to "Niyo",
        Regex("SLICE", RegexOption.IGNORE_CASE) to "Slice",
        // UPI apps
        Regex("PHONEP|PHNPE", RegexOption.IGNORE_CASE) to "PhonePe",
        Regex("GPAY|GOOGLEP", RegexOption.IGNORE_CASE) to "Google Pay",
        Regex("PAYTM(B|BNK)?", RegexOption.IGNORE_CASE) to "Paytm",
        Regex("BHIM(UPI)?", RegexOption.IGNORE_CASE) to "BHIM",
        Regex("AMZNPAY|AMAZONP", RegexOption.IGNORE_CASE) to "Amazon Pay",
        Regex("CREDCLB|CRED", RegexOption.IGNORE_CASE) to "CRED",
        Regex("MOBIKW|MBKWIK", RegexOption.IGNORE_CASE) to "Mobikwik",
    )

    /** All known bank display names (for filters / seed catalog). */
    fun allBankNames(): List<String> = banks.map { it.second }.distinct().sorted()

    fun identify(sender: String?): String? {
        if (sender.isNullOrBlank()) return null
        val cleaned = sender.substringAfter('-').substringAfter(' ').trim()
        return banks.firstOrNull { it.first.containsMatchIn(cleaned) || it.first.containsMatchIn(sender) }?.second
    }
}
