package com.expensetracker.sms.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SmsDateExtractorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val fallback = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `parses Axis compact datetime line with IST`() {
        val instant = SmsDateExtractor.extract(
            SampleSms.OWN_ACCOUNT_UPI_CREDIT_100000,
            zone,
            fallback,
        )
        val expected = LocalDate.of(2026, 8, 1)
            .atTime(LocalTime.of(7, 48, 42))
            .atZone(zone)
            .toInstant()
        assertThat(instant).isEqualTo(expected)
    }

    @Test
    fun `parses Axis compact datetime without IST suffix`() {
        val instant = SmsDateExtractor.extract(
            SampleSms.AXIS_INR_DEBITED_UPI_P2A,
            zone,
            fallback,
        )
        val expected = LocalDate.of(2026, 8, 8)
            .atTime(LocalTime.of(14, 46, 26))
            .atZone(zone)
            .toInstant()
        assertThat(instant).isEqualTo(expected)
    }

    @Test
    fun `parses ICICI on-Aug mon-name date`() {
        val instant = SmsDateExtractor.extract(
            SampleSms.OWN_ACCOUNT_UPI_DEBIT_100000,
            zone,
            fallback,
        )
        val expected = LocalDate.of(2026, 8, 1)
            .atTime(LocalTime.NOON)
            .atZone(zone)
            .toInstant()
        assertThat(instant).isEqualTo(expected)
    }

    @Test
    fun `parses IDBI as-of mon without mistaking time for year`() {
        val body =
            "IDBI Bank A/c NN59024 credited for INR 15000.00 through Net Banking. Bal INR 44996.19 (incl. of chq in clg)  as of 05 AUG 06:44 hrs."
        val fallback2026 = Instant.parse("2026-08-09T12:00:00Z")
        val instant = SmsDateExtractor.extract(body, zone, fallback2026)
        val expected = LocalDate.of(2026, 8, 5)
            .atTime(LocalTime.of(6, 44))
            .atZone(zone)
            .toInstant()
        assertThat(instant).isEqualTo(expected)
    }
}
