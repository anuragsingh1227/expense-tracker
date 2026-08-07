package com.expensetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.expensetracker.data.db.entity.BudgetEntity
import com.expensetracker.data.db.entity.CategoryEntity
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.db.entity.MerchantEntity
import com.expensetracker.data.db.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>
}

@Dao
interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MerchantEntity>)

    @Query("SELECT * FROM merchants")
    suspend fun getAll(): List<MerchantEntity>
}

@Dao
interface LabelRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: LabelRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<LabelRuleEntity>)

    @Query("SELECT * FROM label_rules ORDER BY id DESC")
    suspend fun getAll(): List<LabelRuleEntity>

    @Query("SELECT * FROM label_rules ORDER BY id DESC")
    fun observeAll(): Flow<List<LabelRuleEntity>>

    @Query("DELETE FROM label_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM label_rules")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity): Long

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingsEntity)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    /**
     * Writes every entity in a single DB transaction so a process death
     * mid-write can never leave e.g. a new PIN salt paired with the old hash
     * (which would otherwise lock the user out permanently).
     */
    @Transaction
    suspend fun putAll(entities: List<SettingsEntity>) {
        entities.forEach { put(it) }
    }
}
