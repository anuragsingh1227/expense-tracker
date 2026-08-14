package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.expensetracker.data.repository.CardStatementRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-registers local card-due alarms after reboot. Store flavor has no SMS
 * [BootReceiver], so this lives in the main source set.
 */
@AndroidEntryPoint
class ReminderBootReceiver : BroadcastReceiver() {

    @Inject lateinit var statements: CardStatementRepository
    @Inject lateinit var scheduler: CardDueReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        scope.launch {
            try {
                scheduler.scheduleAll(statements.getAll())
            } finally {
                pending.finish()
            }
        }
    }
}
