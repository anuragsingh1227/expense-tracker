package com.expensetracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        appScope.launch {
            merchantCatalog.refresh()
            labelRuleCatalog.refresh()
        }
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
    }

    companion object {
        const val CHANNEL_TRANSACTIONS = "transactions"
    }
}
