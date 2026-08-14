package com.expensetracker.data

object AppSettings {
    const val LAST_SMS_SCAN_MILLIS = "last_sms_scan_millis"
    const val INITIAL_BACKFILL_DONE = "initial_backfill_done"
    /** Account holder's first name — used to spot self-transfers between own accounts. */
    const val OWNER_NAME = "owner_name"

    /** App-lock (PIN + optional biometric) settings. */
    const val APP_LOCK_ENABLED = "app_lock_enabled"
    const val APP_LOCK_PIN_HASH = "app_lock_pin_hash"
    const val APP_LOCK_PIN_SALT = "app_lock_pin_salt"
    const val APP_LOCK_BIOMETRIC_ENABLED = "app_lock_biometric_enabled"

    /** Whether rupee amounts should be masked behind dots (app-wide). */
    const val AMOUNTS_HIDDEN = "amounts_hidden"

    /** Epoch millis until which PIN entry is locked after too many failures. */
    const val APP_LOCK_LOCKOUT_UNTIL = "app_lock_lockout_until"

    /** Consecutive PIN failures. Persisted so force-stop cannot reset the lockout counter. */
    const val APP_LOCK_PIN_FAILURES = "app_lock_pin_failures"

    /**
     * Credit-card billing cycle start day of month (1–28). 1 = calendar month.
     * Example: 15 → 15th of this month through 14th of next.
     */
    const val CC_BILLING_CYCLE_START_DAY = "cc_billing_cycle_start_day"
}
