package com.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(
    tableName = "card_statements",
    indices = [Index(value = ["dedupeHash"], unique = true), Index(value = ["dueDateEpochDay"])],
)
data class CardStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String?,
    val cardLast4: String?,
    val totalDue: BigDecimal?,
    val minDue: BigDecimal?,
    val dueDateEpochDay: Long,
    val timestamp: Instant,
    val sender: String?,
    val rawSms: String?,
    val dedupeHash: String,
)
