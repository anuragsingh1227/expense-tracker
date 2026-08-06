package com.expensetracker.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * SMS (+ notifications on API 33+) required for auto-import.
 * [sdkInt] is injectable for unit tests.
 */
object RequiredPermissions {

    fun names(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    fun missing(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        names(sdkInt).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    fun allGranted(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        missing(context, sdkInt).isEmpty()
}
