package com.expensetracker.domain.model

import java.time.Instant

enum class TransactionType { DEBIT, CREDIT }

enum class PaymentMode { UPI, CARD_CREDIT, CARD_DEBIT, NET_BANKING, WALLET, CASH, FASTAG, UNKNOWN }

data class Transaction(
    val id: Long = 0,
    val amount: Money,
    val type: TransactionType,
    val merchant: String?,
    val category: String,
    val bank: String?,
    val accountLast4: String?,
    val cardLast4: String?,
    val upiId: String?,
    val referenceNumber: String?,
    val balance: Money?,
    val paymentMode: PaymentMode,
    val timestamp: Instant,
    val sender: String?,
    val rawSms: String?,
    val narration: String?,
    val notes: String?,
    val dedupeHash: String,
    val manuallyEdited: Boolean = false,
    val tags: List<String> = emptyList(),
    val isSplit: Boolean = false,
    val splitShares: List<SplitShare> = emptyList(),
)
