package com.expensetracker.domain.insights

import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import java.time.ZoneId

/**
 * Collapses duplicate bank alerts for the same ACH/NACH/ECS mandate collect.
 *
 * Axis often sends both a NACH/UMRN SMS and a compact `ACH-DR-…` SMS for one
 * EMI or SIP pull. Identical NACH retries with the same UMRN are also collapsed
 * here as a safety net (primary defence is UMRN-stable [SmsParser] dedupe).
 *
 * Prefer keeping the UMRN-bearing NACH row when present; otherwise keep the
 * earliest row. Returns ids of the duplicate legs to delete.
 */
object MandateDuplicateLinker {

    fun idsToRemove(
        transactions: List<Transaction>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val candidates = transactions.filter {
            !it.manuallyEdited &&
                it.type == TransactionType.DEBIT &&
                isMandateRail(it) &&
                (it.category == Categories.EMI || it.category == Categories.INVESTMENT)
        }
        if (candidates.size < 2) return emptyList()

        val groups = candidates.groupBy { tx ->
            val month = tx.timestamp.atZone(zone).toLocalDate().withDayOfMonth(1)
            val acct = tx.accountLast4.orEmpty()
            val amt = tx.amount.amount.stripTrailingZeros().toPlainString()
            val payee = normalizePayee(tx)
            "$month|$acct|$amt|$payee|${tx.category}"
        }

        val remove = mutableListOf<Long>()
        for ((_, group) in groups) {
            if (group.size < 2) continue
            val hasNach = group.any { isNachStyle(it) }
            val hasAchDr = group.any { isAchDrStyle(it) }
            // Collapse cross-rail twins (NACH ↔ ACH-DR) or same-rail retries.
            if (!hasNach && !hasAchDr) continue
            if (hasNach && hasAchDr || group.size >= 2 && (hasNach || hasAchDr)) {
                val keep = group.firstOrNull { hasUmrn(it) }
                    ?: group.minByOrNull { it.timestamp }
                    ?: continue
                for (tx in group) {
                    if (tx.id != keep.id && tx.id > 0) remove += tx.id
                }
            }
        }
        return remove.distinct()
    }

    private fun isMandateRail(tx: Transaction): Boolean {
        val hay = haystack(tx)
        return hay.contains("NACH") || hay.contains("UMRN") || hay.contains("ACH-DR") ||
            hay.contains("ACH/DR") || hay.contains("ACH DR") || hay.contains("ECS") ||
            hay.contains("ACH*")
    }

    private fun isNachStyle(tx: Transaction): Boolean {
        val hay = haystack(tx)
        return hay.contains("NACH") || hay.contains("UMRN") || hay.contains("ECS")
    }

    private fun isAchDrStyle(tx: Transaction): Boolean {
        val hay = haystack(tx)
        return hay.contains("ACH-DR") || hay.contains("ACH/DR") || hay.contains("ACH DR") ||
            hay.contains("ACH*")
    }

    private fun hasUmrn(tx: Transaction): Boolean =
        haystack(tx).contains("UMRN")

    private fun haystack(tx: Transaction): String =
        buildString {
            append(tx.rawSms.orEmpty()).append(' ')
            append(tx.narration.orEmpty()).append(' ')
            append(tx.referenceNumber.orEmpty())
        }.uppercase()

    private fun normalizePayee(tx: Transaction): String {
        val merchant = tx.merchant?.uppercase()?.replace(Regex("""\s+"""), " ")?.trim().orEmpty()
        if (merchant.isNotBlank()) return merchant.take(24)
        val raw = tx.rawSms.orEmpty().uppercase()
        Regex("""ACH[-/ ]?DR[-/ ]([A-Z0-9 .&']{3,40})""").find(raw)?.groupValues?.get(1)?.let {
            return it.trim().take(24)
        }
        Regex("""TOWARDS\s+([A-Z0-9 .&']{3,40})""").find(raw)?.groupValues?.get(1)?.let {
            return it.trim().take(24)
        }
        if (raw.contains("RACPC")) return "RACPC"
        return "UNKNOWN"
    }
}
