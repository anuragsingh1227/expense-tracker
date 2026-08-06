package com.expensetracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.expensetracker.sms.parser.RawSms
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class InboxReadResult {
    data object Unavailable : InboxReadResult()
    data class Ok(val messages: List<RawSms>) : InboxReadResult()
}

/** Abstraction over the device SMS inbox so scanning is unit-testable. */
interface SmsMessageSource {
    fun readSince(sinceMillis: Long): InboxReadResult
}

@Singleton
class AndroidSmsInboxSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsMessageSource {

    override fun readSince(sinceMillis: Long): InboxReadResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return InboxReadResult.Unavailable
        }

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        // Prefer the full SMS provider filtered to inbox — more reliable across OEMs
        // than Inbox.CONTENT_URI alone.
        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.TYPE} = ?"
        val args = arrayOf(
            sinceMillis.toString(),
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
        )
        val sort = "${Telephony.Sms.DATE} ASC"

        val out = mutableListOf<RawSms>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        ) ?: return InboxReadResult.Ok(emptyList())

        cursor.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val address = it.getString(addressIdx)
                val body = it.getString(bodyIdx).orEmpty()
                val date = it.getLong(dateIdx)
                out += RawSms(
                    sender = address?.takeIf { s -> s.isNotBlank() },
                    body = body,
                    timestamp = Instant.ofEpochMilli(date),
                )
            }
        }
        return InboxReadResult.Ok(out)
    }
}
