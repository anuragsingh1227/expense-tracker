package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import java.time.Duration

/**
 * Finds debit↔credit pairs that are own-account transfers (same amount, close in
 * time, owner name like ANURAG in at least one SMS) so both legs can be removed
 * from the ledger / spend totals.
 */
object SelfTransferLinker {

    /** Default owner first-name hint from the account holder's SMS text. */
    val DEFAULT_OWNER_NAMES: List<String> = listOf("ANURAG")

    /** Max gap between the debit and credit SMS of a self-transfer pair. */
    val MATCH_WINDOW: Duration = Duration.ofHours(72)

    data class PairMatch(
        val debit: Transaction,
        val credit: Transaction,
    )

    /**
     * Returns disjoint debit/credit pairs that look like money moved between the
     * user's own accounts. Each transaction appears in at most one pair.
     */
    fun findPairs(
        transactions: List<Transaction>,
        ownerNames: List<String> = DEFAULT_OWNER_NAMES,
        window: Duration = MATCH_WINDOW,
    ): List<PairMatch> {
        if (ownerNames.isEmpty()) return emptyList()
        val nameHints = ownerNames.map { it.trim().uppercase() }.filter { it.length >= 2 }
        if (nameHints.isEmpty()) return emptyList()

        val debits = transactions
            .filter { it.type == TransactionType.DEBIT && !it.manuallyEdited }
            .sortedBy { it.timestamp }
        val credits = transactions
            .filter { it.type == TransactionType.CREDIT && !it.manuallyEdited }
            .sortedBy { it.timestamp }
            .toMutableList()

        val pairs = mutableListOf<PairMatch>()
        val usedCreditIds = mutableSetOf<Long>()

        for (debit in debits) {
            val match = credits.firstOrNull { credit ->
                credit.id !in usedCreditIds &&
                    amountsEqual(debit, credit) &&
                    withinWindow(debit, credit, window) &&
                    mentionsOwner(debit, credit, nameHints)
            } ?: continue
            usedCreditIds += match.id
            pairs += PairMatch(debit = debit, credit = match)
        }
        return pairs
    }

    /** Flattened ids of both legs — convenient for bulk delete. */
    fun idsToRemove(pairs: List<PairMatch>): List<Long> =
        pairs.flatMap { listOf(it.debit.id, it.credit.id) }.filter { it > 0 }

    private fun amountsEqual(a: Transaction, b: Transaction): Boolean =
        a.amount.amount.compareTo(b.amount.amount) == 0

    private fun withinWindow(a: Transaction, b: Transaction, window: Duration): Boolean {
        val delta = Duration.between(a.timestamp, b.timestamp).abs()
        return delta <= window
    }

    private fun mentionsOwner(
        debit: Transaction,
        credit: Transaction,
        nameHints: List<String>,
    ): Boolean {
        val haystack = buildString {
            append(debit.rawSms.orEmpty()).append(' ')
            append(credit.rawSms.orEmpty()).append(' ')
            append(debit.narration.orEmpty()).append(' ')
            append(credit.narration.orEmpty()).append(' ')
            append(debit.merchant.orEmpty()).append(' ')
            append(credit.merchant.orEmpty())
        }.uppercase()
        return nameHints.any { hint ->
            Regex("""\b${Regex.escape(hint)}\b""").containsMatchIn(haystack)
        }
    }
}
