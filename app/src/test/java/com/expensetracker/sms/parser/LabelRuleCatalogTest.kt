package com.expensetracker.sms.parser

import com.expensetracker.data.db.dao.LabelRuleDao
import com.expensetracker.data.db.entity.LabelRuleEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class LabelRuleCatalogTest {

    @Test
    fun `AND criteria require every selected field to match`() = runTest {
        val dao = FakeLabelRuleDao()
        val catalog = LabelRuleCatalog(dao)
        catalog.create(
            label = "Office lunch",
            senderContains = "HDFCBK",
            bodyContains = "SWIGGY",
            merchantContains = null,
        )

        assertThat(catalog.match("VM-HDFCBK", "INR 200 debited at SWIGGY", "Swiggy"))
            .isEqualTo("Office lunch")
        assertThat(catalog.match("VM-ICICIB", "INR 200 debited at SWIGGY", "Swiggy"))
            .isNull()
        assertThat(catalog.match("VM-HDFCBK", "INR 200 debited at ZOMATO", "Zomato"))
            .isNull()
    }

    @Test
    fun `more specific rule wins over broader body-only rule`() = runTest {
        val catalog = LabelRuleCatalog(FakeLabelRuleDao())
        catalog.create(
            label = "Food",
            senderContains = null,
            bodyContains = "SWIGGY",
            merchantContains = null,
        )
        catalog.create(
            label = "Team lunch",
            senderContains = "HDFCBK",
            bodyContains = "SWIGGY",
            merchantContains = "SWIGGY",
        )

        assertThat(catalog.match("VM-HDFCBK", "paid to SWIGGY", "Swiggy"))
            .isEqualTo("Team lunch")
    }

    @Test
    fun `suggestSenderContains strips VM prefix`() {
        assertThat(LabelRuleCatalog.suggestSenderContains("VM-HDFCBK")).isEqualTo("HDFCBK")
        assertThat(LabelRuleCatalog.suggestSenderContains("AD-ICICIB")).isEqualTo("ICICIB")
    }

    @Test
    fun `parser prefers label rule over merchant dictionary`() {
        val rules = LabelRuleMatcher { sender, body, _ ->
            if (sender?.contains("HDFC", ignoreCase = true) == true &&
                body.contains("AMAZON", ignoreCase = true)
            ) {
                "Gadgets"
            } else {
                null
            }
        }
        val parser = SmsParser(labelRules = rules)
        val tx = parser.parse(
            RawSms(
                sender = "VM-HDFCBK",
                body = SampleSms.HDFC_DEBIT,
                timestamp = Instant.parse("2024-01-12T10:15:00Z"),
            ),
        )!!
        assertThat(tx.category).isEqualTo("Gadgets")
        assertThat(tx.merchant).isEqualTo("Amazon")
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
}
