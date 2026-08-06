package com.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.sms.parser.RawSms
import com.expensetracker.sms.parser.SmsParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var parser: SmsParser
    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var notifier: TransactionNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Merge multi-part SMS by sender.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
        val pendingResult = goAsync()

        scope.launch {
            try {
                bySender.forEach { (sender, parts) ->
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val timestampMs = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val raw = RawSms(sender.takeIf { it.isNotBlank() }, body, Instant.ofEpochMilli(timestampMs))
                    if (!parser.isTransactional(body)) return@forEach
                    val tx = parser.parse(raw) ?: return@forEach
                    val inserted = repository.insertIfNew(tx)
                    if (inserted) notifier.notify(context, tx)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
