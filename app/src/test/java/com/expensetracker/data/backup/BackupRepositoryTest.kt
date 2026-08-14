package com.expensetracker.data.backup

import com.expensetracker.data.AppSettings
import com.expensetracker.data.OwnerNameProvider
import com.expensetracker.data.db.dao.BudgetDao
import com.expensetracker.data.db.dao.CardStatementDao
import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.dao.MerchantDao
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.dao.TransactionDao
import com.expensetracker.data.db.entity.BudgetEntity
import com.expensetracker.data.db.entity.CardStatementEntity
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.expensetracker.data.db.entity.MerchantEntity
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.db.entity.TransactionEntity
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.sms.parser.MerchantCatalog
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupRepositoryTest {

    private val now = Instant.parse("2024-08-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `export then import restores merchants labels settings and skips duplicate transactions`() = runTest {
        val txDao = FakeTransactionDao()
        val merchantDao = FakeMerchantDao()
        val labelDao = FakeLabelRuleDao()
        val budgetDao = FakeBudgetDao()
        val settingsDao = FakeSettingsDao()
        settingsDao.put(SettingsEntity(AppSettings.OWNER_NAME, "Priya"))
        settingsDao.put(SettingsEntity(AppSettings.AMOUNTS_HIDDEN, "true"))
        val ownerNames = OwnerNameProvider(settingsDao)
        ownerNames.refresh()

        val catalog = MerchantCatalog(merchantDao)
        val labels = LabelRuleCatalog(labelDao)
        catalog.remember("Groww", Categories.INVESTMENT)
        labels.create(
            label = "SIP",
            senderContains = "HDFCBK",
            bodyContains = "GROWW",
            merchantContains = null,
        )
        budgetDao.upsert(
            BudgetEntity(
                category = Categories.FOOD,
                monthlyLimit = BigDecimal("5000.00"),
                startsAt = now,
            ),
        )

        txDao.insert(
            TransactionEntity(
                amount = BigDecimal("5000.00"),
                type = "DEBIT",
                merchant = "Groww",
                category = Categories.INVESTMENT,
                bank = "HDFC",
                accountLast4 = null,
                cardLast4 = null,
                upiId = null,
                referenceNumber = "1",
                balance = null,
                paymentMode = "UPI",
                timestamp = now,
                sender = "VM-HDFCBK",
                rawSms = "x",
                narration = null,
                notes = null,
                dedupeHash = "hash-groww-1",
            ),
        )

        val repo = BackupRepository(txDao, catalog, labels, budgetDao, FakeCardStatementDao(), settingsDao, ownerNames, clock)
        val json = repo.exportJson()
        assertThat(json).contains("Priya")
        assertThat(json).contains("Food")

        val csv = repo.exportCsv()
        assertThat(csv).contains("Groww")
        assertThat(csv.lines().first()).contains("amount")

        val txDao2 = FakeTransactionDao()
        txDao2.insert(
            TransactionEntity(
                amount = BigDecimal("5000.00"),
                type = "DEBIT",
                merchant = "Groww",
                category = Categories.INVESTMENT,
                bank = "HDFC",
                accountLast4 = null,
                cardLast4 = null,
                upiId = null,
                referenceNumber = "1",
                balance = null,
                paymentMode = "UPI",
                timestamp = now,
                sender = "VM-HDFCBK",
                rawSms = "x",
                narration = null,
                notes = null,
                dedupeHash = "hash-groww-1",
            ),
        )
        val settingsDao2 = FakeSettingsDao()
        val ownerNames2 = OwnerNameProvider(settingsDao2)
        val catalog2 = MerchantCatalog(FakeMerchantDao())
        val labels2 = LabelRuleCatalog(FakeLabelRuleDao())
        val budgetDao2 = FakeBudgetDao()
        val result = BackupRepository(
            txDao2, catalog2, labels2, budgetDao2, FakeCardStatementDao(), settingsDao2, ownerNames2, clock,
        ).importJson(json)

        assertThat(result.transactionsInserted).isEqualTo(0)
        assertThat(result.transactionsSkipped).isEqualTo(1)
        assertThat(result.merchantsRestored).isEqualTo(1)
        assertThat(result.labelRulesRestored).isEqualTo(1)
        assertThat(result.budgetsRestored).isEqualTo(1)
        assertThat(result.settingsRestored).isAtLeast(1)
        assertThat(catalog2.match("paid to GROWW")?.category).isEqualTo(Categories.INVESTMENT)
        assertThat(labels2.match("VM-HDFCBK", "paid GROWW SIP", null)).isEqualTo("SIP")
        assertThat(settingsDao2.get(AppSettings.OWNER_NAME)).isEqualTo("Priya")
        assertThat(ownerNames2.names()).containsExactly("Priya")
    }

    private class FakeTransactionDao : TransactionDao {
        private val rows = mutableListOf<TransactionEntity>()
        private var seq = 1L

        override suspend fun insert(entity: TransactionEntity): Long {
            if (rows.any { it.dedupeHash == entity.dedupeHash }) return -1L
            val id = seq++
            rows += entity.copy(id = id)
            return id
        }

        override suspend fun update(entity: TransactionEntity) = Unit
        override suspend fun deleteById(id: Long) = Unit
        override suspend fun findById(id: Long): TransactionEntity? = rows.find { it.id == id }
        override suspend fun findByHash(hash: String): TransactionEntity? = rows.find { it.dedupeHash == hash }
        override suspend fun findNearDuplicate(
            amount: java.math.BigDecimal,
            last4: String,
            merchant: String?,
            fromInclusive: Instant,
            toInclusive: Instant,
        ): TransactionEntity? = rows.find { row ->
            row.amount.compareTo(amount) == 0 &&
                !row.timestamp.isBefore(fromInclusive) &&
                !row.timestamp.isAfter(toInclusive) &&
                (row.accountLast4 == last4 || row.cardLast4 == last4) &&
                row.merchant?.lowercase() == merchant?.lowercase()
        }
        override fun observeRecent(limit: Int) = flowOf(rows.take(limit))
        override fun observeAll() = flowOf(rows)
        override fun observeBetween(from: Instant, to: Instant, limit: Int) = flowOf(
            rows.filter { !it.timestamp.isBefore(from) && it.timestamp.isBefore(to) }.take(limit),
        )
        override suspend fun getAllOnce(): List<TransactionEntity> = rows.toList()
        override fun observeTotal(type: String, from: Instant, to: Instant) = flowOf(0.0)
        override fun observeIncomeTotal(from: Instant, to: Instant) = flowOf(0.0)
        override fun observeSpendTotal(from: Instant, to: Instant) = flowOf(0.0)
        override fun observeInvestmentTotal(from: Instant, to: Instant) = flowOf(0.0)
        override fun observeCategoryTotals(from: Instant, to: Instant, limit: Int) =
            flowOf(emptyList<com.expensetracker.data.db.dao.CategoryTotal>())
        override fun observeCategoryMonthTotals(from: Instant, to: Instant) =
            flowOf(emptyList<com.expensetracker.data.db.dao.CategoryMonthTotal>())
        override fun search(query: String?): Flow<List<TransactionEntity>> = flowOf(rows)
        override fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<TransactionEntity>> =
            flowOf(rows.filter { !it.timestamp.isBefore(from) && it.timestamp.isBefore(to) })
        override suspend fun getIdAndRawSms() = rows.map {
            com.expensetracker.data.db.dao.IdRawSms(it.id, it.rawSms, it.manuallyEdited)
        }
        override suspend fun deleteByIds(ids: List<Long>) {
            rows.removeAll { it.id in ids }
        }
    }

    private class FakeMerchantDao : MerchantDao {
        private val rows = mutableMapOf<String, MerchantEntity>()
        override suspend fun insertAll(items: List<MerchantEntity>) {
            items.forEach { rows[it.key] = it }
        }

        override suspend fun getAll(): List<MerchantEntity> = rows.values.toList()
        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    private class FakeLabelRuleDao : LabelRuleDao {
        private val rows = mutableListOf<LabelRuleEntity>()
        private var seq = 1L

        override suspend fun insert(rule: LabelRuleEntity): Long {
            val id = if (rule.id == 0L) seq++ else rule.id
            rows.removeAll { it.id == id }
            rows += rule.copy(id = id)
            return id
        }

        override suspend fun insertAll(rules: List<LabelRuleEntity>) {
            rules.forEach { insert(it) }
        }

        override suspend fun getAll(): List<LabelRuleEntity> = rows.toList()
        override fun observeAll(): Flow<List<LabelRuleEntity>> = flowOf(rows.toList())
        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    private class FakeBudgetDao : BudgetDao {
        private val rows = mutableListOf<BudgetEntity>()
        private var seq = 1L

        override suspend fun upsert(entity: BudgetEntity): Long {
            val id = if (entity.id == 0L) seq++ else entity.id
            rows.removeAll { it.id == id || it.category == entity.category }
            rows += entity.copy(id = id)
            return id
        }

        override fun observeAll(): Flow<List<BudgetEntity>> = flowOf(rows.toList())
        override suspend fun getAll(): List<BudgetEntity> = rows.toList()
        override suspend fun delete(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }

    private class FakeCardStatementDao : CardStatementDao {
        val rows = mutableListOf<CardStatementEntity>()
        private var seq = 1L

        override suspend fun insert(entity: CardStatementEntity): Long {
            if (rows.any { it.dedupeHash == entity.dedupeHash }) return -1L
            val id = seq++
            rows += entity.copy(id = id)
            return id
        }

        override fun observeAll(): Flow<List<CardStatementEntity>> = flowOf(rows.toList())
        override suspend fun getAll(): List<CardStatementEntity> = rows.toList()
        override suspend fun findById(id: Long): CardStatementEntity? = rows.find { it.id == id }
        override suspend fun findByHash(hash: String): CardStatementEntity? =
            rows.find { it.dedupeHash == hash }
        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }
    }

    private class FakeSettingsDao : SettingsDao {
        private val map = mutableMapOf<String, String>()
        override suspend fun put(entity: SettingsEntity) {
            map[entity.key] = entity.value
        }

        override suspend fun get(key: String): String? = map[key]
        override suspend fun getAll(): List<SettingsEntity> =
            map.map { SettingsEntity(it.key, it.value) }

        override suspend fun delete(key: String) {
            map.remove(key)
        }

        override fun observe(key: String): Flow<String?> = flowOf(map[key])
    }
}

class BackupSettingsKeysTest {
    @Test
    fun `lockout fields are included in backup settings`() {
        assertThat(BackupRepository.SETTINGS_KEYS).contains(AppSettings.APP_LOCK_LOCKOUT_UNTIL)
        assertThat(BackupRepository.SETTINGS_KEYS).contains(AppSettings.APP_LOCK_PIN_FAILURES)
    }
}
