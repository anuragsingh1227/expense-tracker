package com.expensetracker.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.expensetracker.ExpenseApp
import com.expensetracker.R
import com.expensetracker.data.AppSettings
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.domain.model.Transaction
import com.expensetracker.domain.model.TransactionType
import com.expensetracker.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionNotifier @Inject constructor(
    private val settingsDao: SettingsDao,
) {

    suspend fun notify(context: Context, tx: Transaction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val amountsHidden = settingsDao.get(AppSettings.AMOUNTS_HIDDEN) == "true"
        val isDebit = tx.type == TransactionType.DEBIT
        val title = if (amountsHidden) {
            context.getString(
                if (isDebit) R.string.notify_debited_hidden else R.string.notify_credited_hidden,
            )
        } else {
            context.getString(
                if (isDebit) R.string.notify_debited else R.string.notify_credited,
                tx.amount.formatInr(),
            )
        }
        val subject = tx.merchant ?: tx.bank ?: context.getString(R.string.notify_fallback_subject)
        val text = "$subject • ${tx.category}"

        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            tx.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, ExpenseApp.CHANNEL_TRANSACTIONS)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(context).notify(tx.dedupeHash.hashCode(), builder.build())
    }
}
