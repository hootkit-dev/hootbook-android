package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.cloudbackup

import android.content.Intent
import io.github.hootkit_dev.hootbook.data.cloud.CloudFileInfo
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderConfig
import io.github.hootkit_dev.hootbook.data.cloud.CloudProviderType
import io.github.hootkit_dev.hootbook.data.cloud.SyncStatus

data class BackupSyncState(
    val activeProviderType: CloudProviderType = CloudProviderType.LOCAL_ONLY,
    val webDavConfig: CloudProviderConfig.WebDavConfig = CloudProviderConfig.WebDavConfig(),
    val googleDriveConfig: CloudProviderConfig.GoogleDriveConfig = CloudProviderConfig.GoogleDriveConfig(),
    val isGoogleDriveSignedIn: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val retentionLimit: Int = 10,
    val isE2eEnabled: Boolean = false,
    val e2ePassphrase: String = "",
    val lastBackupTime: Long = 0L,
    val remoteSnapshots: List<CloudFileInfo> = emptyList(),
    val isLoadingSnapshots: Boolean = false,
    val connectionTestResult: String? = null,
    val isTestingConnection: Boolean = false,
    val recoverableAuthIntent: Intent? = null,
    val showRestorePassphraseDialog: Boolean = false,
    val restoreTargetFile: CloudFileInfo? = null
)
