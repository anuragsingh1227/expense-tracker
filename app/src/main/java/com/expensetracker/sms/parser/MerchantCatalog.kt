package com.expensetracker.sms.parser

import com.expensetracker.data.db.dao.MerchantDao
import com.expensetracker.data.db.entity.MerchantEntity
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Built-in dictionary plus user-taught merchant → category overrides
 * (remembered when you edit a transaction’s category).
 */
@Singleton
class MerchantCatalog @Inject constructor(
    private val merchantDao: MerchantDao,
) : MerchantMatcher {

    private val overrides = AtomicReference<List<MerchantEntity>>(emptyList())

    suspend fun refresh() {
        overrides.set(
            merchantDao.getAll().sortedByDescending { it.key.length },
        )
    }

    override fun match(text: String): MerchantDictionary.Entry? {
        val upper = text.uppercase()
        overrides.get().firstOrNull { upper.contains(it.key) }?.let { row ->
            return MerchantDictionary.Entry(row.key, row.displayName, row.category)
        }
        return MerchantDictionary.match(text)
    }

    /** Persist a user choice so future SMS for this merchant get the same category. */
    suspend fun remember(displayName: String, category: String) {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty() || category.isBlank()) return
        val key = trimmed.uppercase()
        merchantDao.insertAll(
            listOf(
                MerchantEntity(
                    key = key,
                    displayName = trimmed,
                    category = category.trim(),
                ),
            ),
        )
        refresh()
    }

    suspend fun allOverrides(): List<MerchantEntity> = merchantDao.getAll()

    suspend fun replaceAll(rows: List<MerchantEntity>) {
        merchantDao.insertAll(rows)
        refresh()
    }
}
