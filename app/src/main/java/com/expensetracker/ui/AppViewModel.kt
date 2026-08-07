package com.expensetracker.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.data.AppSettings
import com.expensetracker.data.backup.BackupImportResult
import com.expensetracker.data.backup.BackupRepository
import com.expensetracker.data.db.dao.SettingsDao
import com.expensetracker.data.db.entity.SettingsEntity
import com.expensetracker.data.repository.TransactionRepository
import com.expensetracker.domain.security.AppForegroundTracker
import com.expensetracker.domain.security.BiometricAuthenticator
import com.expensetracker.domain.security.PinHasher
import com.expensetracker.sms.SmsInboxScanner
import com.expensetracker.sms.SmsScanResult
import com.expensetracker.sms.parser.RawSms
import com.expensetracker.sms.parser.SmsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class BackupErrorKind { EXPORT, IMPORT }

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Working : BackupUiState()
    data class Exported(val bytes: Int) : BackupUiState()
    data class Imported(val result: BackupImportResult) : BackupUiState()
    /** Never carries the raw exception message — the UI shows a friendly, localized string. */
    data class Error(val kind: BackupErrorKind) : BackupUiState()
}

data class SmsTextImportResult(
    val examined: Int,
    val inserted: Int,
    val skipped: Int,
    val rejected: Int,
)

/**
 * App-lock UI state. [locked] gates the whole app behind [com.expensetracker.ui.LockScreen]
 * whenever [enabled] is true — on cold start, and again whenever the process is
 * backgrounded (see [AppForegroundTracker]).
 */
data class AppLockUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    val locked: Boolean = false,
    val pinError: Boolean = false,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val scanner: SmsInboxScanner,
    private val backupRepository: BackupRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsDao: SettingsDao,
    private val parser: SmsParser,
    private val clock: Clock,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _lastScan = MutableStateFlow<SmsScanResult?>(null)
    val lastScan: StateFlow<SmsScanResult?> = _lastScan.asStateFlow()

    /** null while loading; empty string once loaded if no name is set yet. */
    private val _ownerName = MutableStateFlow<String?>(null)
    val ownerName: StateFlow<String?> = _ownerName.asStateFlow()

    private val _appLockState = MutableStateFlow(AppLockUiState())
    val appLockState: StateFlow<AppLockUiState> = _appLockState.asStateFlow()

    init {
        viewModelScope.launch {
            _ownerName.value = settingsDao.get(AppSettings.OWNER_NAME)?.trim().orEmpty()
        }
        viewModelScope.launch { loadAppLockState() }
        viewModelScope.launch {
            // drop(1): ignore the initial sentinel value emitted before any real backgrounding.
            AppForegroundTracker.backgroundedAtMillis.drop(1).collect { relockIfEnabled() }
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

    private suspend fun loadAppLockState() {
        val enabled = settingsDao.get(AppSettings.APP_LOCK_ENABLED) == "true"
        val biometricEnabled = settingsDao.get(AppSettings.APP_LOCK_BIOMETRIC_ENABLED) == "true"
        _appLockState.value = AppLockUiState(
            loading = false,
            enabled = enabled,
            biometricAvailable = BiometricAuthenticator.isAvailable(appContext),
            biometricEnabled = biometricEnabled,
            locked = enabled,
        )
    }

    private fun relockIfEnabled() {
        if (_appLockState.value.enabled) {
            _appLockState.value = _appLockState.value.copy(locked = true, pinError = false)
        }
    }

    /** First-time setup or re-enabling after it was off — no current PIN to check. */
    fun setAppLockPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val (salt, hash) = withContext(Dispatchers.Default) {
                val salt = PinHasher.generateSalt()
                salt to PinHasher.hash(pin, salt)
            }
            // Single transaction: a process death here can never pair a new
            // salt with a stale hash, or half-enable the lock.
            settingsDao.putAll(
                listOf(
                    SettingsEntity(AppSettings.APP_LOCK_PIN_SALT, salt),
                    SettingsEntity(AppSettings.APP_LOCK_PIN_HASH, hash),
                    SettingsEntity(AppSettings.APP_LOCK_ENABLED, "true"),
                ),
            )
            _appLockState.value = _appLockState.value.copy(enabled = true, locked = false, pinError = false)
            onResult(true)
        }
    }

    fun changeAppLockPin(currentPin: String, newPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!verifyStoredPin(currentPin)) {
                onResult(false)
                return@launch
            }
            val (salt, hash) = withContext(Dispatchers.Default) {
                val salt = PinHasher.generateSalt()
                salt to PinHasher.hash(newPin, salt)
            }
            settingsDao.putAll(
                listOf(
                    SettingsEntity(AppSettings.APP_LOCK_PIN_SALT, salt),
                    SettingsEntity(AppSettings.APP_LOCK_PIN_HASH, hash),
                ),
            )
            onResult(true)
        }
    }

    fun disableAppLock(currentPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!verifyStoredPin(currentPin)) {
                onResult(false)
                return@launch
            }
            settingsDao.putAll(
                listOf(
                    SettingsEntity(AppSettings.APP_LOCK_ENABLED, "false"),
                    SettingsEntity(AppSettings.APP_LOCK_BIOMETRIC_ENABLED, "false"),
                ),
            )
            _appLockState.value = _appLockState.value.copy(
                enabled = false,
                biometricEnabled = false,
                locked = false,
                pinError = false,
            )
            onResult(true)
        }
    }

    fun setAppLockBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDao.put(SettingsEntity(AppSettings.APP_LOCK_BIOMETRIC_ENABLED, enabled.toString()))
            _appLockState.value = _appLockState.value.copy(biometricEnabled = enabled)
        }
    }

    /**
     * Attempt to unlock the lock screen with a typed PIN. If the app was
     * backgrounded again while the PIN was being verified, stay locked even on
     * a correct PIN — otherwise a slow verification (PBKDF2) racing a Home-button
     * press could unlock the app into the background/recents preview.
     */
    fun submitUnlockPin(pin: String) {
        val verifyStartedAt = AppForegroundTracker.backgroundedAtMillis.value
        viewModelScope.launch {
            val ok = verifyStoredPin(pin)
            val backgroundedDuringVerify = AppForegroundTracker.backgroundedAtMillis.value != verifyStartedAt
            _appLockState.value = _appLockState.value.copy(
                locked = !ok || backgroundedDuringVerify,
                pinError = !ok,
            )
        }
    }

    fun onBiometricUnlockSucceeded() {
        _appLockState.value = _appLockState.value.copy(locked = false, pinError = false)
    }

    fun clearPinError() {
        if (_appLockState.value.pinError) {
            _appLockState.value = _appLockState.value.copy(pinError = false)
        }
    }

    private suspend fun verifyStoredPin(pin: String): Boolean {
        val salt = settingsDao.get(AppSettings.APP_LOCK_PIN_SALT) ?: return false
        val hash = settingsDao.get(AppSettings.APP_LOCK_PIN_HASH) ?: return false
        return withContext(Dispatchers.Default) { PinHasher.matches(pin, salt, hash) }
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
                _lastScan.value = withContext(Dispatchers.IO) {
                    scanner.scan(forceFullLookback = forceFullLookback)
                }
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
                _backupState.value = BackupUiState.Error(BackupErrorKind.EXPORT)
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
                _backupState.value = BackupUiState.Error(BackupErrorKind.IMPORT)
            }
        }
    }

    fun clearBackupState() {
        _backupState.value = BackupUiState.Idle
    }
}
