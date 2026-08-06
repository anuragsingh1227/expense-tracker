package com.expensetracker.ui

import android.Manifest
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequiredPermissionsTest {

    @Test
    fun `store flavor never requests SMS permissions`() {
        assertThat(RequiredPermissions.names(sdkInt = Build.VERSION_CODES.S, autoSms = false))
            .asList()
            .isEmpty()
        assertThat(
            RequiredPermissions.names(sdkInt = Build.VERSION_CODES.TIRAMISU, autoSms = false),
        ).asList().containsExactly(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(RequiredPermissions.requiresOnboarding(autoSms = false)).isFalse()
    }

    @Test
    fun `sms flavor pre-Tiramisu requires SMS receive and read only`() {
        assertThat(RequiredPermissions.names(sdkInt = Build.VERSION_CODES.S, autoSms = true))
            .asList()
            .containsExactly(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
            )
            .inOrder()
    }

    @Test
    fun `sms flavor Tiramisu and above also requires post notifications`() {
        assertThat(RequiredPermissions.names(sdkInt = Build.VERSION_CODES.TIRAMISU, autoSms = true))
            .asList()
            .containsExactly(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            .inOrder()
    }
}
