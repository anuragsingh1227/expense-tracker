package com.expensetracker.sms

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.expensetracker.ExpenseApp
import com.expensetracker.R
import com.expensetracker.data.repository.CardStatementRepository
import com.expensetracker.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CardDueAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var statements: CardStatementRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CardDueReminderScheduler.ACTION_CARD_DUE) return

        val id = intent.getLongExtra(CardDueReminderScheduler.EXTRA_STATEMENT_ID, -1L)
        val kind = intent.getIntExtra(CardDueReminderScheduler.EXTRA_KIND, 0)
        if (id <= 0L) return
        val pending = goAsync()
        scope.launch {
            try {
                val statement = statements.find(id) ?: return@launch
                notify(context, statement.bank, statement.cardLast4, statement.dueDate.toString(), kind, id)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(
        context: Context,
        bank: String?,
        last4: String?,
        dueDate: String,
        kind: Int,
        statementId: Long,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val card = last4?.let { "••••$it" } ?: context.getString(R.string.card_due_unknown_card)
        val issuer = bank ?: context.getString(R.string.card_due_unknown_bank)
        val title = if (kind == CardDueReminderScheduler.KIND_DUE_DAY) {
            context.getString(R.string.card_due_today_title)
        } else {
            context.getString(R.string.card_due_soon_title)
        }
        val text = context.getString(R.string.card_due_body, issuer, card, dueDate)

        val launch = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(
            context,
            statementId.toInt(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, ExpenseApp.CHANNEL_CARD_DUES)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(context).notify(
            CardDueReminderScheduler.requestCode(statementId, kind),
            builder.build(),
        )
    }
}
