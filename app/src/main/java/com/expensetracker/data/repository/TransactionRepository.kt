package com.expensetracker.data.repository

import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.TransactionEntity
import com.expensetracker.domain.insights.CategoryMonthSpend
import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.insights.LedgerDedupe
import com.expensetracker.domain.insights.SelfTransferLinker
import com.expensetracker.domain.model.HashtagParser
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionExtras
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class CategorySpend(val category: String, val amount: Money)

interface TransactionRepository {
    /** Returns true if a new row was inserted; false if it was a duplicate. */
    suspend fun insertIfNew(tx: Transaction): Boolean
    /** Inserts a user-created transaction (always a new row). Returns the new id. */
    suspend fun insertManual(tx: Transaction): Long
    suspend fun update(tx: Transaction)
    suspend fun delete(id: Long)
    suspend fun deleteIds(ids: Collection<Long>)
    suspend fun find(id: Long): Transaction?
    fun observeRecent(limit: Int = 20): Flow<List<Transaction>>
    fun observeAll(): Flow<List<Transaction>>
    fun observeBetween(from: Instant, to: Instant, limit: Int = 20): Flow<List<Transaction>>
    fun observeTotal(type: TransactionType, from: Instant, to: Instant): Flow<Money>
    /** Credits excluding Transfer — salary/refunds, not self-moves. */
    fun observeIncomeTotal(from: Instant, to: Instant): Flow<Money>
    /** Debits excluding Transfer/Investment — matches typical “spend” views. */
    fun observeSpendTotal(from: Instant, to: Instant): Flow<Money>
    fun observeInvestmentTotal(from: Instant, to: Instant): Flow<Money>
    fun observeCategorySpend(from: Instant, to: Instant, limit: Int = 6): Flow<List<CategorySpend>>
    fun observeCategoryMonthSpend(from: Instant, to: Instant): Flow<List<CategoryMonthSpend>>
    fun search(query: String?): Flow<List<Transaction>>
    fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<Transaction>>
    /** Deletes rows whose raw SMS would no longer pass the transactional gate. */
    suspend fun purgeNonTransactional(parser: SmsParser): Int
    /**
     * Finds debit↔credit pairs — restricted to transactions already categorized
     * [com.expensetracker.sms.parser.Categories.TRANSFER] — that look like
     * own-account transfers, and deletes both legs. Returns the number of rows
     * removed.
     *
     * When [ownerNames] is null, uses the name configured in Settings
     * ([com.expensetracker.data.AppSettings.OWNER_NAME]). If none is set yet,
     * only reference/UTR-based matches are linked (never falls back to a
     * hardcoded name — that would misfire for every other user).
     */
    suspend fun reconcileSelfTransfers(ownerNames: List<String>? = null): Int
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val settingsDao: SettingsDao,
) : TransactionRepository {

    override suspend fun insertIfNew(tx: Transaction): Boolean {
        if (isFuzzyDuplicate(tx)) return false
        val id = dao.insert(tx.toEntity())
        return id != -1L
    }

    override suspend fun insertManual(tx: Transaction): Long {
        val id = dao.insert(tx.toEntity())
        return if (id != -1L) id else dao.findByHash(tx.dedupeHash)?.id ?: -1L
    }

    override suspend fun update(tx: Transaction) = dao.update(tx.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(200).forEach { dao.deleteByIds(it) }
    }

    override suspend fun find(id: Long): Transaction? = dao.findById(id)?.toDomain()

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBetween(from: Instant, to: Instant, limit: Int): Flow<List<Transaction>> =
        dao.observeBetween(from, to, limit).map { list -> list.map { it.toDomain() } }

    override fun observeTotal(type: TransactionType, from: Instant, to: Instant): Flow<Money> =
        dao.observeTotal(type.name, from, to).map { it.toMoney() }

    override fun observeIncomeTotal(from: Instant, to: Instant): Flow<Money> =
        dao.observeIncomeTotal(from, to).map { it.toMoney() }

    override fun observeSpendTotal(from: Instant, to: Instant): Flow<Money> =
        dao.observeSpendTotal(from, to).map { it.toMoney() }

    override fun observeInvestmentTotal(from: Instant, to: Instant): Flow<Money> =
        dao.observeInvestmentTotal(from, to).map { it.toMoney() }

    override fun observeCategorySpend(from: Instant, to: Instant, limit: Int): Flow<List<CategorySpend>> =
        dao.searchBetween(null, from, to).map { rows ->
            LedgerBuckets.spendByCategory(rows.map { it.toDomain() })
                .entries
                .sortedByDescending { it.value.amount }
                .take(limit)
                .map { CategorySpend(it.key, it.value) }
        }

    override fun observeCategoryMonthSpend(from: Instant, to: Instant): Flow<List<CategoryMonthSpend>> =
        dao.searchBetween(null, from, to).map { rows ->
            LedgerBuckets.spendByCategoryAndMonth(
                rows.map { it.toDomain() },
                ZoneId.systemDefault(),
            )
        }

    override fun search(query: String?): Flow<List<Transaction>> =
        dao.search(query?.takeIf { it.isNotBlank() }).map { list -> list.map { it.toDomain() } }

    override fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<Transaction>> =
        dao.searchBetween(query?.takeIf { it.isNotBlank() }, from, to)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun purgeNonTransactional(parser: SmsParser): Int {
        val spamIds = dao.getIdAndRawSms()
            .filter { row -> shouldPurgeImportedSms(row.rawSms, row.manuallyEdited, parser) }
            .map { it.id }
        if (spamIds.isEmpty()) return 0
        spamIds.chunked(200).forEach { dao.deleteByIds(it) }
        return spamIds.size
    }

    companion object {
        /**
         * Clean-spam must only remove imported SMS that the gate no longer accepts.
         * Manual/cash rows (blank rawSms) and user-edited rows are never purged.
         */
        fun shouldPurgeImportedSms(
            rawSms: String?,
            manuallyEdited: Boolean,
            parser: SmsParser,
        ): Boolean {
            if (manuallyEdited) return false
            if (rawSms.isNullOrBlank()) return false
            return !parser.isTransactional(rawSms)
        }
    }

    override suspend fun reconcileSelfTransfers(ownerNames: List<String>?): Int {
        val names = ownerNames ?: resolveConfiguredOwnerNames()
        val all = dao.getAllOnce().map { it.toDomain() }
        val ids = SelfTransferLinker.idsToRemove(SelfTransferLinker.findPairs(all, names))
        if (ids.isEmpty()) return 0
        ids.chunked(200).forEach { dao.deleteByIds(it) }
        return ids.size
    }

    private suspend fun resolveConfiguredOwnerNames(): List<String> {
        val configured = settingsDao.get(AppSettings.OWNER_NAME)?.trim()
        return if (!configured.isNullOrEmpty()) listOf(configured) else emptyList()
    }

    /**
     * Same payment arriving as both an app notification and a bank SMS:
     * identical (timestamp, amount, last4, merchant) within 60 seconds.
     */
    private suspend fun isFuzzyDuplicate(tx: Transaction): Boolean {
        val last4 = LedgerDedupe.last4(tx) ?: return false
        val from = tx.timestamp.minusMillis(LedgerDedupe.WINDOW_MS)
        val to = tx.timestamp.plusMillis(LedgerDedupe.WINDOW_MS)
        return dao.findNearDuplicate(tx.amount.amount, last4, tx.merchant, from, to) != null
    }
}

private fun Double.toMoney(): Money =
    Money(BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP))

private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount.amount,
    type = type.name,
    merchant = merchant,
    category = category,
    bank = bank,
    accountLast4 = accountLast4,
    cardLast4 = cardLast4,
    upiId = upiId,
    referenceNumber = referenceNumber,
    balance = balance?.amount,
    paymentMode = paymentMode.name,
    timestamp = timestamp,
    sender = sender,
    rawSms = rawSms,
    narration = narration,
    notes = notes,
    dedupeHash = dedupeHash,
    manuallyEdited = manuallyEdited,
    tagsJson = TransactionExtras.tagsToJson(HashtagParser.merge(notes, tags)),
    isSplit = isSplit,
    splitJson = TransactionExtras.splitsToJson(splitShares),
)

private fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = Money(amount),
    type = TransactionType.valueOf(type),
    merchant = merchant,
    category = category,
    bank = bank,
    accountLast4 = accountLast4,
    cardLast4 = cardLast4,
    upiId = upiId,
    referenceNumber = referenceNumber,
    balance = balance?.let { Money(it) },
    paymentMode = PaymentMode.valueOf(paymentMode),
    timestamp = timestamp,
    sender = sender,
    rawSms = rawSms,
    narration = narration,
    notes = notes,
    dedupeHash = dedupeHash,
    manuallyEdited = manuallyEdited,
    tags = TransactionExtras.tagsFromJson(tagsJson),
    isSplit = isSplit,
    splitShares = TransactionExtras.splitsFromJson(splitJson),
)
