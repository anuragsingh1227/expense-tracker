package com.expensetracker.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.expensetracker.domain.model.CardStatement
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-only credit-card due reminders: 3 days before, and on the due date,
 * at 09:00 in the device zone. Uses [AlarmManager] — no network, no remote push.
 */
@Singleton
class CardDueReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(statement: CardStatement, zone: ZoneId = ZoneId.systemDefault(), nowEpochMs: Long = System.currentTimeMillis()) {
        if (statement.id <= 0L) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val due = statement.dueDate
        scheduleAt(am, statement, KIND_THREE_DAYS, due.minusDays(3), zone, nowEpochMs)
        scheduleAt(am, statement, KIND_DUE_DAY, due, zone, nowEpochMs)
    }

    fun scheduleAll(statements: Iterable<CardStatement>, zone: ZoneId = ZoneId.systemDefault()) {
        val now = System.currentTimeMillis()
        statements.forEach { schedule(it, zone, now) }
    }

    fun cancel(statementId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(statementId, KIND_THREE_DAYS))
        am.cancel(pending(statementId, KIND_DUE_DAY))
    }

    private fun scheduleAt(
        am: AlarmManager,
        statement: CardStatement,
        kind: Int,
        date: LocalDate,
        zone: ZoneId,
        nowEpochMs: Long,
    ) {
        val trigger = date.atTime(REMIND_AT).atZone(zone).toInstant().toEpochMilli()
        if (trigger <= nowEpochMs) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending(statement.id, kind))
    }

    private fun pending(statementId: Long, kind: Int): PendingIntent {
        val intent = Intent(context, CardDueAlarmReceiver::class.java).apply {
            action = ACTION_CARD_DUE
            putExtra(EXTRA_STATEMENT_ID, statementId)
            putExtra(EXTRA_KIND, kind)
        }
        val requestCode = requestCode(statementId, kind)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_CARD_DUE = "com.expensetracker.action.CARD_DUE_REMINDER"
        const val EXTRA_STATEMENT_ID = "statement_id"
        const val EXTRA_KIND = "kind"
        const val KIND_THREE_DAYS = 1
        const val KIND_DUE_DAY = 2
        val REMIND_AT: LocalTime = LocalTime.of(9, 0)

        fun requestCode(statementId: Long, kind: Int): Int {
            val mixed = statementId * 10 + kind
            return mixed.toInt()
        }
    }
}
