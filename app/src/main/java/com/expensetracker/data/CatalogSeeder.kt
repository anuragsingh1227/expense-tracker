package com.expensetracker.data

import com.expensetracker.data.db.dao.BankDao
import com.expensetracker.data.db.dao.CategoryDao
import com.expensetracker.data.db.entity.BankEntity
import com.expensetracker.data.db.entity.CategoryEntity
import com.expensetracker.sms.parser.BankSenders
import com.expensetracker.sms.parser.Categories
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds the unused-but-present categories/banks tables from known catalogs. */
@Singleton
class CatalogSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val bankDao: BankDao,
) {
    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(
                Categories.defaults.map { CategoryEntity(name = it, isSystem = true) },
            )
        }
        if (bankDao.count() == 0) {
            bankDao.insertAll(
                BankSenders.allBankNames().map { name ->
                    BankEntity(senderCode = name.uppercase().replace(" ", "_"), name = name)
                },
            )
        }
    }
}
