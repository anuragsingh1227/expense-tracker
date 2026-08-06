package com.expensetracker.data.repository

import com.expensetracker.data.db.dao.CategoryMonthTotal
import com.expensetracker.data.db.dao.CategoryTotal
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.TransactionEntity
import com.expensetracker.domain.insights.CategoryMonthSpend
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class CategorySpend(val category: String, val amount: Money)

interface TransactionRepository {
    /** Returns true if a new row was inserted; false if it was a duplicate. */
    suspend fun insertIfNew(tx: Transaction): Boolean
    suspend fun update(tx: Transaction)
    suspend fun delete(id: Long)
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
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override suspend fun insertIfNew(tx: Transaction): Boolean {
        val id = dao.insert(tx.toEntity())
        return id != -1L
    }

    override suspend fun update(tx: Transaction) = dao.update(tx.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

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
        dao.observeCategoryTotals(from, to, limit).map { rows ->
            rows.map { CategorySpend(it.category, it.total.toMoney()) }
        }

    override fun observeCategoryMonthSpend(from: Instant, to: Instant): Flow<List<CategoryMonthSpend>> =
        dao.observeCategoryMonthTotals(from, to).map { rows ->
            rows.map {
                CategoryMonthSpend(
                    category = it.category,
                    monthKey = it.monthKey,
                    amount = it.total.toMoney(),
                )
            }
        }

    override fun search(query: String?): Flow<List<Transaction>> =
        dao.search(query?.takeIf { it.isNotBlank() }).map { list -> list.map { it.toDomain() } }

    override fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<Transaction>> =
        dao.searchBetween(query?.takeIf { it.isNotBlank() }, from, to)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun purgeNonTransactional(parser: SmsParser): Int {
        val spamIds = dao.getIdAndRawSms()
            .filter { row -> row.rawSms.isNullOrBlank() || !parser.isTransactional(row.rawSms) }
            .map { it.id }
        if (spamIds.isEmpty()) return 0
        spamIds.chunked(200).forEach { dao.deleteByIds(it) }
        return spamIds.size
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
)
