package com.expensetracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.expensetracker.R
import com.expensetracker.ui.components.StatusPill
import com.expensetracker.ui.components.SurfaceCard

private enum class AppLockFormMode { NONE, SETUP, CHANGE, DISABLE }

private fun String.isValidPin() = length in 4..8 && all(Char::isDigit)

/**
 * "App lock" section for [SettingsScreen] — enable/disable PIN lock, change PIN,
 * and (when hardware + enrollment allow) toggle biometric unlock.
 */
@Composable
fun AppLockSettingsCard(
    enabled: Boolean,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    onEnableWithPin: (String) -> Unit,
    onChangePin: (current: String, new: String, onResult: (Boolean) -> Unit) -> Unit,
    onDisable: (current: String, onResult: (Boolean) -> Unit) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
) {
    var mode by remember(enabled) { mutableStateOf(AppLockFormMode.NONE) }
    var pinA by remember(mode) { mutableStateOf("") }
    var pinB by remember(mode) { mutableStateOf("") }
    var pinCurrent by remember(mode) { mutableStateOf("") }
    var errorMessage by remember(mode) { mutableStateOf<String?>(null) }

    // Resolve error copy up front — stringResource is only callable from composable
    // scope, not from inside the button click handlers below.
    val errPinTooShort = stringResource(R.string.settings_applock_pin_too_short)
    val errPinMismatch = stringResource(R.string.settings_applock_pin_mismatch)
    val errPinIncorrect = stringResource(R.string.settings_applock_pin_incorrect)

    fun reset() {
        mode = AppLockFormMode.NONE
    }

    SurfaceCard {
        Text(stringResource(R.string.settings_applock_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_applock_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        when (mode) {
            AppLockFormMode.NONE -> {
                StatusPill(
                    text = stringResource(
                        if (enabled) R.string.settings_applock_status_on else R.string.settings_applock_status_off,
                    ),
                    positive = enabled,
                )
                Spacer(Modifier.height(12.dp))
                if (enabled) {
                    if (biometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.settings_applock_biometric_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = biometricEnabled, onCheckedChange = onSetBiometricEnabled)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedButton(
                        onClick = { mode = AppLockFormMode.CHANGE },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.settings_applock_change_pin)) }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { mode = AppLockFormMode.DISABLE },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.settings_applock_disable)) }
                } else {
                    Button(
                        onClick = { mode = AppLockFormMode.SETUP },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.settings_applock_enable)) }
                }
            }

            AppLockFormMode.SETUP -> {
                PinField(
                    value = pinA,
                    onValueChange = { pinA = it },
                    label = stringResource(R.string.settings_applock_pin_new),
                )
                Spacer(Modifier.height(10.dp))
                PinField(
                    value = pinB,
                    onValueChange = { pinB = it },
                    label = stringResource(R.string.settings_applock_pin_confirm),
                )
                ErrorText(errorMessage)
                Spacer(Modifier.height(12.dp))
                FormActions(
                    confirmEnabled = pinA.isNotEmpty() && pinB.isNotEmpty(),
                    onCancel = ::reset,
                    onConfirm = {
                        errorMessage = when {
                            !pinA.isValidPin() -> errPinTooShort
                            pinA != pinB -> errPinMismatch
                            else -> null
                        }
                        if (errorMessage == null) {
                            onEnableWithPin(pinA)
                            reset()
                        }
                    },
                )
            }

            AppLockFormMode.CHANGE -> {
                PinField(
                    value = pinCurrent,
                    onValueChange = { pinCurrent = it },
                    label = stringResource(R.string.settings_applock_pin_current),
                )
                Spacer(Modifier.height(10.dp))
                PinField(
                    value = pinA,
                    onValueChange = { pinA = it },
                    label = stringResource(R.string.settings_applock_pin_new),
                )
                Spacer(Modifier.height(10.dp))
                PinField(
                    value = pinB,
                    onValueChange = { pinB = it },
                    label = stringResource(R.string.settings_applock_pin_confirm),
                )
                ErrorText(errorMessage)
                Spacer(Modifier.height(12.dp))
                FormActions(
                    confirmEnabled = pinCurrent.isNotEmpty() && pinA.isNotEmpty() && pinB.isNotEmpty(),
                    onCancel = ::reset,
                    onConfirm = {
                        val validationError = when {
                            !pinA.isValidPin() -> errPinTooShort
                            pinA != pinB -> errPinMismatch
                            else -> null
                        }
                        if (validationError != null) {
                            errorMessage = validationError
                        } else {
                            onChangePin(pinCurrent, pinA) { ok ->
                                if (ok) reset() else errorMessage = errPinIncorrect
                            }
                        }
                    },
                )
            }

            AppLockFormMode.DISABLE -> {
                PinField(
                    value = pinCurrent,
                    onValueChange = { pinCurrent = it },
                    label = stringResource(R.string.settings_applock_pin_current),
                )
                ErrorText(errorMessage)
                Spacer(Modifier.height(12.dp))
                FormActions(
                    confirmEnabled = pinCurrent.isNotEmpty(),
                    confirmLabel = stringResource(R.string.settings_applock_disable),
                    onCancel = ::reset,
                    onConfirm = {
                        onDisable(pinCurrent) { ok ->
                            if (ok) reset() else errorMessage = errPinIncorrect
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun ErrorText(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(6.dp))
    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun FormActions(
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = stringResource(R.string.settings_applock_confirm),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(stringResource(R.string.settings_applock_cancel)) }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(confirmLabel) }
    }
}
