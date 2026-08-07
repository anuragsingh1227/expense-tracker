package com.expensetracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppSettings
import com.expensetracker.data.backup.BackupImportResult
import com.expensetracker.data.backup.BackupRepository
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.sms.SmsInboxScanner
import com.expensetracker.sms.SmsScanResult
import com.expensetracker.sms.parser.RawSms
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
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Working : BackupUiState()
    data class Exported(val bytes: Int) : BackupUiState()
    data class Imported(val result: BackupImportResult) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

data class SmsTextImportResult(
    val examined: Int,
    val inserted: Int,
    val skipped: Int,
    val rejected: Int,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val scanner: SmsInboxScanner,
    private val backupRepository: BackupRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsDao: SettingsDao,
    private val parser: SmsParser,
    private val clock: Clock,
) : ViewModel() {

    private val _lastScan = MutableStateFlow<SmsScanResult?>(null)
    val lastScan: StateFlow<SmsScanResult?> = _lastScan.asStateFlow()

    /** null while loading; empty string once loaded if no name is set yet. */
    private val _ownerName = MutableStateFlow<String?>(null)
    val ownerName: StateFlow<String?> = _ownerName.asStateFlow()

    init {
        viewModelScope.launch {
            _ownerName.value = settingsDao.get(AppSettings.OWNER_NAME)?.trim().orEmpty()
        }
    }

    fun setOwnerName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            settingsDao.put(SettingsEntity(AppSettings.OWNER_NAME, trimmed))
            _ownerName.value = trimmed
            withContext(Dispatchers.IO) { transactionRepository.reconcileSelfTransfers() }
        }
    }

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _cleanupRemoved = MutableStateFlow<Int?>(null)
    val cleanupRemoved: StateFlow<Int?> = _cleanupRemoved.asStateFlow()

    private val _textImportResult = MutableStateFlow<SmsTextImportResult?>(null)
    val textImportResult: StateFlow<SmsTextImportResult?> = _textImportResult.asStateFlow()

    private val _pendingSharedText = MutableStateFlow<String?>(null)
    val pendingSharedText: StateFlow<String?> = _pendingSharedText.asStateFlow()

    fun offerSharedText(text: String?) {
        val trimmed = text?.trim().orEmpty()
        _pendingSharedText.value = trimmed.takeIf { it.isNotEmpty() }
    }

    fun consumePendingSharedText(): String? {
        val value = _pendingSharedText.value
        _pendingSharedText.value = null
        return value
    }

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

    /**
     * Play-safe import: user pastes or shares SMS bodies. One message per blank line.
     */
    fun importSmsTexts(raw: String, senderHint: String? = null) {
        viewModelScope.launch {
            val chunks = raw
                .split(Regex("\\n\\s*\\n"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty {
                    listOfNotNull(raw.trim().takeIf { it.isNotEmpty() })
                }

            var inserted = 0
            var skipped = 0
            var rejected = 0
            val now = Instant.now(clock)

            withContext(Dispatchers.IO) {
                chunks.forEachIndexed { index, body ->
                    val tx = parser.parse(
                        RawSms(
                            sender = senderHint,
                            body = body,
                            timestamp = now.minusSeconds(index.toLong()),
                        ),
                    )
                    if (tx == null) {
                        rejected++
                        return@forEachIndexed
                    }
                    if (transactionRepository.insertIfNew(tx)) inserted++ else skipped++
                }
                transactionRepository.reconcileSelfTransfers()
            }
            _textImportResult.value = SmsTextImportResult(
                examined = chunks.size,
                inserted = inserted,
                skipped = skipped,
                rejected = rejected,
            )
        }
    }

    fun clearTextImportResult() {
        _textImportResult.value = null
    }

    fun purgeSpam() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) {
                val spam = transactionRepository.purgeNonTransactional(parser)
                val self = transactionRepository.reconcileSelfTransfers()
                spam + self
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
