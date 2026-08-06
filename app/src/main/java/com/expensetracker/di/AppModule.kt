package com.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.expensetracker.data.db.ExpenseDatabase
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.dao.CategoryDao
import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.dao.MerchantDao
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.data.repository.TransactionRepositoryImpl
import com.expensetracker.sms.AndroidSmsInboxSource
import com.expensetracker.sms.SmsMessageSource
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.sms.parser.MerchantCatalog
import com.expensetracker.sms.parser.SmsParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ExpenseDatabase =
        Room.databaseBuilder(ctx, ExpenseDatabase::class.java, ExpenseDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTransactionDao(db: ExpenseDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: ExpenseDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideMerchantDao(db: ExpenseDatabase): MerchantDao = db.merchantDao()
    @Provides fun provideLabelRuleDao(db: ExpenseDatabase): LabelRuleDao = db.labelRuleDao()
    @Provides fun provideBudgetDao(db: ExpenseDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideSettingsDao(db: ExpenseDatabase): SettingsDao = db.settingsDao()

    @Provides @Singleton fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideSmsParser(
        merchants: MerchantCatalog,
        labelRules: LabelRuleCatalog,
    ): SmsParser = SmsParser(merchants, labelRules)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSmsMessageSource(impl: AndroidSmsInboxSource): SmsMessageSource
}
