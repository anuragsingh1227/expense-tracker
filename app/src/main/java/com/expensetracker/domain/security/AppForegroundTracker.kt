package com.expensetracker.domain.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "the app just went to background" signal.
 *
 * Backed by [androidx.lifecycle.ProcessLifecycleOwner] (registered once in
 * `ExpenseApp`) rather than the hosting Activity's lifecycle: a single
 * Activity's `onStop` can fire when a system dialog (e.g. a biometric
 * prompt or permission dialog) is shown on top of it, which would
 * incorrectly re-lock the app mid-unlock. The process lifecycle only
 * reports background when the whole app leaves the foreground.
 */
object AppForegroundTracker {
    private val _backgroundedAtMillis = MutableStateFlow(0L)
    val backgroundedAtMillis: StateFlow<Long> = _backgroundedAtMillis.asStateFlow()

    fun markBackgrounded(atMillis: Long) {
        _backgroundedAtMillis.value = atMillis
    }
}
