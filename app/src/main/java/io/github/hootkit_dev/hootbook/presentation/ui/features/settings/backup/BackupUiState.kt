package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.backup

import dev.hootkit.core.*
import io.github.hootkit_dev.hootbook.core.backup.*
import java.io.File

data class BackupUiState(
    val busy: Boolean = true,
    val source: StagedBackup? = null,
    val legacy: Boolean = false,
    val info: BackupInfo? = null,
    val legacyInfo: LegacyInfo? = null,
    val exportFile: StagedBackup? = null,
    val exportInfo: BackupInfo? = null,
    val operations: List<BackupOperation> = emptyList(),
    val result: BackupOperation? = null,
    val shareFile: File? = null,
    val message: String? = null,
    val error: String? = null,
)
