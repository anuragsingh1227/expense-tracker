package com.expensetracker.ui.screen

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRangesTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    @Test
    fun `at uses start of local day and month`() {
        // 2024-03-15 01:30 IST = 2024-03-14 20:00 UTC
        val clock = Clock.fixed(Instant.parse("2024-03-14T20:00:00Z"), zone)
        val window = DashboardRanges.at(clock)

        assertThat(window.localDate).isEqualTo(LocalDate.of(2024, 3, 15))
        assertThat(window.startOfDay).isEqualTo(LocalDate.of(2024, 3, 15).atStartOfDay(zone).toInstant())
        assertThat(window.startOfMonth).isEqualTo(LocalDate.of(2024, 3, 1).atStartOfDay(zone).toInstant())
        assertThat(window.endExclusive).isGreaterThan(window.startOfDay)
    }

    @Test
    fun `forPeriod week starts on Monday and ends after today`() {
        // Friday 2024-03-15 IST
        val clock = Clock.fixed(Instant.parse("2024-03-14T20:00:00Z"), zone)
        val window = DashboardRanges.forPeriod(SpendPeriod.WEEK, clock)
        assertThat(window.fromInclusive).isEqualTo(LocalDate.of(2024, 3, 11).atStartOfDay(zone).toInstant())
        assertThat(window.toExclusive).isEqualTo(LocalDate.of(2024, 3, 16).atStartOfDay(zone).toInstant())
        assertThat(window.labelRange).contains("11 Mar")
    }

    @Test
    fun `forPeriod day covers only local calendar day`() {
        val clock = Clock.fixed(Instant.parse("2024-03-14T20:00:00Z"), zone)
        val window = DashboardRanges.forPeriod(SpendPeriod.DAY, clock)
        assertThat(window.fromInclusive).isEqualTo(LocalDate.of(2024, 3, 15).atStartOfDay(zone).toInstant())
        assertThat(window.toExclusive).isEqualTo(LocalDate.of(2024, 3, 16).atStartOfDay(zone).toInstant())
    }

    @Test
    fun `forPeriod last month is previous calendar month`() {
        val clock = Clock.fixed(Instant.parse("2024-03-14T20:00:00Z"), zone)
        val window = DashboardRanges.forPeriod(SpendPeriod.LAST_MONTH, clock)
        assertThat(window.fromInclusive).isEqualTo(LocalDate.of(2024, 2, 1).atStartOfDay(zone).toInstant())
        assertThat(window.toExclusive).isEqualTo(LocalDate.of(2024, 3, 1).atStartOfDay(zone).toInstant())
        assertThat(window.labelRange).isEqualTo("Feb 2024")
    }

    @Test
    fun `monthCompareWindows marks partial current month`() {
        val clock = Clock.fixed(Instant.parse("2024-03-14T20:00:00Z"), zone)
        val compare = DashboardRanges.monthCompareWindows(clock)
        assertThat(compare.currentIsPartial).isTrue()
        assertThat(compare.previousFrom).isEqualTo(LocalDate.of(2024, 2, 1).atStartOfDay(zone).toInstant())
        assertThat(compare.previousToExclusive).isEqualTo(LocalDate.of(2024, 3, 1).atStartOfDay(zone).toInstant())
    }

    @Test
    fun `millisUntilNextDay is time remaining until local midnight`() {
        val clock = Clock.fixed(Instant.parse("2024-01-12T18:30:00Z"), ZoneOffset.UTC)
        // 18:30 UTC → 5.5 hours = 19_800_000 ms until midnight UTC
        assertThat(DashboardRanges.millisUntilNextDay(clock, ZoneOffset.UTC)).isEqualTo(19_800_000L)
    }

    @Test
    fun `millisUntilNextDay never returns zero`() {
        val clock = Clock.fixed(Instant.parse("2024-01-12T00:00:00Z"), ZoneOffset.UTC)
        assertThat(DashboardRanges.millisUntilNextDay(clock, ZoneOffset.UTC)).isAtLeast(1L)
    }

    @Test
    fun `dateBoundaryFlow re-emits after crossing local midnight`() = runTest {
        val zoneUtc = ZoneOffset.UTC
        val clock = MutableClock(Instant.parse("2024-01-12T23:59:00Z"), zoneUtc)
        val dates = mutableListOf<LocalDate>()

        val job = backgroundScope.launch {
            dateBoundaryFlow(clock).take(2).toList(dates)
        }
        runCurrent()
        assertThat(dates).containsExactly(LocalDate.of(2024, 1, 12))

        val wait = DashboardRanges.millisUntilNextDay(clock, zoneUtc)
        clock.instant = Instant.parse("2024-01-13T00:00:00Z")
        advanceTimeBy(wait)
        runCurrent()

        assertThat(dates).containsExactly(
            LocalDate.of(2024, 1, 12),
            LocalDate.of(2024, 1, 13),
        ).inOrder()
        job.join()
    }

    /** Mutable [Clock] so tests can advance wall time independently of virtual delay. */
    private class MutableClock(
        @Volatile var instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }
}
