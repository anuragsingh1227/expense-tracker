package com.expensetracker.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * A credit-card statement / due-date SMS — not a ledger transaction.
 * Stored on-device so local reminders can fire 3 days before and on the due date.
 */
data class CardStatement(
    val id: Long = 0,
    val bank: String?,
    val cardLast4: String?,
    val totalDue: Money?,
    val minDue: Money?,
    val dueDate: LocalDate,
    val timestamp: Instant,
    val sender: String?,
    val rawSms: String?,
    val dedupeHash: String,
)
