package com.expensetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.expensetracker.data.CatalogSeeder
import com.expensetracker.data.OwnerNameProvider
import com.expensetracker.data.repository.CardStatementRepository
import com.expensetracker.domain.security.AppForegroundTracker
import com.expensetracker.sms.CardDueReminderScheduler
import com.expensetracker.sms.parser.LabelRuleCatalog
import com.expensetracker.sms.parser.MerchantCatalog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ExpenseApp : Application() {

    @Inject lateinit var merchantCatalog: MerchantCatalog
    @Inject lateinit var labelRuleCatalog: LabelRuleCatalog
    @Inject lateinit var ownerNameProvider: OwnerNameProvider
    @Inject lateinit var catalogSeeder: CatalogSeeder
    @Inject lateinit var cardStatements: CardStatementRepository
    @Inject lateinit var cardDueReminderScheduler: CardDueReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerAppLockLifecycleObserver()
        appScope.launch {
            catalogSeeder.seedIfEmpty()
            merchantCatalog.refresh()
            labelRuleCatalog.refresh()
            ownerNameProvider.refresh()
            cardDueReminderScheduler.scheduleAll(cardStatements.getAll())
        }
    }

    /** Marks the app backgrounded so [com.expensetracker.ui.AppViewModel] can re-lock it. */
    private fun registerAppLockLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    AppForegroundTracker.markBackgrounded(System.currentTimeMillis())
                }
            },
        )
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_TRANSACTIONS,
            getString(R.string.channel_transactions_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.channel_transactions_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
        val dues = NotificationChannel(
            CHANNEL_CARD_DUES,
            getString(R.string.channel_card_dues_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.channel_card_dues_desc)
            setShowBadge(true)
        }
        nm.createNotificationChannel(dues)
    }

    companion object {
        const val CHANNEL_TRANSACTIONS = "transactions"
        const val CHANNEL_CARD_DUES = "card_dues"
    }
}
