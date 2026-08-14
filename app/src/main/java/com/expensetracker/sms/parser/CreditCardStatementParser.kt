package com.expensetracker.sms.parser

import com.expensetracker.domain.model.CardStatement
import com.expensetracker.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Detects Indian credit-card statement / due-date SMS (Total Due, Min Due, Due Date).
 * These are not ledger movements — [SmsParser] still returns null — but they
 * drive on-device payment reminders.
 */
object CreditCardStatementParser {

    private val NUMBER =
        """([0-9]+(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""

    private val TOTAL_DUE = Regex(
        """(?i)(?:total\s*(?:amt|amount)?\s*due|total\s*due|outstanding(?:\s*(?:amt|amount|bal(?:ance)?))?)\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*$NUMBER""",
    )
    private val TOTAL_AMOUNT_IS_DUE = Regex(
        """(?i)total\s+amount\s+(?:of\s+)?(?:rs\.?|inr|₹)\s*$NUMBER.{0,48}\bis\s+due\b""",
    )
    private val MIN_DUE = Regex(
        """(?i)(?:min(?:imum)?\s*(?:amt|amount)?\s*due|min(?:imum)?\s*due)\s*(?:is|:)?\s*(?:rs\.?|inr|₹)?\s*$NUMBER""",
    )
    private val CARD_LAST4 = Regex(
        """(?i)(?:card|cc)(?:\s*(?:ending|no\.?|xx+|[*x]{2,}))?\s*[*x]*(\d{4})\b""",
    )
    private val DUE_DATE_NUMERIC = Regex(
        """(?i)(?:due\s*date|payment\s*due(?:\s*date)?|due\s*on|is\s+due(?:\s+on)?)\s*(?:is|:)?\s*(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})""",
    )
    private val DUE_DATE_MON = Regex(
        """(?i)(?:due\s*date|payment\s*due(?:\s*date)?|due\s*on|is\s+due(?:\s+on)?)\s*(?:is|:)?\s*(\d{1,2})(?:st|nd|rd|th)?[./\-\s]+([A-Za-z]{3,9})(?:[./\-\s]+(\d{2,4}))?""",
    )
    private val CREDIT_CARD = Regex(
        """(?i)\b(?:credit\s*card|\bcc\b|card\s+(?:ending|xx+|[*x]{2,}\d{4}))\b""",
    )

    fun parse(sms: RawSms, zone: ZoneId = ZoneId.systemDefault()): CardStatement? {
        val body = sms.body.trim()
        if (body.isEmpty()) return null
        if (!looksLikeStatement(body)) return null

        val dueDate = extractDueDate(body, zone, sms.timestamp) ?: return null
        val totalDue = extractTotalDue(body)
        val minDue = extractMinDue(body)
        if (totalDue == null && minDue == null) return null

        val cardLast4 = CARD_LAST4.find(body)?.groupValues?.get(1)
        val bank = BankSenders.identify(sms.sender)
        return CardStatement(
            bank = bank,
            cardLast4 = cardLast4,
            totalDue = totalDue,
            minDue = minDue,
            dueDate = dueDate,
            timestamp = sms.timestamp,
            sender = sms.sender,
            rawSms = sms.body,
            dedupeHash = hash(sms.sender, dueDate, cardLast4, totalDue, sms.body),
        )
    }

    fun looksLikeStatement(body: String): Boolean {
        val upper = body.uppercase()
        if (!CREDIT_CARD.containsMatchIn(body)) return false
        val hasDueLanguage = upper.contains("DUE DATE") ||
            upper.contains("PAYMENT DUE") ||
            upper.contains("TOTAL DUE") ||
            upper.contains("MIN DUE") ||
            upper.contains("MIN AMT DUE") ||
            upper.contains("MINIMUM AMOUNT DUE") ||
            upper.contains("MINIMUM AMT DUE") ||
            Regex("""(?i)\btotal\s+amount\b.{0,48}\bis\s+due\b""").containsMatchIn(body) ||
            Regex("""(?i)\bis\s+due\b""").containsMatchIn(body)
        if (!hasDueLanguage) return false
        // Limit-raise / apply-now marketing is not a statement.
        if (upper.contains("APPLY NOW") || upper.contains("INCREAS") && upper.contains("LIMIT")) return false
        if (upper.contains("CRLIM")) return false
        return true
    }

    private fun extractTotalDue(body: String): Money? {
        TOTAL_AMOUNT_IS_DUE.find(body)?.let { return toMoney(it.groupValues[1]) }
        TOTAL_DUE.find(body)?.let { return toMoney(it.groupValues[1]) }
        return null
    }

    private fun extractMinDue(body: String): Money? =
        MIN_DUE.find(body)?.let { toMoney(it.groupValues[1]) }

    private fun extractDueDate(body: String, zone: ZoneId, fallbackTs: Instant): LocalDate? {
        val fallbackYear = LocalDate.ofInstant(fallbackTs, zone).year
        DUE_DATE_NUMERIC.find(body)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = normalizeYear(m.groupValues[3].toInt())
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        DUE_DATE_MON.find(body)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = monthNumber(m.groupValues[2]) ?: return@let
            val yearToken = m.groupValues.getOrNull(3).orEmpty()
            val year = if (yearToken.isBlank()) fallbackYear else normalizeYear(yearToken.toInt())
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        // "Total amount … is due on <card>. … on 02-Aug-26"
        return SmsDateExtractor.extractOrNull(body, zone, fallbackYear)?.let { instant ->
            LocalDate.ofInstant(instant, zone)
        }
    }

    private fun toMoney(raw: String): Money? {
        val decimal = runCatching {
            BigDecimal(raw.replace(",", "").replace(" ", "")).setScale(2, RoundingMode.HALF_UP)
        }.getOrNull() ?: return null
        if (decimal.signum() <= 0) return null
        return Money(decimal)
    }

    private fun normalizeYear(year: Int): Int = if (year < 100) 2000 + year else year

    private fun monthNumber(token: String): Int? {
        val t = token.trim().lowercase(Locale.US).take(3)
        val months = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
        )
        val i = months.indexOf(t)
        return if (i >= 0) i + 1 else null
    }

    private fun hash(
        sender: String?,
        dueDate: LocalDate,
        last4: String?,
        totalDue: Money?,
        body: String,
    ): String {
        val key = buildString {
            append(sender.orEmpty().uppercase()).append('|')
            append(dueDate).append('|')
            append(last4.orEmpty()).append('|')
            append(totalDue?.amount?.toPlainString().orEmpty()).append('|')
            append(body.replace(Regex("\\s+"), " ").take(160))
        }
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
