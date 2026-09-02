package com.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupeHash"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["merchant"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: BigDecimal,
    val type: String,
    val merchant: String?,
    val category: String,
    val bank: String?,
    val accountLast4: String?,
    val cardLast4: String?,
    val upiId: String?,
    val referenceNumber: String?,
    val balance: BigDecimal?,
    val paymentMode: String,
    val timestamp: Instant,
    val sender: String?,
    val rawSms: String?,
    val narration: String?,
    val notes: String?,
    val dedupeHash: String,
    val manuallyEdited: Boolean = false,
    val tagsJson: String? = null,
    val isSplit: Boolean = false,
    val splitJson: String? = null,
)
