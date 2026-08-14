package com.expensetracker.data.repository

import com.expensetracker.data.db.dao.CardStatementDao
import com.expensetracker.data.db.entity.CardStatementEntity
import com.expensetracker.domain.model.CardStatement
import com.expensetracker.domain.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface CardStatementRepository {
    /** Returns true if a new statement row was inserted. */
    suspend fun insertIfNew(statement: CardStatement): CardStatement?
    fun observeAll(): Flow<List<CardStatement>>
    suspend fun getAll(): List<CardStatement>
    suspend fun find(id: Long): CardStatement?
    suspend fun delete(id: Long)
}

@Singleton
class CardStatementRepositoryImpl @Inject constructor(
    private val dao: CardStatementDao,
) : CardStatementRepository {

    override suspend fun insertIfNew(statement: CardStatement): CardStatement? {
        val id = dao.insert(statement.toEntity())
        if (id == -1L) return dao.findByHash(statement.dedupeHash)?.toDomain()
        return dao.findById(id)?.toDomain() ?: statement.copy(id = id)
    }

    override fun observeAll(): Flow<List<CardStatement>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<CardStatement> = dao.getAll().map { it.toDomain() }

    override suspend fun find(id: Long): CardStatement? = dao.findById(id)?.toDomain()

    override suspend fun delete(id: Long) = dao.deleteById(id)
}

internal fun CardStatement.toEntity(): CardStatementEntity = CardStatementEntity(
    id = id,
    bank = bank,
    cardLast4 = cardLast4,
    totalDue = totalDue?.amount,
    minDue = minDue?.amount,
    dueDateEpochDay = dueDate.toEpochDay(),
    timestamp = timestamp,
    sender = sender,
    rawSms = rawSms,
    dedupeHash = dedupeHash,
)

internal fun CardStatementEntity.toDomain(): CardStatement = CardStatement(
    id = id,
    bank = bank,
    cardLast4 = cardLast4,
    totalDue = totalDue?.let { Money(it) },
    minDue = minDue?.let { Money(it) },
    dueDate = LocalDate.ofEpochDay(dueDateEpochDay),
    timestamp = timestamp,
    sender = sender,
    rawSms = rawSms,
    dedupeHash = dedupeHash,
)
