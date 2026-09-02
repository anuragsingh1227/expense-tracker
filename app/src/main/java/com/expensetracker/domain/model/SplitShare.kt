package com.expensetracker.domain.model

/**
 * One peer's share of a split spend. [amountOwed] is what [name] owes the
 * account holder (e.g. "Rohan owes ₹450").
 */
data class SplitShare(
    val name: String,
    val amountOwed: Money,
)
