package com.expensetracker.sms.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class OwnerNameTransferTest {

    private val now = Instant.parse("2026-06-15T15:00:00Z")

    @Test
    fun `configured owner name marks transfer SMS as Transfer`() {
        // Avoid NEFT+A/C heuristics so only the owner-name path can force Transfer.
        val body =
            "Rs 2500.00 transferred to PRIYA SINGH on 15-06-26. UPI Ref 401234567890"
        val withOwner = SmsParser(ownerNames = { listOf("Priya") })
            .parse(RawSms("VM-HDFCBK", body, now))!!
        assertThat(withOwner.category).isEqualTo(Categories.TRANSFER)

        val wrongName = SmsParser(ownerNames = { listOf("Ananya") })
            .parse(RawSms("VM-HDFCBK", body, now))!!
        assertThat(wrongName.category).isNotEqualTo(Categories.TRANSFER)
    }

    @Test
    fun `empty owner names never force Transfer via name hint`() {
        val body =
            "Rs 2500.00 transferred to PRIYA SINGH on 15-06-26. UPI Ref 401234567890"
        val tx = SmsParser(ownerNames = { emptyList() })
            .parse(RawSms("VM-HDFCBK", body, now))!!
        assertThat(tx.category).isNotEqualTo(Categories.TRANSFER)
    }
}
