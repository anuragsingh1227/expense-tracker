package com.expensetracker.data.backup

import com.expensetracker.data.AppSettings
import com.expensetracker.data.OwnerNameProvider
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.BudgetEntity
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.db.entity.MerchantEntity
import com.expensetracker.data.db.entity.SettingsEntity
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
    val budgetsRestored: Int = 0,
    val settingsRestored: Int = 0,
)

/**
 * JSON backup of transactions + learned merchants + label rules + budgets +
 * selected settings (owner name, app lock, amounts hidden).
 * Write/read via the system document picker — pick Google Drive there
 * to sync across phones without giving this app internet access.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val merchantCatalog: MerchantCatalog,
    private val labelRuleCatalog: LabelRuleCatalog,
    private val budgetDao: BudgetDao,
    private val settingsDao: SettingsDao,
    private val ownerNameProvider: OwnerNameProvider,
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

        val budgetArray = JSONArray()
        budgetDao.getAll().forEach { b ->
            budgetArray.put(
                JSONObject()
                    .put("category", b.category)
                    .put("monthlyLimit", b.monthlyLimit.toPlainString())
                    .put("startsAt", b.startsAt.toEpochMilli()),
            )
        }
        root.put("budgets", budgetArray)

        val settingsObj = JSONObject()
        SETTINGS_KEYS.forEach { key ->
            settingsDao.get(key)?.let { settingsObj.put(key, it) }
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    /** CSV of all transactions for spreadsheet tools. */
    suspend fun exportCsv(): String {
        val header = listOf(
            "timestamp", "type", "amount", "merchant", "category", "bank",
            "paymentMode", "notes", "reference", "sender",
        ).joinToString(",")
        val rows = transactionDao.getAllOnce().map { tx ->
            listOf(
                Instant.ofEpochMilli(tx.timestamp.toEpochMilli()).toString(),
                tx.type,
                tx.amount.toPlainString(),
                csvEscape(tx.merchant),
                csvEscape(tx.category),
                csvEscape(tx.bank),
                tx.paymentMode,
                csvEscape(tx.notes),
                csvEscape(tx.referenceNumber),
                csvEscape(tx.sender),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
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

        var budgetsRestored = 0
        val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
        if (budgetArray.length() > 0) {
            budgetDao.deleteAll()
            for (i in 0 until budgetArray.length()) {
                val o = budgetArray.getJSONObject(i)
                budgetDao.upsert(
                    BudgetEntity(
                        category = o.getString("category"),
                        monthlyLimit = BigDecimal(o.getString("monthlyLimit")),
                        startsAt = Instant.ofEpochMilli(o.getLong("startsAt")),
                    ),
                )
                budgetsRestored++
            }
        }

        var settingsRestored = 0
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val entities = mutableListOf<SettingsEntity>()
            SETTINGS_KEYS.forEach { key ->
                if (settingsObj.has(key) && !settingsObj.isNull(key)) {
                    entities += SettingsEntity(key, settingsObj.getString(key))
                    settingsRestored++
                }
            }
            if (entities.isNotEmpty()) {
                settingsDao.putAll(entities)
                ownerNameProvider.refresh()
            }
        }

        return BackupImportResult(
            transactionsInserted = inserted,
            transactionsSkipped = skipped,
            merchantsRestored = merchants.size,
            labelRulesRestored = labelRules.size,
            budgetsRestored = budgetsRestored,
            settingsRestored = settingsRestored,
        )
    }

    companion object {
        const val VERSION = 3
        const val MIME_TYPE = "application/json"
        const val CSV_MIME_TYPE = "text/csv"
        const val FILE_PREFIX = "expense-tracker-backup"
        const val CSV_FILE_PREFIX = "expense-tracker-export"

        /** Settings included in backup (never scan watermarks). */
        val SETTINGS_KEYS = listOf(
            AppSettings.OWNER_NAME,
            AppSettings.APP_LOCK_ENABLED,
            AppSettings.APP_LOCK_PIN_HASH,
            AppSettings.APP_LOCK_PIN_SALT,
            AppSettings.APP_LOCK_BIOMETRIC_ENABLED,
            AppSettings.AMOUNTS_HIDDEN,
        )
    }
}

private fun csvEscape(value: String?): String {
    val raw = value.orEmpty()
    return if (raw.contains(',') || raw.contains('"') || raw.contains('\n')) {
        "\"" + raw.replace("\"", "\"\"") + "\""
    } else {
        raw
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.takeIf { it.isNotEmpty() && it != "null" }
}
