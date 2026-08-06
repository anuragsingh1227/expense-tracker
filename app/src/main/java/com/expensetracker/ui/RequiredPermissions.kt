package com.expensetracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.expensetracker.AppFeatures

/**
 * Runtime permissions required for the active distribution flavor.
 * Store builds never request SMS (Play policy); SMS flavor requests inbox access.
 */
object RequiredPermissions {

    fun names(
        sdkInt: Int = Build.VERSION.SDK_INT,
        autoSms: Boolean = AppFeatures.autoSms,
    ): Array<String> = buildList {
        if (autoSms) {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun missing(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        autoSms: Boolean = AppFeatures.autoSms,
    ): List<String> =
        names(sdkInt, autoSms).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun allGranted(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT,
        autoSms: Boolean = AppFeatures.autoSms,
    ): Boolean = missing(context, sdkInt, autoSms).isEmpty()

    /** Store builds can open the app without any dangerous permission. */
    fun requiresOnboarding(autoSms: Boolean = AppFeatures.autoSms): Boolean = autoSms
}
