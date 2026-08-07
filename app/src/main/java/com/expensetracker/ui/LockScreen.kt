package com.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.expensetracker.R
import com.expensetracker.domain.security.BiometricAuthenticator
import kotlinx.coroutines.delay

/**
 * Full-screen app-lock gate. Shown whenever [com.expensetracker.ui.AppViewModel]
 * reports the app is locked; blocks everything else in [AppRoot].
 */
@Composable
fun LockScreen(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    pinError: Boolean,
    lockoutUntilMillis: Long = 0L,
    onSubmitPin: (String) -> Unit,
    onPinChanged: () -> Unit,
    onBiometricUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val activity = LocalContext.current as? FragmentActivity
    var remainingLockoutMs by remember(lockoutUntilMillis) { mutableLongStateOf(0L) }

    LaunchedEffect(lockoutUntilMillis) {
        while (true) {
            val left = lockoutUntilMillis - System.currentTimeMillis()
            remainingLockoutMs = left.coerceAtLeast(0L)
            if (left <= 0L) break
            delay(250)
        }
    }

    val lockedOut = remainingLockoutMs > 0L
    val canUseBiometric = biometricEnabled && biometricAvailable && activity != null && !lockedOut

    fun tryBiometric() {
        if (lockedOut) return
        val host = activity ?: return
        BiometricAuthenticator.authenticate(
            activity = host,
            title = host.getString(R.string.lock_biometric_prompt_title),
            subtitle = host.getString(R.string.lock_biometric_prompt_subtitle),
            negativeButtonText = host.getString(R.string.lock_biometric_prompt_use_pin),
            onSuccess = onBiometricUnlocked,
            onError = { /* fall back to PIN silently */ },
        )
    }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) tryBiometric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (lockedOut) {
                val seconds = ((remainingLockoutMs + 999) / 1000).toInt().coerceAtLeast(1)
                stringResource(R.string.lock_lockout_wait, seconds)
            } else {
                stringResource(R.string.lock_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (lockedOut) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (!lockedOut && it.length <= 8 && it.all(Char::isDigit)) {
                    pin = it
                    onPinChanged()
                }
            },
            singleLine = true,
            enabled = !lockedOut,
            isError = pinError && !lockedOut,
            label = { Text(stringResource(R.string.lock_pin_hint)) },
            supportingText = if (pinError && !lockedOut) {
                { Text(stringResource(R.string.lock_pin_incorrect)) }
            } else {
                null
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(
                onDone = { if (!lockedOut && pin.isNotEmpty()) onSubmitPin(pin) },
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSubmitPin(pin) },
            enabled = !lockedOut && pin.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.lock_unlock_action))
        }
        if (biometricEnabled && biometricAvailable) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { tryBiometric() },
                enabled = !lockedOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lock_biometric_action))
            }
        }
    }
}
