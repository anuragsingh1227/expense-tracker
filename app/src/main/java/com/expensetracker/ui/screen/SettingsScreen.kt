package com.expensetracker.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.expensetracker.AppFeatures
import com.expensetracker.R
import com.expensetracker.data.backup.BackupRepository
import com.expensetracker.sms.SmsScanResult
import com.expensetracker.ui.BackupUiState
import com.expensetracker.ui.RequiredPermissions
import com.expensetracker.ui.components.ScreenHeader
import com.expensetracker.ui.components.StatusPill
import com.expensetracker.ui.components.SurfaceCard
import java.time.LocalDate

@Composable
fun SettingsScreen(
    onRescanInbox: () -> Unit,
    scanning: Boolean,
    lastScan: SmsScanResult?,
    backupState: BackupUiState,
    onExportBackup: (java.io.OutputStream) -> Unit,
    onImportBackup: (java.io.InputStream) -> Unit,
    onPurgeSpam: () -> Unit = {},
    cleanupRemoved: Int? = null,
    onImportSmsText: (String) -> Unit = {},
    onPermissionsChanged: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val autoSms = AppFeatures.autoSms
    var smsGranted by remember { mutableStateOf(RequiredPermissions.allGranted(ctx)) }
    var pasteBody by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        smsGranted = RequiredPermissions.allGranted(ctx)
        if (smsGranted) onPermissionsChanged()
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        ctx.contentResolver.openOutputStream(uri)?.use(onExportBackup)
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ctx.contentResolver.openInputStream(uri)?.use(onImportBackup)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        ScreenHeader(
            title = stringResource(R.string.tab_settings),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        SurfaceCard {
            Text(stringResource(R.string.settings_paste_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_paste_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pasteBody,
                onValueChange = { pasteBody = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                label = { Text(stringResource(R.string.settings_paste_hint)) },
                shape = RoundedCornerShape(14.dp),
                minLines = 4,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onImportSmsText(pasteBody)
                    pasteBody = ""
                },
                enabled = pasteBody.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.settings_paste_action)) }
        }

        if (autoSms) {
            SurfaceCard {
                Text(stringResource(R.string.settings_sms_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.permission_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (smsGranted) {
                    StatusPill(text = stringResource(R.string.permission_granted), positive = true)
                } else {
                    Button(
                        onClick = { permissionLauncher.launch(RequiredPermissions.names()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(stringResource(R.string.grant_permission)) }
                }
            }

            SurfaceCard {
                Text(stringResource(R.string.settings_import_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.rescan_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRescanInbox,
                    enabled = smsGranted && !scanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        if (scanning) stringResource(R.string.rescanning) else stringResource(R.string.rescan_inbox),
                    )
                }
                lastScan?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    if (result.permissionDenied) {
                        Text(
                            stringResource(R.string.rescan_permission_denied),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.rescan_result,
                                result.examined,
                                result.inserted,
                                result.skipped,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.settings_cleanup_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_cleanup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPurgeSpam,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text(stringResource(R.string.cleanup_spam)) }
            cleanupRemoved?.let { count ->
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.cleanup_result, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val name = "${BackupRepository.FILE_PREFIX}-${LocalDate.now()}.json"
                        createBackup.launch(name)
                    },
                    enabled = backupState !is BackupUiState.Working,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.backup_export)) }
                OutlinedButton(
                    onClick = { openBackup.launch(arrayOf(BackupRepository.MIME_TYPE, "text/*", "*/*")) },
                    enabled = backupState !is BackupUiState.Working,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(stringResource(R.string.backup_import)) }
            }
            when (val state = backupState) {
                is BackupUiState.Working -> {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall)
                }
                is BackupUiState.Exported -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.backup_exported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                is BackupUiState.Imported -> {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(
                            R.string.backup_imported,
                            state.result.transactionsInserted,
                            state.result.transactionsSkipped,
                            state.result.merchantsRestored,
                            state.result.labelRulesRestored,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                is BackupUiState.Error -> {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                BackupUiState.Idle -> Unit
            }
        }

        SurfaceCard {
            Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
