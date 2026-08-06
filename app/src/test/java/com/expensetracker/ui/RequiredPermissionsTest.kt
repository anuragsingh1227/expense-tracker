package com.expensetracker.ui

import android.Manifest
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequiredPermissionsTest {

    @Test
    fun `pre-Tiramisu requires SMS receive and read only`() {
        assertThat(RequiredPermissions.names(sdkInt = Build.VERSION_CODES.S))
            .asList()
            .containsExactly(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
            )
            .inOrder()
    }

    @Test
    fun `Tiramisu and above also requires post notifications`() {
        assertThat(RequiredPermissions.names(sdkInt = Build.VERSION_CODES.TIRAMISU))
            .asList()
            .containsExactly(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.POST_NOTIFICATIONS,
            )
            .inOrder()
    }
}
