package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.cloudbackup

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.SyncStatus
import io.github.hootkit_dev.hootbook.data.cloud.engine.CloudBackupManager
import io.github.hootkit_dev.hootbook.data.cloud.providers.GoogleDriveStorageProvider
import io.github.hootkit_dev.hootbook.data.cloud.providers.WebDavStorageProvider
import io.github.hootkit_dev.hootbook.data.cloud.security.CloudCredentialStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudBackupManager: CloudBackupManager,
    private val cloudCredentialStore: CloudCredentialStore,
    private val webDavProvider: WebDavStorageProvider,
    private val googleDriveProvider: GoogleDriveStorageProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BackupSyncState(
            activeProviderType = cloudCredentialStore.getActiveProviderType(),
            webDavConfig = cloudCredentialStore.getWebDavConfig(),
            googleDriveConfig = cloudCredentialStore.getGoogleDriveConfig(),
            retentionLimit = cloudCredentialStore.getRetentionLimit(),
            isE2eEnabled = cloudCredentialStore.isE2eEncryptionEnabled(),
            e2ePassphrase = cloudCredentialStore.getE2ePassphrase(),
            lastBackupTime = cloudCredentialStore.getLastBackupTimestamp()
        )
    )
    val uiState: StateFlow<BackupSyncState> = _uiState.asStateFlow()

    init {
        // Log the SHA-1 fingerprint for debugging Google Sign-In Error 10
        try {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                android.content.pm.PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageInfo(context.packageName, flags)
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            
            signatures?.forEach { signature ->
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val digest = md.digest(signature.toByteArray())
                val builder = StringBuilder()
                for (b in digest) builder.append(String.format("%02X:", b))
                Log.d("AppFingerprint", "CURRENT SHA-1: " + builder.toString().removeSuffix(":"))
            }
        } catch (e: Exception) {
            Log.e("AppFingerprint", "Error getting fingerprint", e)
        }

        val gConfig = cloudCredentialStore.getGoogleDriveConfig()
        _uiState.update { it.copy(
            isGoogleDriveSignedIn = gConfig.isConfigured && gConfig.accountEmail.isNotBlank()
        ) }
        loadRemoteSnapshots()
    }

    fun setActiveProviderType(type: CloudProviderType) {
        cloudCredentialStore.setActiveProviderType(type)
        _uiState.update { it.copy(activeProviderType = type, connectionTestResult = null) }
        if (type != CloudProviderType.LOCAL_ONLY) {
            loadRemoteSnapshots()
        }
    }

    fun updateWebDavConfig(url: String, username: String, passwordOrToken: String, isEnabled: Boolean) {
        val config = CloudProviderConfig.WebDavConfig(
            url = url.trim(),
            username = username.trim(),
            passwordOrToken = passwordOrToken,
            isEnabled = isEnabled
        )
        cloudCredentialStore.saveWebDavConfig(config)
        _uiState.update { it.copy(webDavConfig = config) }
    }

    fun updateGoogleDriveConfig(email: String, accessToken: String, isEnabled: Boolean) {
        val config = CloudProviderConfig.GoogleDriveConfig(
            accountEmail = email.trim(),
            accessToken = accessToken,
            isEnabled = isEnabled
        )
        cloudCredentialStore.saveGoogleDriveConfig(config)
        _uiState.update { it.copy(googleDriveConfig = config) }
    }

    fun handleGoogleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            _uiState.update { it.copy(connectionTestResult = context.getString(R.string.google_signin_failed_null)) }
            return
        }
        val email = account.email ?: ""
        if (email.isBlank()) {
            _uiState.update { it.copy(connectionTestResult = context.getString(R.string.google_signin_failed_no_email)) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            try {
                val token = withContext(Dispatchers.IO) {
                    // Use a more robust way to get the token, ensuring we handle the scope string correctly
                    GoogleAuthUtil.getToken(
                        context,
                        email,
                        "oauth2:https://www.googleapis.com/auth/drive.appdata"
                    )
                }
                
                if (token == null) {
                    throw Exception(context.getString(R.string.err_token_null))
                }

                val config = CloudProviderConfig.GoogleDriveConfig(
                    accountEmail = email,
                    accessToken = token,
                    isEnabled = true
                )
                cloudCredentialStore.saveGoogleDriveConfig(config)
                _uiState.update { it.copy(
                    googleDriveConfig = config,
                    isGoogleDriveSignedIn = true,
                    isTestingConnection = false,
                    connectionTestResult = context.getString(R.string.signed_in_as_format, email)
                ) }
                loadRemoteSnapshots()
            } catch (e: UserRecoverableAuthException) {
                Log.i("CloudBackupVM", "Triggering user recovery intent for consent")
                _uiState.update { it.copy(
                    isTestingConnection = false,
                    recoverableAuthIntent = e.intent
                ) }
            } catch (e: Exception) {
                Log.e("CloudBackupVM", "Google sign-in token acquisition failed: ${e.javaClass.name}", e)

                val errorMsg = when {
                    e.message?.contains("NETWORK_ERROR") == true -> context.getString(R.string.err_network)
                    e.message?.contains("BAD_AUTHENTICATION") == true -> context.getString(R.string.err_auth_failed_retry)
                    else -> e.message ?: e.javaClass.simpleName
                }

                _uiState.update { it.copy(
                    isTestingConnection = false,
                    connectionTestResult = context.getString(R.string.auth_failed_format, errorMsg)
                ) }
            }
        }
    }

    fun clearRecoverableAuthIntent() {
        _uiState.update { it.copy(recoverableAuthIntent = null) }
    }

    fun onGoogleDriveSignOut() {
        val config = CloudProviderConfig.GoogleDriveConfig(
            accountEmail = "",
            accessToken = "",
            isEnabled = false
        )
        cloudCredentialStore.saveGoogleDriveConfig(config)
        _uiState.update { it.copy(
            googleDriveConfig = config,
            isGoogleDriveSignedIn = false,
            remoteSnapshots = emptyList()
        ) }
    }

    fun testConnection(type: CloudProviderType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
            try {
                val result = when (type) {
                    CloudProviderType.WEBDAV -> webDavProvider.testConnection(cloudCredentialStore.getWebDavConfig())
                    CloudProviderType.GOOGLE_DRIVE -> googleDriveProvider.testConnection(cloudCredentialStore.getGoogleDriveConfig())
                    CloudProviderType.LOCAL_ONLY -> Result.success(true)
                }
                if (result.isSuccess) {
                    _uiState.update { it.copy(isTestingConnection = false, connectionTestResult = context.getString(R.string.connection_verified)) }
                } else {
                    val msg = result.exceptionOrNull()?.message ?: context.getString(R.string.verification_failed)
                    _uiState.update { it.copy(isTestingConnection = false, connectionTestResult = context.getString(R.string.connection_failed_format, msg)) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTestingConnection = false, connectionTestResult = context.getString(R.string.error_testing_connection_format, e.message ?: "")) }
            }
        }
    }

    fun clearConnectionTestResult() {
        _uiState.update { it.copy(connectionTestResult = null) }
    }

    fun performManualBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.BackingUp(0, 100, context.getString(R.string.starting_cloud_backup))) }
            val result = cloudBackupManager.performBackup { progress ->
                _uiState.update { it.copy(syncStatus = SyncStatus.BackingUp(progress, 100, context.getString(R.string.uploading_to_cloud_format, progress))) }
            }
            if (result.isSuccess) {
                val lastTime = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        syncStatus = SyncStatus.Success(context.getString(R.string.backup_uploaded_success)),
                        lastBackupTime = lastTime
                    )
                }
                loadRemoteSnapshots()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.cloud_backup_failed)
                _uiState.update { it.copy(syncStatus = SyncStatus.Error(errorMsg)) }
            }
        }
    }

    fun restoreSnapshot(fileInfo: CloudFileInfo) {
        val isEncrypted = fileInfo.name.endsWith(".enc")
        val storedPassphrase = cloudCredentialStore.getE2ePassphrase()
        if (isEncrypted && storedPassphrase.isBlank()) {
            _uiState.update { it.copy(
                showRestorePassphraseDialog = true,
                restoreTargetFile = fileInfo
            ) }
            return
        }
        executeRestore(fileInfo)
    }

    fun retryRestoreWithPassphrase(passphrase: String) {
        val fileInfo = _uiState.value.restoreTargetFile ?: return
        cloudCredentialStore.setE2ePassphrase(passphrase)
        _uiState.update { it.copy(e2ePassphrase = passphrase, isE2eEnabled = true) }
        dismissRestorePassphraseDialog()
        executeRestore(fileInfo)
    }

    fun dismissRestorePassphraseDialog() {
        _uiState.update { it.copy(showRestorePassphraseDialog = false, restoreTargetFile = null) }
    }

    private fun executeRestore(fileInfo: CloudFileInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = SyncStatus.Restoring(0, 100, context.getString(R.string.downloading_snapshot))) }
            val result = cloudBackupManager.restoreBackup(fileInfo) { progress ->
                _uiState.update { it.copy(syncStatus = SyncStatus.Restoring(progress, 100, context.getString(R.string.restoring_data_format, progress))) }
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(syncStatus = SyncStatus.Success(context.getString(R.string.snapshot_restored_success))) }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.failed_restore_snapshot)
                _uiState.update { it.copy(syncStatus = SyncStatus.Error(errorMsg)) }
            }
        }
    }

    fun deleteSnapshot(fileInfo: CloudFileInfo) {
        viewModelScope.launch {
            val result = cloudBackupManager.deleteBackup(fileInfo)
            if (result.isSuccess) {
                loadRemoteSnapshots()
            } else {
                Log.e("CloudBackupVM", "Failed to delete remote snapshot ${fileInfo.name}")
            }
        }
    }

    fun setRetentionLimit(limit: Int) {
        cloudCredentialStore.setRetentionLimit(limit)
        _uiState.update { it.copy(retentionLimit = limit) }
    }

    fun setE2eEncryption(enabled: Boolean, passphrase: String) {
        cloudCredentialStore.setE2eEncryptionEnabled(enabled)
        if (passphrase.isNotBlank()) {
            cloudCredentialStore.setE2ePassphrase(passphrase)
        }
        _uiState.update { it.copy(isE2eEnabled = enabled, e2ePassphrase = passphrase) }
    }

    fun loadRemoteSnapshots() {
        viewModelScope.launch {
            if (_uiState.value.activeProviderType == CloudProviderType.LOCAL_ONLY) {
                _uiState.update { it.copy(remoteSnapshots = emptyList(), isLoadingSnapshots = false) }
                return@launch
            }
            _uiState.update { it.copy(isLoadingSnapshots = true) }
            val result = cloudBackupManager.listRemoteBackups()
            if (result.isSuccess) {
                _uiState.update { it.copy(remoteSnapshots = result.getOrNull() ?: emptyList(), isLoadingSnapshots = false) }
            } else {
                val error = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                _uiState.update { it.copy(
                    remoteSnapshots = emptyList(),
                    isLoadingSnapshots = false,
                    connectionTestResult = context.getString(R.string.failed_list_backups_format, error)
                ) }
            }
        }
    }

    fun dismissSyncStatus() {
        _uiState.update { it.copy(syncStatus = SyncStatus.Idle) }
    }
}
