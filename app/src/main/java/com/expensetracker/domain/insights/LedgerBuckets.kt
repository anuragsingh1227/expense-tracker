package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories

/**
 * In-memory mirrors of [com.expensetracker.data.db.dao.TransactionDao] spend/income/
 * investment filters. Keep these identical to the SQL `NOT IN` / `=` clauses.
 */
object LedgerBuckets {

    fun isSpend(tx: Transaction): Boolean =
        tx.type == TransactionType.DEBIT &&
            tx.category != Categories.TRANSFER &&
            tx.category != Categories.INVESTMENT

    fun isIncome(tx: Transaction): Boolean =
        tx.type == TransactionType.CREDIT &&
            tx.category != Categories.TRANSFER &&
            tx.category != Categories.REFUND

    fun isInvestment(tx: Transaction): Boolean =
        tx.type == TransactionType.DEBIT && tx.category == Categories.INVESTMENT

    fun isTransfer(tx: Transaction): Boolean =
        tx.category == Categories.TRANSFER

    /** A reversed/refunded amount — must offset spend, never count as income. */
    fun isRefund(tx: Transaction): Boolean =
        tx.type == TransactionType.CREDIT && tx.category == Categories.REFUND

    /** Debit spend minus refunds for the same window, floored at zero. */
    fun spend(txs: Iterable<Transaction>): Money {
        val debitSpend = txs.filter(::isSpend).fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        val refunds = txs.filter(::isRefund).fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        val net = debitSpend - refunds
        return if (net.amount.signum() < 0) Money.ZERO else net
    }

    fun income(txs: Iterable<Transaction>): Money =
        txs.filter(::isIncome).fold(Money.ZERO) { acc, tx -> acc + tx.amount }

    fun investments(txs: Iterable<Transaction>): Money =
        txs.filter(::isInvestment).fold(Money.ZERO) { acc, tx -> acc + tx.amount }
}
