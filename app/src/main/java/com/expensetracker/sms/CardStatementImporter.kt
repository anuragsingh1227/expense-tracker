package com.expensetracker.sms

import com.expensetracker.data.repository.CardStatementRepository
import com.expensetracker.domain.model.CardStatement
import com.expensetracker.sms.parser.CreditCardStatementParser
import com.expensetracker.sms.parser.RawSms
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

fun interface CardStatementIngestor {
    suspend fun ingest(sms: RawSms): CardStatement?
}

/**
 * Parses credit-card statement SMS, persists them, and schedules local due reminders.
 */
@Singleton
class CardStatementImporter @Inject constructor(
    private val repository: CardStatementRepository,
    private val scheduler: CardDueReminderScheduler,
    private val clock: Clock,
) : CardStatementIngestor {
    override suspend fun ingest(sms: RawSms): CardStatement? {
        val parsed = CreditCardStatementParser.parse(sms, clock.zone) ?: return null
        val stored = repository.insertIfNew(parsed) ?: return null
        scheduler.schedule(stored, clock.zone, clock.millis())
        return stored
    }
}
