package com.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val icon: String? = null,
    val color: Long? = null,
    val isSystem: Boolean = true,
)

@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey val key: String,
    val displayName: String,
    val category: String,
)

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey val senderCode: String,
    val name: String,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val monthlyLimit: BigDecimal,
    val startsAt: Instant,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)
