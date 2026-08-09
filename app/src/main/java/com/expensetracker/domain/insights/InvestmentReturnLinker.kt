package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import java.time.Duration

/**
 * Pairs an Investment debit with a matching bank credit when an MF/broker
 * purchase is refunded/bounced (different UPI vs IMPS refs, so
 * [SelfTransferLinker] cannot match).
 *
 * Both legs are removed from Activity so spend/income/investment stay at net
 * zero for the round-trip. Kept Motilal/Groww debits with no matching return
 * remain as Investment.
 *
 * Matching is intentionally narrow to avoid deleting an unrelated same-amount
 * credit (e.g. a friend IMPS-ing the same figure):
 * - debit must be [Categories.INVESTMENT]
 * - credit must be Transfer or Refund (never Salary / income)
 * - credit must look like a refund rail (anonymous mobile IMPS, refund text,
 *   or investment-platform wording)
 * - equal amount, within [MATCH_WINDOW] either direction
 */
object InvestmentReturnLinker {

    /** Broker apps often say refunds take 5–7 working days. */
    val MATCH_WINDOW: Duration = Duration.ofDays(14)

    data class PairMatch(
        val debit: Transaction,
        val credit: Transaction,
    )

    fun findPairs(
        transactions: List<Transaction>,
        window: Duration = MATCH_WINDOW,
    ): List<PairMatch> {
        val editable = transactions.filter { !it.manuallyEdited }
        val debits = editable
            .filter { it.type == TransactionType.DEBIT && it.category == Categories.INVESTMENT }
            .sortedBy { it.timestamp }
        val credits = editable
            .filter { it.type == TransactionType.CREDIT && isEligibleReturnCredit(it) }
            .sortedBy { it.timestamp }

        val usedCreditIds = mutableSetOf<Long>()
        val pairs = mutableListOf<PairMatch>()
        for (debit in debits) {
            val match = credits
                .filter { credit ->
                    credit.id !in usedCreditIds &&
                        amountsEqual(debit, credit) &&
                        withinWindow(debit, credit, window)
                }
                .minByOrNull { Duration.between(debit.timestamp, it.timestamp).abs() }
                ?: continue
            usedCreditIds += match.id
            pairs += PairMatch(debit = debit, credit = match)
        }
        return pairs
    }

    fun idsToRemove(pairs: List<PairMatch>): List<Long> =
        pairs.flatMap { listOf(it.debit.id, it.credit.id) }.filter { it > 0 }

    private fun isEligibleReturnCredit(tx: Transaction): Boolean {
        if (tx.category == Categories.SALARY) return false
        if (tx.category != Categories.TRANSFER && tx.category != Categories.REFUND) return false
        return looksLikeInvestmentReturnRail(tx)
    }

    /**
     * Anonymous IMPS ("account linked to mobile number"), explicit refund text,
     * or investment-platform wording on the credit SMS.
     */
    internal fun looksLikeInvestmentReturnRail(tx: Transaction): Boolean {
        val haystack = buildString {
            append(tx.rawSms.orEmpty()).append(' ')
            append(tx.narration.orEmpty()).append(' ')
            append(tx.merchant.orEmpty())
        }.uppercase()
        if (haystack.contains("REFUND") || haystack.contains("REVERSAL") ||
            haystack.contains("REVERSED") || haystack.contains("REDEMPTION")
        ) {
            return true
        }
        if (haystack.contains("MOBILE NUMBER") || haystack.contains("LINKED TO MOBILE") ||
            haystack.contains("ACCOUNT LINKED TO")
        ) {
            return true
        }
        if (INVESTMENT_RETURN_HINTS.any { haystack.contains(it) }) return true
        return false
    }

    private fun amountsEqual(a: Transaction, b: Transaction): Boolean =
        a.amount.amount.compareTo(b.amount.amount) == 0

    private fun withinWindow(a: Transaction, b: Transaction, window: Duration): Boolean {
        val delta = Duration.between(a.timestamp, b.timestamp).abs()
        return delta <= window
    }

    private val INVESTMENT_RETURN_HINTS = listOf(
        "MOTILAL", "GROWW", "ZERODHA", "ETMONEY", "ET MONEY", "SCRIPBOX",
        "FISDOM", "INDMONEY", "MUTUAL FUND", "PORTFOLIO", "REDEEM",
    )
}
