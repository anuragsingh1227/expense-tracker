package com.expensetracker.sms.parser

import com.expensetracker.domain.insights.LedgerBuckets
import com.expensetracker.domain.model.Transaction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId

class InternetSmsCorpusAuditTest {

    private val parser = SmsParser(
        zone = ZoneId.of("Asia/Kolkata"),
        ownerNames = { listOf("Anurag", "Anur") },
    )
    private val receivedAt = Instant.parse("2026-08-09T12:00:00Z")

    @Test
    fun `audit internet sms corpus against parser and transaction gate`() {
        val results = InternetSmsCorpus.cases.map { case ->
            val tx = parser.parse(RawSms(case.sender, case.body, receivedAt))
            AuditResult(case = case, tx = tx, gaps = gapsFor(case, tx))
        }
        val failures = results.filter { it.gaps.isNotEmpty() }
        val pass = results.size - failures.size
        val ignoreCorrect = results.count { !it.case.expect.keep && it.tx == null }
        val keepCorrect = results.count { it.case.expect.keep && it.tx != null }
        val failureRate = if (results.isEmpty()) 0.0 else failures.size * 100.0 / results.size

        val summary = buildString {
            appendLine("# Internet SMS corpus audit")
            appendLine()
            appendLine("- Total: ${results.size}")
            appendLine("- Pass: $pass")
            appendLine("- Fail: ${failures.size}")
            appendLine("- Failure rate: ${"%.2f".format(failureRate)}%")
            appendLine("- Ignore-correct: $ignoreCorrect")
            appendLine("- Keep-correct: $keepCorrect")
        }
        val fullReport = buildString {
            append(summary)
            appendLine()
            if (failures.isEmpty()) {
                appendLine("No failures.")
            } else {
                appendLine("## Failures")
                appendLine()
                failures.forEach { appendFailure(it) }
            }
        }
        val failureReport = buildString {
            appendLine("# Internet SMS corpus failures")
            appendLine()
            appendLine("- Total: ${results.size}")
            appendLine("- Fail: ${failures.size}")
            appendLine("- Failure rate: ${"%.2f".format(failureRate)}%")
            appendLine()
            failures.forEach { appendFailure(it) }
        }

        writeReport("/opt/cursor/artifacts/internet-sms-corpus-audit.md", fullReport)
        writeReport("/opt/cursor/artifacts/internet-sms-corpus-failures.md", failureReport)
        writeReport("/workspace/app/build/reports/internet-sms-corpus-audit.md", fullReport)
        writeReport("/workspace/app/build/reports/internet-sms-corpus-failures.md", failureReport)

        println(
            "InternetSmsCorpusAudit total=${results.size} pass=$pass fail=${failures.size} " +
                "ignore-correct=$ignoreCorrect keep-correct=$keepCorrect " +
                "failure-rate=${"%.2f".format(failureRate)}%",
        )
        if (failures.isNotEmpty()) {
            println("Failure report: /workspace/app/build/reports/internet-sms-corpus-failures.md")
        }

        assertThat(InternetSmsCorpus.cases.size).isAtLeast(100)
        assertThat(fullReport).contains("Failure rate:")
    }

    private fun gapsFor(case: InternetSmsCorpus.Case, tx: Transaction?): List<String> {
        val expect = case.expect
        val gaps = mutableListOf<String>()
        val actualKeep = tx != null
        if (expect.keep != actualKeep) {
            gaps += if (expect.keep) {
                "Parser/gate rejected an expected ledger movement."
            } else {
                "Gate kept a non-ledger acknowledgement, status, OTP, or promo SMS."
            }
            return gaps
        }
        if (tx == null) return gaps

        expect.type?.let {
            if (tx.type != it) gaps += "type expected $it but was ${tx.type}"
        }
        expect.category?.let {
            if (tx.category != it) gaps += "category expected $it but was ${tx.category}"
        }
        expect.amount?.let {
            val actual = tx.amount.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            if (actual != it) gaps += "amount expected $it but was $actual"
        }
        expect.merchantContains?.let {
            val actual = tx.merchant
            if (actual == null || !actual.contains(it, ignoreCase = true)) {
                gaps += "merchant expected to contain '$it' but was ${actual ?: "null"}"
            }
        }
        expect.spend?.let {
            val actual = LedgerBuckets.isSpend(tx)
            if (actual != it) gaps += "spend expected $it but was $actual"
        }
        return gaps
    }

    private fun StringBuilder.appendFailure(result: AuditResult) {
        val case = result.case
        val expect = case.expect
        val tx = result.tx
        appendLine("### FAIL ${case.id}")
        appendLine("Sender: ${case.sender}")
        appendLine("SMS: ${case.body.replace("\n", "\\n")}")
        appendLine(
            "Expected: keep=${expect.keep} type=${expect.type} category=${expect.category} " +
                "amount=${expect.amount} merchant~=${expect.merchantContains} spend=${expect.spend}",
        )
        appendLine(
            "Actual: keep=${tx != null} type=${tx?.type} category=${tx?.category} " +
                "amount=${tx?.amount?.amount?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()} " +
                "merchant=${tx?.merchant} spend=${tx?.let(LedgerBuckets::isSpend)}",
        )
        appendLine("Gap: ${result.gaps.joinToString("; ")}")
        appendLine()
    }

    private fun writeReport(path: String, contents: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(contents)
    }

    private data class AuditResult(
        val case: InternetSmsCorpus.Case,
        val tx: Transaction?,
        val gaps: List<String>,
    )
}
