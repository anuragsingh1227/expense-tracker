package com.expensetracker.data.backup

import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.db.entity.MerchantEntity
import com.expensetracker.data.db.entity.TransactionEntity
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.sms.parser.MerchantCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class BackupImportResult(
    val transactionsInserted: Int,
    val transactionsSkipped: Int,
    val merchantsRestored: Int,
    val labelRulesRestored: Int = 0,
)

/**
 * JSON backup of transactions + learned merchant categories + label rules.
 * Write/read via the system document picker — pick Google Drive there
 * to sync across phones without giving this app internet access.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantCatalog: MerchantCatalog,
    private val labelRuleCatalog: LabelRuleCatalog,
    private val clock: Clock,
) {

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", Instant.now(clock).toString())
        root.put("app", "com.expensetracker")

        val txArray = JSONArray()
        transactionDao.getAllOnce().forEach { tx ->
            txArray.put(
                JSONObject()
                    .put("amount", tx.amount.toPlainString())
                    .put("type", tx.type)
                    .put("merchant", tx.merchant)
                    .put("category", tx.category)
                    .put("bank", tx.bank)
                    .put("accountLast4", tx.accountLast4)
                    .put("cardLast4", tx.cardLast4)
                    .put("upiId", tx.upiId)
                    .put("referenceNumber", tx.referenceNumber)
                    .put("balance", tx.balance?.toPlainString())
                    .put("paymentMode", tx.paymentMode)
                    .put("timestamp", tx.timestamp.toEpochMilli())
                    .put("sender", tx.sender)
                    .put("rawSms", tx.rawSms)
                    .put("narration", tx.narration)
                    .put("notes", tx.notes)
                    .put("dedupeHash", tx.dedupeHash)
                    .put("manuallyEdited", tx.manuallyEdited),
            )
        }
        root.put("transactions", txArray)

        val merchantArray = JSONArray()
        merchantCatalog.allOverrides().forEach { m ->
            merchantArray.put(
                JSONObject()
                    .put("key", m.key)
                    .put("displayName", m.displayName)
                    .put("category", m.category),
            )
        }
        root.put("merchants", merchantArray)

        val labelArray = JSONArray()
        labelRuleCatalog.all().forEach { rule ->
            labelArray.put(
                JSONObject()
                    .put("label", rule.label)
                    .put("senderContains", rule.senderContains)
                    .put("bodyContains", rule.bodyContains)
                    .put("merchantContains", rule.merchantContains),
            )
        }
        root.put("labelRules", labelArray)
        return root.toString(2)
    }

    suspend fun importJson(json: String): BackupImportResult {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        require(version in 1..VERSION) { "Unsupported backup version $version" }

        var inserted = 0
        var skipped = 0
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val o = txArray.getJSONObject(i)
            val entity = TransactionEntity(
                id = 0,
                amount = BigDecimal(o.getString("amount")),
                type = o.getString("type"),
                merchant = o.optStringOrNull("merchant"),
                category = o.getString("category"),
                bank = o.optStringOrNull("bank"),
                accountLast4 = o.optStringOrNull("accountLast4"),
                cardLast4 = o.optStringOrNull("cardLast4"),
                upiId = o.optStringOrNull("upiId"),
                referenceNumber = o.optStringOrNull("referenceNumber"),
                balance = o.optStringOrNull("balance")?.let(::BigDecimal),
                paymentMode = o.getString("paymentMode"),
                timestamp = Instant.ofEpochMilli(o.getLong("timestamp")),
                sender = o.optStringOrNull("sender"),
                rawSms = o.optStringOrNull("rawSms"),
                narration = o.optStringOrNull("narration"),
                notes = o.optStringOrNull("notes"),
                dedupeHash = o.getString("dedupeHash"),
                manuallyEdited = o.optBoolean("manuallyEdited", false),
            )
            val id = transactionDao.insert(entity)
            if (id != -1L) inserted++ else skipped++
        }

        val merchants = mutableListOf<MerchantEntity>()
        val merchantArray = root.optJSONArray("merchants") ?: JSONArray()
        for (i in 0 until merchantArray.length()) {
            val o = merchantArray.getJSONObject(i)
            merchants += MerchantEntity(
                key = o.getString("key"),
                displayName = o.getString("displayName"),
                category = o.getString("category"),
            )
        }
        if (merchants.isNotEmpty()) {
            merchantCatalog.replaceAll(merchants)
        }

        val labelRules = mutableListOf<LabelRuleEntity>()
        val labelArray = root.optJSONArray("labelRules") ?: JSONArray()
        for (i in 0 until labelArray.length()) {
            val o = labelArray.getJSONObject(i)
            labelRules += LabelRuleEntity(
                label = o.getString("label"),
                senderContains = o.optStringOrNull("senderContains"),
                bodyContains = o.optStringOrNull("bodyContains"),
                merchantContains = o.optStringOrNull("merchantContains"),
            )
        }
        if (labelRules.isNotEmpty()) {
            labelRuleCatalog.replaceAll(labelRules)
        }

        return BackupImportResult(
            transactionsInserted = inserted,
            transactionsSkipped = skipped,
            merchantsRestored = merchants.size,
            labelRulesRestored = labelRules.size,
        )
    }

    companion object {
        const val VERSION = 2
        const val MIME_TYPE = "application/json"
        const val FILE_PREFIX = "expense-tracker-backup"
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.takeIf { it.isNotEmpty() && it != "null" }
}
