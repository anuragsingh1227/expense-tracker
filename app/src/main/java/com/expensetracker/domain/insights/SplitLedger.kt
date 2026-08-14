package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import java.math.BigDecimal
import java.math.RoundingMode

data class PeerBalance(
    val name: String,
    /** Positive = they owe the account holder; negative = the holder owes them. */
    val owedToMe: Money,
)

/**
 * Aggregates offline split-spend shares into a per-person balance.
 */
object SplitLedger {

    fun balances(txs: Iterable<Transaction>): List<PeerBalance> {
        val totals = linkedMapOf<String, BigDecimal>()
        txs.asSequence().filter { it.isSplit }.forEach { tx ->
            tx.splitShares.forEach { share ->
                val name = share.name.trim()
                if (name.isEmpty()) return@forEach
                val key = totals.keys.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
                val current = totals[key] ?: BigDecimal.ZERO
                totals[key] = current + share.amountOwed.amount
            }
        }
        return totals.map { (name, amount) ->
            PeerBalance(name, Money(amount.setScale(2, RoundingMode.HALF_UP)))
        }.filter { it.owedToMe.amount.signum() != 0 }
            .sortedByDescending { it.owedToMe.amount }
    }

    fun totalOwedToMe(balances: Iterable<PeerBalance>): Money =
        balances.filter { it.owedToMe.amount.signum() > 0 }
            .fold(Money.ZERO) { acc, row -> acc + row.owedToMe }

    fun totalIOwe(balances: Iterable<PeerBalance>): Money =
        balances.filter { it.owedToMe.amount.signum() < 0 }
            .fold(Money.ZERO) { acc, row -> acc + Money(row.owedToMe.amount.abs()) }
}
