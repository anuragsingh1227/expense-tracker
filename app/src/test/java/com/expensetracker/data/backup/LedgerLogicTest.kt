package com.expensetracker.data.backup

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
import com.expensetracker.data.repository.TransactionRepositoryImpl
import com.expensetracker.domain.insights.LedgerDedupe
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.PaymentMode
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
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

/**
 * Deduplication window + JSON backup schema validation (does not wipe SQLite
 * when the payload is malformed).
 */
class LedgerLogicTest {

    private val now = Instant.parse("2026-08-14T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `app notification and SMS with same timestamp amount last4 merchant within 60s is one entry`() = runTest {
        val dao = RecordingTransactionDao()
        val repo = TransactionRepositoryImpl(dao, FakeSettingsDao())
        val fromNotification = tx(
            amount = "450.00",
            last4 = "1234",
            merchant = "Swiggy",
            timestamp = now,
            hash = "notif-hash",
        )
        val fromSms = tx(
            amount = "450.00",
            last4 = "1234",
            merchant = "Swiggy",
            timestamp = now.plusSeconds(45),
            hash = "sms-hash-different",
        )

        assertThat(LedgerDedupe.isDuplicate(fromNotification, fromSms)).isTrue()
        assertThat(repo.insertIfNew(fromNotification)).isTrue()
        assertThat(repo.insertIfNew(fromSms)).isFalse()
        assertThat(dao.rows).hasSize(1)
        assertThat(dao.rows.single().dedupeHash).isEqualTo("notif-hash")
    }

    @Test
    fun `same amount outside 60s window is not collapsed`() = runTest {
        val dao = RecordingTransactionDao()
        val repo = TransactionRepositoryImpl(dao, FakeSettingsDao())
        val first = tx("450.00", "1234", "Swiggy", now, "a")
        val later = tx("450.00", "1234", "Swiggy", now.plusSeconds(61), "b")
        assertThat(LedgerDedupe.isDuplicate(first, later)).isFalse()
        assertThat(repo.insertIfNew(first)).isTrue()
        assertThat(repo.insertIfNew(later)).isTrue()
        assertThat(dao.rows).hasSize(2)
    }

    @Test
    fun `different merchant in the 60s window is not collapsed`() = runTest {
        val dao = RecordingTransactionDao()
        val repo = TransactionRepositoryImpl(dao, FakeSettingsDao())
        assertThat(repo.insertIfNew(tx("450.00", "1234", "Swiggy", now, "a"))).isTrue()
        assertThat(repo.insertIfNew(tx("450.00", "1234", "Zomato", now.plusSeconds(10), "b"))).isTrue()
        assertThat(dao.rows).hasSize(2)
    }

    @Test
    fun `JSON export contains transactions label rules and budgets`() = runTest {
        val txDao = RecordingTransactionDao()
        txDao.insert(sampleEntity("hash-1"))
        val merchantDao = FakeMerchantDao()
        val labelDao = FakeLabelRuleDao()
        val budgetDao = FakeBudgetDao()
        val settingsDao = FakeSettingsDao()
        val catalog = MerchantCatalog(merchantDao)
        val labels = LabelRuleCatalog(labelDao)
        labels.create("SIP", "HDFCBK", "GROWW", null)
        budgetDao.upsert(
            BudgetEntity(category = Categories.FOOD, monthlyLimit = BigDecimal("5000.00"), startsAt = now),
        )
        val json = BackupRepository(
            txDao, catalog, labels, budgetDao, FakeCardStatementDao(), settingsDao, OwnerNameProvider(settingsDao), clock,
        ).exportJson()

        assertThat(json).contains("\"version\"")
        assertThat(json).contains("\"transactions\"")
        assertThat(json).contains("\"labelRules\"")
        assertThat(json).contains("\"budgets\"")
        assertThat(json).contains("hash-1")
        assertThat(json).contains("SIP")
        assertThat(json).contains("Food")
        BackupSchema.validate(json)
    }

    @Test
    fun `v1_1_8 backup without tags still imports and does not wipe existing rows`() = runTest {
        val existing = sampleEntity("keep-me")
        val txDao = RecordingTransactionDao()
        txDao.insert(existing)
        val settingsDao = FakeSettingsDao()
        val json = """
            {
              "version": 3,
              "app": "com.expensetracker",
              "transactions": [
                {
                  "amount": "99.00",
                  "type": "DEBIT",
                  "merchant": "Zepto",
                  "category": "Groceries",
                  "paymentMode": "UPI",
                  "timestamp": ${now.toEpochMilli()},
                  "dedupeHash": "imported-zepto"
                }
              ],
              "labelRules": [],
              "budgets": [],
              "merchants": []
            }
        """.trimIndent()

        val result = BackupRepository(
            txDao,
            MerchantCatalog(FakeMerchantDao()),
            LabelRuleCatalog(FakeLabelRuleDao()),
            FakeBudgetDao(),
            FakeCardStatementDao(),
            settingsDao,
            OwnerNameProvider(settingsDao),
            clock,
        ).importJson(json)

        assertThat(result.transactionsInserted).isEqualTo(1)
        assertThat(txDao.rows.map { it.dedupeHash }).containsExactly("keep-me", "imported-zepto")
    }

    @Test
    fun `malformed JSON throws schema error and leaves existing rows`() = runTest {
        val txDao = RecordingTransactionDao()
        txDao.insert(sampleEntity("keep-me"))
        val settingsDao = FakeSettingsDao()
        val repo = BackupRepository(
            txDao,
            MerchantCatalog(FakeMerchantDao()),
            LabelRuleCatalog(FakeLabelRuleDao()),
            FakeBudgetDao(),
            FakeCardStatementDao(),
            settingsDao,
            OwnerNameProvider(settingsDao),
            clock,
        )

        val thrown = runCatching { repo.importJson("{not-json") }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BackupSchemaException::class.java)
        assertThat(thrown!!.message).contains("Malformed")
        assertThat(txDao.rows).hasSize(1)
        assertThat(txDao.rows.single().dedupeHash).isEqualTo("keep-me")
    }

    @Test
    fun `corrupted transaction object throws schema error without wiping db`() = runTest {
        val txDao = RecordingTransactionDao()
        txDao.insert(sampleEntity("keep-me"))
        val settingsDao = FakeSettingsDao()
        val repo = BackupRepository(
            txDao,
            MerchantCatalog(FakeMerchantDao()),
            LabelRuleCatalog(FakeLabelRuleDao()),
            FakeBudgetDao(),
            FakeCardStatementDao(),
            settingsDao,
            OwnerNameProvider(settingsDao),
            clock,
        )
        val payload = """
            {"version": 3, "transactions": [{"amount": "10.00", "type": "DEBIT"}]}
        """.trimIndent()

        val thrown = runCatching { repo.importJson(payload) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BackupSchemaException::class.java)
        assertThat(thrown!!.message).contains("missing required field")
        assertThat(txDao.rows).hasSize(1)
        assertThat(txDao.deleteAllCalled).isFalse()
    }

    @Test
    fun `unsupported backup version throws schema error`() {
        val thrown = runCatching {
            BackupSchema.validate("""{"version": 99, "transactions": []}""")
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BackupSchemaException::class.java)
    }

    @Test
    fun `transactions field that is not an array is rejected`() {
        val thrown = runCatching {
            BackupSchema.validate("""{"version": 3, "transactions": "oops"}""")
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BackupSchemaException::class.java)
    }

    @Test
    fun `card statements survive JSON backup restore and do not wipe transactions`() = runTest {
        val txDao = RecordingTransactionDao()
        txDao.insert(sampleEntity("keep-me"))
        val cards = FakeCardStatementDao()
        cards.insert(
            CardStatementEntity(
                bank = "HDFC",
                cardLast4 = "4321",
                totalDue = BigDecimal("12500.00"),
                minDue = BigDecimal("625.00"),
                dueDateEpochDay = java.time.LocalDate.of(2026, 8, 20).toEpochDay(),
                timestamp = now,
                sender = "VM-HDFCBK",
                rawSms = "statement",
                dedupeHash = "card-hash-1",
            ),
        )
        val settingsDao = FakeSettingsDao()
        val json = BackupRepository(
            txDao,
            MerchantCatalog(FakeMerchantDao()),
            LabelRuleCatalog(FakeLabelRuleDao()),
            FakeBudgetDao(),
            cards,
            settingsDao,
            OwnerNameProvider(settingsDao),
            clock,
        ).exportJson()
        assertThat(json).contains("cardStatements")
        assertThat(json).contains("card-hash-1")
        BackupSchema.validate(json)

        val destCards = FakeCardStatementDao()
        val destTx = RecordingTransactionDao()
        destTx.insert(sampleEntity("keep-me"))
        val result = BackupRepository(
            destTx,
            MerchantCatalog(FakeMerchantDao()),
            LabelRuleCatalog(FakeLabelRuleDao()),
            FakeBudgetDao(),
            destCards,
            FakeSettingsDao(),
            OwnerNameProvider(FakeSettingsDao()),
            clock,
        ).importJson(json)

        assertThat(result.cardStatementsRestored).isEqualTo(1)
        assertThat(destCards.rows.single().dedupeHash).isEqualTo("card-hash-1")
        assertThat(destCards.rows.single().cardLast4).isEqualTo("4321")
        assertThat(destTx.rows.map { it.dedupeHash }).contains("keep-me")
    }

    private fun tx(
        amount: String,
        last4: String,
        merchant: String,
        timestamp: Instant,
        hash: String,
    ) = Transaction(
        amount = Money.ofRupees(amount),
        type = TransactionType.DEBIT,
        merchant = merchant,
        category = Categories.FOOD,
        bank = "HDFC",
        accountLast4 = last4,
        cardLast4 = null,
        upiId = null,
        referenceNumber = null,
        balance = null,
        paymentMode = PaymentMode.UPI,
        timestamp = timestamp,
        sender = "VM-HDFCBK",
        rawSms = "sms",
        narration = null,
        notes = null,
        dedupeHash = hash,
    )

    private fun sampleEntity(hash: String) = TransactionEntity(
        amount = BigDecimal("10.00"),
        type = "DEBIT",
        merchant = "Amazon",
        category = Categories.SHOPPING,
        bank = "HDFC",
        accountLast4 = "1234",
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
        dedupeHash = hash,
    )

    private class RecordingTransactionDao : TransactionDao {
        val rows = mutableListOf<TransactionEntity>()
        var deleteAllCalled = false
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
            amount: BigDecimal,
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
            deleteAllCalled = true
            rows.removeAll { it.id in ids }
        }
    }

    private class FakeMerchantDao : MerchantDao {
        private val rows = mutableMapOf<String, MerchantEntity>()
        override suspend fun insertAll(items: List<MerchantEntity>) {
            items.forEach { rows[it.key] = it }
        }
        override suspend fun getAll(): List<MerchantEntity> = rows.values.toList()
        override suspend fun deleteAll() = rows.clear()
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
        override suspend fun deleteAll() = rows.clear()
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
        override suspend fun deleteAll() = rows.clear()
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
        override suspend fun getAll(): List<SettingsEntity> = map.map { SettingsEntity(it.key, it.value) }
        override fun observe(key: String): Flow<String?> = flowOf(map[key])
        override suspend fun delete(key: String) {
            map.remove(key)
        }
    }
}
