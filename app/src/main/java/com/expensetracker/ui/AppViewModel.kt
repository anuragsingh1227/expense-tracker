package com.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.backup.BackupImportResult
import com.expensetracker.data.backup.BackupRepository
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.sms.SmsInboxScanner
import com.expensetracker.sms.SmsScanResult
import com.expensetracker.sms.parser.SmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Working : BackupUiState()
    data class Exported(val bytes: Int) : BackupUiState()
    data class Imported(val result: BackupImportResult) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val scanner: SmsInboxScanner,
    private val backupRepository: BackupRepository,
    private val transactionRepository: TransactionRepository,
    private val parser: SmsParser,
) : ViewModel() {

    private val _lastScan = MutableStateFlow<SmsScanResult?>(null)
    val lastScan: StateFlow<SmsScanResult?> = _lastScan.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _cleanupRemoved = MutableStateFlow<Int?>(null)
    val cleanupRemoved: StateFlow<Int?> = _cleanupRemoved.asStateFlow()

    fun runInboxScan(forceFullLookback: Boolean = false) {
        if (_scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                _lastScan.value = scanner.scan(forceFullLookback = forceFullLookback)
            } finally {
                _scanning.value = false
            }
        }
    }

    fun purgeSpam() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                transactionRepository.purgeNonTransactional(parser)
            }
            _cleanupRemoved.value = removed
        }
    }

    fun exportBackup(to: OutputStream) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            try {
                val json = withContext(Dispatchers.IO) { backupRepository.exportJson() }
                withContext(Dispatchers.IO) {
                    to.bufferedWriter().use { it.write(json) }
                }
                _backupState.value = BackupUiState.Exported(json.length)
            } catch (t: Throwable) {
                _backupState.value = BackupUiState.Error(t.message ?: "Backup failed")
            }
        }
    }

    fun importBackup(from: InputStream) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working
            try {
                val json = withContext(Dispatchers.IO) {
                    from.bufferedReader().use { it.readText() }
                }
                val result = withContext(Dispatchers.IO) { backupRepository.importJson(json) }
                _backupState.value = BackupUiState.Imported(result)
            } catch (t: Throwable) {
                _backupState.value = BackupUiState.Error(t.message ?: "Restore failed")
            }
        }
    }

    fun clearBackupState() {
        _backupState.value = BackupUiState.Idle
    }
}
