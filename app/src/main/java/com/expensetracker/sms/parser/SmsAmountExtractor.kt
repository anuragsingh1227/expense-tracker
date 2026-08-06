package com.expensetracker.sms.parser

import com.expensetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Picks the ledger amount in INR — never balance / available credit limit /
 * outstanding. Foreign-currency-only SMS (e.g. SGD 1.38 + Avl limit INR …)
 * return null so we do not book the limit as a fake txn.
 */
object SmsAmountExtractor {

    private const val NUMBER =
        """([0-9]+(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""

    /** INR markers only — FX codes are handled separately. */
    val AMOUNT_PATTERN = Regex("""(?i)(?:rs\.?|inr|₹)\s*$NUMBER""")

    private val FX_AMOUNT = Regex(
        """(?i)\b(?:usd|sgd|eur|gbp|aed|aud|cad|chf|hkd|jpy|myr|nzd|thb|cny|qar|sar|omr|bhd|kwd)\s*$NUMBER""",
    )

    private val INR_EQUIV = Regex(
        """(?i)(?:equiv(?:alent)?(?:\s+to)?|inr\s*equiv(?:alent)?|≈|~)\s*(?:(?:amount|amt|of)\s*)?(?:rs\.?|inr|₹)?\s*$NUMBER""",
    )

    /**
     * Prefix immediately before an INR amount that marks non-ledger figures.
     * Allows optional "is"/"of"/":" between the label and the currency.
     */
    private val SKIP_PREFIX = Regex(
        """(?i)(?:avl\.?\s*bal(?:ance)?|avbl\.?\s*bal(?:ance)?|available\s*balance|available\s*credit\s*limit|avbl\.?\s*credit\s*limit|avl\.?\s*(?:credit\s*)?limit|credit\s*limit|total\s*credit\s*limit|bal(?:ance)?|(?:avl\.?\s*)?limit|outstanding|due(?:\s*amt)?|cashback|offer|save|win|get\s+up\s+to|upto|flat|discount|min(?:imum)?\s*(?:amt|amount)|total\s*(?:due|payment)|matured\s*amount)(?:\s+(?:is|of|was|stands\s+at))?\s*[:\-=]*\s*$""",
    )

    /** Prefer amounts tied to the actual movement, not trailing limit lines. */
    private val TXN_CONTEXT = Regex(
        """(?i)(?:(?:txn|transaction|amt|amount)\s+of|(?:has\s+been\s+)?(?:debited|credited)(?:\s+with|\s+for|\s+to|\s+from)?|(?:spent|paid|charged|withdrawn|reversed|refund(?:ed)?)|purchase\s+of|sent\s+(?:rs|inr|₹))""",
    )

    fun extract(body: String): Money? {
        val matches = AMOUNT_PATTERN.findAll(body).toList()
        if (matches.isEmpty()) return null

        val ledger = matches.filterNot { isSkipped(body, it) }
        if (ledger.isEmpty()) return null // never fall back to balance/limit

        // Prefer INR amount sitting near a txn verb (handles FX SMS that also quote INR eq).
        val contextual = ledger.firstOrNull { match ->
            val windowStart = (match.range.first - 64).coerceAtLeast(0)
            TXN_CONTEXT.containsMatchIn(body.substring(windowStart, match.range.first))
        }
        contextual?.let { toMoney(it.groupValues[1])?.let { m -> return m } }

        INR_EQUIV.find(body)?.let { eq ->
            // Only accept equiv if it wasn't a skipped limit figure.
            val amt = toMoney(eq.groupValues[1]) ?: return@let
            val around = body.substring(
                (eq.range.first - 40).coerceAtLeast(0),
                eq.range.first,
            )
            if (!SKIP_PREFIX.containsMatchIn(around)) return amt
        }

        // FX movement without a usable INR ledger amount — refuse (do not book limit).
        if (FX_AMOUNT.containsMatchIn(body)) return null

        return toMoney(ledger.first().groupValues[1])
    }

    private fun isSkipped(body: String, match: MatchResult): Boolean {
        val prefixStart = (match.range.first - 56).coerceAtLeast(0)
        val prefix = body.substring(prefixStart, match.range.first)
        return SKIP_PREFIX.containsMatchIn(prefix)
    }

    private fun toMoney(raw: String): Money? {
        val decimal = runCatching {
            BigDecimal(raw.replace(",", "").replace(" ", "")).setScale(2, RoundingMode.HALF_UP)
        }.getOrNull() ?: return null
        if (decimal.signum() <= 0) return null
        return Money(decimal)
    }
}
