package io.github.hootkit_dev.hootbook.data.cloud.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "cashiro_cloud_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("CloudCredentialStore", "EncryptedSharedPreferences not available, using fallback", e)
            context.getSharedPreferences("cashiro_cloud_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _webDavConfigFlow = MutableStateFlow(getWebDavConfig())
    val webDavConfigFlow: StateFlow<CloudProviderConfig.WebDavConfig> = _webDavConfigFlow.asStateFlow()

    private val _googleDriveConfigFlow = MutableStateFlow(getGoogleDriveConfig())
    val googleDriveConfigFlow: StateFlow<CloudProviderConfig.GoogleDriveConfig> = _googleDriveConfigFlow.asStateFlow()

    private val _activeProviderTypeFlow = MutableStateFlow(getActiveProviderType())
    val activeProviderTypeFlow: StateFlow<CloudProviderType> = _activeProviderTypeFlow.asStateFlow()

    private val _retentionFlow = MutableStateFlow(getRetentionLimit())
    val retentionFlow: StateFlow<Int> = _retentionFlow.asStateFlow()

    private val _e2eEnabledFlow = MutableStateFlow(isE2eEncryptionEnabled())
    val e2eEnabledFlow: StateFlow<Boolean> = _e2eEnabledFlow.asStateFlow()

    fun getWebDavConfig(): CloudProviderConfig.WebDavConfig {
        return CloudProviderConfig.WebDavConfig(
            url = prefs.getString(KEY_WEBDAV_URL, "") ?: "",
            username = prefs.getString(KEY_WEBDAV_USERNAME, "") ?: "",
            passwordOrToken = prefs.getString(KEY_WEBDAV_PASSWORD, "") ?: "",
            isEnabled = prefs.getBoolean(KEY_WEBDAV_ENABLED, false)
        )
    }

    fun saveWebDavConfig(config: CloudProviderConfig.WebDavConfig) {
        prefs.edit().apply {
            putString(KEY_WEBDAV_URL, config.url.trim())
            putString(KEY_WEBDAV_USERNAME, config.username.trim())
            putString(KEY_WEBDAV_PASSWORD, config.passwordOrToken)
            putBoolean(KEY_WEBDAV_ENABLED, config.isEnabled)
            apply()
        }
        _webDavConfigFlow.value = config
    }

    fun getGoogleDriveConfig(): CloudProviderConfig.GoogleDriveConfig {
        return CloudProviderConfig.GoogleDriveConfig(
            accountEmail = prefs.getString(KEY_GDRIVE_EMAIL, "") ?: "",
            accessToken = prefs.getString(KEY_GDRIVE_TOKEN, "") ?: "",
            isEnabled = prefs.getBoolean(KEY_GDRIVE_ENABLED, false)
        )
    }

    fun saveGoogleDriveConfig(config: CloudProviderConfig.GoogleDriveConfig) {
        prefs.edit().apply {
            putString(KEY_GDRIVE_EMAIL, config.accountEmail.trim())
            putString(KEY_GDRIVE_TOKEN, config.accessToken)
            putBoolean(KEY_GDRIVE_ENABLED, config.isEnabled)
            apply()
        }
        _googleDriveConfigFlow.value = config
    }

    fun getActiveProviderType(): CloudProviderType {
        val typeStr = prefs.getString(KEY_ACTIVE_PROVIDER, CloudProviderType.LOCAL_ONLY.name)
            ?: CloudProviderType.LOCAL_ONLY.name
        return try {
            CloudProviderType.valueOf(typeStr)
        } catch (e: Exception) {
            CloudProviderType.LOCAL_ONLY
        }
    }

    fun setActiveProviderType(type: CloudProviderType) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, type.name).apply()
        _activeProviderTypeFlow.value = type
    }

    fun getActiveConfig(): CloudProviderConfig? {
        return when (getActiveProviderType()) {
            CloudProviderType.WEBDAV -> getWebDavConfig().takeIf { it.isConfigured }
            CloudProviderType.GOOGLE_DRIVE -> getGoogleDriveConfig().takeIf { it.isConfigured }
            CloudProviderType.LOCAL_ONLY -> null
        }
    }

    fun getRetentionLimit(): Int {
        return prefs.getInt(KEY_BACKUP_RETENTION, 10)
    }

    fun setRetentionLimit(limit: Int) {
        prefs.edit().putInt(KEY_BACKUP_RETENTION, limit).apply()
        _retentionFlow.value = limit
    }

    fun isE2eEncryptionEnabled(): Boolean {
        return prefs.getBoolean(KEY_E2E_ENABLED, false)
    }

    fun setE2eEncryptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_E2E_ENABLED, enabled).apply()
        _e2eEnabledFlow.value = enabled
    }

    fun getE2ePassphrase(): String {
        return prefs.getString(KEY_E2E_PASSPHRASE, "") ?: ""
    }

    fun setE2ePassphrase(passphrase: String) {
        prefs.edit().putString(KEY_E2E_PASSPHRASE, passphrase).apply()
    }

    fun getLastBackupTimestamp(): Long {
        return prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
    }

    fun setLastBackupTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp).apply()
    }

    /**
     * Drops what the removed automatic schedule and Drive/WebDAV device sync kept here (replaced
     * by manual backups and Firebase sync). Safe to call on every start.
     */
    fun forgetRemovedSyncSettings() {
        val stale = prefs.all.keys.filter {
            it == KEY_BACKUP_SCHEDULE || it == KEY_DEVICE_ID || it == KEY_LAST_SYNC_TIMESTAMP || it.startsWith(KEY_PEER_SYNC_PREFIX)
        }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    companion object {
        private const val KEY_WEBDAV_URL = "webdav_url"
        private const val KEY_WEBDAV_USERNAME = "webdav_username"
        private const val KEY_WEBDAV_PASSWORD = "webdav_password"
        private const val KEY_WEBDAV_ENABLED = "webdav_enabled"

        private const val KEY_GDRIVE_EMAIL = "gdrive_email"
        private const val KEY_GDRIVE_TOKEN = "gdrive_token"
        private const val KEY_GDRIVE_ENABLED = "gdrive_enabled"

        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        // Removed settings, only for forgetRemovedSyncSettings
        private const val KEY_BACKUP_SCHEDULE = "backup_schedule"
        private const val KEY_BACKUP_RETENTION = "backup_retention"

        private const val KEY_E2E_ENABLED = "e2e_enabled"
        private const val KEY_E2E_PASSPHRASE = "e2e_passphrase"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "last_backup_time"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_time"
        private const val KEY_PEER_SYNC_PREFIX = "peer_sync_"
    }
}
