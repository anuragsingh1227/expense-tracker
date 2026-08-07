package com.expensetracker.sms

import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.RawSms
import com.expensetracker.sms.parser.SampleSms
import com.expensetracker.sms.parser.SmsParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class SmsInboxScannerTest {

    private val now = Instant.parse("2024-06-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `first scan uses 90-day lookback and imports transactional SMS`() = runTest {
        val older = RawSms("VM-HDFCBK", SampleSms.HDFC_DEBIT, now.minusSeconds(3600))
        val otp = RawSms("VM-HDFCBK", SampleSms.OTP_MESSAGE, now.minusSeconds(1800))
        val promo = RawSms("VM-PROMO", SampleSms.PROMOTIONAL, now.minusSeconds(900))
        val source = RecordingSource(listOf(older, otp, promo))
        val repo = FakeTransactionRepository()
        val settings = FakeSettingsDao()

        val result = SmsInboxScanner(source, SmsParser(), repo, settings, clock).scan()

        assertThat(result.examined).isEqualTo(3)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(2)
        assertThat(repo.stored).hasSize(1)
        assertThat(settings.get(AppSettings.INITIAL_BACKFILL_DONE)).isEqualTo("true")
        assertThat(settings.get(AppSettings.LAST_SMS_SCAN_MILLIS)).isEqualTo(now.toEpochMilli().toString())
        val expectedSince = now.toEpochMilli() - TimeUnit.DAYS.toMillis(90)
        assertThat(source.lastSince).isEqualTo(expectedSince)
    }

    @Test
    fun `incremental scan starts from watermark`() = runTest {
        val settings = FakeSettingsDao().apply {
            put(SettingsEntity(AppSettings.INITIAL_BACKFILL_DONE, "true"))
            put(SettingsEntity(AppSettings.LAST_SMS_SCAN_MILLIS, "1000"))
        }
        val source = RecordingSource(emptyList())

        SmsInboxScanner(source, SmsParser(), FakeTransactionRepository(), settings, clock)
            .scan(forceFullLookback = false)

        assertThat(source.lastSince).isEqualTo(1000L)
    }

    @Test
    fun `forceFullLookback ignores watermark`() = runTest {
        val settings = FakeSettingsDao().apply {
            put(SettingsEntity(AppSettings.INITIAL_BACKFILL_DONE, "true"))
            put(SettingsEntity(AppSettings.LAST_SMS_SCAN_MILLIS, "1000"))
        }
        val source = RecordingSource(emptyList())

        SmsInboxScanner(source, SmsParser(), FakeTransactionRepository(), settings, clock)
            .scan(forceFullLookback = true)

        val expectedSince = now.toEpochMilli() - TimeUnit.DAYS.toMillis(90)
        assertThat(source.lastSince).isEqualTo(expectedSince)
    }

    @Test
    fun `duplicate SMS across scans are not re-inserted`() = runTest {
        val raw = RawSms("VM-HDFCBK", SampleSms.HDFC_DEBIT, now)
        val repo = FakeTransactionRepository()
        val settings = FakeSettingsDao()
        val scanner = SmsInboxScanner(RecordingSource(listOf(raw)), SmsParser(), repo, settings, clock)

        val first = scanner.scan()
        settings.put(SettingsEntity(AppSettings.LAST_SMS_SCAN_MILLIS, "1"))
        val second = SmsInboxScanner(RecordingSource(listOf(raw)), SmsParser(), repo, settings, clock).scan()

        assertThat(first.inserted).isEqualTo(1)
        assertThat(second.inserted).isEqualTo(0)
        assertThat(second.skipped).isEqualTo(1)
        assertThat(repo.stored).hasSize(1)
    }

    @Test
    fun `permission denied does not mark backfill done or advance watermark`() = runTest {
        val settings = FakeSettingsDao()
        val result = SmsInboxScanner(
            UnavailableSource,
            SmsParser(),
            FakeTransactionRepository(),
            settings,
            clock,
        ).scan()

        assertThat(result.permissionDenied).isTrue()
        assertThat(result.examined).isEqualTo(0)
        assertThat(settings.get(AppSettings.INITIAL_BACKFILL_DONE)).isNull()
        assertThat(settings.get(AppSettings.LAST_SMS_SCAN_MILLIS)).isNull()
    }

    private object UnavailableSource : SmsMessageSource {
        override fun readSince(sinceMillis: Long): InboxReadResult = InboxReadResult.Unavailable
    }

    private class RecordingSource(private val messages: List<RawSms>) : SmsMessageSource {
        var lastSince: Long? = null
        override fun readSince(sinceMillis: Long): InboxReadResult {
            lastSince = sinceMillis
            return InboxReadResult.Ok(messages)
        }
    }

    private class FakeSettingsDao : SettingsDao {
        private val map = mutableMapOf<String, String>()
        override suspend fun put(entity: SettingsEntity) {
            map[entity.key] = entity.value
        }

        override suspend fun get(key: String): String? = map[key]
    }

    private class FakeTransactionRepository : TransactionRepository {
        val stored = mutableListOf<Transaction>()

        override suspend fun insertIfNew(tx: Transaction): Boolean {
            if (stored.any { it.dedupeHash == tx.dedupeHash }) return false
            stored += tx.copy(id = stored.size + 1L)
            return true
        }

        override suspend fun insertManual(tx: Transaction): Long {
            val id = stored.size + 1L
            stored += tx.copy(id = id)
            return id
        }

        override suspend fun update(tx: Transaction) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteIds(ids: Collection<Long>) {
            stored.removeAll { it.id in ids }
        }
        override suspend fun find(id: Long): Transaction? = stored.find { it.id == id }
        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(stored.take(limit))
        override fun observeAll(): Flow<List<Transaction>> = flowOf(stored)
        override fun observeBetween(from: Instant, to: Instant, limit: Int): Flow<List<Transaction>> =
            flowOf(stored.filter { !it.timestamp.isBefore(from) && it.timestamp.isBefore(to) }.take(limit))
        override fun observeTotal(type: TransactionType, from: Instant, to: Instant): Flow<Money> =
            flowOf(Money.ZERO)
        override fun observeIncomeTotal(from: Instant, to: Instant): Flow<Money> = flowOf(Money.ZERO)
        override fun observeSpendTotal(from: Instant, to: Instant): Flow<Money> = flowOf(Money.ZERO)
        override fun observeInvestmentTotal(from: Instant, to: Instant): Flow<Money> = flowOf(Money.ZERO)
        override fun observeCategorySpend(from: Instant, to: Instant, limit: Int) =
            flowOf(emptyList<com.expensetracker.data.repository.CategorySpend>())
        override fun observeCategoryMonthSpend(from: Instant, to: Instant) =
            flowOf(emptyList<com.expensetracker.domain.insights.CategoryMonthSpend>())
        override fun search(query: String?): Flow<List<Transaction>> = flowOf(stored)
        override fun searchBetween(query: String?, from: Instant, to: Instant): Flow<List<Transaction>> =
            flowOf(stored.filter { !it.timestamp.isBefore(from) && it.timestamp.isBefore(to) })
        override suspend fun purgeNonTransactional(parser: com.expensetracker.sms.parser.SmsParser): Int = 0
        override suspend fun reconcileSelfTransfers(ownerNames: List<String>?): Int = 0
    }
}
