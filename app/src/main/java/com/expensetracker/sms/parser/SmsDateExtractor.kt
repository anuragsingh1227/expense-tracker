package com.expensetracker.sms.parser

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pulls a transaction calendar date from SMS body text when present.
 * Critical for paste/share import (no telephony timestamp).
 */
object SmsDateExtractor {

    /** `on 05-02-19` / `on 05/08/2026` / `dated 12-01-24 07:27:11` */
    private val NUMERIC = Regex(
        """(?i)\b(?:on|dated)\s+(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""",
    )

    /** `on 23-Jul-2026` / `on 05/Aug/2026` / `on 15-APR` (year optional) */
    private val MON_NAME = Regex(
        """(?i)\b(?:on|dated)\s+(\d{1,2})[./\-\s]+([A-Za-z]{3,9})(?:[./\-\s]+(\d{2,4}))?(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?""",
    )

    /** `on August 05, 2026` / `on July 23, 2026 at 14:15` */
    private val MONTH_FIRST = Regex(
        """(?i)\b(?:on|dated)\s+([A-Za-z]{3,9})\s+(\d{1,2}),?\s+(\d{2,4})(?:\s+(?:at\s+)?(\d{1,2}):(\d{2})(?::(\d{2}))?)?""",
    )

    /**
     * Axis compact multi-line template puts the stamp on its own line without "on":
     * `15-06-26 20:43:26` or `04-08-26, 07:32:27 IST`
     */
    private val BARE_NUMERIC_WITH_TIME = Regex(
        """(?i)(?<![\d])(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4}),?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*IST\b)?""",
    )

    fun extract(body: String, zone: ZoneId, fallback: Instant): Instant {
        val fallbackYear = LocalDate.ofInstant(fallback, zone).year
        return extractOrNull(body, zone, fallbackYear) ?: fallback
    }

    fun extractOrNull(body: String, zone: ZoneId, fallbackYear: Int = LocalDate.now(zone).year): Instant? {
        NUMERIC.find(body)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = normalizeYear(m.groupValues[3].toInt())
            val time = parseTime(m.groupValues.getOrNull(4), m.groupValues.getOrNull(5), m.groupValues.getOrNull(6))
            toInstant(year, month, day, time, zone)?.let { return it }
        }
        MONTH_FIRST.find(body)?.let { m ->
            val month = monthNumber(m.groupValues[1]) ?: return@let
            val day = m.groupValues[2].toInt()
            val year = normalizeYear(m.groupValues[3].toInt())
            val time = parseTime(m.groupValues.getOrNull(4), m.groupValues.getOrNull(5), m.groupValues.getOrNull(6))
            toInstant(year, month, day, time, zone)?.let { return it }
        }
        MON_NAME.find(body)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = monthNumber(m.groupValues[2]) ?: return@let
            val yearToken = m.groupValues.getOrNull(3).orEmpty()
            val year = if (yearToken.isBlank()) fallbackYear else normalizeYear(yearToken.toInt())
            val time = parseTime(m.groupValues.getOrNull(4), m.groupValues.getOrNull(5), m.groupValues.getOrNull(6))
            toInstant(year, month, day, time, zone)?.let { return it }
        }
        BARE_NUMERIC_WITH_TIME.find(body)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = normalizeYear(m.groupValues[3].toInt())
            val time = parseTime(m.groupValues[4], m.groupValues[5], m.groupValues.getOrNull(6))
            toInstant(year, month, day, time, zone)?.let { return it }
        }
        return null
    }

    private fun parseTime(h: String?, mi: String?, s: String?): LocalTime {
        if (h.isNullOrBlank() || mi.isNullOrBlank()) return LocalTime.NOON
        return LocalTime.of(h.toInt(), mi.toInt(), s?.toIntOrNull() ?: 0)
    }

    private fun toInstant(year: Int, month: Int, day: Int, time: LocalTime, zone: ZoneId): Instant? =
        runCatching {
            LocalDateTime.of(LocalDate.of(year, month, day), time).atZone(zone).toInstant()
        }.getOrNull()

    private fun normalizeYear(year: Int): Int = when {
        year < 100 -> 2000 + year
        else -> year
    }

    private fun monthNumber(token: String): Int? {
        val t = token.trim().lowercase(Locale.US).take(3)
        val months = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
        )
        val idx = months.indexOf(t)
        return if (idx >= 0) idx + 1 else null
    }
}
