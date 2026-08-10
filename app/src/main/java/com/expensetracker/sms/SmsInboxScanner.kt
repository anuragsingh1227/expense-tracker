package com.expensetracker.sms

import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.sms.parser.SmsParser
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class SmsScanResult(
    val examined: Int,
    val inserted: Int,
    val skipped: Int,
    /** True when READ_SMS was missing — watermark is left untouched. */
    val permissionDenied: Boolean = false,
)

/**
 * Reads historical / recent SMS from the inbox, parses transactional ones,
 * and inserts them with the same dedupe path as [SmsReceiver].
 *
 * First install (and force rescan) imports only the **current calendar month**.
 * Later runs are incremental from the last watermark so the on-device ledger
 * fills progressively as new months accumulate.
 */
@Singleton
class SmsInboxScanner @Inject constructor(
    private val source: SmsMessageSource,
    private val parser: SmsParser,
    private val repository: TransactionRepository,
    private val settingsDao: SettingsDao,
    private val clock: Clock,
) {

    suspend fun scan(forceFullLookback: Boolean = false): SmsScanResult {
        val nowMillis = clock.millis()
        val since = resolveSince(nowMillis, forceFullLookback)

        when (val read = source.readSince(since)) {
            is InboxReadResult.Unavailable -> {
                return SmsScanResult(
                    examined = 0,
                    inserted = 0,
                    skipped = 0,
                    permissionDenied = true,
                )
            }
            is InboxReadResult.Ok -> {
                var inserted = 0
                var skipped = 0
                for (raw in read.messages) {
                    if (!parser.isTransactional(raw.body)) {
                        skipped++
                        continue
                    }
                    val tx = parser.parse(raw)
                    if (tx == null) {
                        skipped++
                        continue
                    }
                    if (repository.insertIfNew(tx)) inserted++ else skipped++
                }

                // Drop debit↔credit pairs that are own-account moves (owner name in SMS).
                repository.reconcileSelfTransfers()

                settingsDao.put(SettingsEntity(AppSettings.LAST_SMS_SCAN_MILLIS, nowMillis.toString()))
                settingsDao.put(SettingsEntity(AppSettings.INITIAL_BACKFILL_DONE, "true"))
                return SmsScanResult(
                    examined = read.messages.size,
                    inserted = inserted,
                    skipped = skipped,
                )
            }
        }
    }

    private suspend fun resolveSince(nowMillis: Long, forceFullLookback: Boolean): Long {
        val monthStart = startOfCurrentMonthMillis(nowMillis)
        if (forceFullLookback) return monthStart
        val backfillDone = settingsDao.get(AppSettings.INITIAL_BACKFILL_DONE) == "true"
        if (!backfillDone) return monthStart
        val watermark = settingsDao.get(AppSettings.LAST_SMS_SCAN_MILLIS)?.toLongOrNull()
        return watermark ?: monthStart
    }

    /** Inclusive lower bound for first-install / force rescan: 00:00 local on the 1st. */
    internal fun startOfCurrentMonthMillis(nowMillis: Long = clock.millis()): Long {
        val zone = clock.zone
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return YearMonth.from(today).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
