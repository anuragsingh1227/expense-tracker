package com.expensetracker

/**
 * Compile-time feature flags from product flavors.
 * [AUTO_SMS] is false for store builds (Play-safe: no READ/RECEIVE SMS).
 */
object AppFeatures {
    val autoSms: Boolean = BuildConfig.FEATURE_AUTO_SMS
}
