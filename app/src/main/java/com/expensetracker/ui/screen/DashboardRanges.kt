package com.expensetracker.ui.screen

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DashboardWindow(
    val startOfDay: Instant,
    val startOfMonth: Instant,
    /** Exclusive upper bound far enough ahead that live inserts still count. */
    val endExclusive: Instant,
    val localDate: LocalDate,
)

object DashboardRanges {

    fun at(clock: Clock, zone: ZoneId = clock.zone): DashboardWindow {
        val today = LocalDate.now(clock.withZone(zone))
        val startOfDay = today.atStartOfDay(zone).toInstant()
        val startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant()
        // ~ year 2100 — same sentinel the dashboard used before; keeps open-ended "now".
        val endExclusive = Instant.ofEpochSecond(4_102_444_800L)
        return DashboardWindow(startOfDay, startOfMonth, endExclusive, today)
    }

    /** Milliseconds until local midnight after [clock]'s current instant. At least 1ms. */
    fun millisUntilNextDay(clock: Clock, zone: ZoneId = clock.zone): Long {
        val zoned = clock.instant().atZone(zone)
        val nextMidnight = zoned.toLocalDate().plusDays(1).atStartOfDay(zone)
        return ChronoUnit.MILLIS.between(zoned, nextMidnight).coerceAtLeast(1L)
    }
}
