package com.expensetracker.data

object AppSettings {
    const val LAST_SMS_SCAN_MILLIS = "last_sms_scan_millis"
    const val INITIAL_BACKFILL_DONE = "initial_backfill_done"
    /** Account holder's first name — used to spot self-transfers between own accounts. */
    const val OWNER_NAME = "owner_name"
}
