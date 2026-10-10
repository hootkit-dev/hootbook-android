package io.github.hootkit_dev.hootbook.presentation.ui.features.settings.dataprivacy

import io.github.hootkit_dev.hootbook.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hootkit_dev.hootbook.data.backup.BackupConfiguration
import io.github.hootkit_dev.hootbook.data.backup.BackupExporter
import io.github.hootkit_dev.hootbook.data.backup.BackupImporter
import io.github.hootkit_dev.hootbook.data.backup.CashewImporter
import io.github.hootkit_dev.hootbook.data.backup.ExportResult
import io.github.hootkit_dev.hootbook.data.backup.ImportResult
import io.github.hootkit_dev.hootbook.data.backup.ImportStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DataPrivacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupExporter: BackupExporter,
    private val backupImporter: BackupImporter,
    private val cashewImporter: CashewImporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataPrivacyUiState())
    val uiState: StateFlow<DataPrivacyUiState> = _uiState.asStateFlow()

    fun exportBackup(config: BackupConfiguration) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_backup_creating)) }
                when (val result = backupExporter.exportBackup(config)) {
                    is ExportResult.Success -> {
                        _uiState.update { it.copy(
                            exportedBackupFile = result.file,
                            importExportMessage = context.getString(R.string.msg_backup_created)
                        ) }
                    }
                    is ExportResult.Error -> {
                        _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_export_failed, result.message)) }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_export_failed, e.message.orEmpty())) }
            }
        }
    }

    fun saveBackupToFile(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value.exportedBackupFile?.let { file ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        // Saved where the user chose: no plain copy stays in the cache
                        file.delete()
                    }
                    _uiState.update { it.copy(
                        importExportMessage = context.getString(R.string.msg_backup_saved),
                        exportedBackupFile = null
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_backup_save_failed, e.message.orEmpty())) }
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_importing)) }
                when (val result = backupImporter.importBackup(uri, ImportStrategy.MERGE)) {
                    is ImportResult.Success -> {
                        _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_import_done, result.importedTransactions, result.importedCategories)) }
                    }
                    is ImportResult.Error -> {
                        _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_import_failed, result.message)) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_import_failed, e.message.orEmpty())) }
            }
        }
    }

    fun importCashew(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_importing)) }
                when (val result = cashewImporter.importCashew(uri)) {
                    is ImportResult.Success -> {
                        _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_cashew_import_done, result.importedTransactions, result.importedCategories, result.importedAttachments, result.failedAttachments)) }
                    }
                    is ImportResult.Error -> {
                        _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_import_failed, result.message)) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(importExportMessage = context.getString(R.string.msg_import_failed, e.message.orEmpty())) }
            }
        }
    }

    fun shareBackup() {
        _uiState.value.exportedBackupFile?.let { shareBackupFile(it) }
    }

    private fun shareBackupFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_file_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_backup)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e("DataPrivacyViewModel", "Error sharing backup file", e)
        }
    }

    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null, hasNewAccountsCreated = false) }
    }

    fun clearExportedFile() {
        _uiState.update { it.copy(exportedBackupFile = null) }
    }
}
