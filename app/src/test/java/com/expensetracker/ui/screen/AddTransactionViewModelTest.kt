package com.expensetracker.ui.screen

import com.expensetracker.data.repository.CategorySpend
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.insights.CategoryMonthSpend
import com.expensetracker.domain.model.Money
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.sms.parser.Categories
import com.expensetracker.sms.parser.SmsParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-08-12T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid double save inserts only one row`() = runTest {
        val repo = RecordingRepo()
        val vm = AddTransactionViewModel(repo, clock)
        var done = 0

        repeat(3) {
            vm.save(
                amountText = "250",
                type = TransactionType.DEBIT,
                merchant = "Cash tea",
                category = Categories.FOOD,
                date = LocalDate.of(2026, 8, 12),
                notes = "",
                onDone = { done++ },
            )
        }
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(repo.insertCount).isEqualTo(1)
        assertThat(repo.stored).hasSize(1)
        assertThat(repo.stored.single().manuallyEdited).isTrue()
        assertThat(repo.stored.single().rawSms).isNull()
        assertThat(done).isEqualTo(1)
        assertThat(vm.isSaveInFlight()).isTrue()
    }

    private class RecordingRepo : TransactionRepository {
        val stored = mutableListOf<Transaction>()
        var insertCount = 0

        override suspend fun insertIfNew(tx: Transaction): Boolean = error("unused")
        override suspend fun insertManual(tx: Transaction): Long {
            insertCount++
            val id = stored.size + 1L
            stored += tx.copy(id = id)
            return id
        }

        override suspend fun update(tx: Transaction) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteIds(ids: Collection<Long>) = Unit
        override suspend fun find(id: Long): Transaction? = stored.find { it.id == id }
        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(stored)
        override fun observeAll(): Flow<List<Transaction>> = flowOf(stored)
        override fun observeBetween(from: Instant, to: Instant, limit: Int) = flowOf(stored)
        override fun observeTotal(type: TransactionType, from: Instant, to: Instant) = flowOf(Money.ZERO)
        override fun observeIncomeTotal(from: Instant, to: Instant) = flowOf(Money.ZERO)
        override fun observeSpendTotal(from: Instant, to: Instant) = flowOf(Money.ZERO)
        override fun observeInvestmentTotal(from: Instant, to: Instant) = flowOf(Money.ZERO)
        override fun observeCategorySpend(from: Instant, to: Instant, limit: Int) =
            flowOf(emptyList<CategorySpend>())
        override fun observeCategoryMonthSpend(from: Instant, to: Instant) =
            flowOf(emptyList<CategoryMonthSpend>())
        override fun search(query: String?): Flow<List<Transaction>> = flowOf(stored)
        override fun searchBetween(query: String?, from: Instant, to: Instant) = flowOf(stored)
        override suspend fun purgeNonTransactional(parser: SmsParser): Int = 0
        override suspend fun reconcileSelfTransfers(ownerNames: List<String>?): Int = 0
    }
}
