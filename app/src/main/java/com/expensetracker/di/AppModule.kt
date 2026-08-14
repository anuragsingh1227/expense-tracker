package com.expensetracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expensetracker.data.OwnerNameProvider
import com.expensetracker.data.db.ExpenseDatabase
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.dao.CardStatementDao
import com.expensetracker.data.db.dao.CategoryDao
import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.dao.MerchantDao
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.repository.CardStatementRepository
import com.expensetracker.data.repository.CardStatementRepositoryImpl
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.data.repository.TransactionRepositoryImpl
import com.expensetracker.sms.AndroidSmsInboxSource
import com.expensetracker.sms.CardStatementImporter
import com.expensetracker.sms.CardStatementIngestor
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

    /**
     * v1 → v2: ensure tables introduced after the first release exist.
     * Idempotent CREATE IF NOT EXISTS — never drops user data.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `label_rules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `label` TEXT NOT NULL,
                    `senderContains` TEXT,
                    `bodyContains` TEXT,
                    `merchantContains` TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `budgets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `category` TEXT NOT NULL,
                    `monthlyLimit` TEXT NOT NULL,
                    `startsAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `banks` (
                    `senderCode` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    PRIMARY KEY(`senderCode`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `categories` (
                    `name` TEXT NOT NULL,
                    `icon` TEXT,
                    `color` INTEGER,
                    `isSystem` INTEGER NOT NULL,
                    PRIMARY KEY(`name`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `merchants` (
                    `key` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `settings` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent(),
            )
        }
    }

    /** Version bump only — keeps existing rows; catalogs are seeded on app start. */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    /**
     * v3 → v4: tags/split columns on transactions + card statement table.
     * Additive only — v1.1.8 JSON restores still apply (new fields default empty).
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tagsJson` TEXT")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isSplit` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `splitJson` TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_statements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `bank` TEXT,
                    `cardLast4` TEXT,
                    `totalDue` TEXT,
                    `minDue` TEXT,
                    `dueDateEpochDay` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `sender` TEXT,
                    `rawSms` TEXT,
                    `dedupeHash` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_card_statements_dedupeHash` ON `card_statements` (`dedupeHash`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_card_statements_dueDateEpochDay` ON `card_statements` (`dueDateEpochDay`)",
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ExpenseDatabase =
        Room.databaseBuilder(ctx, ExpenseDatabase::class.java, ExpenseDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides fun provideTransactionDao(db: ExpenseDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: ExpenseDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideMerchantDao(db: ExpenseDatabase): MerchantDao = db.merchantDao()
    @Provides fun provideLabelRuleDao(db: ExpenseDatabase): LabelRuleDao = db.labelRuleDao()
    @Provides fun provideBudgetDao(db: ExpenseDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideBankDao(db: ExpenseDatabase): com.expensetracker.data.db.dao.BankDao = db.bankDao()
    @Provides fun provideSettingsDao(db: ExpenseDatabase): SettingsDao = db.settingsDao()
    @Provides fun provideCardStatementDao(db: ExpenseDatabase): CardStatementDao = db.cardStatementDao()

    @Provides @Singleton fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideSmsParser(
        merchants: MerchantCatalog,
        labelRules: LabelRuleCatalog,
        clock: Clock,
        ownerNameProvider: OwnerNameProvider,
    ): SmsParser = SmsParser(
        merchants = merchants,
        labelRules = labelRules,
        zone = clock.zone,
        ownerNames = { ownerNameProvider.names() },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCardStatementRepository(impl: CardStatementRepositoryImpl): CardStatementRepository

    @Binds
    @Singleton
    abstract fun bindCardStatementIngestor(impl: CardStatementImporter): CardStatementIngestor

    @Binds
    @Singleton
    abstract fun bindSmsMessageSource(impl: AndroidSmsInboxSource): SmsMessageSource
}
