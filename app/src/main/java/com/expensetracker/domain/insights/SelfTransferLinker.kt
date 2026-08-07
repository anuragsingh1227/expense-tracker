package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import java.time.Duration

/**
 * Finds debit↔credit pairs that are own-account transfers so both legs can be
 * removed from Activity (they're already excluded from spend/income totals via
 * the `Transfer` category — this only declutters the list).
 *
 * Candidates are restricted to transactions the parser already categorized as
 * [Categories.TRANSFER]. This is the key safety property: an unrelated spend
 * (e.g. a Food debit) or unrelated income can never be swept up just because it
 * happens to share an amount and a time window with a real transfer — it's
 * simply not in the candidate pool.
 *
 * Within that pool, a pair sharing the same bank-assigned reference/UTR is an
 * authoritative match (both legs of one transfer always cite the same
 * reference) and is preferred over the weaker "owner name mentioned somewhere"
 * heuristic, which is used only as a fallback for legs without a reference.
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
        val nameHints = ownerNames.map { it.trim().uppercase() }.filter { it.length >= 2 }

        val candidates = transactions.filter { it.category == Categories.TRANSFER && !it.manuallyEdited }
        val debits = candidates.filter { it.type == TransactionType.DEBIT }.sortedBy { it.timestamp }
        val credits = candidates.filter { it.type == TransactionType.CREDIT }.sortedBy { it.timestamp }

        val usedCreditIds = mutableSetOf<Long>()
        val pairs = mutableListOf<PairMatch>()

        for (debit in debits) {
            val candidatesForDebit = credits.filter { credit ->
                credit.id !in usedCreditIds && amountsEqual(debit, credit) && withinWindow(debit, credit, window)
            }
            if (candidatesForDebit.isEmpty()) continue

            // Prefer a shared reference/UTR — authoritative, no ambiguity.
            val referenceMatch = debit.referenceNumber
                ?.takeIf { it.isNotBlank() }
                ?.let { ref -> candidatesForDebit.firstOrNull { it.referenceNumber == ref } }

            val match = referenceMatch
                ?: candidatesForDebit
                    .filter { nameHints.isNotEmpty() && mentionsOwner(debit, it, nameHints) }
                    // Weaker signal: prefer the closest-in-time candidate, not just the first.
                    .minByOrNull { Duration.between(debit.timestamp, it.timestamp).abs() }
                ?: continue

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
