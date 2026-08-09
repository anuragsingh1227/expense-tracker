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

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :from AND timestamp < :to
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun observeBetween(from: Instant, to: Instant, limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :from AND timestamp < :to
          AND (:query IS NULL OR
               merchant LIKE '%' || :query || '%' OR
               category LIKE '%' || :query || '%' OR
               notes LIKE '%' || :query || '%' OR
               rawSms LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        """,
    )
    fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query(
        """
        SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
        WHERE type = :type AND timestamp >= :from AND timestamp < :to
        """,
    )
    fun observeTotal(type: String, from: Instant, to: Instant): Flow<Double>

    /**
     * Real inflows only — excludes Transfer credits (IMPS self-moves / card bill credits)
     * and Refund credits (those offset spend below, not treated as income) so net cash
     * is not inflated by money that already left another account or was returned.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CAST(amount AS REAL)), 0) FROM transactions
        WHERE type = 'CREDIT'
          AND timestamp >= :from AND timestamp < :to
          AND category NOT IN ('Transfer', 'Refund', 'Investment')
        """,
    )
    fun observeIncomeTotal(from: Instant, to: Instant): Flow<Double>

    /**
     * Debit spend minus Refund credits for the same window. Not floored — a
     * negative result means net refunds, which is real information.
     * Keep in sync with [com.expensetracker.domain.insights.LedgerBuckets.spend].
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN type = 'DEBIT' AND category NOT IN ('Transfer', 'Investment')
                    THEN CAST(amount AS REAL)
                WHEN type = 'CREDIT' AND category = 'Refund'
                    THEN -CAST(amount AS REAL)
                ELSE 0
            END
        ), 0) FROM transactions
        WHERE timestamp >= :from AND timestamp < :to
        """,
    )
    fun observeSpendTotal(from: Instant, to: Instant): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN type = 'DEBIT' AND category = 'Investment'
                    THEN CAST(amount AS REAL)
                WHEN type = 'CREDIT' AND category = 'Investment'
                    THEN -CAST(amount AS REAL)
                ELSE 0
            END
        ), 0) FROM transactions
        WHERE timestamp >= :from AND timestamp < :to
        """,
    )
    fun observeInvestmentTotal(from: Instant, to: Instant): Flow<Double>

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

    /**
     * Category spend grouped by local calendar month (`yyyy-MM`).
     * Aggregation stays in SQLite — no raw SMS leaves the device.
     */
    @Query(
        """
        SELECT category AS category,
               strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS monthKey,
               SUM(CAST(amount AS REAL)) AS total
        FROM transactions
        WHERE type = 'DEBIT'
          AND timestamp >= :from AND timestamp < :to
          AND category NOT IN ('Transfer', 'Investment')
        GROUP BY category, monthKey
        """,
    )
    fun observeCategoryMonthTotals(from: Instant, to: Instant): Flow<List<CategoryMonthTotal>>

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

    @Query("SELECT id, rawSms, manuallyEdited FROM transactions")
    suspend fun getIdAndRawSms(): List<IdRawSms>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

data class CategoryTotal(val category: String, val total: Double)

data class CategoryMonthTotal(
    val category: String,
    val monthKey: String,
    val total: Double,
)

data class IdRawSms(
    val id: Long,
    val rawSms: String?,
    val manuallyEdited: Boolean = false,
)
