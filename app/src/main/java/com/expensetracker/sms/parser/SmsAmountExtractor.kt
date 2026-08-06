package com.expensetracker.sms.parser

import com.expensetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** Picks the ledger amount, not balance / limit / offer figures. */
object SmsAmountExtractor {

    val AMOUNT_PATTERN = Regex(
        """(?i)(?:rs\.?|inr|₹)\s*([0-9]+(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
    )

    private val SKIP_PREFIX = Regex(
        """(?i)(avl\.?\s*bal|available\s*balance|bal(?:ance)?|limit|outstanding|due(?:\s*amt)?|cashback|offer|save|win|get\s+up\s+to|upto|flat|discount|min(?:imum)?\s*(?:amt|amount)|total\s*due)[:\s]*$""",
    )

    fun extract(body: String): Money? {
        val matches = AMOUNT_PATTERN.findAll(body).toList()
        if (matches.isEmpty()) return null

        for (match in matches) {
            val prefixStart = (match.range.first - 48).coerceAtLeast(0)
            val prefix = body.substring(prefixStart, match.range.first)
            if (SKIP_PREFIX.containsMatchIn(prefix.trimEnd())) continue
            toMoney(match.groupValues[1])?.let { return it }
        }
        // Last resort: first positive amount (still better than balance-only if none matched skip)
        return matches.firstNotNullOfOrNull { toMoney(it.groupValues[1]) }
    }

    private fun toMoney(raw: String): Money? {
        val decimal = runCatching {
            BigDecimal(raw.replace(",", "").replace(" ", "")).setScale(2, RoundingMode.HALF_UP)
        }.getOrNull() ?: return null
        if (decimal.signum() <= 0) return null
        return Money(decimal)
    }
}
