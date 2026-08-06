package com.expensetracker.ui.screen

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Time filters for Spends / Activity.
 * Closed ranges only (no free date picker) — cheap queries, clear UX.
 */
enum class SpendPeriod {
    DAY,
    WEEK,
    MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
}

data class PeriodWindow(
    val period: SpendPeriod,
    val fromInclusive: Instant,
    val toExclusive: Instant,
    val localDate: LocalDate,
    val labelRange: String,
)

data class DashboardWindow(
    val startOfDay: Instant,
    val startOfMonth: Instant,
    val endExclusive: Instant,
    val localDate: LocalDate,
)

/** Calendar month pair for MoM compare (this month may be partial). */
data class MonthCompareWindows(
    val currentFrom: Instant,
    val currentToExclusive: Instant,
    val previousFrom: Instant,
    val previousToExclusive: Instant,
    val currentLabel: String,
    val previousLabel: String,
    val currentIsPartial: Boolean,
)

object DashboardRanges {

    private val RANGE_FMT = DateTimeFormatter.ofPattern("d MMM")
    private val RANGE_YEAR_FMT = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy")

    fun at(clock: Clock, zone: ZoneId = clock.zone): DashboardWindow {
        val today = LocalDate.now(clock.withZone(zone))
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        val endExclusive = Instant.ofEpochSecond(4_102_444_800L)
        return DashboardWindow(startOfDay, startOfMonth, endExclusive, today)
    }

    fun forPeriod(period: SpendPeriod, clock: Clock, zone: ZoneId = clock.zone): PeriodWindow {
        val today = LocalDate.now(clock.withZone(zone))
        val (startDate, endExclusiveDate) = when (period) {
            SpendPeriod.DAY -> today to today.plusDays(1)
            SpendPeriod.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to today.plusDays(1)
            }
            SpendPeriod.MONTH -> today.withDayOfMonth(1) to today.plusDays(1)
            SpendPeriod.LAST_MONTH -> {
                val last = YearMonth.from(today).minusMonths(1)
                last.atDay(1) to last.plusMonths(1).atDay(1)
            }
            SpendPeriod.LAST_3_MONTHS -> {
                val startYm = YearMonth.from(today).minusMonths(2)
                startYm.atDay(1) to today.plusDays(1)
            }
        }
        val fromInclusive = startDate.atStartOfDay(zone).toInstant()
        val toExclusive = endExclusiveDate.atStartOfDay(zone).toInstant()
        val endInclusive = endExclusiveDate.minusDays(1)
        return PeriodWindow(
            period = period,
            fromInclusive = fromInclusive,
            toExclusive = toExclusive,
            localDate = today,
            labelRange = formatRange(period, startDate, endInclusive),
        )
    }

    /** This calendar month (through tomorrow start) vs previous full calendar month. */
    fun monthCompareWindows(clock: Clock, zone: ZoneId = clock.zone): MonthCompareWindows {
        val today = LocalDate.now(clock.withZone(zone))
        val currentYm = YearMonth.from(today)
        val previousYm = currentYm.minusMonths(1)
        val currentFrom = currentYm.atDay(1).atStartOfDay(zone).toInstant()
        val currentTo = today.plusDays(1).atStartOfDay(zone).toInstant()
        val previousFrom = previousYm.atDay(1).atStartOfDay(zone).toInstant()
        val previousTo = currentYm.atDay(1).atStartOfDay(zone).toInstant()
        return MonthCompareWindows(
            currentFrom = currentFrom,
            currentToExclusive = currentTo,
            previousFrom = previousFrom,
            previousToExclusive = previousTo,
            currentLabel = currentYm.format(MONTH_FMT),
            previousLabel = previousYm.format(MONTH_FMT),
            currentIsPartial = today.dayOfMonth < currentYm.lengthOfMonth(),
        )
    }

    /** Start of (today's month − 2) through end of today — three calendar months for stack. */
    fun lastThreeMonthsWindow(clock: Clock, zone: ZoneId = clock.zone): PeriodWindow =
        forPeriod(SpendPeriod.LAST_3_MONTHS, clock, zone)

    fun monthKeysForLastThree(clock: Clock, zone: ZoneId = clock.zone): List<String> {
        val today = LocalDate.now(clock.withZone(zone))
        val current = YearMonth.from(today)
        return (2 downTo 0).map { current.minusMonths(it.toLong()).toString() }
    }

    fun formatRange(period: SpendPeriod, start: LocalDate, end: LocalDate): String = when (period) {
        SpendPeriod.DAY -> end.format(RANGE_YEAR_FMT)
        SpendPeriod.WEEK, SpendPeriod.MONTH, SpendPeriod.LAST_3_MONTHS -> {
            if (start.year == end.year) {
                "${start.format(RANGE_FMT)} – ${end.format(RANGE_FMT)} ${end.year}"
            } else {
                "${start.format(RANGE_YEAR_FMT)} – ${end.format(RANGE_YEAR_FMT)}"
            }
        }
        SpendPeriod.LAST_MONTH -> {
            val ym = YearMonth.from(start)
            ym.format(MONTH_FMT)
        }
    }

    fun millisUntilNextDay(clock: Clock, zone: ZoneId = clock.zone): Long {
        val zoned = clock.instant().atZone(zone)
        val nextMidnight = zoned.toLocalDate().plusDays(1).atStartOfDay(zone)
        return ChronoUnit.MILLIS.between(zoned, nextMidnight).coerceAtLeast(1L)
    }
}
