package com.expensetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dedupeHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
        WHERE type = :type AND timestamp >= :from AND timestamp < :to
        """,
    )
    fun observeTotal(type: String, from: Instant, to: Instant): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
        WHERE type = 'DEBIT'
          AND timestamp >= :from AND timestamp < :to
          AND category NOT IN ('Transfer', 'Investment')
        """,
    )
    fun observeSpendTotal(from: Instant, to: Instant): Flow<Double>

    @Query(
        """
        SELECT category AS category, SUM(CAST(amount AS REAL)) AS total FROM transactions
        WHERE type = 'DEBIT'
          AND timestamp >= :from AND timestamp < :to
          AND category NOT IN ('Transfer', 'Investment')
        GROUP BY category ORDER BY total DESC LIMIT :limit
        """,
    )
    fun observeCategoryTotals(from: Instant, to: Instant, limit: Int = 6): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (:query IS NULL OR
               merchant LIKE '%' || :query || '%' OR
               category LIKE '%' || :query || '%' OR
               notes LIKE '%' || :query || '%' OR
               rawSms LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        """,
    )
    fun search(query: String?): Flow<List<TransactionEntity>>

    @Query("SELECT id, rawSms FROM transactions")
    suspend fun getIdAndRawSms(): List<IdRawSms>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

data class CategoryTotal(val category: String, val total: Double)

data class IdRawSms(val id: Long, val rawSms: String?)
