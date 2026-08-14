package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Transaction
import java.math.BigDecimal
import java.time.Instant
import kotlin.math.abs

/**
 * Collapses the same payment arriving twice — typically a UPI-app notification
 * and the matching bank SMS — into one ledger row.
 *
 * Two rows are duplicates when `(timestamp, amount, last4, merchant)` match
 * inside a [WINDOW_MS] window.
 */
object LedgerDedupe {

    const val WINDOW_MS = 60_000L

    fun last4(tx: Transaction): String? =
        tx.accountLast4?.takeIf { it.isNotBlank() } ?: tx.cardLast4?.takeIf { it.isNotBlank() }

    fun isDuplicate(existing: Transaction, incoming: Transaction): Boolean {
        if (existing.amount.amount.compareTo(incoming.amount.amount) != 0) return false
        val aLast4 = last4(existing) ?: return false
        val bLast4 = last4(incoming) ?: return false
        if (aLast4 != bLast4) return false
        if (normalizeMerchant(existing.merchant) != normalizeMerchant(incoming.merchant)) return false
        return withinWindow(existing.timestamp, incoming.timestamp)
    }

    fun isDuplicate(
        amount: BigDecimal,
        last4: String?,
        merchant: String?,
        timestamp: Instant,
        existingAmount: BigDecimal,
        existingLast4: String?,
        existingMerchant: String?,
        existingTimestamp: Instant,
    ): Boolean {
        if (last4.isNullOrBlank() || existingLast4.isNullOrBlank()) return false
        if (amount.compareTo(existingAmount) != 0) return false
        if (last4 != existingLast4) return false
        if (normalizeMerchant(merchant) != normalizeMerchant(existingMerchant)) return false
        return withinWindow(timestamp, existingTimestamp)
    }

    fun withinWindow(a: Instant, b: Instant): Boolean =
        abs(a.toEpochMilli() - b.toEpochMilli()) <= WINDOW_MS

    private fun normalizeMerchant(merchant: String?): String? =
        merchant?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}
