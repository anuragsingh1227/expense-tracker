package com.expensetracker.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Serializes tags and split shares to/from JSON columns so v1.1.8 backups
 * (which omit these fields) restore with empty defaults.
 */
object TransactionExtras {

    fun tagsToJson(tags: List<String>): String? {
        val clean = tags.map { HashtagParser.canonicalize(it) }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (clean.isEmpty()) return null
        return JSONArray(clean).toString()
    }

    fun tagsFromJson(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i)?.let(HashtagParser::canonicalize)?.takeIf { it.isNotEmpty() }
            }.distinctBy { it.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun splitsToJson(shares: List<SplitShare>): String? {
        val clean = shares.mapNotNull { share ->
            val name = share.name.trim()
            if (name.isEmpty() || share.amountOwed.amount.signum() == 0) return@mapNotNull null
            JSONObject()
                .put("name", name)
                .put("amountOwed", share.amountOwed.amount.toPlainString())
        }
        if (clean.isEmpty()) return null
        val arr = JSONArray()
        clean.forEach { arr.put(it) }
        return arr.toString()
    }

    fun splitsFromJson(raw: String?): List<SplitShare> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                val amount = o.optString("amountOwed")
                if (name.isEmpty() || amount.isBlank()) return@mapNotNull null
                val money = runCatching {
                    Money(BigDecimal(amount).setScale(2, RoundingMode.HALF_UP))
                }.getOrNull() ?: return@mapNotNull null
                SplitShare(name, money)
            }
        }.getOrDefault(emptyList())
    }
}
