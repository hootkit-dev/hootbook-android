package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy

import io.github.hootkit_dev.hootbook.data.backup.BackupConfiguration
import java.io.File

data class DataPrivacyUiState(
    val importExportMessage: String? = null,
    val exportedBackupFile: File? = null,
    val backupConfiguration: BackupConfiguration = BackupConfiguration(),
    val hasNewAccountsCreated: Boolean = false
)
