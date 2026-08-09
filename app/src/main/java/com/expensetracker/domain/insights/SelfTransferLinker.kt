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
 * Matching rules (in order):
 * 1. **Shared bank reference/UTR/UPI id** across any non-manually-edited
 *    debit↔credit of equal amount in the time window — authoritative. Safe
 *    even when an older parse miscategorized a leg (e.g. UPI self-move booked
 *    as spend before Transfer detection improved).
 * 2. Within rows already categorized [Categories.TRANSFER], the weaker
 *    "owner name mentioned" heuristic as a fallback when no reference exists.
 *
 * Unrelated same-amount spends without a shared reference are never paired.
 */
object SelfTransferLinker {

    /** Max gap between the debit and credit SMS of a self-transfer pair. */
    val MATCH_WINDOW: Duration = Duration.ofHours(72)

    data class PairMatch(
        val debit: Transaction,
        val credit: Transaction,
    )

    /**
     * Returns disjoint debit/credit pairs that look like money moved between the
     * user's own accounts. Each transaction appears in at most one pair.
     *
     * Pass the configured owner name(s) from Settings. When empty, only
     * reference/UTR-based matches are linked (never a hardcoded name).
     */
    fun findPairs(
        transactions: List<Transaction>,
        ownerNames: List<String> = emptyList(),
        window: Duration = MATCH_WINDOW,
    ): List<PairMatch> {
        val nameHints = ownerNames.map { it.trim().uppercase() }.filter { it.length >= 2 }
        val editable = transactions.filter { !it.manuallyEdited }
        val allDebits = editable.filter { it.type == TransactionType.DEBIT }.sortedBy { it.timestamp }
        val allCredits = editable.filter { it.type == TransactionType.CREDIT }.sortedBy { it.timestamp }

        val usedDebitIds = mutableSetOf<Long>()
        val usedCreditIds = mutableSetOf<Long>()
        val pairs = mutableListOf<PairMatch>()

        // Pass 1: shared reference — authoritative across any category.
        // Prefer stored referenceNumber; fall back to raw SMS for legacy rows.
        for (debit in allDebits) {
            val ref = effectiveReference(debit) ?: continue
            val match = allCredits.firstOrNull { credit ->
                credit.id !in usedCreditIds &&
                    effectiveReference(credit) == ref &&
                    amountsEqual(debit, credit) &&
                    withinWindow(debit, credit, window)
            } ?: continue
            usedDebitIds += debit.id
            usedCreditIds += match.id
            pairs += PairMatch(debit = debit, credit = match)
        }

        // Pass 2: Transfer-category + owner-name heuristic when refs don't already
        // pair (e.g. Axis IMPS/P2A/…/Anur debit vs IDBI credit with no shared UTR).
        val transferDebits = allDebits.filter {
            it.id !in usedDebitIds && it.category == Categories.TRANSFER
        }
        val transferCredits = allCredits.filter {
            it.id !in usedCreditIds && it.category == Categories.TRANSFER
        }
        for (debit in transferDebits) {
            if (debit.id in usedDebitIds) continue
            val match = transferCredits
                .filter { credit ->
                    credit.id !in usedCreditIds &&
                        amountsEqual(debit, credit) &&
                        withinWindow(debit, credit, window) &&
                        nameHints.isNotEmpty() &&
                        mentionsOwner(debit, credit, nameHints)
                }
                .minByOrNull { Duration.between(debit.timestamp, it.timestamp).abs() }
                ?: continue
            usedDebitIds += debit.id
            usedCreditIds += match.id
            pairs += PairMatch(debit = debit, credit = match)
        }
        return pairs
    }

    /** Stored ref, or one parsed from [Transaction.rawSms] for legacy rows. */
    internal fun effectiveReference(tx: Transaction): String? =
        tx.referenceNumber?.takeIf { it.isNotBlank() } ?: extractReferenceFromRaw(tx.rawSms)

    internal fun extractReferenceFromRaw(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        RAW_REFERENCE_PATTERNS.forEach { pattern ->
            pattern.find(raw)?.let { return it.groupValues[1] }
        }
        return null
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

    private val RAW_REFERENCE_PATTERNS = listOf(
        Regex("""(?i)UPI(?:\s*ref)?[:\s]*([0-9]{9,})"""),
        Regex("""(?i)UPI/[A-Z0-9]+/([0-9]{9,})"""),
        Regex("""(?i)(?:NEFT|IMPS|RTGS)/[A-Z0-9]{1,3}/([A-Z0-9]{8,})"""),
        Regex("""(?i)(?:ref(?:erence)?\.?(?:\s*no\.?)?|txn(?:\s*id)?\.?|utr)[:\s#]*([A-Z0-9]{6,})"""),
    )
}
