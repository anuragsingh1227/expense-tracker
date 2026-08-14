package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import java.time.ZoneId

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

    /**
     * Debit spend minus refunds for the same window. May legitimately be negative
     * when refunds exceed spend (e.g. a refund-only month) — that is real
     * information, not something to hide behind a zero floor.
     */
    fun spend(txs: Iterable<Transaction>): Money {
        val debitSpend = txs.filter(::isSpend).fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        val refunds = txs.filter(::isRefund).fold(Money.ZERO) { acc, tx -> acc + tx.amount }
        return debitSpend - refunds
    }

    fun income(txs: Iterable<Transaction>): Money =
        txs.filter(::isIncome).fold(Money.ZERO) { acc, tx -> acc + tx.amount }

    fun investments(txs: Iterable<Transaction>): Money =
        txs.filter(::isInvestment).fold(Money.ZERO) { acc, tx -> acc + tx.amount }

    /**
     * Spend by category for [txs], with refunds netted against the original
     * merchant's spend category (FIFO). Unmatched refunds land in
     * [Categories.REFUND] as a negative amount so bars still sum to [spend].
     */
    fun spendByCategory(txs: Iterable<Transaction>): Map<String, Money> {
        data class Pool(val merchant: String?, val category: String, var remaining: Money)

        val byCat = mutableMapOf<String, Money>()
        fun add(category: String, delta: Money) {
            val next = (byCat[category] ?: Money.ZERO) + delta
            if (next.amount.signum() == 0) byCat.remove(category) else byCat[category] = next
        }

        val pools = txs.filter(::isSpend).sortedBy { it.timestamp }.map { tx ->
            add(tx.category, tx.amount)
            Pool(normalizeMerchant(tx.merchant), tx.category, tx.amount)
        }

        for (refund in txs.filter(::isRefund).sortedBy { it.timestamp }) {
            var left = refund.amount
            val merchant = normalizeMerchant(refund.merchant)
            if (merchant != null) {
                for (pool in pools) {
                    if (left.amount.signum() <= 0) break
                    if (pool.remaining.amount.signum() <= 0) continue
                    if (pool.merchant == null || !merchantsMatch(pool.merchant, merchant)) continue
                    val take = if (pool.remaining.amount <= left.amount) pool.remaining else left
                    pool.remaining = pool.remaining - take
                    add(pool.category, Money.ZERO - take)
                    left = left - take
                }
            }
            if (left.amount.signum() > 0) {
                add(Categories.REFUND, Money.ZERO - left)
            }
        }
        return byCat.toMap()
    }

    /**
     * Same as [spendByCategory] but grouped by local calendar month (`yyyy-MM`)
     * so stacked-month columns net refunds in the month they arrived.
     */
    fun spendByCategoryAndMonth(
        txs: Iterable<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CategoryMonthSpend> =
        txs.groupBy { monthKey(it, zone) }.flatMap { (monthKey, monthTxs) ->
            spendByCategory(monthTxs).map { (category, amount) ->
                CategoryMonthSpend(category = category, monthKey = monthKey, amount = amount)
            }
        }

    private fun normalizeMerchant(merchant: String?): String? =
        merchant?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** Exact match, or one name contains the other (min 4 chars) — "swiggy" vs "swiggy instamart". */
    internal fun merchantsMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        return a.contains(b) || b.contains(a)
    }

    private fun monthKey(tx: Transaction, zone: ZoneId): String {
        val date = tx.timestamp.atZone(zone).toLocalDate()
        return "%04d-%02d".format(date.year, date.monthValue)
    }
}
