@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.hootkit_dev.hootbook.presentation.ui.components

import androidx.compose.material3.ripple
import androidx.compose.material3.ButtonDefaults
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.hootkit_dev.hootbook.data.service.AttachmentService
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.hootkit_dev.hootbook.R
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Iconax
import androidx.compose.material.icons.rounded.Link
import androidx.compose.ui.text.style.TextAlign
import io.github.hootkit_dev.hootbook.presentation.ui.icons.Paperclip2
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Dimensions
import io.github.hootkit_dev.hootbook.presentation.ui.theme.Spacing

/**
 * A composable for picking and displaying attachments.
 * 
 * @param attachments List of attachment relative paths
 * @param attachmentService The AttachmentService for file operations
 * @param onAddAttachment Callback when a new attachment is added (receives relative path)
 * @param onRemoveAttachment Callback when an attachment is removed
 * @param onAttachmentClick Callback when an attachment is clicked for viewing
 * @param modifier Modifier for the composable
 * @param isEditable Whether attachments can be added/removed
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AttachmentSection(
    attachments: List<String>,
    attachmentService: AttachmentService,
    onAddAttachment: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAttachmentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isEditable: Boolean = true
) {
    val context = LocalContext.current
    
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            // Save file to internal storage (use 0L as placeholder, will be updated on save)
            attachmentService.saveAttachment(it, 0L)?.let { path ->
                onAddAttachment(path)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Header with Add button
        ListItem(
            headline = {
                Text(
                    stringResource(R.string.attachments),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            supporting = if (attachments.isNotEmpty()) {
                {
                    Text(
                        pluralStringResource(R.plurals.attachments_count, attachments.size, attachments.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else null,
            leading = {
                Icon(
                    Iconax.Paperclip2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailing = if (isEditable) {
                {
                    FilledTonalIconButton(
                        onClick = {
                            filePicker.launch(arrayOf(
                                "image/*",
                                "application/pdf",
                                "text/csv",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            ))
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add_attachment_cd)
                        )
                    }
                }
            } else null,
            listColor = MaterialTheme.colorScheme.surfaceContainerLow,
            padding = PaddingValues(0.dp),
            shape = listSingleItemShape,
        )

        // Attachment previews
        if (attachments.isNotEmpty()) {
            val count = attachments.size
            if (count <= 3) {
                // Spread layout for few items
                val itemHeight = if (count == 1) 200.dp else 140.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    attachments.forEach { attachment ->
                        AttachmentPreviewItem(
                            attachmentPath = attachment,
                            attachmentService = attachmentService,
                            onClick = { onAttachmentClick(attachment) },
                            onRemove = if (isEditable) {
                                { onRemoveAttachment(attachment) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                // Scrollable layout for many items
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.md)
                ) {

                    items(attachments) { attachment ->
                        AttachmentPreviewItem(
                            attachmentPath = attachment,
                            attachmentService = attachmentService,
                            onClick = { onAttachmentClick(attachment) },
                            onRemove = if (isEditable) {
                                { onRemoveAttachment(attachment) }
                            } else null,
                            modifier = Modifier.size(100.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewItem(
    attachmentPath: String,
    attachmentService: AttachmentService,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUrl = attachmentService.isUrl(attachmentPath)
    val isImage = !isUrl && attachmentService.isImage(attachmentPath)
    val fileName = if (isUrl) stringResource(R.string.attachment_link) else attachmentPath.substringAfterLast('/')
    val fileUri = if (isUrl) null else attachmentService.getAttachmentUri(attachmentPath)
    val isFileExists = isUrl || fileUri != null

    Card(
        modifier = modifier
            .clickable(
                enabled = true,
                onClick = onClick,
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(Dimensions.Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isFileExists) MaterialTheme.colorScheme.surfaceContainerHigh 
                           else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isImage && isFileExists) {
                // Image preview
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(fileUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Document icon or Link icon or Error
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.sm),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isUrl) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.view_on_drive),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    } else if (isFileExists) {
                        Icon(
                            imageVector = when {
                                attachmentPath.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                                attachmentPath.endsWith(".csv") || 
                                attachmentPath.endsWith(".xls") ||
                                attachmentPath.endsWith(".xlsx") -> Icons.Default.TableChart
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fileName.takeLast(15),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        // Error State
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = stringResource(R.string.file_missing),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.missing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Remove button (always enabled to allow cleanup)
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.remove_cd),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
