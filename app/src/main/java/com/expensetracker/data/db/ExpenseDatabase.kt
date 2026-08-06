package com.expensetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.expensetracker.data.db.converter.Converters
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.dao.CategoryDao
import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.dao.MerchantDao
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.BudgetEntity
import com.expensetracker.data.db.entity.CategoryEntity
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.db.entity.MerchantEntity
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.db.entity.TransactionEntity
import com.expensetracker.data.db.entity.BankEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantEntity::class,
        LabelRuleEntity::class,
        BankEntity::class,
        BudgetEntity::class,
        SettingsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun labelRuleDao(): LabelRuleDao
    abstract fun budgetDao(): BudgetDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "expense.db"
    }
}
