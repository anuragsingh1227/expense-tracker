package com.expensetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.expensetracker.data.db.entity.CardStatementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardStatementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: CardStatementEntity): Long

    @Query("SELECT * FROM card_statements ORDER BY dueDateEpochDay ASC")
    fun observeAll(): Flow<List<CardStatementEntity>>

    @Query("SELECT * FROM card_statements ORDER BY dueDateEpochDay ASC")
    suspend fun getAll(): List<CardStatementEntity>

    @Query("SELECT * FROM card_statements WHERE id = :id")
    suspend fun findById(id: Long): CardStatementEntity?

    @Query("SELECT * FROM card_statements WHERE dedupeHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): CardStatementEntity?

    @Query("DELETE FROM card_statements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM card_statements")
    suspend fun deleteAll()
}
