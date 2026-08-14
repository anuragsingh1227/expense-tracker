package com.expensetracker.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Validates a JSON backup **before** any SQLite writes. Malformed payloads throw
 * [BackupSchemaException] and must leave the existing database untouched.
 */
object BackupSchema {

    private val REQUIRED_TX_FIELDS = listOf(
        "amount", "type", "category", "paymentMode", "timestamp", "dedupeHash",
    )
    private val REQUIRED_LABEL_FIELDS = listOf("label")
    private val REQUIRED_BUDGET_FIELDS = listOf("category", "monthlyLimit", "startsAt")
    private val REQUIRED_MERCHANT_FIELDS = listOf("key", "displayName", "category")
    private val REQUIRED_CARD_FIELDS = listOf("dueDateEpochDay", "timestamp", "dedupeHash")

    fun validate(json: String): JSONObject {
        if (json.isBlank()) {
            throw BackupSchemaException("Backup JSON is empty")
        }
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw BackupSchemaException("Malformed backup JSON: ${e.message}", e)
        }

        val version = root.optInt("version", 1)
        if (version !in 1..BackupRepository.VERSION) {
            throw BackupSchemaException("Unsupported backup version $version")
        }

        validateArray(root, "transactions") { obj, index ->
            REQUIRED_TX_FIELDS.forEach { field ->
                if (!obj.has(field) || obj.isNull(field)) {
                    throw BackupSchemaException("transactions[$index] missing required field '$field'")
                }
            }
            val amount = obj.optString("amount")
            if (amount.isBlank()) {
                throw BackupSchemaException("transactions[$index] has an empty amount")
            }
            val type = obj.optString("type")
            if (type != "DEBIT" && type != "CREDIT") {
                throw BackupSchemaException("transactions[$index] has invalid type '$type'")
            }
            if (!obj.has("timestamp") || obj.optLong("timestamp", Long.MIN_VALUE) == Long.MIN_VALUE) {
                throw BackupSchemaException("transactions[$index] has an invalid timestamp")
            }
            val hash = obj.optString("dedupeHash")
            if (hash.isBlank()) {
                throw BackupSchemaException("transactions[$index] missing dedupeHash")
            }
            if (obj.has("tags") && !obj.isNull("tags") && obj.optJSONArray("tags") == null) {
                throw BackupSchemaException("transactions[$index].tags must be a JSON array")
            }
            if (obj.has("splitShares") && !obj.isNull("splitShares") && obj.optJSONArray("splitShares") == null) {
                throw BackupSchemaException("transactions[$index].splitShares must be a JSON array")
            }
        }

        validateArray(root, "labelRules") { obj, index ->
            REQUIRED_LABEL_FIELDS.forEach { field ->
                if (!obj.has(field) || obj.optString(field).isBlank()) {
                    throw BackupSchemaException("labelRules[$index] missing required field '$field'")
                }
            }
        }

        validateArray(root, "budgets") { obj, index ->
            REQUIRED_BUDGET_FIELDS.forEach { field ->
                if (!obj.has(field) || obj.isNull(field)) {
                    throw BackupSchemaException("budgets[$index] missing required field '$field'")
                }
            }
        }

        validateArray(root, "merchants") { obj, index ->
            REQUIRED_MERCHANT_FIELDS.forEach { field ->
                if (!obj.has(field) || obj.optString(field).isBlank()) {
                    throw BackupSchemaException("merchants[$index] missing required field '$field'")
                }
            }
        }

        validateArray(root, "cardStatements") { obj, index ->
            REQUIRED_CARD_FIELDS.forEach { field ->
                if (!obj.has(field) || obj.isNull(field)) {
                    throw BackupSchemaException("cardStatements[$index] missing required field '$field'")
                }
            }
            if (obj.optString("dedupeHash").isBlank()) {
                throw BackupSchemaException("cardStatements[$index] missing dedupeHash")
            }
        }

        if (root.has("settings") && !root.isNull("settings") && root.optJSONObject("settings") == null) {
            throw BackupSchemaException("settings must be a JSON object")
        }

        return root
    }

    private fun validateArray(
        root: JSONObject,
        key: String,
        validateItem: (JSONObject, Int) -> Unit,
    ) {
        if (!root.has(key) || root.isNull(key)) return
        val arr = root.optJSONArray(key)
            ?: throw BackupSchemaException("'$key' must be a JSON array")
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
                ?: throw BackupSchemaException("$key[$i] must be a JSON object")
            validateItem(obj, i)
        }
    }
}
