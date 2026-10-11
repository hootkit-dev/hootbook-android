@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.features.transactions

import io.github.hootkit_dev.hootbook.utils.DateFormats

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogDismissButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogConfirmButton
import io.github.hootkit_dev.hootbook.presentation.ui.components.DialogActionsRow
import io.github.hootkit_dev.hootbook.presentation.ui.components.HootbookDialogDefaults
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import io.github.hootkit_dev.hootbook.presentation.format.toJavaDateTime
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.hootkit.core.TransactionFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import io.github.hootkit_dev.hootbook.presentation.ui.icons.ImportArrow01
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.LocalBlurEffects
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R

@OptIn(ExperimentalHazeApi::class)
@Composable
fun ExportTransactionsDialog(
    filter: TransactionFilter,
    total: UInt,
    onDismiss: () -> Unit,
    blurEffects: Boolean = LocalBlurEffects.current,
    viewModel: ExportViewModel = hiltViewModel(),
    hazeState: HazeState = remember { HazeState() },
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportState by viewModel.state.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    LaunchedEffect(filter) { viewModel.reset(); viewModel.observeFilter(filter) }
    DisposableEffect(viewModel) { onDispose { viewModel.stopPreview() } }

    Dialog(onDismissRequest = {
        if (exportState !is ExportState.Exporting) {
            onDismiss()
        }
    }) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = HootbookDialogDefaults.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                val icon = when (exportState) {
                    is ExportState.Ready -> Iconax.ImportArrow01
                    is ExportState.Exporting -> Icons.Rounded.HourglassTop
                    is ExportState.Success -> Icons.Rounded.CheckCircle
                    is ExportState.Error -> Icons.Rounded.Error
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = when (exportState) {
                        is ExportState.Success -> MaterialTheme.colorScheme.primary
                        is ExportState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = when (exportState) {
                        is ExportState.Ready -> stringResource(R.string.export_transactions)
                        is ExportState.Exporting -> stringResource(R.string.exporting)
                        is ExportState.Success -> stringResource(R.string.export_complete)
                        is ExportState.Error -> stringResource(R.string.export_failed)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Content based on state
                when (val state = exportState) {
                    is ExportState.Ready -> {
                        Text(
                            text = stringResource(R.string.export_transactions_desc, total.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Summary info
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Total transactions row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.total_transactions_export),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = total.toInt().toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (preview.oldest != null && preview.newest != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.date_range), style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val formatter = io.github.hootkit_dev.hootbook.utils.DateFormats.fullDateFormatter()
                                        Text("${preview.oldest!!.dateTime.toJavaDateTime().format(formatter)} - ${preview.newest!!.dateTime.toJavaDateTime().format(formatter)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }


                            }
                        }
                    }

                    is ExportState.Exporting -> {
                        LinearWavyProgressIndicator(
                            progress = { if (state.total == 0u) 0f else state.written.toFloat() / state.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (state.total > 0u) "${state.written} / ${state.total}" else stringResource(R.string.exporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is ExportState.Success -> {
                        Text(
                            text = stringResource(R.string.export_success_msg_format, state.transactionCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // File info
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = state.fileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.size_format, formatFileSize(state.fileSizeBytes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    is ExportState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                DialogActionsRow {
                    when (val state = exportState) {
                        is ExportState.Ready -> {
                            DialogDismissButton(stringResource(R.string.cancel), onDismiss)
                            DialogConfirmButton(
                                text = stringResource(R.string.export),
                                enabled = total > 0u,
                                onClick = {
                                    viewModel.exportTransactions(filter)
                                }
                            )
                        }

                        is ExportState.Exporting -> {
                            // No buttons during export
                        }

                        is ExportState.Success -> {
                            DialogDismissButton(stringResource(R.string.done), onDismiss)
                            DialogConfirmButton(
                                text = stringResource(R.string.share),
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, state.uri)
                                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.csv_export_subject))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_csv)))
                                }
                            )
                        }

                        is ExportState.Error -> {
                            DialogDismissButton(stringResource(R.string.close), onDismiss)
                            DialogConfirmButton(
                                text = stringResource(R.string.retry),
                                onClick = { viewModel.reset() }
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class ExportState {
    object Ready : ExportState()
    data class Exporting(val written: UInt = 0u, val total: UInt = 0u) : ExportState()
    data class Success(
        val uri: Uri,
        val fileName: String,
        val transactionCount: Int,
        val fileSizeBytes: Long
    ) : ExportState()
    data class Error(val message: String) : ExportState()
}

private fun formatFileSize(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
    }
}
