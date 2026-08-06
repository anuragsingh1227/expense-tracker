package com.expensetracker.sms.parser

import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.entity.LabelRuleEntity
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

fun interface LabelRuleMatcher {
    /** Returns the label (category) when a user rule matches, else null. */
    fun match(sender: String?, body: String, merchant: String?): String?
}

object NoLabelRules : LabelRuleMatcher {
    override fun match(sender: String?, body: String, merchant: String?): String? = null
}

/**
 * User-defined SMS → label rules. Matched with AND across non-empty criteria.
 * More specific rules (more criteria, longer body phrase) win.
 */
@Singleton
class LabelRuleCatalog @Inject constructor(
    private val labelRuleDao: LabelRuleDao,
) : LabelRuleMatcher {

    private val rules = AtomicReference<List<LabelRuleEntity>>(emptyList())

    suspend fun refresh() {
        rules.set(labelRuleDao.getAll().sortedWith(SPECIFICITY))
    }

    override fun match(sender: String?, body: String, merchant: String?): String? {
        val upperSender = sender.orEmpty().uppercase()
        val upperBody = body.uppercase()
        val upperMerchant = merchant.orEmpty().uppercase()
        return rules.get().firstOrNull { rule -> matches(rule, upperSender, upperBody, upperMerchant) }?.label
    }

    suspend fun create(
        label: String,
        senderContains: String?,
        bodyContains: String?,
        merchantContains: String?,
    ): LabelRuleEntity? {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isEmpty()) return null
        val sender = senderContains?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        val body = bodyContains?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        val merchant = merchantContains?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()
        if (sender == null && body == null && merchant == null) return null

        val entity = LabelRuleEntity(
            label = trimmedLabel,
            senderContains = sender,
            bodyContains = body,
            merchantContains = merchant,
        )
        val id = labelRuleDao.insert(entity)
        refresh()
        return entity.copy(id = id)
    }

    suspend fun all(): List<LabelRuleEntity> = labelRuleDao.getAll()

    suspend fun delete(id: Long) {
        labelRuleDao.delete(id)
        refresh()
    }

    suspend fun replaceAll(rows: List<LabelRuleEntity>) {
        labelRuleDao.deleteAll()
        if (rows.isNotEmpty()) labelRuleDao.insertAll(rows.map { it.copy(id = 0) })
        refresh()
    }

    companion object {
        private val SPECIFICITY = compareByDescending<LabelRuleEntity> { specificityScore(it) }
            .thenByDescending { it.bodyContains?.length ?: 0 }
            .thenByDescending { it.id }

        fun specificityScore(rule: LabelRuleEntity): Int {
            var score = 0
            if (!rule.senderContains.isNullOrBlank()) score += 1
            if (!rule.bodyContains.isNullOrBlank()) score += 1
            if (!rule.merchantContains.isNullOrBlank()) score += 1
            return score
        }

        fun matches(
            rule: LabelRuleEntity,
            upperSender: String,
            upperBody: String,
            upperMerchant: String,
        ): Boolean {
            val senderOk = rule.senderContains.isNullOrBlank() ||
                upperSender.contains(rule.senderContains)
            val bodyOk = rule.bodyContains.isNullOrBlank() ||
                upperBody.contains(rule.bodyContains)
            val merchantOk = rule.merchantContains.isNullOrBlank() ||
                upperMerchant.contains(rule.merchantContains)
            val hasCriterion = !rule.senderContains.isNullOrBlank() ||
                !rule.bodyContains.isNullOrBlank() ||
                !rule.merchantContains.isNullOrBlank()
            return hasCriterion && senderOk && bodyOk && merchantOk
        }

        /** Prefer bank code after VM-/AD- prefix when suggesting sender criterion. */
        fun suggestSenderContains(sender: String?): String? {
            if (sender.isNullOrBlank()) return null
            val upper = sender.trim().uppercase()
            val afterDash = upper.substringAfter('-', missingDelimiterValue = "").trim()
            return when {
                afterDash.length >= 3 -> afterDash
                else -> upper
            }
        }

        /** Prefer merchant name when it appears in the SMS body. */
        fun suggestBodyContains(rawSms: String?, merchant: String?, narration: String?): String? {
            val body = rawSms.orEmpty()
            merchant?.trim()?.takeIf { it.length >= 3 && body.contains(it, ignoreCase = true) }?.let {
                return it
            }
            narration?.trim()?.takeIf { it.length >= 3 }?.let { return it.take(40) }
            // Fall back to a longer alphanumeric token from the body (skip tiny words).
            val token = Regex("""[A-Za-z][A-Za-z0-9]{3,}""").findAll(body)
                .map { it.value }
                .firstOrNull { candidate ->
                    val u = candidate.uppercase()
                    u !in COMMON_NOISE
                }
            return token
        }

        private val COMMON_NOISE = setOf(
            "DEBITED", "CREDITED", "DEBIT", "CREDIT", "RS", "INR", "UPI", "INFO",
            "FROM", "YOUR", "ACCOUNT", "AVAL", "AVAILABLE", "BALANCE", "REF", "TXN",
            "PAID", "SENT", "TO", "AT", "THE", "AND", "CARD",
        )
    }
}
