package com.expensetracker.data

import com.expensetracker.data.db.dao.SettingsDao
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cached account-holder name(s) used by [com.expensetracker.sms.parser.SmsParser]
 * for parse-time Transfer detection and by self-transfer reconcile.
 *
 * Call [refresh] after reading/writing [AppSettings.OWNER_NAME].
 */
@Singleton
class OwnerNameProvider @Inject constructor(
    private val settingsDao: SettingsDao,
) {
    private val cached = AtomicReference<List<String>>(emptyList())

    fun names(): List<String> = cached.get()

    suspend fun refresh() {
        val name = settingsDao.get(AppSettings.OWNER_NAME)?.trim().orEmpty()
        cached.set(if (name.length >= 2) listOf(name) else emptyList())
    }
}
